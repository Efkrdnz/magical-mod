package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.arcane.ArcanePlayerData;
import com.efkrdnz.magical.arcane.ArcaneSpellResolver;
import com.efkrdnz.magical.arcane.SpellPreset;
import com.efkrdnz.magical.client.ClientArcaneState;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.ClientStatusState;
import com.efkrdnz.magical.client.MagicalKeyMappings;
import com.efkrdnz.magical.client.hud.HudLayout.PoolsPlan;
import com.efkrdnz.magical.client.hud.HudLayout.Rect;
import com.efkrdnz.magical.client.hud.HudSnapshot.Chip;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.client.hud.HudSnapshot.Readout;
import com.efkrdnz.magical.client.hud.HudSnapshot.Slot;
import com.efkrdnz.magical.client.hud.HudSnapshot.Stamp;
import com.efkrdnz.magical.client.renderer.fx.FxTextures;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.DarkService;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The tick side of the HUD: owns the {@link HudSnapshot}, rebuilds it only when something it
 * depends on changed, and integrates the two pool bars one step per client tick.
 *
 * <p>What can change is enumerated: the player state packet, the cooldown clock, the statuses,
 * the key bindings, the window, the language, the options, the level, which sins are showing and
 * the sword count. Each has a version or an identity compared here. The only per-tick string work
 * is the seconds under a cooling slot, reshaped when the second changes. Nothing in the render
 * path allocates, formats or measures.
 */
public final class HudState {
    /** Sins in registration order; each has a fixed place in the readouts. */
    private static final MagicPassiveDefinition[] SINS = {
            MagicPassiveContent.SIN_PRIDE, MagicPassiveContent.SIN_GREED, MagicPassiveContent.SIN_LUST,
            MagicPassiveContent.SIN_ENVY, MagicPassiveContent.SIN_GLUTTONY, MagicPassiveContent.SIN_WRATH,
            MagicPassiveContent.SIN_SLOTH};
    /** Lust has no gauge, so it never has a readout; Greed's is folded into the vault's. */
    private static final int LUST = 2;
    private static final int GREED = 1;
    private static final int SLOTH = 6;
    /** A rested Sloth reads a step lighter, as its satellite did. */
    private static final float RESTED_LIFT = 0.35F;
    /** Each sin's stamp, by seat; the vault wears Greed's. */
    private static final int[] SIN_CELLS = new int[SINS.length];
    /** Each sin's name, by seat, for a reading written out in full: "Pride 9%". */
    private static final String[] SIN_NAMES = {
            "hud.magical.sin.pride", null, null, "hud.magical.sin.envy", "hud.magical.sin.gluttony",
            "hud.magical.sin.wrath", "hud.magical.sin.sloth"};
    /**
     * The resources' stamps. A sin wears its own ({@link HudGlyphs#sinCell}), and no reading may
     * wear another's - which is why Notice is a spiral and not the eye Envy already is.
     */
    static final StampId SWORDS_MARK = StampId.EDGE;
    static final StampId VESSEL_MARK = StampId.DROP;
    static final StampId CORRUPTION_MARK = StampId.BONE;
    static final StampId NOTICE_MARK = StampId.SPIRAL;
    static final StampId ARCANE_MARK = StampId.HEX;
    static final StampId CHARGE_MARK = StampId.CHEVRON;
    /** A sin's readout stays this long after its gauge empties, so a gauge on the edge does not flicker. */
    private static final int SIN_LINGER_TICKS = 40;
    /** Below this fraction the mana bar and its numeral go to the danger red. */
    public static final float LOW_MANA = 0.2F;
    /** Arcane stability under which a spell can misfire; the arcane readout goes to the danger red. */
    private static final int ARCANE_UNSTABLE_BELOW = 65;
    /** The arcane preset's name is cut to this, which leaves its reading well inside the narrowest line. */
    private static final int ARCANE_NAME_MAX_W = 64;
    private static final int JOIN_FADE_TICKS = 24;
    private static final int KEY_LABEL_MAX_W = 10;
    /** How long a slot's key stays lit after its cooldown runs out. */
    public static final int READY_KEY_TICKS = 10;

    private static HudOptions options = HudOptions.DEFAULTS;
    private static HudSnapshot snapshot;
    private static int builds;
    private static int buildsThisSecond;
    private static int buildsLastSecond;
    private static long debugSecond = -1L;
    private static Label debugLabel;

