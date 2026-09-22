package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.Projection;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordMath;
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
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SWORD T-3 - throw the half of the Array that is already pointing the right way.
 *
 * <p>The forward projection over a re-anchored origin, and <b>there is no blade cap</b>: the
 * projection is the cap. A wielder who authored a forward cone fires all of it; a wielder who
 * authored a full ring fires exactly half, always, and nobody had to pick a number. A defensive
 * shape halves its own strike by construction.
 *
 * <p>Two things happen and the order between them matters. The blades leave <em>their own
 * bearings</em>, which are where the formation is standing at the instant of the press, and fly to
 * the body under the crosshair. Only then does the frame bind to that body - so the stations that
 * did <em>not</em> fire are the ones left hanging around the enemy, which is precisely the leash
 * that makes their footwork spend the wielder's budget from Sword Saint on. Run, and his own Array
 * starts tearing itself apart at you.
 *
 * <p>A plain press, so the registry bills the mana and the cooldown and casts the aim ray. Every
 * blade goes out through {@code SwordService.detach}, which is the one door out of a station and
 * the reason conservation holds without anybody counting; a blade refused by
 * {@link SwordBladeEntity#MAX_IN_FLIGHT} leaves its station's Edge exactly where it was.
 *
 * <p>The wound itself is {@code SwordBladeEntity}'s and it carries both halves of the i-frame
 * rule - the victim's {@code invulnerableTime} cleared so a fan landing in one tick lands whole,
 * and a per-blade per-victim clock so that clearing it is not twelve full hits in one tick.
 */
public final class LooseSkill implements SkillModule {

    /** How far the crosshair reaches for a body to bind. Past this, nothing is bound. */
    public static final double AIM_RANGE = 28.0D;

    /** Blocks <em>and</em> bodies: a zero tolerance would quietly mean blocks only. */
    public static final double AIM_TOLERANCE = 1.6D;

    /**
     * What the wielder is told when the forward projection is empty, or when every blade in it
     * was refused for want of room in the air.
     *
     * <p>The school ships five refusal strings and all five belong to Call the Blade; this is the
     * nearest of them that is also true - there is no edge pointing that way - and a dedicated
     * {@code message.magical.sword_nothing_forward} would read better. A press that finds no body
     * is <em>not</em> a refusal: the throw still happens along the look and simply binds nothing,
     * because a bind is a leash and there is nothing on the end of it.
     */
    private static final String KEY_NOTHING_TO_THROW = "message.magical.sword_no_edge";

    /**
     * The mark this skill wears on its circle.
     *
     * <p>The design asks for a leaning blade with a trailing tick, and {@code EmblemId} carries no
     * such cell yet - emblems are globally unique and every existing one but {@code GEAR} is
     * already spoken for. Held in one constant so adding the cell is a one-word change here.
     */
    private static final EmblemId EMBLEM = EmblemId.VOLLEY;

    /** Frame sides, unique within the SWORD school. Its six skills take 3..8 in kit order. */
    private static final int FRAME_SIDES = 6;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LOOSE;
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
                Vec3 look = ctx.look();

                int[] forward = Projection.forward(array, frame, new double[] {look.x, look.y, look.z});
                if (forward.length == 0) {
                    // Nothing manned is facing the way the wielder is looking, so there is
                    // nothing to throw. A refusal costs nothing: FAILED refunds the mana and
                    // writes no cooldown.
                    player.displayClientMessage(Component.translatable(KEY_NOTHING_TO_THROW), true);
                    return CastResult.FAILED;
                }

                LivingEntity body = ctx.aim().living();
                Vec3 target = body != null ? body.getBoundingBox().getCenter() : ctx.aim().point();
                // Taken once, before any Edge moves: every blade in this volley is as sharp as
                // the Array was at the press, and a station emptying mid-volley must not blunt
                // the ones behind it.
                int strain = SwordService.strain(player);
                ServerLevel level = ctx.level();

                int fired = 0;
                for (int slot : forward) {
                    Station station = array.station(slot);
                    if (station == null || !station.manned()) {
                        continue;
                    }
                    int edge = station.edge();
                    Vec3 at = stationAt(station, frame);
                    SwordBladeEntity blade = SwordBladeEntity.loose(level, player, MagicContent.LOOSE.id(),
                            at, target.subtract(at), slot, edge,
                            SwordMath.bladeDamage(edge, strain), ctx.stats().knockback(),
                            ctx.stats().speed(), ctx.duration());
                    if (blade == null) {
                        // The wielder already has MAX_IN_FLIGHT out. Leave this station's metal
                        // on it rather than paying for a blade that was refused.
                        continue;
                    }
                    SwordService.detach(player, state, slot, edge);
                    fired++;
                }
                if (fired == 0) {
                    player.displayClientMessage(Component.translatable(KEY_NOTHING_TO_THROW), true);
                    return CastResult.FAILED;
                }

                // Last, and only once something has actually left: the origin goes onto whoever
                // was under the crosshair, and what is left of the shape hangs on them.
                if (body != null) {
                    SwordService.bindTo(player, body);
                }
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
                // A mob has no PlayerMagicState and therefore no Array, so the forward projection
                // is always empty for one - NONE rather than null, because a null here is an NPE
                // on the server thread inside entity ticking.
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT;
            }
        };
    }

    /** Where a station's blade is standing right now, in world coordinates. */
    private static Vec3 stationAt(Station station, Frame frame) {
        double[] offset = ArrayPose.worldOffset(station, frame);
        return new Vec3(frame.x() + offset[0], frame.y() + offset[1], frame.z() + offset[2]);
    }

    // ---- the look ------------------------------------------------------------------------------

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SWORD)
                .circle(CircleScript.of(SchoolMaterial.SWORD).emblem(EMBLEM).frame(FRAME_SIDES)
                        .band(GlyphKind.TOOTH_BAND, 18, ColorRole.BRIGHT)
                        // Sixteen facets in DIM, and both numbers are corrections.
                        //
                        // INK is not the darkest of four colours, it is a render mode.
                        // GlyphCirclePainter switches on the role alone and sends an INK layer to
                        // glyphVoid(), a straight-alpha pass (SRC_ALPHA / ONE_MINUS_SRC_ALPHA),
                        // where the shader writes tint * 0.08 over whatever is behind it instead
                        // of adding to it. This skill carries its own colour, so its ink slot
                        // derives to 0x001440 and 8% of that is (0,2,5) at full coverage: an
                        // opaque black ring over the sand, over an iron golem and over the
                        // daylight sky, and the only opaque black anything in the mod draws. The
                        // schools that ask for INK - Blood, Dark, Void, Eldritch - want a member
                        // that darkens the ground. A school of polished steel does not, and the
                        // three other SWORD skills with a second band all take DIM, which goes
                        // out on glyphInk() (ONE / ONE) and so cannot subtract light from
                        // anything.
                        //
                        // Six was the other half of it. FACET_BAND strokes |lu| + 0.75|c| = 0.85
                        // per cell, so one facet's run along the band against its rise across it
                        // is 1.5 * PI * rMid / (count * (r1 - r0)) - scale-free, since every term
                        // is a fraction of the circle's radius. On the 0.62..0.71 annulus the
                        // builder hands a second band, six comes to 5.8:1: ten degrees off the
                        // tangent, at which the diamonds stretch until their tips meet and the
                        // band stops reading as facets and starts reading as two smooth
                        // overlapping circles. Sixteen is 2.2:1 - a ring of sixteen small blades,
                        // which is the volley said in the right number as well as the right
                        // shape: many, outward, fast.
                        .band(GlyphKind.FACET_BAND, 16, ColorRole.DIM)
                        .core(CoreKind.SUNBURST, ColorRole.HOT).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("loose", 2.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.FLASH)
                .budget(2)
                .bounds(4.0F, 3.0F, 3.0F);
    }
}
