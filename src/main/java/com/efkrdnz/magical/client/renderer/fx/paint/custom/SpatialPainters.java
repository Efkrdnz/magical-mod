package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.FxMesh;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Bespoke painters for the SPATIAL school: stitches, gauges, armatures, seams and star-cracks. */
public final class SpatialPainters {
    private SpatialPainters() {}

    /** Two-strand stitch between the exchanged spots, crossing at a hot midpoint, with address columns at each end. */
    public static void transposition(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        if (d == null || !d.contains("AX")) {
            return;
        }
        Vec3 a = new Vec3(d.getDouble("AX"), d.getDouble("AY"), d.getDouble("AZ")).subtract(ctx.origin);
        Vec3 b = new Vec3(d.getDouble("BX"), d.getDouble("BY"), d.getDouble("BZ")).subtract(ctx.origin);
        float fade = ctx.fade();
        int base = profile.color(ColorRole.BASE);
        int bright = profile.color(ColorRole.BRIGHT);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int strand = MagicVertex.pack(FxKinds.Filament.HELIX.id(), 2, 0, ctx.phase, ctx.seed, 0);
        Vec3 up = new Vec3(0.0D, 0.9D, 0.0D);
        Vec3 low = new Vec3(0.0D, 0.3D, 0.0D);
        FilamentPainter.link(ctx, beam, a.add(up), b.add(low), 0.06F, base, 0.9F * fade, strand);
        FilamentPainter.link(ctx, beam, a.add(low), b.add(up), 0.06F, bright, 0.9F * fade, strand);
        int column = MagicVertex.pack(FxKinds.Filament.RUNE_THREAD.id(), 6, 0, ctx.phase, ctx.seed, 0);
        for (Vec3 p : new Vec3[] {a, b}) {
            ctx.pose.pushPose();
            ctx.pose.translate(p.x, p.y, p.z);
            FxMesh.emit(beam, ctx.pose.last().pose(), FxMesh.column(), 0.12F, 1.3F, 0.12F, base, 0.8F * fade, column);
            ctx.pose.popPose();
        }
        Vec3 mid = a.add(b).scale(0.5D).add(0.0D, 0.6D, 0.0D);
        ctx.pose.pushPose();
        ctx.pose.translate(mid.x, mid.y, mid.z);
        OrbPainter.billboard(ctx, FxKinds.Orb.BLOOM_FLASH, 0.35F, hot, fade, ctx.phase, 4, 0);
        ctx.pose.popPose();
        FxBudget.countQuads(8);
    }