    private static final HudTween MANA = new HudTween();
    private static final HudTween BARRIER = new HudTween();
    private static final HudTween FADE = new HudTween();
    private static final boolean[] SIN_WAS_LIT = new boolean[SINS.length];
    private static final long[] SIN_HIDE_AT = new long[SINS.length];
    private static final long[] READY_AT = new long[MagicContent.LOADOUT_SIZE];
    private static final Label[] SLOT_SECONDS = new Label[MagicContent.LOADOUT_SIZE];
    private static final int[] SLOT_SECONDS_VALUE = new int[MagicContent.LOADOUT_SIZE];
    private static final int[] STATUS_INITIAL = new int[MagicStatus.values().length];
    private static final long[] STATUS_SEEN_AT = new long[MagicStatus.values().length];
    private static final InputConstants.Key[] KEYS = new InputConstants.Key[MagicContent.LOADOUT_SIZE];

    private static ClientLevel lastLevel;
    private static int magicVersion = -1;
    private static int cooldownVersion = -1;
    private static int statusVersion = -1;
    private static int sinVisibleMask;
    /** How many swords are with the wielder, and how many their stance fields. -1 while sheathed. */
    private static int swordsPresent = -1;
    private static int swordsWhole;
    private static int guiWidth;
    private static int guiHeight;
    private static Language language;
    private static boolean dirty = true;

    static {
        Arrays.fill(SIN_HIDE_AT, -1L);
        Arrays.fill(READY_AT, Long.MIN_VALUE);
        for (int seat = 0; seat < SINS.length; seat++) {
            SIN_CELLS[seat] = HudGlyphs.sinCell(SINS[seat].id());
        }
    }

    private HudState() {}

    public static HudSnapshot snapshot() {
        return snapshot;
    }

    public static HudOptions options() {
        return options;
    }

    /** The config listener calls this; anything else is a test. */
    public static void setOptions(HudOptions value) {
        options = value == null ? HudOptions.DEFAULTS : value;
        dirty = true;
    }

    public static void markDirty() {
        dirty = true;
    }

    public static int builds() {
        return builds;
    }

    /** The cost line for the corner, when the debug option is on; rebuilt once a second. */
    public static Label debugLabel() {
        return options.debug() ? debugLabel : null;
    }

    /** The seconds under a cooling slot, or null while it is ready. Reshaped only when the second changes. */
    public static Label slotSeconds(int slot) {
        return SLOT_SECONDS[slot];
    }

    public static HudTween mana() {
        return MANA;
    }

    public static HudTween barrier() {
        return BARRIER;
    }

    /** The whole HUD's opacity envelope: the fade-in on joining a world. */
    public static HudTween fade() {
        return FADE;
    }

    /** Game time plus the partial tick, the clock every extrapolation in the renderer reads. */
    public static float now(float partialTick) {
        return nowTicks() + partialTick;
    }

