package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Bespoke painters for the LIGHT school's geometry that no family form covers. */
public final class LightPainters {
    private LightPainters() {}

    /** Three rune-thread rays fanned at -10/0/+10 degrees with lengths from synced data. */
    public static void cleansingRay(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 dir = ctx.direction.lengthSqr() > 1.0E-6D ? ctx.direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        boolean vertical = ctx.data != null && ctx.data.getBoolean("Vertical");
        CompoundTag data = ctx.data;
        boolean windup = ctx.age < 6.0F;
        float fade = windup ? 0.4F : Mth.clamp(1.0F - (ctx.age - 12.0F) / 8.0F, 0.0F, 1.0F);
        float width = windup ? 0.02F : 0.09F;
        int rgb = profile.color(ColorRole.HOT);
        for (int i = 0; i < 3; i++) {
            float len = data != null && data.contains("L" + i) ? data.getFloat("L" + i) : s.sizeA();
            Vec3 ray = vertical ? dir.xRot((float) Math.toRadians((i - 1) * 10.0F)) : dir.yRot((float) Math.toRadians((i - 1) * 10.0F));
            ctx.pose.pushPose();
            FilamentPainter.orientAlong(ctx.pose, ray);
            FilamentPainter.beam(ctx, FxKinds.Filament.RUNE_THREAD, width, len, rgb, fade, 0.5F, Math.max(4, Math.round(len * 1.2F)), 0);
            ctx.pose.popPose();
        }
    }

    /** The ricochet polyline: three offset ribbons per segment plus a lens flare at every bounce. */
    public static void prismCascade(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag data = ctx.data;
        if (data == null) {
            return;
        }
        ListTag list = data.getList("Points", Tag.TAG_COMPOUND);
        if (list.size() < 2) {
            return;
        }
        boolean windup = ctx.age < 11.0F;
        float flood = Mth.clamp((ctx.age - 11.0F) / 6.0F, 0.0F, 1.0F);
        float fade = ctx.life > 0.0F ? Mth.clamp((ctx.life - ctx.age) / 10.0F, 0.0F, 1.0F) : 1.0F;
        int[] colours = {profile.color(ColorRole.HOT), 0xFFFFFF, profile.color(ColorRole.BRIGHT)};
        Vec3 prev = point(list, 0);
        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        for (int i = 1; i < list.size(); i++) {
            Vec3 p = point(list, i);
            Vec3 seg = p.subtract(prev);
            if (seg.lengthSqr() < 1.0E-4D) {
                prev = p;
                continue;
            }
            Vec3 side = seg.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize().scale(0.12D);
            if (side.lengthSqr() < 1.0E-6D) {
                side = new Vec3(0.12D, 0.0D, 0.0D);
            }
            for (int r = 0; r < 3; r++) {
                Vec3 off = side.scale(r - 1);
                float width = windup ? 0.02F : 0.09F + 0.05F * flood;
                float opacity = (windup ? 0.5F : 0.6F + 0.4F * flood) * fade;
                int packed = MagicVertex.pack(windup ? FxKinds.Filament.DASH_TRAIN.id() : FxKinds.Filament.RIBBON.id(), 8, 0, windup ? 0.5F : 0.5F, (ctx.seed + i) & 63, 0);
                FilamentPainter.link(ctx, consumer, prev.add(off), p.add(off), width, colours[r], opacity, packed);
            }
            if (!windup) {
                ctx.pose.pushPose();
                ctx.pose.translate(p.x, p.y, p.z);
                OrbPainter.billboard(ctx, FxKinds.Orb.LENS_STREAKS, 0.9F * flood, colours[0], 0.9F * fade, 0.5F, 4, 8);
                ctx.pose.popPose();
            }
            prev = p;
        }
        FxBudget.countQuads(list.size() * 8);
    }

    /** Searchlight cone from the sky eye to the moving spot, and a twelve-ray spotlight on the ground. */
    public static void heavensGaze(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag data = ctx.data;
        if (data == null || !data.contains("SX")) {
            return;
        }
        float open = Mth.clamp((ctx.age - 20.0F) / 8.0F, 0.0F, 1.0F);
        if (ctx.life > 0.0F) {
            open *= Mth.clamp((ctx.life - ctx.age) / 12.0F, 0.0F, 1.0F);
        }
        if (open <= 0.0F) {
            return;
        }
        Vec3 spot = new Vec3(data.getDouble("SX"), data.getDouble("SY"), data.getDouble("SZ")).subtract(ctx.origin);
        int base = profile.color(ColorRole.BASE);
        int hot = profile.color(ColorRole.HOT);
        // cone: a wide tapered beam from the eye down to the spot
        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int packed = MagicVertex.pack(FxKinds.Filament.PLASMA_TUBE.id(), 4, 0, 0.5F, ctx.seed, 0);
        Vec3 dir = spot.normalize();
        double len = spot.length();
        Vec3 up = Math.abs(dir.y) < 0.9D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 sideA = dir.cross(up).normalize();
        Vec3 sideB = dir.cross(sideA).normalize();
        float radius = s.sizeA() * 0.1F;
        for (int k = 0; k < 2; k++) {
            Vec3 side = (k == 0 ? sideA : sideB);
            var m = ctx.pose.last().pose();
            Vec3 a0 = side.scale(0.6D);
            Vec3 a1 = side.scale(radius);
            MagicVertex.emit(consumer, m, (float) -a0.x, (float) -a0.y, (float) -a0.z, 0.0F, 0.0F, base, 0.35F * open, packed);
            MagicVertex.emit(consumer, m, (float) a0.x, (float) a0.y, (float) a0.z, 0.0F, 1.0F, base, 0.35F * open, packed);
            MagicVertex.emit(consumer, m, (float) (spot.x + a1.x), (float) (spot.y + a1.y), (float) (spot.z + a1.z), 1.0F, 1.0F, base, 0.15F * open, packed);
            MagicVertex.emit(consumer, m, (float) (spot.x - a1.x), (float) (spot.y - a1.y), (float) (spot.z - a1.z), 1.0F, 0.0F, base, 0.15F * open, packed);
        }
        // spotlight on the ground
        ctx.pose.pushPose();
        ctx.pose.translate(spot.x, spot.y + 0.05D, spot.z);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.RAY_BURST, radius, hot, 0.9F * open, 0.5F + 0.5F * Mth.sin(ctx.age * 0.15F), 12, 6);
        ctx.pose.popPose();
        FxBudget.countQuads(3);
    }

    private static Vec3 point(ListTag list, int i) {
        CompoundTag pt = list.getCompound(i);
        return new Vec3(pt.getDouble("X"), pt.getDouble("Y"), pt.getDouble("Z"));
    }

    public static void register() {
        com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters.register("cleansing_ray", LightPainters::cleansingRay);
        com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters.register("prism_cascade", LightPainters::prismCascade);
        com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters.register("heavens_gaze", LightPainters::heavensGaze);
    }
}
