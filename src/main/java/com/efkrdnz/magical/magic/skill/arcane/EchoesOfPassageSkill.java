package com.efkrdnz.magical.magic.skill.arcane;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T4 - ECHO_PATH / SELF_TRAIL / POINT_CHAIN. A recording halo follows the caster and stamps
 * a glass hourglass echo every 15 ticks (max 10). When the recording ends (duration, or pressing
 * the slot again) the echoes detonate oldest to newest, 3 ticks apart. Sneak = all echoes stamp at
 * the cast point. The long cooldown starts when the chain fires.
 */
public final class EchoesOfPassageSkill implements SkillModule {
    private static final int STAMP_INTERVAL = 15;
    private static final int MAX_ECHOES = 10;
    private static final byte MODE_CHILD = 2;

    /** The recorder's draw mode: the mode byte above the sneak bit, which the recorder never sets. */
    private static final int DRAW_RECORDER = 0;

    /** An echo's draw mode, from {@link #MODE_CHILD} the same way. */
    private static final int DRAW_ECHO = MODE_CHILD >> 1;

    /**
     * How tall an echo's glass hourglass stands: under the eyes of a caster standing (1.62) or
     * crouched (1.27). A caster who records without walking stands in their own first echo, and a
     * prism reaching the eyes is seen from inside it - its walls across the whole lower half of a
     * first-person view. At this height its rim is 60 degrees under a standing eye line and 37
     * under a crouched one, both outside a level view's 35.
     */
    private static final float ECHO_HEIGHT = 1.0F;

    /** Runes that lift off the footprint as an echo is stamped. */
    private static final int STAMP_RUNES = 4;

    /** Glass thrown off an echo as it detonates. */
    private static final int SHATTER_SHARDS = 8;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ECHOES_OF_PASSAGE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                // a second press ends the recording early
                for (SpellEffectEntity active : ctx.level().getEntitiesOfClass(SpellEffectEntity.class, ctx.caster().getBoundingBox().inflate(48.0D),
                        e -> e.skillId().equals(ctx.definition().id()) && e.mode() != MODE_CHILD && ctx.caster().getUUID().equals(e.ownerUuid()))) {
                    active.serverData().putBoolean("Stop", true);
                    return CastResult.FAILED; // mana back, the chain fires now
                }
                int record = Math.max(40, ctx.duration());
                SpellEffectEntity controller = SpellEffectEntity.spawn(ctx, ctx.feet(), record + MAX_ECHOES * 3 + 10, 3.5F * Math.max(0.5F, ctx.size()), ctx.look());
                controller.setExtra(record);
                return CastResult.CONSUMED_NO_COOLDOWN;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(0.0F, 10.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                if (entity.mode() == MODE_CHILD) {
                    return; // echoes are inert until the controller fires them
                }
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                if (owner == null || !owner.isAlive()) {
                    entity.finish();
                    return;
                }
                CompoundTag data = entity.serverData();
                int record = Math.max(40, entity.extra());
                boolean chaining = data.getBoolean("Chaining");
                if (!chaining) {
                    if (!entity.sneakMode()) {
                        entity.setPos(owner.position());
                    }
                    ListTag echoes = data.getList("Echoes", Tag.TAG_COMPOUND);
                    if (entity.tickCount % STAMP_INTERVAL == 0 && echoes.size() < MAX_ECHOES) {
                        Vec3 pos = entity.position();
                        SpellEffectEntity echo = SpellEffectEntity.create(level, entity.definition(), null, owner, pos, record + MAX_ECHOES * 3 + 20, 1.6F, Vec3.ZERO, (entity.seed() + echoes.size()) & 63);
                        echo.setMode(MODE_CHILD);
                        echo.setExtra(echoes.size());
                        level.addFreshEntity(echo);
                        stampRunes(level, entity.definition(), pos);
                        CompoundTag rec = new CompoundTag();
                        rec.putUUID("Id", echo.getUUID());
                        echoes.add(rec);
                        data.put("Echoes", echoes);
                        entity.setValue(echoes.size() / (float) MAX_ECHOES);
                    }
                    if (entity.tickCount >= record || data.getBoolean("Stop") || echoes.size() >= MAX_ECHOES && entity.tickCount % STAMP_INTERVAL == 0) {
                        data.putBoolean("Chaining", true);
                        data.putInt("ChainTick", entity.tickCount);
                        data.putInt("Fired", 0);
                        entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                        if (owner instanceof ServerPlayer player) {
                            var state = player.getData(MagicalAttachments.MAGIC_STATE);
                            state.setSkillCooldown(entity.skillId(), entity.definition().baseCooldownTicks());
                            state.sync(player);
                        }
                    }
                    return;
                }
                int since = entity.tickCount - data.getInt("ChainTick");
                ListTag echoes = data.getList("Echoes", Tag.TAG_COMPOUND);
                int fired = data.getInt("Fired");
                if (since % 3 == 0 && fired < echoes.size()) {
                    Entity echo = level.getEntity(echoes.getCompound(fired).getUUID("Id"));
                    if (echo instanceof SpellEffectEntity e && e.isAlive()) {
                        detonate(entity, e);
                    }
                    data.putInt("Fired", fired + 1);
                }
                if (fired >= echoes.size()) {
                    entity.finish();
                }
            }