    public static long nowTicks() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }

    /** Runs at the end of every client tick, after every input handler. */
    public static void tick(Minecraft minecraft) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("magical_hud_tick");
        try {
            if (minecraft.level != lastLevel) {
                reset();
                lastLevel = minecraft.level;
                if (minecraft.level != null) {
                    FADE.snap(0.0F);
                    FADE.ramp(1.0F, options.reducedMotion() ? 6 : JOIN_FADE_TICKS);
                }
            }
            if (minecraft.player == null || minecraft.level == null) {
                snapshot = null;
                return;
            }
            long now = minecraft.level.getGameTime();
            RuleFlash.tick(now);
            noteFinishedCooldowns(now);
            tickSwords();
            tickSins(now);
            if (dirty || environmentChanged(minecraft) || stateChanged()) {
                rebuild(minecraft, now);
            }
            MANA.tick();
            BARRIER.tick();
            FADE.tick();
            tickSlotSeconds(minecraft.font, now);
            tickDebug(minecraft, now);
        } finally {
            profiler.pop();
        }
    }

    private static void reset() {
        snapshot = null;
        dirty = true;
        magicVersion = -1;
        cooldownVersion = -1;
        statusVersion = -1;
        RuleFlash.reset();
        sinVisibleMask = 0;
        Arrays.fill(SIN_WAS_LIT, false);
        Arrays.fill(SIN_HIDE_AT, -1L);
        Arrays.fill(READY_AT, Long.MIN_VALUE);
        Arrays.fill(SLOT_SECONDS, null);
        Arrays.fill(SLOT_SECONDS_VALUE, 0);
        Arrays.fill(STATUS_INITIAL, 0);
        Arrays.fill(STATUS_SEEN_AT, 0L);
        MANA.snap(0.0F);
        BARRIER.snap(0.0F);
        swordsPresent = -1;
        swordsWhole = 0;
        ClientCooldowns.reset();
        com.efkrdnz.magical.client.ClientForgeCombo.clear();
    }

    private static void noteFinishedCooldowns(long now) {
        HudSnapshot current = snapshot;
        if (current == null) {
            return;
        }
        for (Slot slot : current.slots()) {
            if (slot.skill() != null && ClientCooldowns.justFinished(slot.skill())) {
                READY_AT[slot.slot()] = now;
                dirty = true;
            }
        }
    }

    private static boolean environmentChanged(Minecraft minecraft) {
        boolean changed = false;
        int width = minecraft.getWindow().getGuiScaledWidth();
        int height = minecraft.getWindow().getGuiScaledHeight();
        if (width != guiWidth || height != guiHeight) {
            guiWidth = width;
            guiHeight = height;
            changed = true;
        }
        for (int i = 0; i < KEYS.length; i++) {
            InputConstants.Key key = MagicalKeyMappings.CAST_SLOTS[i].getKey();
            if (!key.equals(KEYS[i])) {
                KEYS[i] = key;
                changed = true;
            }
        }
        if (Language.getInstance() != language) {
            language = Language.getInstance();
            changed = true;
        }
        return changed;
    }

    private static boolean stateChanged() {
        boolean changed = false;
        if (ClientMagicState.version() != magicVersion) {
            magicVersion = ClientMagicState.version();
            changed = true;
        }
        if (ClientCooldowns.version() != cooldownVersion) {
            cooldownVersion = ClientCooldowns.version();
            changed = true;
        }
        if (ClientStatusState.version() != statusVersion) {
            statusVersion = ClientStatusState.version();
            changed = true;
        }
        return changed;
    }

    private static void tickDebug(Minecraft minecraft, long now) {
        long second = now / 20L;
        if (second == debugSecond) {
            return;
        }
        debugSecond = second;
        buildsLastSecond = buildsThisSecond;
        buildsThisSecond = 0;
        if (options.debug()) {
            debugLabel = label(minecraft.font, Component.translatable("hud.magical.debug",
                    snapshot == null ? 0 : snapshot.version(), SigilRenderer.lastQuads(), SigilRenderer.lastTextDraws(), buildsLastSecond),
                    HudPalette.TEXT_MUTED);
        } else {
            debugLabel = null;
        }
    }

    /**
     * The sword count, every tick, because it follows a formation nothing else reports.
     *
     * <p>Kept out of {@link #rebuild} on purpose: swords leave and come home with the formation's
     * own entity and not with a state packet, so a reading built only when
     * {@code ClientMagicState.version()} changes would sit still through a volley. A rebuild is
     * asked for only when the printed count changes, which is at most once a tick and usually
     * never. The present count comes from {@link com.efkrdnz.magical.client.SwordKeelClient},
     * which already has the wielder's own formation entity cached for the ride.
     */
    private static void tickSwords() {
        PlayerMagicState state = ClientMagicState.get();
        com.efkrdnz.magical.magic.sword.SwordArray array = state.swordArray();
        if (!array.drawn()) {
            if (swordsPresent >= 0) {
                swordsPresent = -1;
                swordsWhole = 0;
                dirty = true;
            }
            return;
        }
        // Through the stance and the rack, because both cap the rung: a Sword God in Guard fields
        // six, and a wielder with two swords racked fields two.
        int whole = Math.max(1, com.efkrdnz.magical.magic.sword.SwordArray.fielded(
                com.efkrdnz.magical.magic.sword.SwordService.rulesFor(state), array.stance(), array.racked()));
        int present = Math.min(whole, com.efkrdnz.magical.client.SwordKeelClient.presentSwords(whole));
        if (present != swordsPresent || whole != swordsWhole) {
            swordsPresent = present;
            swordsWhole = whole;
            dirty = true;
        }
    }

    /**
     * Which sins have a readout: every enabled sin whose gauge is above zero, plus any that emptied
     * less than {@link #SIN_LINGER_TICKS} ago. A change in that set is a rebuild, because the
     * readouts are placed in the snapshot.
     */
    private static void tickSins(long now) {
        PlayerMagicState state = ClientMagicState.get();
        int mask = 0;
        for (int seat = 0; seat < SINS.length; seat++) {
            boolean enabled = seat != LUST && state.isSinEnabled(SINS[seat].id());
            boolean lit = enabled && sinValue(state, seat) > 0;
            if (lit || !enabled) {
                SIN_HIDE_AT[seat] = -1L;
            } else if (SIN_WAS_LIT[seat]) {
                SIN_HIDE_AT[seat] = now + SIN_LINGER_TICKS;
            }
            SIN_WAS_LIT[seat] = lit;
            if (lit || (SIN_HIDE_AT[seat] >= 0L && now < SIN_HIDE_AT[seat])) {
                mask |= 1 << seat;
            }
        }
        if (mask != sinVisibleMask) {
            sinVisibleMask = mask;
            dirty = true;
        }
    }

    /** The seconds under a cooling slot: whole seconds rounded up, reshaped only when they change. */
    private static void tickSlotSeconds(Font font, long now) {
        HudSnapshot current = snapshot;
        for (int slot = 0; slot < SLOT_SECONDS.length; slot++) {
            int seconds = 0;
            if (current != null && slot < current.slots().length && current.slots()[slot].onCooldown()) {
                Slot entry = current.slots()[slot];
                long left = entry.cooldownRemaining() - (now - entry.cooldownStart());
                seconds = left > 0L ? (int) ((left + 19L) / 20L) : 0;
            }
            if (seconds != SLOT_SECONDS_VALUE[slot]) {
                SLOT_SECONDS_VALUE[slot] = seconds;
                SLOT_SECONDS[slot] = seconds > 0 ? label(font, Component.literal(seconds(seconds)), HudPalette.TEXT_PRIMARY) : null;
            }
        }
    }

    // ---- the build -------------------------------------------------------------------------------

    private static void rebuild(Minecraft minecraft, long now) {
        dirty = false;
        builds++;
        buildsThisSecond++;
        PlayerMagicState state = ClientMagicState.get();
        Font font = minecraft.font;
        HudLayout layout = HudLayout.of(guiWidth, guiHeight, options.anchor(), options.layoutScale());

        float mana = fraction(state.mana(), state.maxMana());
        float barrier = fraction(state.barrier(), state.maxBarrier());
        if (options.reducedMotion()) {
            MANA.snap(mana);
            BARRIER.snap(barrier);
        } else {
            MANA.set(mana, now);
            BARRIER.set(barrier, now);
        }
        boolean lowMana = mana < LOW_MANA;
        MagicSchool school = dominantSchool(state);
        int manaFill = lowMana ? HudPalette.DANGER : HudPalette.owner(school);
        Readout[] pools = pools(state, font, layout, lowMana);

        Slot[] slots = new Slot[MagicContent.LOADOUT_SIZE];
        boolean slotBand = false;
        for (int k = 0; k < slots.length; k++) {
            slots[k] = slot(state, k, font, layout);
            slotBand |= !slots[k].empty();
        }

        Readout[] readouts = options.compact() ? HudSnapshot.NO_READOUTS : readouts(state, school, font, layout);
        Chip[] chips = options.showStatuses() ? chips(layout, now) : HudSnapshot.NO_CHIPS;

        snapshot = new HudSnapshot(
                magicVersion ^ (cooldownVersion << 8) ^ (statusVersion << 16), now, options, layout,
                manaFill, pools, slotBand, slots, readouts, chips);
    }

    private static Slot slot(PlayerMagicState state, int k, Font font, HudLayout layout) {
        ResourceLocation skill = state.equippedSkill(k);
        MagicSkillDefinition definition = skill == null ? null : MagicContent.get(skill);
        boolean empty = definition == null;
        int cell = empty ? 0 : Math.max(0, HudGlyphs.skillCell(skill));
        int ink = empty ? HudPalette.TEXT_MUTED : HudPalette.textTint(HudPalette.cardTint(VisualProfiles.of(skill)));
        String keyName = MagicalKeyMappings.CAST_SLOTS[k].getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
        Label key = label(font, Component.literal(font.plainSubstrByWidth(keyName, KEY_LABEL_MAX_W)), HudPalette.TEXT_MUTED);
        long cooldownStart = 0L;
        int cooldownRemaining = 0;
        int cooldownTotal = 0;
        if (!empty) {
            CooldownClock.Entry entry = ClientCooldowns.entry(skill);
            if (entry != null) {
                cooldownStart = entry.startTick();
                cooldownRemaining = entry.remaining();
                cooldownTotal = entry.total();
            }
        }
        float[] rows = FxTextures.inkRows(cell);
        return new Slot(k, empty ? null : skill, cell, ink, rows[0], rows[1], empty, cooldownStart, cooldownRemaining,
                cooldownTotal, READY_AT[k], key, layout.glyph(k), layout.cell(k));
    }

    /**
     * The counts beside the bars: the mana as current over maximum, the current white (the
     * trouble red under a fifth) and "/max" muted, then the barrier in its bar's cyan while it
     * holds any. The form and every x come from the maxima, so nothing shifts as the values move.
     */
    private static Readout[] pools(PlayerMagicState state, Font font, HudLayout layout, boolean lowMana) {
        String max = "/" + numeral(state.maxMana());
        PoolsPlan plan = layout.planPools(font.width(numeral(state.maxMana())), font.width(max), font.width(numeral(state.maxBarrier())));
        String now = numeral(state.mana());
        MutableComponent mana = Component.literal(now).withColor(lowMana ? HudPalette.TROUBLE : HudPalette.TEXT_PRIMARY);
        if (plan.showsMax()) {
            mana.append(Component.literal(max).withColor(HudPalette.TEXT_MUTED));
        }
        Label manaLabel = label(font, mana, HudPalette.TEXT_PRIMARY);
        Readout manaCount = new Readout(manaLabel, new Rect("mana count", plan.slashX() - font.width(now), plan.manaY(), manaLabel.width(), HudLayout.TEXT_H), null);
        if (state.barrier() <= 0) {
            return new Readout[] {manaCount};
        }
        Label barrier = label(font, Component.literal(numeral(state.barrier())), HudPalette.BARRIER);
        return new Readout[] {manaCount,
                new Readout(barrier, new Rect("barrier count", plan.barrierRight() - barrier.width(), plan.barrierY(), barrier.width(), HudLayout.TEXT_H), null)};
    }

    /**
     * A reading before it is placed: its string written out, its string cut short, and its stamp.
     * A resource has one string; a passive's short one drops its name, "9%" for "Pride 9%".
     */
    private record Token(Label label, Label brief, Stamp stamp) {}

    /**
     * The readings, each a stamp in its owner's hue and its words: the resources first - swords,
     * vessel, corruption, notice, arcane, charge - then the passives - the vault with Greed's
     * hoard, then the lit sins in registration order - each only while it applies, flowed onto the
     * readout lines. Every reading names itself in its hue and gives its number in white, so red
     * only ever means trouble. The passives are written out in full where the whole group fits
     * and all cut to their stamp and number where it does not - never a mix on one line. Anything
     * left off is counted in a muted "+n".
     */
    private static Readout[] readouts(PlayerMagicState state, MagicSchool school, Font font, HudLayout layout) {
        List<Token> tokens = new ArrayList<>();
        if (swordsPresent >= 0) {
            tokens.add(resource(font, Component.translatable("hud.magical.readout.swords", outOf(swordsPresent, swordsWhole)),
                    MagicSchool.SWORD, SWORDS_MARK));
        }
        if (BloodService.isBloodMage(state)) {
            // An empty Vessel means the next price is paid in hearts.
            tokens.add(resource(font, Component.translatable("hud.magical.readout.vessel", value(state.bloodVessel(), state.bloodVessel() <= 0)),
                    MagicSchool.BLOOD, VESSEL_MARK));
        }
        if (DarkService.isDarkMage(state) && state.corruption() > 0) {
            boolean full = state.corruption() >= PlayerMagicState.MAX_CORRUPTION;
            boolean ledger = state.isPassiveEnabled(MagicPassiveContent.LEDGER.id()) && !full;
            int next = Math.min(PlayerMagicState.MAX_CORRUPTION, (DarkService.threshold(state) + 1) * DarkService.THRESHOLD_STEP);
            Component reading = ledger ? outOf(state.corruption(), next) : value(state.corruption(), full);
            tokens.add(resource(font, Component.translatable("hud.magical.readout.corruption", reading),
                    MagicSchool.DARK, CORRUPTION_MARK));
        }
        if (EldritchService.isEldritchMage(state) && state.notice() > 0) {
            tokens.add(resource(font, Component.translatable("hud.magical.readout.notice", value(state.notice(), state.notice() >= EldritchService.NOTICED_AT)),
                    MagicSchool.ELDRITCH, NOTICE_MARK));
        }
        ArcanePlayerData arcane = ClientArcaneState.get();
        SpellPreset preset = arcane == null ? null : arcane.activePreset();
        if (preset != null) {
            boolean unstable = ArcaneSpellResolver.resolve(preset.recipe()).stability() < ARCANE_UNSTABLE_BELOW;
            MutableComponent mana = value(arcane.mana(), false);
            Component name = Component.literal(font.plainSubstrByWidth(preset.name(), ARCANE_NAME_MAX_W))
                    .withColor(unstable ? HudPalette.TROUBLE : HudPalette.owner(MagicSchool.ARCANE));
            tokens.add(resource(font, Component.translatable("hud.magical.readout.arcane", name, mana), MagicSchool.ARCANE, ARCANE_MARK));
        }
        if (state.manaChargeTicks() > 0 && state.manaChargeLevel() > 0) {
            tokens.add(resource(font, Component.translatable("hud.magical.gauge.charge", value(state.manaChargeLevel(), false)),
                    school, CHARGE_MARK));
        }
        int passivesFrom = tokens.size();
        boolean greed = state.hasPassive(MagicPassiveContent.SIN_GREED.id());
        if (greed || equipped(state, MagicContent.VAULT_OF_AVARICE.id())) {
            tokens.add(vault(state, font));
        }
        if (options.showSins()) {
            for (int seat = 0; seat < SINS.length; seat++) {
                if (seat != GREED && (sinVisibleMask & (1 << seat)) != 0) {
                    tokens.add(sin(state, font, seat));
                }
            }
        }
        if (tokens.isEmpty()) {
            return HudSnapshot.NO_READOUTS;
        }
        HudLayout.Flow flow = layout.flow(widths(tokens, false), passivesFrom);
        boolean brief = !placesEvery(flow, passivesFrom, tokens.size());
        if (brief) {
            flow = layout.flow(widths(tokens, true), passivesFrom);
        }
        Rect[] at = flow.tokens();
        int[] from = flow.sources();
        Readout[] readouts = new Readout[at.length + (flow.more() == null ? 0 : 1)];
        for (int i = 0; i < at.length; i++) {
            Token token = tokens.get(from[i]);
            readouts[i] = new Readout(brief ? token.brief() : token.label(), at[i], token.stamp());
        }
        if (flow.more() != null) {
            Label more = label(font, Component.translatable("hud.magical.readout.more", flow.dropped()), HudPalette.TEXT_MUTED);
            Rect box = flow.more();
            int x = layout.right() ? box.right() - more.width() : box.x();
            readouts[at.length] = new Readout(more, new Rect("more", x, box.y(), more.width(), HudLayout.TEXT_H), null);
        }
        return readouts;
    }

    private static int[] widths(List<Token> tokens, boolean brief) {
        int[] widths = new int[tokens.size()];
        for (int i = 0; i < widths.length; i++) {
            Token token = tokens.get(i);
            widths[i] = HudLayout.STAMP_LEAD + (brief ? token.brief() : token.label()).width();
        }
        return widths;
    }

    /** Whether every token from {@code from} on - every passive - found a place. */
    static boolean placesEvery(HudLayout.Flow flow, int from, int count) {
        int placed = 0;
        for (int source : flow.sources()) {
            if (source >= from) {
                placed++;
            }
        }
        return placed == count - from;
    }

    /** A resource: its word in its school's hue, its numbers as the arguments made them. */
    private static Token resource(Font font, MutableComponent text, MagicSchool owner, StampId mark) {
        int hue = HudPalette.owner(owner);
        Label label = label(font, text.withColor(hue), hue);
        return new Token(label, label, new Stamp(mark.atlasCell(), hue));
    }

    /**
     * Greed's vault, which is a resource the sin owns, so it leads the passives: "Vault 1.2k", and
     * while the sin is lit its hoard after it, "hoard 40" - never "+40", because a "+" is the mark
     * for readings left off. Cut short it is the vault alone: the hoard is the one number here that
     * is worth less than a sin's place on the line. It shows whenever Greed is owned or the Vault of
     * Avarice is on the bar, as the dashboard did.
     */
    private static Token vault(PlayerMagicState state, Font font) {
        int hue = HudPalette.ink(MagicPassiveContent.SIN_GREED.color());
        MutableComponent amount = Component.literal(compact(state.manaVault())).withColor(HudPalette.TEXT_PRIMARY);
        Label brief = label(font, Component.translatable("hud.magical.gauge.vault", amount).withColor(hue), hue);
        if (!options.showSins() || (sinVisibleMask & (1 << GREED)) == 0) {
            return new Token(brief, brief, new Stamp(SIN_CELLS[GREED], hue));
        }
        MutableComponent full = Component.translatable("hud.magical.gauge.vault", amount).withColor(hue)
                .append(" ").append(Component.translatable("hud.magical.token.hoard", value(state.greedHoard(), false)));
        return new Token(label(font, full, hue), brief, new Stamp(SIN_CELLS[GREED], hue));
    }

    /**
     * A lit sin: "Pride 9%", its name in its own hue and its number in white, as a resource is
     * written; cut short it is the number alone after its stamp, and a rested Sloth keeps only its
     * lighter hue.
     */
    private static Token sin(PlayerMagicState state, Font font, int seat) {
        MagicPassiveDefinition sin = SINS[seat];
        boolean rested = seat == SLOTH && state.restedStillnessTicks() > 0;
        int hue = HudPalette.ink(rested ? HudPalette.lift(sin.color(), RESTED_LIFT) & 0xFFFFFF : sin.color());
        MutableComponent number = switch (seat) {
            case 0 -> Component.translatable("hud.magical.token.percent", percent(state.prideGauge(), PlayerMagicState.MAX_SIN_GAUGE));
            case 3 -> Component.translatable("hud.magical.token.percent", Math.round(envyGauge(state) * 100.0F));
            case 4 -> Component.translatable("hud.magical.token.seconds", (state.gluttonyCooldownTicks() + 19) / 20);
            case 5 -> Component.translatable("hud.magical.token.percent", percent(state.wrathGauge(), PlayerMagicState.MAX_SIN_GAUGE));
            default -> Component.translatable("hud.magical.token.percent", percent(state.slothStillness(), PlayerMagicState.MAX_SIN_GAUGE));
        };
        number.withColor(HudPalette.TEXT_PRIMARY);
        MutableComponent reading = rested ? Component.translatable("hud.magical.token.rested", number).withColor(hue) : number;
        MutableComponent full = Component.translatable(SIN_NAMES[seat], reading).withColor(hue);
        return new Token(label(font, full, hue), label(font, number, hue), new Stamp(SIN_CELLS[seat], hue));
    }

    /** A number in white, or in the trouble red when it is the thing in trouble. */
    private static MutableComponent value(int number, boolean trouble) {
        return Component.literal(Integer.toString(number)).withColor(trouble ? HudPalette.TROUBLE : HudPalette.TEXT_PRIMARY);
    }

    /** "7/12": the number white, "/12" muted. */
    private static MutableComponent outOf(int number, int max) {
        return value(number, false).append(Component.literal("/" + max).withColor(HudPalette.TEXT_MUTED));
    }

    /** Every stamp a reading can wear: the resources', then each sin with a gauge, the vault's being Greed's. */
    static int[] readoutCells() {
        StampId[] marks = {SWORDS_MARK, VESSEL_MARK, CORRUPTION_MARK, NOTICE_MARK, ARCANE_MARK, CHARGE_MARK};
        int[] cells = new int[marks.length + SINS.length - 1];
        int n = 0;
        for (StampId mark : marks) {
            cells[n++] = mark.atlasCell();
        }
        for (int seat = 0; seat < SINS.length; seat++) {
            if (seat != LUST) {
                cells[n++] = SIN_CELLS[seat];
            }
        }
        return cells;
    }

    private static boolean equipped(PlayerMagicState state, ResourceLocation skill) {
        for (int k = 0; k < MagicContent.LOADOUT_SIZE; k++) {
            if (skill.equals(state.equippedSkill(k))) {
                return true;
            }
        }
        return false;
    }

    /** Above zero while the sin has something to say; Lust never does. */
    private static int sinValue(PlayerMagicState state, int seat) {
        return switch (seat) {
            case 0 -> state.prideGauge();
            case 1 -> state.greedHoard();
            case 3 -> Math.round(envyGauge(state) * 100.0F);
            case 4 -> state.gluttonyCooldownTicks();
            case 5 -> state.wrathGauge();
            case 6 -> state.slothStillness();
            default -> 0;
        };
    }

    private static float envyGauge(PlayerMagicState state) {
        float best = 0.0F;
        for (Map.Entry<ResourceLocation, Integer> entry : state.envyProgress().entrySet()) {
            MagicSkillDefinition definition = MagicContent.get(entry.getKey());
            if (definition == null) {
                continue;
            }
            best = Math.max(best, fraction(entry.getValue(), state.envyRequired(definition)));
        }
        return best;
    }

    private static Chip[] chips(HudLayout layout, long now) {
        int count = ClientStatusState.activeCount();
        if (count == 0) {
            return HudSnapshot.NO_CHIPS;
        }
        List<Chip> list = new ArrayList<>(Math.min(count, HudLayout.STATUS_CHIPS_MAX));
        int shown = Math.min(count, HudLayout.STATUS_CHIPS_MAX);
        ClientStatusState.forEachActive((status, remaining, amplifier, value) -> {
            int index = status.ordinal();
            if (STATUS_SEEN_AT[index] == 0L || remaining > STATUS_INITIAL[index]) {
                STATUS_SEEN_AT[index] = now;
                STATUS_INITIAL[index] = remaining;
            }
            if (list.size() < shown) {
                boolean harmful = status != MagicStatus.REVEALED && status != MagicStatus.IMMOVABLE;
                list.add(new Chip(status, HudGlyphs.statusCell(status), HudPalette.status(status), harmful, amplifier,
                        now, Math.max(remaining, STATUS_INITIAL[index]), layout.statusChip(list.size(), shown)));
            }
        });
        for (MagicStatus status : MagicStatus.values()) {
            if (!ClientStatusState.has(status)) {
                STATUS_SEEN_AT[status.ordinal()] = 0L;
                STATUS_INITIAL[status.ordinal()] = 0;
            }
        }
        return list.toArray(new Chip[0]);
    }

    /** The school with the most equipped skills; a tie goes to the lowest slot; none is Arcane. */
    static MagicSchool dominantSchool(PlayerMagicState state) {
        MagicSchool[] schools = MagicSchool.values();
        int[] counts = new int[schools.length];
        MagicSchool best = null;
        int bestCount = 0;
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            ResourceLocation skill = state.equippedSkill(slot);
            MagicSkillDefinition definition = skill == null ? null : MagicContent.get(skill);
            if (definition == null) {
                continue;
            }
            int count = ++counts[definition.school().ordinal()];
            if (count > bestCount) {
                bestCount = count;
                best = definition.school();
            }
        }
        return best == null ? MagicSchool.ARCANE : best;
    }

    /** The mana numeral: four digits, then thousands - 9999, 12k, 999k. */
    static String numeral(int value) {
        if (value < 10000) {
            return Integer.toString(value);
        }
        return Math.min(999, value / 1000) + "k";
    }

    /** The seconds under a cooling slot: at most three characters - 999, then 17m, capped at 99m. */
    static String seconds(int seconds) {
        if (seconds < 1000) {
            return Integer.toString(seconds);
        }
        return Math.min(99, (seconds + 59) / 60) + "m";
    }

    /**
     * Vault numerals, never more than four characters and never rounded up: 999, 1k, 1.2k, 9.9k,
     * 12k, 999k, 1.2m, capped at 99m.
     */
    static String compact(int value) {
        if (value < 1000) {
            return Integer.toString(Math.max(0, value));
        }
        if (value < 10_000) {
            return tenths(value / 100) + "k";
        }
        if (value < 1_000_000) {
            return value / 1000 + "k";
        }
        if (value < 10_000_000) {
            return tenths(value / 100_000) + "m";
        }
        return Math.min(99, value / 1_000_000) + "m";
    }

    /** A count of tenths as "1.2", or "1" when it is whole. */
    private static String tenths(int tenths) {
        return tenths % 10 == 0 ? Integer.toString(tenths / 10) : tenths / 10 + "." + tenths % 10;
    }

    private static int percent(int value, int max) {
        return Math.round(value * 100.0F / Math.max(1, max));
    }

    private static float fraction(int value, int max) {
        return max <= 0 ? 0.0F : Math.max(0.0F, Math.min(1.0F, value / (float) max));
    }

    private static Label label(Font font, Component text, int color) {
        FormattedCharSequence shaped = text.getVisualOrderText();
        return new Label(shaped, font.width(shaped), color);
    }
}
