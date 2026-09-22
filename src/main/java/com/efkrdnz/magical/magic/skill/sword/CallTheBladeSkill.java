package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import com.efkrdnz.magical.magic.visual.TierProfile;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.server.level.ServerPlayer;

/**
 * SWORD T-3 - the toggle. One press and the swords are there; one more and they are gone.
 *
 * <p><b>Off means gone.</b> Not hidden, not idle, not drifting behind you costing upkeep: while
 * the steel is sheathed there is no formation entity in the world, nothing to draw, nothing
 * intercepting, no Watch running and no mana upkeep, and {@code SwordPassives} hands back no
 * bonus pool either. Drawing summons the wielder's whole complement - four, seven, ten or twelve
 * by the rung - into whatever stance they are standing in, in one motion. That is the entire
 * skill.
 *
 * <p>It used to be the class's authoring surface: a press wrote one bearing onto a lattice at the
 * crosshair, a sneak-press poured extra Edge onto it, and five named refusals taught the rules.
 * The wielder's verdict on that was <i>"none of the abilities are actually straightforward or
 * clear"</i>, and they were right - half the shape being authored sat behind the wielder's head
 * where the crosshair could not reach it. The shape is chosen from six designed stances now, by
 * {@link SwordStanceSkill}, and this key does the one thing its name always promised.
 *
 * <p><b>{@code selfManaged}</b>, which means {@code MagicCastingService.castViaRegistry} returns
 * before it resolves a stat, casts an aim ray, spends a point of mana or starts a clock - so
 * {@code ctx.aim()} is <b>null</b> here, and the definition's mana and cooldown are decoration
 * until {@link SwordService#payFor} is called. It is called once the press has established which
 * way it is toggling, so a press that refuses itself is never charged. Sheathing is free and
 * uncooled on purpose: putting your own swords away is not a power, and a wielder who cannot
 * afford to sheathe is a wielder stuck holding an Array.
 */
public final class CallTheBladeSkill implements SkillModule {

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

    /**
     * The cast circle's radius in blocks, and it is a property of the formation rather than of
     * the tier.
     *
     * <p>Every tier below zero resolves to {@code TierProfile.forTier(4)}, whose radius is 3.0 -
     * a disc six blocks across. The circle is anchored on the caster now rather than on a surface
     * the crosshair found, and the thing it is announcing is a formation that stands inside
     * {@code Formation.MAX_EXTENT} = 4.0 blocks of the wielder. A disc wider than the formation
     * reads as a spell going off somewhere near the swords; at 1.8 it is a ring the swords appear
     * out of, which is what happened.
     */
    public static final float MARK_RADIUS = 1.8F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CALL_THE_BLADE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                PlayerMagicState state = ctx.state();
                if (state.swordArray().drawn()) {
                    // Free and uncooled. See the class note: a wielder who cannot afford to put
                    // their own swords away is a wielder stuck holding them.
                    SwordService.sheathe(player, state);
                    state.sync(player);
                    return CastResult.SUCCESS;
                }
                if (!SwordService.payFor(player, state, definition(), -1)) {
                    return CastResult.FAILED;
                }
                SwordService.draw(player, state);
                state.sync(player);
                return CastResult.SUCCESS;
            }

            /**
             * The registry stops before the aim ray, the mana and the clock, and this skill takes
             * all three itself through {@link SwordService#payFor} - once it knows which way it
             * is toggling, because only one of the two directions is charged.
             */
            @Override
            public boolean selfManaged() {
                return true;
            }

            @Override
            public MobCastProfile mob() {
                // The formation is a shape on a PlayerMagicState. A mob has none, so there is
                // nothing here for one to cast - and NONE rather than null, because a null is an
                // NPE on the server thread inside entity ticking.
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.crush", "screen.magical.tuning.rise",
                        "screen.magical.tuning.reach", "screen.magical.tuning.hold", "screen.magical.tuning.thrift");
            }
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
                        // sample cell 0 - NEEDLE - at runtime with a completely green build.
                        // Twelve ticks rather than the lattice's twenty-four: one per sword the
                        // apex rung fields, so the ring the swords come out of is a count of the
                        // swords coming out of it.
                        .band(GlyphKind.TICK_BAND, 12, ColorRole.BRIGHT)
                        .core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                // On the caster and not on a surface the crosshair found: the toggle happens
                // where the wielder is standing, and there is no aim ray in a selfManaged handler
                // to have found a surface with.
                .anchor(CircleAnchor.GROUND)
                .tier(TierProfile.forTier(definition().tier()).withRadius(MARK_RADIUS))
                // Tier 4 draws its windup through terrain. Occluded, this one reads as lying on
                // the floor the wielder is standing on, which is where it is.
                .throughTerrain(false)
                // Never painted, and it must stay that way. SwordArrayEntity and SwordBladeEntity
                // both carry CALL_THE_BLADE's id so they wear this profile, and both renderers
                // call ProfileRendererShell.render, which walks profile.silhouettes() and paints
                // every one whose mode mask admits the entity's draw mode. There is no painter
                // registered under this id - CustomPainters.paint therefore takes its "visible
                // fallback so a missing painter is noticed" branch and draws Orb.PLASMA at
                // max(0.4, sizeA * 0.5) on the additive plasma_orb type, whose PLASMA branch is
                // core 1.8 mixed 85% toward white and then multiplied by the glow again: it clips
                // to white at every size. The Array's entity carries Life 0, so FxContext.fade()
                // never falls, and that was a one-block white disc parked on the wielder's chest
                // for as long as they held an Array, plus one on every blade in flight. An empty
                // mode mask is what says "this profile's object is drawn by its own renderer":
                // the silhouette stays because VisualProfile has no way to carry none, and
                // Family.CUSTOM keeps it out of the roster's uniqueness maps where it would
                // otherwise claim a form the school has not spent.
                .silhouette(Silhouette.custom("call_the_blade", 1.0F).forModes())
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.PRISM_RING)
                .budget(1)
                .bounds(1.5F, 1.5F, 1.5F);
    }
}
