package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.MagicalMod;
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
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * BLACKSMITH - ARMOR_BURN / HITSCAN_TOUCH / WORN_HARNESS. A heated palm brands the first living thing
 * in reach: every pulse deals armour-bypassing fire that scales with the victim's own armour, and
 * its armour is halved for the brand.
 */
public final class HeatedIronSkill implements SkillModule {
    private static final ResourceLocation ARMOR_MODIFIER = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "heated_iron");

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HEATED_IRON;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                if (victim == null || !SkillTargets.isHostile(ctx.caster(), victim)) {
                    SpellFx.decal(ctx.level(), ctx.definition(), ctx.aim().point(), ctx.aim().normal(), 0.7F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                int ticks = Math.max(20, ctx.duration());
                MagicStatusService.apply(victim, MagicStatus.BRANDED, ticks, ctx.definition().id(), ctx.caster());
                SpellEffectEntity brand = SpellEffectEntity.spawn(ctx, victim.position(), ticks, victim.getBbWidth() * 0.75F, new Vec3(0.0D, 1.0D, 0.0D));
                brand.setTarget(victim);
                SpellFx.impact(ctx.level(), ctx.definition(), victim.getBoundingBox().getCenter(), ctx.look().scale(-1.0D), victim, ctx.caster(), 0.8F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 6.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.55D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(0.0F, 5.0F);
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
            public void onSpawn(SpellEffectEntity entity) {
                if (entity.target() instanceof LivingEntity victim) {
                    AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
                    if (armor != null) {
                        armor.removeModifier(ARMOR_MODIFIER);
                        armor.addTransientModifier(new AttributeModifier(ARMOR_MODIFIER, -0.5D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                }
            }

            @Override
            public void tick(SpellEffectEntity entity) {
                Entity target = entity.target();
                if (!(target instanceof LivingEntity victim) || !victim.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(victim.position());
                entity.setValue(victim.getBbHeight());
                if (entity.tickCount % 10 == 0) {
                    // the halved armour is what the pulse reads, so use the base value for the scaling
                    AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
                    double base = armor != null ? armor.getBaseValue() : victim.getArmorValue();
                    float pulse = entity.damage() + 0.25F * (float) Math.min(base, 8.0D);
                    SkillTargets.hurt(entity.serverLevel(), entity.owner(), victim, pulse, entity.definition().id());
                    SpellFx.zoneTick(entity.serverLevel(), entity.definition(), victim.position(), 0.6F);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.target() instanceof LivingEntity victim) {
                    AttributeInstance armor = victim.getAttribute(Attributes.ARMOR);
                    if (armor != null) {
                        armor.removeModifier(ARMOR_MODIFIER);
                    }
                    MagicStatusService.clear(victim, MagicStatus.BRANDED);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.CUIRASS).frame(4).band(GlyphKind.CHAIN_BAND, 8).band(GlyphKind.TICK_BAND, 16).stamps(StampId.SQUARE, 4).core(CoreKind.EMBER_PIT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.field(Silhouette.Form.CAGE, FxKinds.Field.SCALE_PLATES, 0.5F, 1.8F, 3, 8).withOpacity(0.8F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.HEAT_SHIMMER)
                .bounds(2.0F, 2.5F, 0.5F);
    }
}