            private void detonate(SpellEffectEntity controller, SpellEffectEntity echo) {
                ServerLevel level = controller.serverLevel();
                Entity owner = controller.owner();
                Set<UUID> struck = new HashSet<>();
                ListTag struckList = controller.serverData().getList("Struck", Tag.TAG_COMPOUND);
                for (int i = 0; i < struckList.size(); i++) {
                    struck.add(struckList.getCompound(i).getUUID("Id"));
                }
                Vec3 centre = echo.position().add(0.0D, 0.8D, 0.0D);
                for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, centre, controller.radius())) {
                    float damage = controller.damage() * (struck.contains(hostile.getUUID()) ? 0.6F : 1.0F);
                    SkillTargets.hurt(level, owner, hostile, damage, controller.definition(), true);
                    SkillTargets.shove(hostile, centre, 0.5D * controller.knockback(), 0.2D);
                    if (struck.add(hostile.getUUID())) {
                        CompoundTag rec = new CompoundTag();
                        rec.putUUID("Id", hostile.getUUID());
                        struckList.add(rec);
                    }
                }
                controller.serverData().put("Struck", struckList);
                SpellFx.impact(level, controller.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 1.4F);
                shatter(level, controller.definition(), echo.position());
                echo.discard();
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.mode() == MODE_CHILD) {
                    return;
                }
                ListTag echoes = entity.serverData().getList("Echoes", Tag.TAG_COMPOUND);
                for (int i = 0; i < echoes.size(); i++) {
                    Entity echo = entity.serverLevel().getEntity(echoes.getCompound(i).getUUID("Id"));
                    if (echo != null) {
                        echo.discard();
                    }
                }
            }
        };
    }

    /**
     * Runes lifting off the footprint an echo was just stamped on, so a walked path reads as it is
     * laid. A rune keeps nine tenths of its speed a tick, so it rises ten times what it starts with:
     * from the ankles to the waist, under the caster's own eyes.
     */
    private static void stampRunes(ServerLevel level, MagicSkillDefinition definition, Vec3 at) {
        TintedParticleOptions rune = new TintedParticleOptions(MagicalParticles.RUNE.get(), VisualProfiles.of(definition).color(ColorRole.BRIGHT), 1.0F);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < STAMP_RUNES; i++) {
            double a = offset + i * Math.PI * 2.0D / STAMP_RUNES;
            level.sendParticles(rune, at.x + Math.cos(a) * 0.35D, at.y + 0.1D, at.z + Math.sin(a) * 0.35D, 0, 0.0D, 0.07D, 0.0D, 1.0D);
        }
    }

    /**
     * The glass of a detonated echo: fragments off the whole height of the hourglass that fall and
     * settle where it stood. The impact cue carries the blast; this is the body it broke. Each is
     * thrown out and a little down and never up: a shard keeps nearly all its speed and hardly
     * falls, so one handed a random upward speed climbs two blocks - through the eyes of a caster
     * who recorded without walking and stands on their own stacked echoes.
     */
    private static void shatter(ServerLevel level, MagicSkillDefinition definition, Vec3 base) {
        TintedParticleOptions shard = new TintedParticleOptions(MagicalParticles.SHARD.get(), VisualProfiles.of(definition).color(ColorRole.BRIGHT), 1.2F);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < SHATTER_SHARDS; i++) {
            double a = offset + i * Math.PI * 2.0D / SHATTER_SHARDS;
            double y = base.y + ECHO_HEIGHT * (0.15D + 0.75D * level.random.nextDouble());
            double speed = 0.04D + 0.03D * level.random.nextDouble();
            level.sendParticles(shard, base.x + Math.cos(a) * 0.3D, y, base.z + Math.sin(a) * 0.3D, 0, Math.cos(a), -0.3D, Math.sin(a), speed);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        // The halo rides the recorder and only an echo is glass. Both used to be drawn on every
        // entity of the skill, so the recorder - which stands on its caster's feet for the whole
        // recording - wrapped them in a glass prism, and a first-person view looked out through it.
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.HOURGLASS).frame(5).band(GlyphKind.RUNE_BAND, 16).band(GlyphKind.WAVE_BAND, 10).stamps(StampId.FOOTPRINT, 10).orbit(7, 0.88F, 5).core(CoreKind.RIPPLE).stack(3, 0.35F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.filament(Silhouette.Form.RING, FxKinds.Filament.DASH_TRAIN, 12, 0.05F, 0.9F, 0).forModes(DRAW_RECORDER))
                .silhouette(Silhouette.body(Silhouette.Form.PRISM, FxKinds.Body.GLASS, 4, 0.35F, ECHO_HEIGHT).forModes(DRAW_ECHO))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(5.0F, 3.0F, 1.0F);
    }
}
