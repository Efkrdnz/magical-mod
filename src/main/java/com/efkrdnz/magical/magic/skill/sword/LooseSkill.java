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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SWORD T-3 - throw every sword you have, in the shape you are standing in.
 *
 * <p><b>There is no blade cap: how many swords are present is the cap.</b> That is the one good
 * idea the old forward projection had and it is kept for its reason - a wielder who spent six
 * swords a moment ago Looses with six, and the number has been on screen the whole time. What is
 * gone is the projection itself: it fired only the bearings already pointing the right way, so
 * the volley's size was a property of a shape the wielder had authored blind and could not see.
 *
 * <p>The shape of the volley is the stance's {@link Pattern}, which is the whole of "different
 * stances do different things with abilities". Guard throws a wall abreast, Vanguard a column one
 * behind another, Crown a ring closing from every side, Wings two converging arcs, Coil a wide
 * fast scatter, Rain from overhead. One press, six pictures, and the wielder chose which by
 * standing somewhere.
 *
 * <p>Each sword leaves <em>its own place in the formation</em> - the position it was standing in
 * at the instant of the press, which is where the wielder can see it - and converges on the point
 * the pattern gave it relative to whatever is under the crosshair.
 *
 * <p>A plain press, so the registry bills the mana and the cooldown and casts the aim ray. Every
 * sword that leaves goes out through {@code SwordService.spendSword}, which is the one door out
 * of the formation and the reason the count stays honest without anybody adding anything up; a
 * sword refused by {@link SwordBladeEntity#MAX_IN_FLIGHT} is never spent.
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
     * What the wielder is told when they have no swords out, or when every one of them was
     * refused for want of room in the air.
     *
     * <p>A press that finds no <em>body</em> is not a refusal: the volley still happens, aimed at
     * whatever point the crosshair found, because a wall is a thing you can throw swords at.
     */
    private static final String KEY_NOTHING_TO_THROW = "message.magical.sword_none_present";

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
                int mask = SwordService.presentMask(player, state);
                if (mask == 0) {
                    // No steel out, or all of it already away. A refusal costs nothing: FAILED
                    // refunds the mana and writes no cooldown.
                    player.displayClientMessage(Component.translatable(KEY_NOTHING_TO_THROW), true);
                    return CastResult.FAILED;
                }

                LivingEntity body = ctx.aim().living();
                Vec3 target = body != null ? body.getBoundingBox().getCenter() : ctx.aim().point();
                Pattern pattern = state.swordArray().stance().pattern();
                Vec3 look = ctx.look();
                // The pattern speaks in the target's own frame - +Z from the caster toward the
                // target - so it is turned into the world by the same rotation ArrayPose uses for
                // everything else, about an origin at the target rather than at the wielder.
                double[] line = {look.x, look.y, look.z};
                Frame aim = new Frame(target.x, target.y, target.z,
                        ArrayPose.yawOf(line), ArrayPose.pitchOf(line), 1.0F);
                double reach = target.distanceTo(player.getEyePosition());
                ServerLevel level = ctx.level();

                int count = Integer.bitCount(mask);
                int fired = 0;
                int nth = 0;
                for (int index = 0; index < Integer.SIZE; index++) {
                    if ((mask & (1 << index)) == 0) {
                        continue;
                    }
                    Vec3 at = SwordService.swordPosition(player, state, index);
                    if (at == null) {
                        continue;
                    }
                    Slot spread = Pattern.spread(pattern, nth++, count, reach);
                    double[] offset = ArrayPose.worldOffset(spread, aim);
                    Vec3 to = target.add(offset[0], offset[1], offset[2]);
                    SwordBladeEntity blade = SwordBladeEntity.loose(level, player, MagicContent.LOOSE.id(),
                            at, to.subtract(at), index, 1,
                            SwordMath.bladeDamage(), ctx.stats().knockback(),
                            ctx.stats().speed(), ctx.duration());
                    if (blade == null) {
                        // The wielder already has MAX_IN_FLIGHT out. Leave this sword where it is
                        // rather than paying for a blade that was refused.
                        continue;
                    }
                    SwordService.spendSword(player, state);
                    fired++;
                }
                if (fired == 0) {
                    player.displayClientMessage(Component.translatable(KEY_NOTHING_TO_THROW), true);
                    return CastResult.FAILED;
                }

                SwordService.tendArrayEntity(player, state);
                state.sync(player);
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
                // A mob has no PlayerMagicState and therefore no swords, so the present mask is
                // always zero for one - NONE rather than null, because a null here is an NPE on
                // the server thread inside entity ticking.
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT;
            }
        };
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
                // Never hung in the world: the circle is where the HUD card takes its emblem from.
                .anchor(CircleAnchor.NONE)
                // Never painted, and the empty mask is the whole of the statement.
                //
                // Every blade Loose fires is a SwordBladeEntity carrying this skill's id, so each
                // of them wears this profile; SwordBladeRenderer extends ProfileRendererShell and
                // calls super.render, which walks profile.silhouettes() and paints every one whose
                // mode mask admits the blade's draw mode. Nothing is registered under the painter
                // id "loose", so that walk went straight into CustomPainters' miss branch, which
                // drew Orb.PLASMA at max(0.4, sizeA * 0.5) - a one-block additive disc on a
                // ONE, ONE pass, which clips to white at every size and every distance - centred
                // on every blade in the air. Call the Blade and the Bearing were emptied when that
                // was found and this one was missed, which is why a volley capture still came back
                // with white discs on the blades while the Array's captures came back clean.
                //
                // The silhouette stays rather than going away: VisualProfile.build() has no way to
                // carry none and inserts a default PLASMA orb into an empty list, which is the same
                // disc by another road, and CustomPainterCoverageTest reads this declaration to
                // check it against its own list of ids that are drawn somewhere else. The extent
                // is left at 2.0 because nothing reads it any more and changing it would only
                // suggest that something does.
                .silhouette(Silhouette.custom("loose", 2.0F).forModes())
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.NONE)
                .firstPerson(ProfileCues.FirstPersonSpec.NONE)
                // Never fired: no sword hit goes through SpellFx.impact, and SwordSteelOnlyTest holds
                // that. It stays because VisualProfiles.validate keys every school victim overlay, and
                // six Sword profiles on the default overlay is five hard collisions at common setup.
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.FLASH)
                .budget(2)
                .bounds(4.0F, 3.0F, 3.0F);
    }
}