    /** Armillary gauge: three orthogonal hairline rings whose radius follows the synced scale, precessing. */
    public static void compression(FxContext ctx, VisualProfile profile, Silhouette s) {
        float scale = ctx.extra > 0.0F ? ctx.extra : 1.0F;
        float r = Mth.clamp(0.5F + 1.6F * scale, 0.5F, 3.4F);
        int base = profile.color(ColorRole.BASE);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int packed = MagicVertex.pack(FxKinds.Filament.RUNE_THREAD.id(), 24, 0, ctx.phase, ctx.seed, 0);
        float spin = ctx.age * 2.5F;
        float[] mesh = FxMesh.ring(48, 0.03F);
        for (int i = 0; i < 3; i++) {
            ctx.pose.pushPose();
            switch (i) {
                case 0 -> {
                    ctx.pose.mulPose(Axis.YP.rotationDegrees(spin));
                    ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                }
                case 1 -> ctx.pose.mulPose(Axis.YP.rotationDegrees(-spin * 0.7F));
                default -> {
                    ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F + spin * 0.4F));
                    ctx.pose.mulPose(Axis.ZP.rotationDegrees(spin * 0.3F));
                }
            }
            FxMesh.emit(beam, ctx.pose.last().pose(), mesh, r, r, 1.0F, i == 1 ? hot : base, 0.9F, packed);
            ctx.pose.popPose();
        }
        OrbPainter.billboard(ctx, FxKinds.Orb.HEX_LENS, 0.25F, hot, 0.8F, ctx.phase, 3, 0);
        FxBudget.countQuads(48 * 3 + 1);
    }

    /** Rigid brass armature: a three-ring hub, struts to every locked victim, gimbal clamps; hairlines while telegraphing. */
    public static void rigidFrame(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        boolean locked = d != null && d.getBoolean("Lock");
        int[] ids = d != null ? d.getIntArray("V") : new int[0];
        Level level = Minecraft.getInstance().level;
        int base = profile.color(ColorRole.BASE);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int band = MagicVertex.pack(FxKinds.Body.METAL_BANDS.id(), 6, 8, 0.5F, ctx.seed, 0);
        float[] hubRing = FxMesh.ring(32, 0.06F);
        for (int i = 0; i < 3; i++) {
            ctx.pose.pushPose();
            switch (i) {
                case 0 -> ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
                case 1 -> ctx.pose.mulPose(Axis.YP.rotationDegrees(ctx.age * 1.5F));
                default -> ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F + ctx.age * 1.5F));
            }
            FxMesh.emit(solid, ctx.pose.last().pose(), hubRing, 0.55F, 0.55F, 1.0F, base, 1.0F, band);
            ctx.pose.popPose();
        }
        int quads = 96;
        if (level == null) {
            FxBudget.countQuads(quads);
            return;
        }
        VertexConsumer thread = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int hair = MagicVertex.pack(FxKinds.Filament.RUNE_THREAD.id(), 8, 0, ctx.phase, ctx.seed, 0);
        float[] clamp = FxMesh.ring(24, 0.08F);
        for (int id : ids) {
            Entity e = level.getEntity(id);
            if (e == null) {
                continue;
            }
            Vec3 to = e.getBoundingBox().getCenter().subtract(ctx.origin);
            if (!locked) {
                FilamentPainter.link(ctx, thread, Vec3.ZERO, to, 0.02F, hot, 0.7F, hair);
                quads += 2;
                continue;
            }
            double len = to.length();
            ctx.pose.pushPose();
            FilamentPainter.orientAlong(ctx.pose, to);
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.tube(), 0.07F, 0.07F, (float) len, base, 1.0F, band);
            ctx.pose.popPose();
            ctx.pose.pushPose();
            ctx.pose.translate(to.x, to.y, to.z);
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), clamp, e.getBbWidth() * 0.7F, e.getBbWidth() * 0.7F, 1.0F, base, 1.0F, band);
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            ctx.pose.mulPose(Axis.YP.rotationDegrees(ctx.age * 3.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), clamp, e.getBbWidth() * 0.7F, e.getBbWidth() * 0.7F, 1.0F, hot, 1.0F, band);
            ctx.pose.popPose();
            quads += 2 + 48;
        }
        FxBudget.countQuads(quads);
    }

    /** The seam: a flat zipper rift across the look, a half-disc lattice scan on the folding side, and the fold lens ring. */
    public static void crease(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = ctx.direction.lengthSqr() > 1.0E-6D ? ctx.direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        float yaw = (float) Math.atan2(f.x, f.z);
        float open = Mth.clamp(ctx.age / 12.0F, 0.0F, 1.0F);
        float fade = ctx.life > 0.0F ? Mth.clamp((ctx.life - ctx.age) / 12.0F, 0.0F, 1.0F) : 1.0F;
        float radius = s.sizeA();
        int base = profile.color(ColorRole.BASE);
        int bright = profile.color(ColorRole.BRIGHT);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer rift = ctx.buffers.getBuffer(MagicalFxRenderTypes.riftCut());
        int zipper = MagicVertex.pack(FxKinds.Rift.ZIPPER.id(), 12, FxKinds.RiftInterior.LENSED_STARS.ordinal(), open, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.03F, 0.0F);
        ctx.pose.mulPose(Axis.YP.rotation(yaw));
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        FxMesh.emit(rift, ctx.pose.last().pose(), FxMesh.square(), radius * open, 0.45F, 1.0F, base, 0.9F * fade, zipper);
        ctx.pose.popPose();
        // the half that folds: a lattice scan offset along +f (or -f under sneak)
        boolean near = ctx.data != null && ctx.data.getBoolean("Sneak");
        float side = near ? -1.0F : 1.0F;
        ctx.pose.pushPose();
        ctx.pose.translate(f.x * radius * 0.5F * side, 0.04F, f.z * radius * 0.5F * side);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.LATTICE_GRID, radius * 0.55F * open, bright, 0.55F * fade, open, 6, 4);
        ctx.pose.popPose();
        // fold ring once the fold has happened
        if (ctx.age >= 12.0F) {
            float fold = Mth.clamp((ctx.age - 12.0F) / 8.0F, 0.0F, 1.0F);
            VertexConsumer lens = ctx.buffers.getBuffer(MagicalFxRenderTypes.lensWarp());
            int ring = MagicVertex.pack(FxKinds.Lens.SHOCK_LENS.id(), 4, 12, fold, ctx.seed, 0);
            ctx.pose.pushPose();
            ctx.pose.translate(0.0F, 0.6F, 0.0F);
            if (ctx.cameraOrientation != null) {
                ctx.pose.mulPose(ctx.cameraOrientation);
            }
            FxMesh.emit(lens, ctx.pose.last().pose(), FxMesh.square(), radius * (0.3F + 0.7F * fold), radius * (0.3F + 0.7F * fold), 1.0F, hot, (1.0F - fold) * fade, ring);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(3);
    }

    /** Star-crack with one arm per victim opening flat on the ground, and a spiral-tear exit portal wherever someone lands. */
    public static void diaspora(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        int arms = d != null && d.contains("Arms") ? d.getInt("Arms") : 6;
        float baseYaw = d != null ? d.getFloat("Yaw") : 0.0F;
        float open = Mth.clamp(ctx.age / 20.0F, 0.0F, 1.0F);
        float fade = ctx.life > 0.0F ? Mth.clamp((ctx.life - ctx.age) / 20.0F, 0.0F, 1.0F) : 1.0F;
        float radius = s.sizeA();
        int base = profile.color(ColorRole.BASE);
        int bright = profile.color(ColorRole.BRIGHT);
        VertexConsumer rift = ctx.buffers.getBuffer(MagicalFxRenderTypes.riftCut());
        int star = MagicVertex.pack(FxKinds.Rift.RADIAL_STAR_CRACK.id(), arms, FxKinds.RiftInterior.LENSED_STARS.ordinal(), open, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.03F, 0.0F);
        ctx.pose.mulPose(Axis.YP.rotation(-baseYaw));
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        FxMesh.emit(rift, ctx.pose.last().pose(), FxMesh.square(), radius * open, radius * open, 1.0F, base, 0.95F * fade, star);
        ctx.pose.popPose();
        int quads = 1;
        if (d != null) {
            ListTag exits = d.getList("Exits", Tag.TAG_COMPOUND);
            for (int i = 0; i < exits.size(); i++) {
                CompoundTag p = exits.getCompound(i);
                Vec3 at = new Vec3(p.getDouble("X"), p.getDouble("Y") + 1.0D, p.getDouble("Z")).subtract(ctx.origin);
                float portalAge = ctx.age - p.getInt("T");
                float portal = Mth.clamp(portalAge / 6.0F, 0.0F, 1.0F) * Mth.clamp((30.0F - portalAge) / 10.0F, 0.0F, 1.0F);
                if (portal <= 0.0F) {
                    continue;
                }
                Vec3 rel = ctx.cameraPos.subtract(at);
                float yaw = (float) Math.atan2(rel.x, rel.z);
                int tear = MagicVertex.pack(FxKinds.Rift.SPIRAL_TEAR.id(), 5, FxKinds.RiftInterior.LENSED_STARS.ordinal(), portal, (ctx.seed + i) & 63, 0);
                ctx.pose.pushPose();
                ctx.pose.translate(at.x, at.y, at.z);
                ctx.pose.mulPose(Axis.YP.rotation(yaw));
                FxMesh.emit(rift, ctx.pose.last().pose(), FxMesh.square(), 0.6F * portal, 1.1F * portal, 1.0F, bright, 0.9F * fade, tear);
                ctx.pose.popPose();
                quads++;
            }
        }
        FxBudget.countQuads(quads);
    }

    public static void register() {
        CustomPainters.register("transposition", SpatialPainters::transposition);
        CustomPainters.register("compression", SpatialPainters::compression);
        CustomPainters.register("rigid_frame", SpatialPainters::rigidFrame);
        CustomPainters.register("crease", SpatialPainters::crease);
        CustomPainters.register("diaspora", SpatialPainters::diaspora);
    }
}
