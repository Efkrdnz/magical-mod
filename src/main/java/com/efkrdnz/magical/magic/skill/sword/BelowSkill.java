package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Bind;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.SwordMath;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.sword.stance.Pattern;
import com.efkrdnz.magical.magic.sword.stance.Slot;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.TierProfile;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SWORD T-3, Sword Rider and above - the Array is sunk under a <b>place</b> and turned upside
 * down, and everything that pointed at the floor comes back up through that spot.
 *
 * <p>Two things make this a projection rather than a skill with numbers. The first is that the
 * eruption's strength is exactly how many swords the wielder has with them - no cap, no scaling
 * term, no separate damage stat - so a wielder who has just Loosed their whole formation erupts
 * with nothing, and the number has been on screen the whole time. The second is that the origin
 * is a <b>point committed at the press and never re-acquired</b>: the crosshair point, or the
 * feet of the body that was under the crosshair at that instant, and after {@link #COMMIT_TICK}
 * nothing about the strike can change.
 *
 * <p>It used to read the <em>downward half</em> of an authored lattice, which was a fine rule
 * with one fatal property: the shape it was a projection of had been authored blind, through a
 * crosshair that could not reach half of it. The stance is the choice now, and the stance's
 * {@link Pattern} is what the swords left standing in the ground are laid out on - so the six
 * postures give six pictures of the same skill without a per-skill dial anywhere.
 *
 * <p><b>The telegraph and the wielder's feet must never be drawn in the same place.</b> Below
 * commits to a point and not to a body, which reads as a bug to anyone trained on homing area
 * attacks unless the mark visibly stays where it was put while the target walks out of it. The
 * dodge is arithmetic and not a promise: sixteen ticks of warning to clear
 * {@link #ERUPT_RADIUS} blocks laterally is 5.7 ticks sprinting and 7.4 walking, and 24.7
 * sneaking. Air is not a refuge - no floor is consulted anywhere in this file, the origin is a
 * point in three dimensions, and jumping peaks at 1.25 blocks against a cylinder 3.5 tall.
 * Speed is the refuge.
 *
 * <p>The swords are gone from the formation the moment they sink: a sword under the ground is
 * not a sword at your shoulder, so every one of them leaves through
 * {@link SwordService#spendSword}. What comes back depends on the hit test - a sword that struck
 * walks home on the return clock, a sword that struck nothing stands in the ground where the
 * wielder (or the enemy) can get at it.
 */
public final class BelowSkill implements SkillModule {

    /** How far off a point may be committed. */
    public static final double AIM_RANGE = 20.0D;

    /** Ticks of descent before the ring appears. */
    public static final int SINK_TICKS = 6;

    /** Ticks the ring is on the ground before the strike is locked. This is the whole warning. */
    public static final int TELEGRAPH_TICKS = 10;

    /** Nothing about the strike can change after this tick. */
    public static final int COMMIT_TICK = SINK_TICKS + TELEGRAPH_TICKS;

    /** Ticks each blade takes to travel from {@code reach} below the origin to {@code reach} above. */
    public static final int RISE_TICKS = 6;

    /** Press to hit. Twenty-two, and sixteen of them are a ring telling you to move. */
    public static final int HIT_TICK = COMMIT_TICK + RISE_TICKS;

    /** The cylinder's radius, and the distance a dodge has to cover. */
    public static final double ERUPT_RADIUS = 1.6D;

    /** The cylinder runs from half a block under the origin to this far above it. */
    public static final double ERUPT_HEIGHT = 3.0D;

    /**
     * How many plates the eruption is drawn with. Six, because {@code FxMesh.plateFan} fans its
     * plates 37 degrees apart, so six of them span 222 degrees and there is no bearing the fan is
     * edge-on from - the sheet problem this mod has now met in the wave, the thread and the thrown
     * blade, answered the same way each time.
     */
    public static final int ERUPT_PLATES = 6;

    /**
     * {@code FxMesh.plateFan} lays plate <i>i</i> of <i>n</i> between {@code y = i/n} and
     * {@code y = (i+1)/n + 0.08} - the top band overshoots so consecutive plates overlap instead of
     * seaming - so a fan asked for height <i>h</i> is drawn 1.08<i>h</i> tall. Public because the
     * extent test measures what is drawn and not what was asked for.
     */
    public static final float PLATE_FAN_OVERSHOOT = 1.08F;

    /** Asked for, so the drawn fan tops out at exactly {@link #ERUPT_HEIGHT} and no higher. */
    public static final float RISE_HEIGHT = (float) (ERUPT_HEIGHT / PLATE_FAN_OVERSHOOT);

    /**
     * How wide the swords that struck nothing are laid out, as a reach handed to the pattern.
     *
     * <p>Deliberately much smaller than the patterns own constants would give at full size: a
     * ring of swords standing two blocks out reads as a fence somebody built rather than as the
     * wreckage of one eruption. The spans the patterns carry are aiming spans measured off a
     * target, so they are scaled down to this rather than passed through.
     */
    private static final double PLANT_SPREAD = 0.35D;

    private static final String TAG_COUNT = "Swords";
    private static final String TAG_PATTERN = "Pattern";
    private static final String TAG_BILL = "Bill";
    private static final String TAG_STRUCK = "Struck";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BELOW;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerPlayer player = ctx.player();
                if (player == null) {
                    return CastResult.FAILED;
                }
                PlayerMagicState state = ctx.state();
                if (!SwordService.holds(state) || !SwordService.rulesFor(state).worldOrigin()) {
                    player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
                    return CastResult.FAILED;
                }
                return sink(ctx, player, state);
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            /**
             * Blocks <em>and</em> bodies. Zero here would mean blocks only, silently, and the
             * commonest press of this skill is aimed at something standing on open ground.
             */
            @Override
            public double aimTolerance() {
                return 1.6D;
            }

            /**
             * <b>False, and it is the counterplay.</b> Dropping a block-less aim point to the
             * ground would put the origin under the feet of anything airborne and make Below a
             * homing attack on a falling target. The origin is a point in three dimensions and no
             * floor is consulted: air is not a refuge, and it is not a trap either.
             */
            @Override
            public boolean aimDropsToGround() {
                return false;
            }

            /** Never null. A null here is an NPE on the server thread inside entity ticking. */
            @Override
            public MobCastProfile mob() {
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.crush", "screen.magical.tuning.rise",
                        "screen.magical.tuning.reach", "screen.magical.tuning.patience",
                        "screen.magical.tuning.thrift");
            }
        };
    }

    /**
     * The press: a point is recorded, the frame goes under it, and every sword goes with it.
     *
     * <p>A plain press, so the registry has already taken the mana and will write the cooldown -
     * there is no {@code payFor} here and a refusal answers {@link CastResult#FAILED}, which
     * refunds. A wielder with no swords present still pays and still gets the ring: the skill did
     * what it does, and what it does is decided by how much steel they still have.
     */
    private static CastResult sink(CastContext ctx, ServerPlayer player, PlayerMagicState state) {
        Vec3 origin = committedPoint(ctx);
        // Through spendSwords, always: it is the one door out of the formation, and it is the
        // only reason the count stays honest without anybody adding anything up.
        int sunk = SwordService.spendSwords(player, state, SwordService.present(player, state));

        SwordService.sink(player, origin);
        SwordService.tendArrayEntity(player, state);
        state.sync(player);

        int life = Math.max(HIT_TICK + 1, ctx.duration());
        SpellEffectEntity eruption = SpellEffectEntity.spawn(ctx, origin, life, (float) ERUPT_RADIUS, new Vec3(0.0D, 1.0D, 0.0D));
        CompoundTag scratch = eruption.serverData();
        scratch.putInt(TAG_COUNT, sunk);
        // The stance is read once, here, and not again at the strike: a wielder who changes
        // posture during the sixteen ticks of warning has not moved the swords that are already
        // under the ground, and the shape they come up in is the shape they went down in.
        scratch.putInt(TAG_PATTERN, state.swordArray().stance().pattern().ordinal());
        ctx.level().playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 0.6F);
        return CastResult.SUCCESS;
    }

    /**
     * Where the eruption will be, decided once.
     *
     * <p>A body under the crosshair contributes its <em>feet</em> and not its centre, because the
     * cylinder already reaches {@link #ERUPT_HEIGHT} upward and anchoring at a chest would put
     * half of it inside the ground for anything standing still.
     */
    private static Vec3 committedPoint(CastContext ctx) {
        if (ctx.aim() == null) {
            return ctx.feet().add(ctx.look().scale(4.0D));
        }
        LivingEntity body = ctx.aim().living();
        return body != null ? body.position() : ctx.aim().point();
    }

    // ---- the timeline -----------------------------------------------------------------------------

    /**
     * Sink, warn, commit, rise, strike - once, at {@link #HIT_TICK}, and never again.
     *
     * <p>The phases are the entity's own: {@code WINDUP} while the blades descend, {@code ACTIVE}
     * for the ten ticks the ring is on the ground, {@code CLOSING} for the rise. That is all the
     * renderer needs and it costs no accessor. {@code value} carries the progress inside the
     * current phase so the ring can open and the blade tips can break the surface without the
     * client ever being told where a blade is - {@link ArrayPose} is pure and both sides run it.
     */
    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                int age = effect.tickCount;
                if (age < SINK_TICKS) {
                    effect.setValue((float) age / SINK_TICKS);
                    return;
                }
                if (age < COMMIT_TICK) {
                    effect.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    effect.setValue((float) (age - SINK_TICKS) / TELEGRAPH_TICKS);
                    return;
                }
                if (age == COMMIT_TICK) {
                    commit(effect);
                    return;
                }
                if (age < HIT_TICK) {
                    effect.setValue((float) (age - COMMIT_TICK) / RISE_TICKS);
                    return;
                }
                if (age == HIT_TICK) {
                    strike(effect);
                }
            }

            /**
             * The frame comes back to the wielder's body.
             *
             * <p>A sunk frame is the Array hanging under a place rather than round its owner, so
             * Ward reads bearings around that place while it lasts - which is correct and is part
             * of the price - but it must not outlive the eruption that sank it.
             */
            @Override
            public void onExpire(SpellEffectEntity effect) {
                if (effect.livingOwner() instanceof ServerPlayer player && SwordService.bind(player) == Bind.SUNK) {
                    SwordService.hold(player);
                    PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                    SwordService.tendArrayEntity(player, state);
                    state.sync(player);
                }
            }
        };
    }

    /**
     * COMMIT: the bill is written down, and nothing after this tick can move it.
     *
     * <p>Written here rather than read at the hit for the reason the spec gives the tick a name -
     * sixteen ticks in, nothing about the strike can change. How many swords went down was fixed
     * six ticks earlier, at the press; this is the other half of the number, and with one flat
     * {@link SwordMath#bladeDamage} per sword the whole eruption is a multiplication the wielder
     * can read off their own HUD before they press it.
     */
    private static void commit(SpellEffectEntity effect) {
        effect.setPhase(SpellEffectEntity.PHASE_CLOSING);
        effect.setValue(0.0F);
        // The same tick, said twice: nothing about the strike can change, and the steel is now
        // allowed to be in the world. See drawModeAt.
        effect.setMode(modeFor(effect.mode(), drawModeAt(COMMIT_TICK)));
        CompoundTag scratch = effect.serverData();
        scratch.putDouble(TAG_BILL, scratch.getInt(TAG_COUNT) * SwordMath.bladeDamage());
    }

    /**
     * One hit test, once, in a vertical cylinder at the committed point.
     *
     * <p><b>One wound and not one per blade</b>, and that is the i-frame rule answered rather than
     * dodged: every blade arrives in the same tick and never arrives again, so the sum is
     * arithmetically what "{@code bladeDamage} per risen blade" means and there is no second hit
     * for a hurt cooldown to eat. The cooldown is cleared anyway, because the victim may be
     * carrying one from something else entirely and the eruption is not a follow-up to it.
     */
    private static void strike(SpellEffectEntity effect) {
        CompoundTag scratch = effect.serverData();
        if (scratch.getBoolean(TAG_STRUCK)) {
            return;
        }
        scratch.putBoolean(TAG_STRUCK, true);
        ServerLevel level = effect.serverLevel();
        Vec3 origin = effect.position();
        float amount = (float) scratch.getDouble(TAG_BILL);
        float knockback = Math.max(0.0F, effect.knockback());

        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.6F);

        List<LivingEntity> caught = amount <= 0.0F ? List.of()
                : SkillTargets.hostilesInCylinder(level, effect.owner(), origin, ERUPT_RADIUS, ERUPT_HEIGHT);
        for (LivingEntity victim : caught) {
            victim.invulnerableTime = 0;
            MagicDamageService.hurt(victim, level.damageSources().indirectMagic(effect, effect.owner()),
                    amount, effect.skillId());
            // Straight up: the blades came out of the floor, so the shove has no direction in the
            // plane and SkillTargets.shove with zero strength is exactly that.
            SkillTargets.shove(victim, origin, 0.0D, knockback);
        }
        if (caught.isEmpty()) {
            standInTheGround(effect, level, origin);
        }
        effect.finish();
    }

    /**
     * A sword that hit nothing stays where it came up, and the stance says where that is.
     *
     * <p>It is already away from the formation, so this creates no steel - it creates the place
     * the wielder can walk to and take it back in a stride, and the place the enemy can break.
     * That is the whole recovery loop of the class pointed at the ground the wielder was just
     * losing, and it is the one place Below's {@link Pattern} is visible after the fact: Guard
     * leaves a fence across the path, Crown a ring round the spot, Rain a scatter.
     *
     * <p>The pattern's y is dropped and its spread scaled to {@link #PLANT_SPREAD}. A sword
     * standing in the ground stands in the ground whatever the pattern thought about height.
     */
    private static void standInTheGround(SpellEffectEntity effect, ServerLevel level, Vec3 origin) {
        if (!(effect.livingOwner() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag scratch = effect.serverData();
        int count = scratch.getInt(TAG_COUNT);
        if (count <= 0) {
            return;
        }
        Pattern[] patterns = Pattern.values();
        Pattern pattern = patterns[Math.floorMod(scratch.getInt(TAG_PATTERN), patterns.length)];
        // Pattern-local, turned into the world about the origin by the same rotation everything
        // else uses. Yaw only: the eruption came straight up, so there is no pitch to apply and
        // a facing would only tilt a layout that is already lying on the floor.
        Frame flat = new Frame(origin.x, origin.y, origin.z, player.getYRot(), 0.0F, 1.0F);
        double scale = PLANT_SPREAD / Pattern.RING_RADIUS;
        for (int i = 0; i < count; i++) {
            Slot spread = Pattern.spread(pattern, i, count, PLANT_SPREAD);
            double[] offset = ArrayPose.worldOffset(
                    Slot.at(spread.x() * scale, 0.0D, spread.z() * scale, 0.0D, 1.0D, 0.0D), flat);
            Vec3 at = origin.add(offset[0], 0.0D, offset[2]);
            SwordBladeEntity.plant(level, player, MagicContent.BELOW.id(), at, i, 1);
        }
    }

    // ---- what it looks like ------------------------------------------------------------------------

    /**
     * Which subset of the drawing is live, as a function of the eruption's age.
     *
     * <p>The ring on the ground <em>is</em> the sixteen ticks of warning, so steel standing in the
     * world during them would be a lie - the dodge would be read off the blades and not off the
     * mark, and the mark is the only thing that stays where it was put. Mode 0 is the telegraph and
     * nothing else; mode 1 is the rise. {@link #commit} flips it on the tick the strike stops being
     * changeable, which is the same tick for the same reason.
     */
    public static int drawModeAt(int age) {
        return age < COMMIT_TICK ? 0 : 1;
    }

    /** The draw mode as a {@code SpellEffectEntity} mode byte, with bit 0 - the sneak flag - kept. */
    private static byte modeFor(byte current, int drawMode) {
        return (byte) ((current & 1) | (drawMode << 1));
    }

    /**
     * {@code EmblemId.UPTHRUST} is three blades of unequal height breaking up through a ground
     * line - the pitch reflection, drawn. An emblem may not be shared: {@code VisualProfiles}
     * calls a repeated one a <b>hard</b> collision and throws at common setup, so each of the six
     * SWORD marks is its own constant with its own arm in {@code FxTextures.emblemStrokes}.
     *
     * <p>The telegraph is deliberately a <b>horizontal</b> mark: the caster is looking down at the
     * eruption and a vertical blade is end-on from above too, which is the first-person bolt trap
     * pointed at the floor. No {@code stamps(...)} layer - the school's stamp is
     * {@code StampId.EDGE} at atlas cell 32 and {@code STAMP_BAND}'s {@code paramB} is five bits.
     *
     * <p><b>Everything here is measured against the cylinder that catches</b>
     * ({@link #ERUPT_RADIUS} by {@link #ERUPT_HEIGHT}), because the first version of this profile
     * was not, and a third-person capture of it at midnight is a single white ellipse with no
     * terrain, no player and no sky left in the frame. Three things had gone wrong and they
     * compounded:
     *
     * <ul>
     * <li><b>{@link ReleaseMode#SLAM}.</b> A slam hangs {@code Mark.SIGIL_SLAM_FLASH} flat under
     * the caster's own hand at {@code tier.radius() * 1.3}, and that kind is the one mark in the
     * library that is a <em>filled disc</em> rather than a stroke: {@code rendertype_ground_mark}
     * lights it at {@code 2.5 * (1 - phase)} over a colour it mixes all the way to {@code vec3(1)}
     * for the first half of its life, on the additive twin. At the tier radius that is a
     * pure-white disc <b>7.8 blocks across, hung a stride in front of the caster's face</b>. It
     * survives on every other skill that asks for it because in first person that quad all but
     * contains the eye and is seen edge-on; the sword kit is judged from behind. It is also the
     * one
     * reading this skill may never give - Below commits to a <em>point</em>, and a slam stamps an
     * impact under the wielder's feet at the instant of the press.</li>
     * <li><b>The tier radius.</b> Every tier below zero resolves to {@code TierProfile.forTier(4)}
     * and its 3.0-block circle, 1.9 times the ring that actually catches - so the telegraph
     * promised a cylinder three and a half times the area of the one you had to leave, and
     * {@code TransientVisuals} then grew it another 1.4 on the slam. Pinned to
     * {@link #ERUPT_RADIUS} here: the circle at the committed point <em>is</em> the hit ring, at
     * whatever tier this skill is ever given.</li>
     * <li><b>The object was a sphere.</b> {@code Form.SPIKE_CLUSTER} is
     * {@code FxMesh.spikeCluster}, which lays its spikes over a whole sphere on the golden angle -
     * at six, three of them point downward - and {@code BodyPainter} scales {@code sizeA} on all
     * three axes and never reads {@code sizeB}, so the authored three-block height was dead code
     * and the drawing was a 1.6-block ball half buried in the floor. Nothing about it went up.</li>
     * </ul>
     *
     * <p>So the object is a {@code PLATE_FAN}: the only body form in the library that grows out of
     * {@code y = 0} and spends both extents, on the depth-writing {@code shardBody} path where a
     * thing can be an object rather than a light. Its glow is the rim that shader already carries.
     * The telegraph is a {@code CLOCK_SPOKES} ring laid flat, all hairline strokes, whose hand
     * sweeps on the effect's own phase - so the warning is a clock and not a glare - and
     * {@code ProfileRendererShell} sizes it off the synced radius, which is {@link #ERUPT_RADIUS}
     * by construction. A shallow pool of grit marks the soil line as the steel comes through; it
     * is the one thing here allowed outside the measurement, because debris thrown out of a hole
     * genuinely leaves the hole, the way a wake is allowed behind a forged wave.
     */
    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SWORD)
                .circle(CircleScript.of(SchoolMaterial.SWORD).emblem(EmblemId.UPTHRUST).frame(7)
                        .band(GlyphKind.TICK_BAND, 24, ColorRole.BRIGHT)
                        .band(GlyphKind.TOOTH_BAND, 12, ColorRole.DIM)
                        .spokes(6, 0.25F, true)
                        .core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .tier(TierProfile.forTier(definition().tier()).withRadius((float) ERUPT_RADIUS))
                // Tier 4 draws its windup through terrain, which for a circle lying on the floor
                // twenty blocks out means it is drawn over every block and every body between it
                // and the camera - including the wielder, whose feet this mark may never appear
                // on. Occluded, it reads as lying on the ground, which is where it is.
                .throughTerrain(false)
                .silhouette(Silhouette.body(Silhouette.Form.PLATE_FAN, FxKinds.Body.METAL_BANDS,
                        ERUPT_PLATES, (float) ERUPT_RADIUS, RISE_HEIGHT).forModes(1))
                .silhouette(Silhouette.mark(FxKinds.Mark.CLOCK_SPOKES, (float) ERUPT_RADIUS, ERUPT_PLATES)
                        .withRole(ColorRole.BRIGHT).forModes(0))
                .silhouette(Silhouette.swarm(Silhouette.Form.POOL, FxKinds.Smoke.DUST, 8,
                        (float) ERUPT_RADIUS, 0.5F).withRole(ColorRole.DIM).withOpacity(0.45F).forModes(1))
                // FUNNEL, not SLAM: the circle collapses to a quarter and goes, which is what the
                // frame actually does - it leaves the wielder and sinks under a place elsewhere.
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.DUST, FxKinds.Overlay.SHOCK_RING)
                .budget(3)
                .bounds(3.0F, 4.0F, 2.0F);
    }
}
