package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicMobCastingService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * A target that never dies and reports exactly what you did to it.
 *
 * <p>Three things in one, because they are the same tool: a damage meter, a configurable caster,
 * and a parry post. Everything it casts is driven from its own {@link PlayerMagicState}, the same
 * type a player carries, so a skill thrown at you by a dummy resolves through the identical handler
 * a mob would use - there is no second code path to keep in step.
 *
 * <p>It is deliberately immortal rather than merely tough. A dummy with a real health bar measures
 * how long it survives, which is a different and far less useful number than how hard you hit.
 */
public final class TrainingDummyEntity extends Mob {
    /** Health is a formality; it exists only so vanilla damage maths has something to subtract from. */
    private static final float POOL = 1_000_000F;
    /** How long a rolling window is, and how long a silence has to be before a new run starts. */
    public static final int WINDOW_TICKS = 100, IDLE_RESET_TICKS = 100;
    /** Nothing under this, or a one-tick delay would spawn a skill per tick and strangle the server. */
    public static final int MIN_DELAY = 5, MAX_DELAY = 200, DEFAULT_DELAY = 40;
    /**
     * The difficulty the dummy casts at.
     *
     * <p>{@code MagicMobCastingService.canMobUse} gates sub-skills, authority skills and created
     * fusions behind {@code AscendantTier.isAscendant}, so an ordinary mob difficulty would silently
     * drop most of the list this dummy is supposed to be able to cast from. Ten is the top tier,
     * which is what makes "every skill in the game" true rather than aspirational.
     */
    public static final int DIFFICULTY = 10;
    /**
     * The longest a forced counter window stays open when every cast is gated behind one.
     *
     * <p>Generous on purpose. A boss picks this to be hard; a practice post picks it to be
     * learnable. See {@link #qteWindow(int)} for why it is a ceiling rather than the value.
     */
    public static final int QTE_WINDOW = 25;

    private static final EntityDataAccessor<String> READOUT =
            SynchedEntityData.defineId(TrainingDummyEntity.class, EntityDataSerializers.STRING);
    /**
     * The whole configuration, so the screen can draw it without a payload of its own.
     *
     * <p>Format is {@code delay;parry;qte;skillPaths;passivePaths}, the two lists comma-separated
     * and carrying paths rather than full ids - everything here is registered under this mod's
     * namespace, so the namespace would be the same forty-odd wasted bytes on every entry.
     */
    private static final EntityDataAccessor<String> CONFIG =
            SynchedEntityData.defineId(TrainingDummyEntity.class, EntityDataSerializers.STRING);

    /** One landed hit: when, how much actually came off, and what to credit it to. */
    private record Hit(int tick, float amount, String source) {}

    private final Deque<Hit> window = new ArrayDeque<>();
    private final Map<String, Float> bySource = new LinkedHashMap<>();
    private final List<ResourceLocation> skills = new ArrayList<>();
    private final Set<ResourceLocation> passives = new LinkedHashSet<>();
    private PlayerMagicState magic = new PlayerMagicState();

    private float total, peak;
    private int hits, firstHit = -1, lastHit = -1, nextSkill, castAt;
    private int delayTicks = DEFAULT_DELAY;
    private boolean parryIncoming, alwaysQte;
    /** Client side only: the CONFIG string the mirrored fields were last built from. */
    private String parsedConfig = "";

