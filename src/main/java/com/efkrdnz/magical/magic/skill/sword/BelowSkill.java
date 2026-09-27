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
import com.efkrdnz.magical.magic.sword.SwordImpacts;
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
 * <p>The stance's {@link Pattern} is the shape the swords come up in and the shape the ones that
 * struck nothing are left standing in, so the six postures give six pictures of the same skill
 * without a per-skill dial anywhere.
 *
 * <p><b>The warning is the ground, and it stays where it was put.</b> Below commits to a point and
 * not to a body, which reads as a bug to anyone trained on homing area attacks unless the warning
 * visibly stays put while the target walks out of it. So for the whole of it the ground on the rim
 * of the ring that will catch shudders - crumbs of whatever it is made of, every
 * {@link #TREMOR_INTERVAL} ticks, sent by the server - and nothing else is drawn: no ring and no
 * steel. The dodge is arithmetic and not a promise: sixteen ticks of warning to clear
 * {@link #ERUPT_RADIUS} blocks laterally is 5.7 ticks sprinting and 7.4 walking, and 24.7
 * sneaking. Air is not a refuge - no floor is consulted anywhere in this file, the origin is a
 * point in three dimensions, and jumping peaks at 1.25 blocks against a cylinder 3.5 tall. Speed
 * is the refuge.
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

    /** Ticks of descent before the warning proper. */
    public static final int SINK_TICKS = 6;

    /** Ticks after the sink before the strike is locked. With the sink, this is the whole warning. */
    public static final int TELEGRAPH_TICKS = 10;

    /** Nothing about the strike can change after this tick. */
    public static final int COMMIT_TICK = SINK_TICKS + TELEGRAPH_TICKS;

    /** Ticks the risers take to come up out of the ground to their full height. */
    public static final int RISE_TICKS = 6;

    /** Press to hit. Twenty-two, and sixteen of them are the ground telling you to move. */
    public static final int HIT_TICK = COMMIT_TICK + RISE_TICKS;

    /** The cylinder's radius, and the distance a dodge has to cover. */
    public static final double ERUPT_RADIUS = 1.6D;

    /** The cylinder runs from half a block under the origin to this far above it. */
    public static final double ERUPT_HEIGHT = 3.0D;

    /**
     * How high the middle of a riser comes up to, over the committed point.
     *
     * <p>A drawn blade reaches 0.63 blocks either way along its own axis, so the risers top out at
     * 2.83: inside {@link #ERUPT_HEIGHT}, and well over half of it. {@code BelowSilhouetteTest}
     * measures both against the blade renderer's own geometry rather than trusting this sentence.
     */
    public static final double RISE_LIFT = 2.2D;

    /**
     * How far out the widest riser comes up, from the committed point to its middle.
     *
     * <p>A drawn blade reaches 0.41 blocks sideways, so the widest steel stands 1.51 out: inside
     * {@link #ERUPT_RADIUS}, and far enough out that a volley comes up as a ring and not a knot.
     */
    public static final double RISE_SPREAD = 1.1D;

    /** Ticks between two shudders of the ground during the warning. */
    public static final int TREMOR_INTERVAL = 2;

    /**
     * The draw mode the risers are drawn in, and it is two rather than one on purpose: every
     * {@code SwordBladeEntity} is draw mode one (its mode byte is {@code MODE_SILENT}), and a blade
     * Below leaves standing in the ground carries this skill's id - so a riser painter on mode one
     * would be painted again round every one of them.
     */
    public static final int RISE_DRAW_MODE = 2;

    /** Synced for the painter: how many swords went under. */
    public static final String DATA_SWORDS = "Swords";

    /** Synced for the painter: the ordinal of the stance's pattern at the press. */
    public static final String DATA_PATTERN = "Pattern";

    /** Synced for the painter: the wielder's yaw at the press, which the pattern is turned by. */
    public static final String DATA_YAW = "Yaw";

    /**
     * How wide the swords that struck nothing are laid out, as a reach handed to the pattern.
     *
     * <p>Deliberately much smaller than the patterns own constants would give at full size: a
     * ring of swords standing two blocks out reads as a fence somebody built rather than as the
     * wreckage of one eruption. The spans the patterns carry are aiming spans measured off a
     * target, so they are scaled down to this rather than passed through.
     */
    private static final double PLANT_SPREAD = 0.35D;

    private static final Vec3 DOWN = new Vec3(0.0D, -1.0D, 0.0D);

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
     * refunds. A wielder with no swords present still pays and the ground still shakes: the skill
     * did what it does, and what it does is decided by how much steel they still have.
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
        // The stance is read once, here, and not again at the strike: a wielder who changes
        // posture during the sixteen ticks of warning has not moved the swords that are already
        // under the ground, and the shape they come up in is the shape they went down in.
        int pattern = state.swordArray().stance().pattern().ordinal();
        CompoundTag scratch = eruption.serverData();
        scratch.putInt(TAG_COUNT, sunk);
        scratch.putInt(TAG_PATTERN, pattern);
        // The same numbers again for every client, which draws the risers from them with the same
        // arithmetic the planted swords are laid out with here.
        CompoundTag picture = new CompoundTag();
        picture.putInt(DATA_SWORDS, sunk);
        picture.putInt(DATA_PATTERN, pattern);
        picture.putFloat(DATA_YAW, player.getYRot());
        eruption.setSyncedData(picture);
        // The first shudder is the press itself; the entity's own tick never sees age zero.
        SwordImpacts.tremor(ctx.level(), origin, ERUPT_RADIUS);
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
     * for the rest of the warning, {@code CLOSING} for the rise. The ground shudders on
     * {@link #tremorAt} through all of the warning and stops the tick the strike commits.
     */
    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                int age = effect.tickCount;
                if (tremorAt(age)) {
                    SwordImpacts.tremor(effect.serverLevel(), effect.position(), ERUPT_RADIUS);
                }
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
     * <p>How many swords went down was fixed six ticks earlier, at the press; this is the other
     * half of the number, and with one flat {@link SwordMath#bladeDamage} per sword the whole
     * eruption is a multiplication the wielder can read off their own HUD before they press it.
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
        // The ground breaks across the whole ring the steel came up through, hit or miss.
        SwordImpacts.eruption(level, origin, ERUPT_RADIUS);

        List<LivingEntity> caught = amount <= 0.0F ? List.of()
                : SkillTargets.hostilesInCylinder(level, effect.owner(), origin, ERUPT_RADIUS, ERUPT_HEIGHT);
        for (LivingEntity victim : caught) {
            victim.invulnerableTime = 0;
            MagicDamageService.hurt(victim, level.damageSources().indirectMagic(effect, effect.owner()),
                    amount, effect.skillId());
            // Straight up: the blades came out of the floor, so the shove has no direction in the
            // plane and SkillTargets.shove with zero strength is exactly that.
            SkillTargets.shove(victim, origin, 0.0D, knockback);
            // Steel coming up through a body throws its sparks up and out of the top of it. A cut
            // rings against the travel it is handed, so it is handed the way down.
            SwordImpacts.cut(level, victim.getBoundingBox().getCenter(), DOWN);
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
     * losing: Guard leaves a fence across the path, Crown a ring round the spot, Rain a scatter.
     *
     * <p>The pattern's y is dropped and its spread scaled to {@link #PLANT_SPREAD}, and it is
     * turned by the yaw the risers were drawn at, so what is left standing is the eruption's shape.
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
        // Yaw only: the eruption came straight up, so there is no pitch to apply and a facing
        // would only tilt a layout that is already lying on the floor.
        Frame flat = new Frame(origin.x, origin.y, origin.z, effect.syncedData().getFloat(DATA_YAW), 0.0F, 1.0F);
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
     * Where riser {@code index} of {@code count} comes up, as {@code {dx, dz}} from the committed
     * point.
     *
     * <p>The stance's pattern laid on the ground - a pattern stood square-on to the aim tips over
     * onto its back, so its height becomes depth, and one already lying level keeps its plan -
     * centred on the mark, scaled so the widest riser stands {@link #RISE_SPREAD} out, and turned by
     * the wielder's yaw at the press. A single sword comes up on the mark itself. Pure, so the
     * painter and the test run the same arithmetic.
     */
    public static double[] riseOffset(Pattern pattern, int index, int count, float yaw) {
        int n = Math.max(1, count);
        if (n == 1) {
            return new double[] {0.0D, 0.0D};
        }
        double[] xs = new double[n];
        double[] zs = new double[n];
        double cx = 0.0D;
        double cz = 0.0D;
        for (int i = 0; i < n; i++) {
            Slot slot = Pattern.spread(pattern, i, n, RISE_SPREAD);
            xs[i] = slot.x();
            zs[i] = slot.z() + slot.y();
            cx += xs[i];
            cz += zs[i];
        }
        cx /= n;
        cz /= n;
        double widest = 0.0D;
        for (int i = 0; i < n; i++) {
            widest = Math.max(widest, Math.hypot(xs[i] - cx, zs[i] - cz));
        }
        int i = Math.max(0, Math.min(n - 1, index));
        double scale = widest < 1.0E-9D ? 0.0D : RISE_SPREAD / widest;
        double[] turned = ArrayPose.rotate((xs[i] - cx) * scale, 0.0D, (zs[i] - cz) * scale,
                new Frame(0.0D, 0.0D, 0.0D, yaw, 0.0F, 1.0F));
        return new double[] {turned[0], turned[2]};
    }

    /**
     * How high the middle of every riser is at {@code age}, over the committed point.
     *
     * <p>At the commit a riser is wholly underground - its middle {@code buried} below the mark,
     * where {@code buried} is how far the drawn blade reaches along its own axis - and it comes up
     * fast and settles, to {@link #RISE_LIFT} on the very tick the strike lands.
     */
    public static double riseHeight(float age, double buried) {
        double t = Math.max(0.0D, Math.min(1.0D, (age - COMMIT_TICK) / (double) RISE_TICKS));
        double eased = 1.0D - (1.0D - t) * (1.0D - t);
        return -buried + (RISE_LIFT + buried) * eased;
    }

    /** Whether the ground shudders on this tick of the eruption: every other tick of the warning. */
    public static boolean tremorAt(int age) {
        return age >= 0 && age < COMMIT_TICK && age % TREMOR_INTERVAL == 0;
    }

    /**
     * Which subset of the drawing is live, as a function of the eruption's age.
     *
     * <p>Nothing during the warning: the warning is the ground, and steel standing in the world
     * during it would be read as the dodge instead of the ground. The risers from the tick the
     * strike stops being changeable, on {@link #RISE_DRAW_MODE}; {@link #commit} flips it on that
     * tick for that reason.
     */
    public static int drawModeAt(int age) {
        return age < COMMIT_TICK ? 0 : RISE_DRAW_MODE;
    }

    /** The draw mode as a {@code SpellEffectEntity} mode byte, with bit 0 - the sneak flag - kept. */
    private static byte modeFor(byte current, int drawMode) {
        return (byte) ((current & 1) | (drawMode << 1));
    }

    /**
     * {@code EmblemId.UPTHRUST} is three blades of unequal height breaking up through a ground line.
     * The circle carries it to the HUD card and the codex and is never hung in the world:
     * {@link CircleAnchor#NONE}, so the press draws no ring at the mark and the release no flash at
     * the hand. The one thing drawn is the risers - Duskfall, one per sword that went under, painted
     * by {@code SwordPainters.below} off {@link #riseOffset} and {@link #riseHeight} - and the ground
     * breaking is vanilla crumbs thrown by {@code SwordImpacts}, not a silhouette.
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
                .anchor(CircleAnchor.NONE)
                .silhouette(Silhouette.custom("below", (float) ERUPT_RADIUS).forModes(RISE_DRAW_MODE))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.NONE)
                .firstPerson(ProfileCues.FirstPersonSpec.NONE)
                // Never fired: no sword hit goes through SpellFx.impact, and SwordSteelOnlyTest holds
                // that. It stays because VisualProfiles.validate keys every school victim overlay, and
                // six Sword profiles on the default overlay is five hard collisions at common setup.
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.DUST, FxKinds.Overlay.SHOCK_RING)
                .budget(3)
                .bounds(3.0F, 4.0F, 2.0F);
    }
}
