package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.visual.ProfileCues.BoundsSpec;
import com.efkrdnz.magical.magic.visual.ProfileCues.FirstPersonPreset;
import com.efkrdnz.magical.magic.visual.ProfileCues.FirstPersonSpec;
import com.efkrdnz.magical.magic.visual.ProfileCues.ImpactSpec;
import com.efkrdnz.magical.magic.visual.ProfileCues.LingerCue;
import com.efkrdnz.magical.magic.visual.ProfileCues.ReleaseCue;
import com.efkrdnz.magical.magic.visual.ProfileCues.SoundSpec;
import com.efkrdnz.magical.magic.visual.ProfileCues.TrailSpec;
import com.efkrdnz.magical.magic.visual.ProfileCues.WindupCue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything a skill looks, feels and sounds like, registered next to its definition. Plain data
 * only, so it is safe on the dedicated server; client painters turn it into draw calls.
 */
public record VisualProfile(
        ResourceLocation skillId,
        SchoolMaterial material,
        int paletteVariant,
        Palette palette,
        TierProfile tier,
        CircleScript castCircle,
        CircleAnchor anchor,
        boolean windupThroughTerrain,
        CircleScript deliveryCircle,
        WindupCue windup,
        ReleaseCue release,
        List<Silhouette> silhouettes,
        TrailSpec trail,
        ImpactSpec impact,
        LingerCue linger,
        FirstPersonSpec firstPerson,
        SoundSpec sounds,
        int budgetClass,
        BoundsSpec bounds,
        boolean holdable,
        boolean explicit) {

    public Silhouette primary() {
        return silhouettes.get(0);
    }

    public Silhouette.SilhouetteKey silhouette() {
        return primary().key();
    }

    public int color(ColorRole role) {
        return palette.of(role);
    }

    public static Builder builder(MagicSkillDefinition definition) {
        return new Builder(definition);
    }

    /** A never-null fallback so a half-registered skill renders instead of crashing. */
    public static VisualProfile defaultFor(MagicSkillDefinition definition) {
        return builder(definition).explicit(false).build();
    }

    public static final class Builder {
        private final MagicSkillDefinition definition;
        private SchoolMaterial material;
        private int paletteVariant;
        private Palette palette;
        private TierProfile tier;
        private CircleScript castCircle;
        private CircleAnchor anchor = CircleAnchor.GROUND;
        private Boolean windupThroughTerrain;
        private CircleScript deliveryCircle;
        private WindupCue windup;
        private ReleaseCue release = ReleaseCue.DEFAULT;
        private final List<Silhouette> silhouettes = new ArrayList<>();
        private TrailSpec trail = TrailSpec.NONE;
        private ImpactSpec impact;
        private LingerCue linger = LingerCue.NONE;
        private FirstPersonSpec firstPerson = FirstPersonSpec.DEFAULT;
        private SoundSpec sounds;
        private int budgetClass = -1;
        private BoundsSpec bounds = BoundsSpec.DEFAULT;
        private boolean holdable;
        private boolean explicit = true;
        private FxKinds.Mark impactMark = FxKinds.Mark.SHOCK_RING;
        private FxKinds.Smoke impactMatter = FxKinds.Smoke.DUST;
        private FxKinds.Overlay victimOverlay = FxKinds.Overlay.VIGNETTE;

        private Builder(MagicSkillDefinition definition) {
            this.definition = definition;
            this.material = SchoolMaterial.of(definition.school());
        }

        public Builder material(SchoolMaterial value) { this.material = value; return this; }
        public Builder palette(int variant) { this.paletteVariant = variant; return this; }
        public Builder palette(Palette value) { this.palette = value; return this; }
        public Builder tier(TierProfile value) { this.tier = value; return this; }
        public Builder circle(CircleScript value) { this.castCircle = value; return this; }
        public Builder circle(CircleScript.Builder value) { if (value.paletteVariant() != 0) { this.paletteVariant = value.paletteVariant(); } this.castCircle = value.build(); return this; }
        public Builder anchor(CircleAnchor value) { this.anchor = value; return this; }
        public Builder throughTerrain(boolean value) { this.windupThroughTerrain = value; return this; }
        public Builder deliveryCircle(CircleScript value) { this.deliveryCircle = value; return this; }
        public Builder windup(WindupCue value) { this.windup = value; return this; }
        public Builder release(ReleaseMode mode, FirstPersonPreset caster) { this.release = new ReleaseCue(mode, release.muzzleFlashKind(), release.muzzleParticleBurst(), caster, release.casterOverlay()); return this; }
        public Builder release(ReleaseCue value) { this.release = value; return this; }
        public Builder silhouette(Silhouette value) { this.silhouettes.add(value); return this; }
        public Builder trail(TrailSpec value) { this.trail = value; return this; }
        public Builder impact(ImpactSpec value) { this.impact = value; return this; }
        public Builder impact(FxKinds.Mark mark, FxKinds.Smoke matter, FxKinds.Overlay victim) { this.impactMark = mark; this.impactMatter = matter; this.victimOverlay = victim; return this; }
        public Builder linger(LingerCue value) { this.linger = value; return this; }
        public Builder firstPerson(FirstPersonSpec value) { this.firstPerson = value; return this; }
        public Builder sounds(SoundSpec value) { this.sounds = value; return this; }
        public Builder budget(int value) { this.budgetClass = value; return this; }
        public Builder bounds(float horizontal, float up, float down) { this.bounds = new BoundsSpec(horizontal, up, down); return this; }
        public Builder holdable(boolean value) { this.holdable = value; return this; }
        public Builder explicit(boolean value) { this.explicit = value; return this; }

        public VisualProfile build() {
            int t = definition.tier();
            TierProfile tp = tier != null ? tier : TierProfile.forTier(t);
            int baseColor = definition.color() != 0 ? definition.color() : material.variantColor(paletteVariant);
            Palette pal = palette != null ? palette : Palette.derive(baseColor, material.variantColor(paletteVariant + 1));
            CircleScript cast = castCircle != null ? castCircle : CircleScript.of(material).emblem(EmblemId.BLANK).band(material.defaultBand(), 12).core(material.defaultCore()).build();
            CircleScript delivery = deliveryCircle != null ? deliveryCircle : cast.identityOnly().scaled(0.7F);
            WindupCue wu = windup != null ? windup : WindupCue.forTier(tp);
            ImpactSpec imp = impact != null ? impact : ImpactSpec.forTier(tp, impactMark, impactMatter, victimOverlay);
            SoundSpec snd = sounds != null ? sounds : SoundSpec.defaults(material, Math.max(0, t));
            int budget = budgetClass >= 0 ? budgetClass : Math.min(3, Math.max(0, t < 0 ? 3 : t));
            if (silhouettes.isEmpty()) {
                silhouettes.add(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.PLASMA, Math.max(0.4F, definition.baseSize() * 0.5F)));
            }
            boolean through = windupThroughTerrain != null ? windupThroughTerrain : tp.throughTerrain();
            return new VisualProfile(definition.id(), material, paletteVariant, pal, tp, cast, anchor, through, delivery, wu, release,
                    List.copyOf(silhouettes), trail, imp, linger, firstPerson, snd, budget, bounds, holdable, explicit);
        }
    }
}