    public TrainingDummyEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        refreshState();
    }

    public TrainingDummyEntity(Level level) {
        this(MagicalEntities.TRAINING_DUMMY.get(), level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // No armour on purpose: it would quietly eat a slice of every hit, and a meter that reports
        // less than it was given is worse than no meter at all.
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, POOL)
                .add(Attributes.MOVEMENT_SPEED, 0)
                .add(Attributes.ARMOR, 0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(READOUT, "");
        builder.define(CONFIG, "");
    }

    @Override protected void registerGoals() {}
    @Override public boolean isPushable() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity entity) {}
    @Override public boolean removeWhenFarAway(double distance) { return false; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean isAffectedByPotions() { return false; }

    /** What the client draws above it. Lines, already formatted, because only the server has the data. */
    public String readout() { return entityData.get(READOUT); }

    // ------------------------------------------------------------------ the meter

    /**
     * Every hit counts, including the ones vanilla would have swallowed.
     *
     * <p>Invulnerability frames are cleared on both sides of the call. They exist to stop a mob
     * being chain-killed, and a dummy cannot be killed - what they would actually do here is drop
     * roughly half the hits of any fast attack and report a number that is simply wrong.
     *
     * <p>The amount recorded is the health that actually came off, not the amount offered, so
     * absorption, resistance and any reduction along the way are already accounted for.
     */
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (parryIncoming) {
            parry(level, source);
            return false;
        }
        invulnerableTime = 0;
        float before = getHealth();
        boolean taken = super.hurtServer(level, source, amount);
        float applied = Math.max(0, before - getHealth());
        setHealth(getMaxHealth());
        invulnerableTime = 0;
        if (taken && applied > 0) record(applied, label(source));
        return taken;
    }

    private void record(float applied, String source) {
        int now = tickCount;
        // A long silence ends the run. Resetting when the hits stop would wipe the number just as
        // you look at it, so the reset happens when the next one lands instead.
        if (lastHit >= 0 && now - lastHit > IDLE_RESET_TICKS) reset();
        if (firstHit < 0) firstHit = now;
        lastHit = now;
        total += applied;
        hits++;
        peak = Math.max(peak, applied);
        window.addLast(new Hit(now, applied, source));
        bySource.merge(source, applied, Float::sum);
    }

    public void reset() {
        window.clear(); bySource.clear();
        total = 0; peak = 0; hits = 0; firstHit = -1; lastHit = -1;
    }

    /**
     * What to credit a hit to: the thing that hit you, the weapon that swung, or the damage type.
     *
     * <p>Most of this mod's spell entities have no lang entry - they are invisible carriers nobody
     * was ever meant to read a name off - so their description resolves to the raw key. Rather than
     * print {@code entity.magical.judgement_beam} in the breakdown, an unresolved key falls back to
     * its own last segment, spelled out.
     */
    private String label(DamageSource source) {
        var direct = source.getDirectEntity();
        if (direct != null && direct.getType() != EntityType.PLAYER) {
            var description = direct.getType().getDescription();
            String resolved = description.getString();
            String key = description.getContents() instanceof TranslatableContents contents
                    ? contents.getKey()
                    : null;
            return key != null && key.equals(resolved) ? prettify(key) : resolved;
        }
        var weapon = source.getWeaponItem();
        if (weapon != null && !weapon.isEmpty()) return weapon.getHoverName().getString();
        return prettify(source.getMsgId());
    }

    /** {@code entity.magical.judgement_beam} to {@code Judgement Beam}. */
    private static String prettify(String key) {
        String tail = key.substring(key.lastIndexOf('.') + 1).replace('_', ' ');
        if (tail.isEmpty()) return key;
        StringBuilder out = new StringBuilder(tail);
        out.setCharAt(0, Character.toUpperCase(out.charAt(0)));
        for (int i = 1; i < out.length(); i++) {
            if (out.charAt(i - 1) == ' ') out.setCharAt(i, Character.toUpperCase(out.charAt(i)));
        }
        return out.toString();
    }

    /**
     * The rolling figure: the last {@link #WINDOW_TICKS} of damage, per second.
     *
     * <p>Divided by elapsed time, not by the gap between the hits that happen to still be in the
     * window. Dividing by the gap makes two quick hits at the start of a run read as an enormous
     * rate, because the denominator is the distance between them rather than the time they took.
     */
    private float windowDps() {
        while (!window.isEmpty() && tickCount - window.peekFirst().tick() > WINDOW_TICKS) window.removeFirst();
        if (window.isEmpty() || firstHit < 0) return 0;
        float sum = 0;
        for (Hit hit : window) sum += hit.amount();
        int span = Math.max(1, Math.min(WINDOW_TICKS, tickCount - firstHit + 1));
        return sum * 20F / span;
    }

    /** Ticks from the first landed hit to the last; zero until a second hit lands. */
    private int runSpan() {
        return firstHit < 0 ? 0 : lastHit - firstHit;
    }

    /**
     * The whole run: first hit to last, which is the number people actually compare.
     *
     * <p>Undefined until the run has length. One hit is a number, not a rate, and reporting it as
     * {@code damage x 20} was the meter's worst lie - a single 500 hit read as "DPS 10000".
     */
    private float runDps() {
        int span = runSpan();
        return span <= 0 ? 0 : total * 20F / span;
    }

    private void publish() {
        if (hits == 0) {
            entityData.set(READOUT, "");
            return;
        }
        StringBuilder out = new StringBuilder();
        int span = runSpan();
        out.append(span <= 0 ? "DPS  -" : String.format("DPS %.1f", runDps()));
        out.append('\n').append(String.format("last 5s  %.1f", windowDps()));
        out.append('\n').append(String.format("total  %s  over %.1fs", number(total), span / 20F));
        out.append('\n').append(String.format("hits %d   avg %s   peak %s",
                hits, number(total / hits), number(peak)));
        bySource.entrySet().stream()
                .sorted(Map.Entry.<String, Float>comparingByValue().reversed())
                .limit(4)
                .forEach(e -> out.append('\n').append(String.format("  %s  %s (%.0f%%)",
                        e.getKey(), number(e.getValue()), e.getValue() * 100F / Math.max(1e-4F, total))));
        entityData.set(READOUT, out.toString());
    }

    private static String number(float value) {
        return value >= 10_000 ? String.format("%.1fk", value / 1000F) : String.format("%.1f", value);
    }

    // ------------------------------------------------------------------ the caster

    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            readConfig();
            return;
        }
        setHealth(getMaxHealth());
        // Cheap, and the alternative is a readout that lags the fight it is describing.
        if (tickCount % 4 == 0) publish();
        if (skills.isEmpty() || tickCount < castAt) return;
        castAt = tickCount + Math.clamp(delayTicks, MIN_DELAY, MAX_DELAY);
        Player nearest = level.getNearestPlayer(this, 48);
        if (nearest instanceof ServerPlayer target && target.isAlive()) cast(level, target);
    }

    /**
     * Cast the next skill in the rotation, skipping any the handler refuses.
     *
     * <p>One pass over the list at most, so a rotation where nothing is currently castable - every
     * one on cooldown, say - costs one loop and no more. Mana is topped up first: a dummy running
     * dry halfway through a rotation would be measuring its own mana pool rather than your defence.
     */
    private void cast(ServerLevel level, ServerPlayer target) {
        magic.refillMana();
        for (int attempt = 0; attempt < skills.size(); attempt++) {
            ResourceLocation id = skills.get(Math.floorMod(nextSkill++, skills.size()));
            MagicSkillDefinition definition = MagicContent.get(id);
            if (definition == null) continue;
            lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
            if (!alwaysQte) {
                if (!MagicMobCastingService.cast(this, magic, definition, target, DIFFICULTY)) continue;
                return;
            }
            // Gated: the window has to open before the spell exists, or answering it would cancel
            // nothing. The same gate the caster applies is asked first so an uncastable skill is
            // skipped here rather than eating a whole window and then quietly failing.
            if (!MagicMobCastingService.canMobUse(this, magic, definition, DIFFICULTY)) continue;
            level.addFreshEntity(TrainingThreatEntity.create(target, this, definition, qteWindow(delayTicks),
                    () -> MagicMobCastingService.cast(this, magic, definition, target, DIFFICULTY)));
            return;
        }
    }

    /**
     * How long a window may stay open, given how often the dummy casts.
     *
     * <p>{@code MagicCounterService} keeps exactly one live prompt per player and a new offer
     * overwrites it, so two overlapping windows are not two chances - the first becomes
     * unanswerable and its spell lands with no prompt ever shown. Ending a beat before the next
     * cast keeps every window answerable at every delay; shortening the rotation then genuinely
     * makes the setting harder instead of quietly switching it off.
     */
    public static int qteWindow(int delayTicks) {
        return Math.max(1, Math.min(QTE_WINDOW, delayTicks - 1));
    }

    /**
     * Refuse a hit and answer it, which is what the parry setting is for.
     *
     * <p>Deliberately a refusal rather than a counter window: {@code MagicCounterService} prompts a
     * {@code ServerPlayer} defender and a dummy is not one, so a dummy that "takes the QTE" would
     * have nobody to press the key. What it can do is show what a perfect parry looks like from the
     * attacking side - the clash, the sound, and nothing landing - which is the thing worth seeing.
     */
    private void parry(ServerLevel level, DamageSource source) {
        var from = source.getSourcePosition();
        MagicCounterService.spawnClash(level,
                from == null ? getEyePosition() : getEyePosition().lerp(from, 0.35), 0xE9E7E2, 0x7FEFD4);
        level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK,
                SoundSource.NEUTRAL, 0.7F, 1.35F);
        invulnerableTime = 0;
    }

    // ------------------------------------------------------------------ configuration

    @Override public net.minecraft.world.InteractionResult mobInteract(Player player,
            net.minecraft.world.InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            // Sneak-click clears the meter: the one action wanted often enough that making it cost a
            // screen open would be the wrong trade.
            if (player.isShiftKeyDown()) {
                reset();
                publish();
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            com.efkrdnz.magical.magic.menu.TrainingDummyMenu.open(serverPlayer, this);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    public List<ResourceLocation> skills() { return List.copyOf(skills); }
    public Set<ResourceLocation> passives() { return Set.copyOf(passives); }
    public int delayTicks() { return delayTicks; }
    public boolean parryIncoming() { return parryIncoming; }
    public boolean alwaysQte() { return alwaysQte; }

    public void toggleSkill(ResourceLocation id) {
        if (!skills.remove(id) && MagicContent.get(id) != null) skills.add(id);
        refreshState();
    }

    public void togglePassive(ResourceLocation id) {
        if (!passives.remove(id) && MagicPassiveContent.get(id) != null) passives.add(id);
        refreshState();
    }

    public void clearSkills() { skills.clear(); refreshState(); }
    public void setDelayTicks(int ticks) { delayTicks = Math.clamp(ticks, MIN_DELAY, MAX_DELAY); publishConfig(); }
    public void setParryIncoming(boolean value) { parryIncoming = value; publishConfig(); }
    public void setAlwaysQte(boolean value) { alwaysQte = value; publishConfig(); }

    // ------------------------------------------------------------------ configuration sync

    /** Server side: fold the configuration into the one synched string the screen reads. */
    private void publishConfig() {
        StringBuilder out = new StringBuilder();
        out.append(delayTicks).append(';')
                .append(parryIncoming ? 1 : 0).append(';')
                .append(alwaysQte ? 1 : 0).append(';');
        join(out, skills);
        out.append(';');
        join(out, passives);
        entityData.set(CONFIG, out.toString());
    }

    private static void join(StringBuilder out, Iterable<ResourceLocation> ids) {
        boolean first = true;
        for (ResourceLocation id : ids) {
            if (!first) out.append(',');
            out.append(id.getPath());
            first = false;
        }
    }

    /**
     * Client side: mirror the synched string back into the same fields the server keeps.
     *
     * <p>One set of fields and one set of accessors for both sides, so the screen asks the dummy
     * what it is configured as rather than knowing about a wire format. Re-parsed only when the
     * string actually changes, which is on configuration edits and nothing else.
     */
    private void readConfig() {
        String raw = entityData.get(CONFIG);
        if (raw.equals(parsedConfig)) return;
        parsedConfig = raw;
        skills.clear();
        passives.clear();
        String[] parts = raw.split(";", -1);
        if (parts.length < 5) return;
        delayTicks = Math.clamp(parseInt(parts[0], DEFAULT_DELAY), MIN_DELAY, MAX_DELAY);
        parryIncoming = "1".equals(parts[1]);
        alwaysQte = "1".equals(parts[2]);
        split(parts[3], skills::add);
        split(parts[4], passives::add);
    }

    private static void split(String csv, java.util.function.Consumer<ResourceLocation> out) {
        if (csv.isEmpty()) return;
        for (String path : csv.split(",")) {
            if (!path.isEmpty()) {
                out.accept(ResourceLocation.fromNamespaceAndPath(com.efkrdnz.magical.MagicalMod.MODID, path));
            }
        }
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * Rebuild the magic state from the configured lists.
     *
     * <p>Rebuilt whole rather than patched, because unlocking is one-way: there is no
     * {@code lock(id)}, so turning a skill back off in the screen could not otherwise take it away
     * again and the dummy would go on casting something the list no longer contains.
     */
    private void refreshState() {
        magic = new PlayerMagicState();
        magic.setClassPoolBonuses(100_000, 0);
        magic.unlockAll(new LinkedHashSet<>(skills));
        magic.unlockPassives(new LinkedHashSet<>(passives));
        magic.refillMana();
        publishConfig();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag skillTag = new ListTag();
        for (ResourceLocation id : skills) skillTag.add(StringTag.valueOf(id.toString()));
        tag.put("Skills", skillTag);
        ListTag passiveTag = new ListTag();
        for (ResourceLocation id : passives) passiveTag.add(StringTag.valueOf(id.toString()));
        tag.put("Passives", passiveTag);
        tag.putInt("DelayTicks", delayTicks);
        tag.putBoolean("ParryIncoming", parryIncoming);
        tag.putBoolean("AlwaysQte", alwaysQte);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        skills.clear(); passives.clear();
        for (Tag entry : tag.getList("Skills", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null && MagicContent.get(id) != null) skills.add(id);
        }
        for (Tag entry : tag.getList("Passives", Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.getAsString());
            if (id != null && MagicPassiveContent.get(id) != null) passives.add(id);
        }
        // A dummy saved before the delay existed reads back as zero, which would cast every tick.
        delayTicks = tag.contains("DelayTicks")
                ? Math.clamp(tag.getInt("DelayTicks"), MIN_DELAY, MAX_DELAY)
                : DEFAULT_DELAY;
        parryIncoming = tag.getBoolean("ParryIncoming");
        alwaysQte = tag.getBoolean("AlwaysQte");
        refreshState();
    }
}
