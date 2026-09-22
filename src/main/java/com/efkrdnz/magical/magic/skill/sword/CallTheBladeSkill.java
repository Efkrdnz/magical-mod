package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPrice;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.PlantResult;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordService;
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
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * SWORD T-3 - write a bearing onto the Array, or put more metal on one already written.
 *
 * <p>The structure's only write operation and the entire authoring surface of the class. There is
 * no build screen: <em>where you stand and where you point when you call each blade is the
 * build</em>, permanently, and it is the half of the kit that is saved. Everything else the class
 * does is a projection of what this skill wrote.
 *
 * <p>A plain press hangs one Edge on the bearing under the crosshair. A sneak-press pours as much
 * as the rung's cap and the wielder's loose metal allow, and pays {@link #POUR_FLAT} {@code +}
 * {@link #POUR_PER_EDGE} per point for it - the registry has already taken the definition's own
 * mana by the time this handler runs, so only the difference is charged here, and only once the
 * plant has been accepted. Every refusal is printed by name and costs nothing at all:
 * {@link CastResult#FAILED} hands the base mana back and writes no cooldown.
 *
 * <p>A plain press, so the registry bills it, resolves the stats and casts the aim ray - which is
 * why {@code ctx.aim()} may be dereferenced here and may <b>not</b> be in the self-managed and
 * hold-gated halves of this kit.
 */
public final class CallTheBladeSkill implements SkillModule {

    /** How far out a bearing may be hung, in blocks. {@code Station.REACH_MAX} is the same 6. */
    public static final double AIM_RANGE = 6.0D;

    /** Blocks <em>and</em> bodies: a zero tolerance would quietly mean blocks only. */
    public static final double AIM_TOLERANCE = 1.6D;

    /** The flat half of a sneak-press pour's price, in mana. */
    public static final int POUR_FLAT = 4;

    /** The per-point half of a sneak-press pour's price, in mana. */
    public static final int POUR_PER_EDGE = 2;

    private static final String KEY_FULL = "message.magical.sword_full";
    private static final String KEY_TOO_CLOSE = "message.magical.sword_too_close";
    private static final String KEY_TOO_DEAR = "message.magical.sword_too_dear";
    private static final String KEY_NO_EDGE = "message.magical.sword_no_edge";
    private static final String KEY_OUT_OF_REACH = "message.magical.sword_out_of_reach";

    /**
     * The mark this skill wears on its circle.
     *
     * <p>The design asks for a single vertical edge of its own, and {@code EmblemId} carries no
     * such cell yet - emblems are globally unique and every existing one but {@code GEAR} is
     * already spoken for. It is held in one constant so that adding the cell is a one-word change
     * here rather than a hunt through the profile.
     */
    private static final EmblemId EMBLEM = EmblemId.STATION;

    /** Frame sides, unique within the SWORD school. Its six skills take 3..8 in kit order. */
    private static final int FRAME_SIDES = 3;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CALL_THE_BLADE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player) || ctx.aim() == null) {
                    return CastResult.FAILED;
                }
                PlayerMagicState state = ctx.state();
                SwordArray array = state.swordArray();
                Frame frame = SwordService.frame(player);

                Vec3 offset = ctx.aim().point().subtract(origin(frame));
                Station bearing = Station.nearestTo(offset.x, offset.y, offset.z, frame, 0);
                if (bearing == null) {
                    return refuse(player, KEY_OUT_OF_REACH);
                }

                int paid = ctx.stats().manaCost();
                // A plain press is one Edge at the definition's own price, which the registry has
                // already taken - the pour's arithmetic must not be allowed to clamp it, or a
                // thrifty wielder with an empty pool is refused a press they have paid for.
                int want = 1;
                if (ctx.sneak()) {
                    want = Math.min(array.rules().maxEdge(), SwordService.loose(player, state));
                    want = Math.min(want, affordablePour(player, state, paid));
                }
                if (want < 1) {
                    return refuse(player, KEY_NO_EDGE);
                }

                int before = array.bound();
                PlantResult result = array.plant(bearing.withEdge(want), SwordService.spent(player));
                if (result != PlantResult.PLANTED && result != PlantResult.TOPPED_UP) {
                    return refuse(player, refusalKey(result));
                }

                // The plant clamps, so what it actually took is the difference in metal in the
                // air and never the number that was asked for.
                int placed = array.bound() - before;
                int surcharge = ctx.sneak() ? Math.max(0, POUR_FLAT + POUR_PER_EDGE * placed - paid) : 0;
                if (surcharge > 0) {
                    MagicSinService.spendManaForSkill(player, state, surcharge);
                }
                // The slow tick is ten ticks wide and a wielder who has just called their first
                // blade should not spend half a second looking at nothing.
                SwordService.tendArrayEntity(player, state);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public double aimTolerance() {
                return AIM_TOLERANCE;
            }

            @Override
            public boolean aimDropsToGround() {
                return false;
            }

            @Override
            public MobCastProfile mob() {
                // An Array is a saved, authored shape on a PlayerMagicState. A mob has none, so
                // there is nothing here for one to cast - and NONE rather than null, because a
                // null is an NPE on the server thread inside entity ticking.
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.crush", "screen.magical.tuning.rise",
                        "screen.magical.tuning.reach", "screen.magical.tuning.hold", "screen.magical.tuning.thrift");
            }
        };
    }

    // ---- the lattice ------------------------------------------------------------------------------

    /**
     * The frame's origin as a vector, which is the point every station's offset is measured from.
     *
     * <p>All that is left here of the lattice: this skill used to carry its own inverse of
     * {@link Station#unitBearing()}, character for character the same as the one
     * {@code SwordRiteService} carried privately, and a sign in either copy would have been a
     * silent mirror with a green build. The inverse is {@link Station#nearestTo} now and there is
     * one of it.
     */
    public static Vec3 origin(Frame frame) {
        return new Vec3(frame.x(), frame.y(), frame.z());
    }

    // ---- the price and the refusals ---------------------------------------------------------------

    /**
     * How much metal the wielder can still pay to pour, on top of what the registry already took.
     *
     * <p>A pour of {@code n} costs {@code POUR_FLAT + POUR_PER_EDGE * n} and the definition's own
     * mana is already spent, so the surcharge is that less {@code paid} and the pool bounds it.
     * <b>It is a third clamp on the pour and not a refusal</b>, which is the same decision
     * {@link SwordArray#plant} makes about the rung's cap and the loose Edge: a cap is a cap and
     * there is no result for hitting one. Clamping here rather than charging afterwards is what
     * keeps the two halves of the price honest - a pour is never larger than what was paid for it,
     * and a refusal is never charged at all.
     *
     * <p>Deliberately conservative: {@link PlayerMagicState#mana()} is the pool alone, while
     * {@code spendManaForSkill} can also reach a Greed hoard or convert barrier, so a wielder
     * carrying either pours a little less than they could have afforded rather than a little more.
     */
    private static int affordablePour(ServerPlayer player, PlayerMagicState state, int paid) {
        if (MagicPrice.waived(player)) {
            return Station.EDGE_MAX;
        }
        return Math.max(0, (state.mana() + paid - POUR_FLAT) / POUR_PER_EDGE);
    }

    /** Every refusal is named on the actionbar, and named refusals are the whole teaching surface. */
    private static CastResult refuse(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
        return CastResult.FAILED;
    }

    private static String refusalKey(PlantResult result) {
        return switch (result) {
            case FULL -> KEY_FULL;
            case TOO_CLOSE -> KEY_TOO_CLOSE;
            case TOO_DEAR -> KEY_TOO_DEAR;
            case NO_EDGE -> KEY_NO_EDGE;
            // PLANTED and TOPPED_UP never reach here; OUT_OF_REACH is the only one left.
            default -> KEY_OUT_OF_REACH;
        };
    }

    // ---- the look ----------------------------------------------------------------------------------

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SWORD)
                .circle(CircleScript.of(SchoolMaterial.SWORD).emblem(EMBLEM).frame(FRAME_SIDES)
                        // No stamp band. StampId.EDGE is the thirty-third stamp so its cell is
                        // 32, and STAMP_BAND packs a cell into a five-bit paramB, which would
                        // sample cell 0 - NEEDLE - at runtime with a completely green build. The
                        // twenty-four ticks below are the yaw lattice, which is the better ring
                        // for this skill anyway: one tick per bearing a blade can be called to.
                        .band(GlyphKind.TICK_BAND, 24, ColorRole.BRIGHT)
                        .core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("call_the_blade", 1.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.PRISM_RING)
                .budget(1)
                .bounds(1.5F, 1.5F, 1.5F);
    }
}
