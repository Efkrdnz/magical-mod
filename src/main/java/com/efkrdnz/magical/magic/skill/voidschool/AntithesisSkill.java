package com.efkrdnz.magical.magic.skill.voidschool;

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
import com.efkrdnz.magical.magic.service.SpellIntercept;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T2 - REFLECT_INVERT / PLACED_PANE / VERTICAL_WALL. A free-standing pane: hostile
 * projectiles crossing it from the far side are mirrored back at their shooters (re-owned), living
 * enemies bounce back, are hurt and have their active effects inverted. The caster passes freely.
 * Sneak = place it behind you facing away.
 */
public final class AntithesisSkill implements SkillModule {
    private static final double THICKNESS = 0.35D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ANTITHESIS;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                Vec3 normal = ctx.sneak() ? flat : flat.scale(-1.0D); // faces the caster (or away when behind)
                Vec3 centre = ctx.feet().add(flat.scale(ctx.sneak() ? -2.5D : 2.5D)).add(0.0D, 2.0D * Math.max(0.5D, ctx.size()), 0.0D);
                SpellEffectEntity pane = SpellEffectEntity.spawn(ctx, centre, Math.max(60, ctx.duration()), 2.5F * Math.max(0.5F, ctx.size()), normal);
                pane.setValue(2.0F * Math.max(0.5F, ctx.size()));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.defence();
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static final Holder<MobEffect>[][] INVERSIONS = new Holder[][] {
        {MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN},
        {MobEffects.DAMAGE_BOOST, MobEffects.WEAKNESS},
        {MobEffects.REGENERATION, MobEffects.POISON},
        {MobEffects.DIG_SPEED, MobEffects.DIG_SLOWDOWN},
    };

    private static void invertEffects(LivingEntity target) {
        List<MobEffectInstance> replacements = new ArrayList<>();
        for (Holder<MobEffect>[] pair : INVERSIONS) {
            for (int i = 0; i < 2; i++) {
                MobEffectInstance current = target.getEffect(pair[i]);
                if (current != null) {
                    replacements.add(new MobEffectInstance(pair[1 - i], current.getDuration(), current.getAmplifier()));
                    target.removeEffect(pair[i]);
                }
            }
        }
        replacements.forEach(target::addEffect);
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            Vec3 c = entity.position();
            Vec3 n = entity.direction();
            double halfW = entity.radius();
            double halfH = entity.value() > 0.0F ? entity.value() : 2.0D;
            AABB box = new AABB(c, c).inflate(halfW + 1.0D, halfH + 1.0D, halfW + 1.0D);
            CompoundTag data = entity.serverData();
            for (Entity e : level.getEntities(entity, box, en -> en.isAlive() && en != owner)) {
                Vec3 p = e.position().add(0.0D, e.getBbHeight() * 0.5D, 0.0D);
                Vec3 rel = p.subtract(c);
                double s = rel.dot(n);
                Vec3 lateral = rel.subtract(n.scale(s));
                double lx = Math.abs(lateral.dot(new Vec3(-n.z, 0.0D, n.x)));
                double ly = Math.abs(lateral.y);
                String key = "side_" + e.getId();
                int prev = data.contains(key) ? data.getInt(key) : (s >= 0.0D ? 1 : -1);
                int now = s >= 0.0D ? 1 : -1;
                data.putInt(key, now);
                if (lx > halfW || ly > halfH || Math.abs(s) > THICKNESS + Math.max(0.5D, e.getBbWidth())) {
                    continue;
                }
                // crossing from the far side (negative = behind the normal) toward the caster's side
                boolean crossed = prev < 0 && now >= 0 || (prev < 0 && Math.abs(s) < THICKNESS);
                if (!crossed) {
                    continue;
                }
                if (SpellIntercept.isProjectile(e) && !SpellIntercept.ownedBy(e, owner)) {
                    SpellIntercept.reflect(e, n, 1.3D, owner);
                    e.setPos(p.add(n.scale(-0.6D)).subtract(0.0D, e.getBbHeight() * 0.5D, 0.0D));
                    SpellFx.barrierHit(level, entity.definition(), p, n);
                    data.putInt(key, -1);
                } else if (e instanceof LivingEntity living && SkillTargets.isHostile(owner, living)) {
                    Vec3 v = living.getDeltaMovement();
                    Vec3 mirrored = v.subtract(n.scale(2.0D * v.dot(n)));
                    living.setDeltaMovement(mirrored.add(n.scale(-0.9D * Math.max(0.5D, entity.knockback()))).add(0.0D, 0.15D, 0.0D));
                    living.setPos(p.add(n.scale(-0.8D)).subtract(0.0D, living.getBbHeight() * 0.5D, 0.0D));
                    living.hurtMarked = true;
                    SkillTargets.hurt(level, owner, living, entity.damage(), entity.definition(), true);
                    invertEffects(living);
                    data.putInt(key, -1);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.MIRROR).frame(4).band(GlyphKind.BRAID_BAND, 4, ColorRole.BASE).band(GlyphKind.TICK_BAND, 24, ColorRole.DIM).stamps(StampId.ARROW, 8).mirror(2).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.field(Silhouette.Form.WALL, FxKinds.Field.MIRROR_SHEEN, 2.5F, 2.0F, 2, 6).withOpacity(0.85F))
                .silhouette(Silhouette.mark(FxKinds.Mark.HEX_CELLS, 2.6F, 6).withOffset(-2.0F).withOpacity(0.5F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.HEX_FRAGMENT, FxKinds.Overlay.PRISM_RING)
                .bounds(4.0F, 3.0F, 3.0F);
    }
}
