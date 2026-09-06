package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.magic.MagicContent;

/**
 * Profiles for the KEPT skills (space_walker, black_flames + subs, gabriel + subs, negative-tier
 * content). Their world visuals stay on their bespoke renderers; the profile supplies the cast
 * circle, palette, sounds and first-person feedback so the shared cast language applies to them.
 * Their circle signatures are reserved here so new skills cannot reuse them.
 */
public final class MagicVisualContentKept {
    private MagicVisualContentKept() {}

    public static void register() {
        VisualProfiles.register(MagicContent.SPACE_WALKER, VisualProfile.builder(MagicContent.SPACE_WALKER)
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.COMPASS).frame(4).band(GlyphKind.DASHED_RING, 16).stamps(StampId.NEEDLE, 4).core(CoreKind.CROSS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("space_walker", 1.5F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.PRISM_RING));
        VisualProfiles.register(MagicContent.BLACK_FLAMES, VisualProfile.builder(MagicContent.BLACK_FLAMES)
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.THORN_CROWN).frame(5).band(GlyphKind.TOOTH_BAND, 20).band(GlyphKind.PETAL_BAND, 10, ColorRole.INK).stamps(StampId.THORN, 5).pentagram().core(CoreKind.VOID_PIT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("black_flames", 2.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.INK_BLEED));
        VisualProfiles.register(MagicContent.GABRIEL, VisualProfile.builder(MagicContent.GABRIEL)
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.WING).frame(14, CircleScript.FrameStyle.NESTED).band(GlyphKind.PETAL_BAND, 24).band(GlyphKind.RUNE_BAND, 12, ColorRole.BASE).stamps(StampId.FEATHER, 12).orbit(7, 0.86F, 6).core(CoreKind.SUNBURST).stack(3, 0.5F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.GROUND)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("gabriel", 3.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.FEATHER, FxKinds.Overlay.BLOOM_RAYS));
        VisualProfiles.register(MagicContent.ABYSSAL_DISCHARGE, VisualProfile.builder(MagicContent.ABYSSAL_DISCHARGE)
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.VOID_RING).frame(9).band(GlyphKind.WAVE_BAND, 18).stamps(StampId.TOOTH, 9).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("abyssal_discharge", 4.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.HEARTBEAT));
    }
}
