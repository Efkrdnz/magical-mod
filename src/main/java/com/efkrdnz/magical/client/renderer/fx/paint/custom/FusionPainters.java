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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Bespoke painters for the fusion outputs. */
public final class FusionPainters {
    private FusionPainters() {}

    private static Vec3 dir(FxContext ctx) {
        return ctx.direction.lengthSqr() > 1.0E-6D ? ctx.direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    /** White-cored steam column capped by mist during an eruption (ctx.extra = eruption 1..0), hissing ring between. */
    public static void scaldingGeyser(FxContext ctx, VisualProfile profile, Silhouette s) {
        float erupt = Mth.clamp(ctx.extra, 0.0F, 1.0F);
        float height = s.sizeA();
        int base = profile.color(ColorRole.BASE);
        int bright = profile.color(ColorRole.BRIGHT);
        int quads = 0;
        if (erupt > 0.0F) {
            VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
            int packed = MagicVertex.pack(FxKinds.Filament.WATER.id(), 6, 0, 1.0F - erupt, ctx.seed, 1);
            ctx.pose.pushPose();
            FxMesh.emit(dark, ctx.pose.last().pose(), FxMesh.column(), 0.45F * erupt + 0.3F, height * (0.4F + 0.6F * erupt), 0.45F * erupt + 0.3F, base, 0.9F, packed);
            ctx.pose.mulPose(Axis.YP.rotationDegrees(45.0F));
            FxMesh.emit(dark, ctx.pose.last().pose(), FxMesh.column(), 0.3F * erupt + 0.2F, height * (0.4F + 0.6F * erupt), 0.3F * erupt + 0.2F, bright, 0.9F, packed);
            ctx.pose.popPose();
            ctx.pose.pushPose();
            ctx.pose.translate(0.0F, height * (0.4F + 0.6F * erupt), 0.0F);
            OrbPainter.billboard(ctx, FxKinds.Orb.PLASMA, 1.4F * erupt + 0.4F, bright, 0.5F, ctx.phase, 4, 6);
            ctx.pose.popPose();
            quads += 5;
        } else {
            // steam ring hissing between eruptions
            ctx.pose.pushPose();
            ctx.pose.translate(0.0F, 0.08F, 0.0F);
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            MarkPainter.mark(ctx, FxKinds.Mark.RIPPLES, 2.0F, bright, 0.35F + 0.15F * Mth.sin(ctx.age * 0.2F), 0.5F, 6, 4);
            ctx.pose.popPose();
            quads++;
        }
        FxBudget.countQuads(quads);
    }

    /** Orbit track at chest height, the dark node, tethers to each captive, and the ground orrery of nested rings. */
    public static void blackOrrery(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        float radius = s.sizeA();
        boolean reversed = d != null && d.getBoolean("Rev");
        int[] ids = d != null ? d.getIntArray("V") : new int[0];
        int ink = profile.color(ColorRole.INK);
        int base = profile.color(ColorRole.BASE);
        int hot = profile.color(ColorRole.HOT);
        float form = Mth.clamp(ctx.age / 10.0F, 0.0F, 1.0F);
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int track = MagicVertex.pack(FxKinds.Filament.DASH_TRAIN.id(), 36, 0, 0.5F, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        ctx.pose.mulPose(Axis.ZP.rotationDegrees(ctx.age * (reversed ? 6.0F : -6.0F)));
        FxMesh.emit(beam, ctx.pose.last().pose(), FxMesh.ring(48, 0.02F), radius * form, radius * form, 1.0F, base, 0.7F, track);
        ctx.pose.popPose();
        // the node at bearing 0 (+X)
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int node = MagicVertex.pack(FxKinds.Body.OBSIDIAN.id(), 4, 12, 0.1F, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(radius * form, 0.0F, 0.0F);
        ctx.pose.mulPose(Axis.YP.rotationDegrees(ctx.age * 5.0F));
        ctx.pose.mulPose(Axis.XP.rotationDegrees(45.0F));
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.tube(), 0.2F, 0.2F, 0.7F, ink, 1.0F, node);
        ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.tube(), 0.2F, 0.2F, 0.7F, ink, 1.0F, node);
        ctx.pose.popPose();
        // tethers
        int quads = 48 + 4;
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
            int tether = MagicVertex.pack(FxKinds.Filament.INK_TENDRIL.id(), 4, 0, 0.5F, ctx.seed, 1);
            for (int id : ids) {
                Entity e = level.getEntity(id);
                if (e == null) {
                    continue;
                }
                Vec3 to = e.getBoundingBox().getCenter().subtract(ctx.origin);
                FilamentPainter.link(ctx, dark, Vec3.ZERO, to, 0.05F, ink, 0.85F, tether);
                quads += 2;
            }
        }
        // ground orrery: nested dark rings
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, -1.15F, 0.0F);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.VORTEX_SPIRAL, radius * 1.1F * form, hot, 0.5F, 0.5F, 4, 8);
        ctx.pose.popPose();
        FxBudget.countQuads(quads + 1);
    }

    /** A big spoked slag wheel standing on edge, rolling by ctx.extra, with a lilac rein thread up to the hub. */
    public static void cinderChariot(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = dir(ctx);
        float radius = s.sizeA();
        int rock = profile.color(ColorRole.DIM);
        int seam = profile.color(ColorRole.HOT);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.MAGMA_ROCK.id(), 8, 10, 0.2F, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, radius, 0.0F);
        FilamentPainter.orientAlong(ctx.pose, f);
        // the wheel plane contains the travel direction: rotate the XY ring so its normal is local X
        ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        ctx.pose.mulPose(Axis.ZP.rotationDegrees(-ctx.extra));
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.ring(24, 0.18F), radius, radius, 1.0F, rock, 1.0F, packed);
        float[] spokes = FxMesh.spikeCluster(8);
        FxMesh.emit(solid, ctx.pose.last().pose(), spokes, radius * 0.9F, radius * 0.9F, 0.15F, seam, 1.0F, packed);
        // hub
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.ring(12, 0.5F), radius * 0.22F, radius * 0.22F, 1.0F, seam, 1.0F, packed);
        ctx.pose.popPose();
        // the rein: a thread from above the hub back to the rider's hands
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int rein = MagicVertex.pack(FxKinds.Filament.THREAD_KNOTS.id(), 6, 0, 0.5F, ctx.seed, 0);
        Vec3 hub = new Vec3(0.0D, radius, 0.0D);
        Vec3 hands = hub.add(0.0D, radius * 0.9D, 0.0D).subtract(f.scale(0.2D));
        FilamentPainter.link(ctx, beam, hub.add(f.scale(radius * 0.8D)), hands, 0.03F, profile.color(ColorRole.BRIGHT), 0.8F, rein);
        FxBudget.countQuads(24 + spokes.length / 20 + 12 + 2);
    }

    /** The focal beam from the sun's core to the aimed point. */
    public static void fallenSun(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        if (d == null || !d.contains("BX")) {
            return;
        }
        Vec3 core = new Vec3(0.0D, 1.5D, 0.0D);
        Vec3 end = new Vec3(d.getDouble("BX"), d.getDouble("BY"), d.getDouble("BZ")).subtract(ctx.origin);
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int tongue = MagicVertex.pack(FxKinds.Filament.FLAME_TONGUE.id(), 6, 2, 0.5F + 0.5F * Mth.sin(ctx.age * 0.4F), ctx.seed, 0);
        FilamentPainter.link(ctx, beam, core, end, 0.35F, profile.color(ColorRole.HOT), 0.9F, tongue);
        FilamentPainter.link(ctx, beam, core, end, 0.15F, 0xFFFFFF, 0.9F, tongue);
        ctx.pose.pushPose();
        ctx.pose.translate(end.x, end.y, end.z);
        OrbPainter.billboard(ctx, FxKinds.Orb.BLOOM_FLASH, 1.1F, profile.color(ColorRole.BRIGHT), 0.9F, 0.5F, 4, 2);
        ctx.pose.popPose();
        FxBudget.countQuads(5);
    }

    /** The eclipse: a giant void-core disc with a thin corona in the sky, ink shadow on the ground with a gold burning rim. */
    public static void totalEclipse(FxContext ctx, VisualProfile profile, Silhouette s) {
        float radius = s.sizeA();
        float height = Math.max(1.0F, ctx.extra);
        float open = Mth.clamp((ctx.age - 20.0F) / 12.0F, 0.0F, 1.0F);
        if (ctx.life > 0.0F) {
            open *= Mth.clamp((ctx.life - ctx.age) / 16.0F, 0.0F, 1.0F);
        }
        if (open <= 0.0F) {
            return;
        }
        int ink = profile.color(ColorRole.INK);
        int hot = profile.color(ColorRole.HOT);
        int bright = profile.color(ColorRole.BRIGHT);
        // sky disc: a dark plasma core seen from below plus a thin gold corona
        ctx.pose.pushPose();
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        VertexConsumer darkOrb = ctx.buffers.getBuffer(MagicalFxRenderTypes.plasmaOrbDark());
        int core = MagicVertex.pack(FxKinds.Orb.VOID_CORE.id(), 8, 4, ctx.phase, ctx.seed, 1);
        FxMesh.emit(darkOrb, ctx.pose.last().pose(), FxMesh.square(), radius * open, radius * open, 1.0F, ink, 1.0F, core);
        VertexConsumer orb = ctx.buffers.getBuffer(MagicalFxRenderTypes.plasmaOrb());
        int halo = MagicVertex.pack(FxKinds.Orb.THIN_HALO.id(), 6, 3, 0.5F + 0.5F * Mth.sin(ctx.age * 0.1F), ctx.seed, 0);
        FxMesh.emit(orb, ctx.pose.last().pose(), FxMesh.square(), radius * 1.08F * open, radius * 1.08F * open, 1.0F, bright, 0.9F, halo);
        ctx.pose.popPose();
        // ground shadow and rim
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, -height + 0.05F, 0.0F);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.INK_STAIN, radius * open, ink, 0.9F, 0.5F, 8, 2);
        MarkPainter.mark(ctx, FxKinds.Mark.SHOCK_RING, radius * 1.05F * open, hot, 0.85F, 0.85F + 0.1F * Mth.sin(ctx.age * 0.3F), 4, 6);
        ctx.pose.popPose();
        // lensed star backdrop inside the shadow
        VertexConsumer lens = ctx.buffers.getBuffer(MagicalFxRenderTypes.lensWarp());
        int warp = MagicVertex.pack(FxKinds.Lens.GRAVITY_LENS.id(), 4, 6, ctx.phase, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, -height * 0.5F, 0.0F);
        if (ctx.cameraOrientation != null) {
            ctx.pose.mulPose(ctx.cameraOrientation);
        }
        FxMesh.emit(lens, ctx.pose.last().pose(), FxMesh.square(), radius * 0.6F * open, height * 0.45F * open, 1.0F, ink, 0.5F, warp);
        ctx.pose.popPose();
        FxBudget.countQuads(5);
    }

    /** The molten seam: a hairline that becomes a vein-lit wound, crack webs spreading from it. */
    public static void tectonicSeam(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = dir(ctx);
        Vec3 axis = new Vec3(-f.z, 0.0D, f.x);
        float half = s.sizeA();
        float open = Mth.clamp(ctx.age / 24.0F, 0.0F, 1.0F);
        float wound = Mth.clamp((ctx.age - 24.0F) / 20.0F, 0.0F, 1.0F);
        int hot = profile.color(ColorRole.HOT);
        int base = profile.color(ColorRole.BASE);
        VertexConsumer rift = ctx.buffers.getBuffer(MagicalFxRenderTypes.riftCut());
        int packed = MagicVertex.pack(FxKinds.Rift.SEAM_HAIRLINE.id(), 8, FxKinds.RiftInterior.TINTED_REALM.ordinal(), open, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.04F, 0.0F);
        ctx.pose.mulPose(Axis.YP.rotation((float) Math.atan2(f.x, f.z)));
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        FxMesh.emit(rift, ctx.pose.last().pose(), FxMesh.square(), half * open, 0.25F + 0.35F * wound, 1.0F, hot, 0.95F, packed);
        ctx.pose.popPose();
        // vein along the seam
        VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
        int vein = MagicVertex.pack(FxKinds.Filament.VEIN.id(), 6, 0, 0.5F + 0.5F * Mth.sin(ctx.age * 0.25F), ctx.seed, 1);
        FilamentPainter.link(ctx, dark, axis.scale(-half * open).add(0.0D, 0.1D, 0.0D), axis.scale(half * open).add(0.0D, 0.1D, 0.0D), 0.12F, hot, 0.9F * wound, vein);
        // crack webs at the seam's ends and middle
        for (int i = -1; i <= 1; i++) {
            ctx.pose.pushPose();
            ctx.pose.translate(axis.x * half * 0.6F * i, 0.03F, axis.z * half * 0.6F * i);
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            MarkPainter.mark(ctx, FxKinds.Mark.CRACK_WEB, 2.5F * open, base, 0.7F, wound, 6, i + 4);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(6);
    }

    /** One stepped box of a hinged rock plate: a slab along the seam axis, tilted by the synced angle, crack glow on the seam side. */
    public static void tectonicPlate(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 axis = dir(ctx);
        float angle = ctx.extra;
        int stone = profile.color(ColorRole.DIM);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.STONE.id(), 6, 8, Mth.clamp(Math.abs(angle) / 1.4F, 0.0F, 0.6F), ctx.seed, 0);
        ctx.pose.pushPose();
        FilamentPainter.orientAlong(ctx.pose, axis);
        // local +Z is the seam axis; tilt about it
        ctx.pose.mulPose(Axis.ZP.rotation(angle));
        ctx.pose.translate(0.0F, -0.3F, 0.0F);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.slab(), 1.2F, 0.6F, s.sizeA(), stone, 1.0F, packed);
        ctx.pose.popPose();
        FxBudget.countQuads(6);
    }

    public static void register() {
        CustomPainters.register("scalding_geyser", FusionPainters::scaldingGeyser);
        CustomPainters.register("black_orrery", FusionPainters::blackOrrery);
        CustomPainters.register("cinder_chariot", FusionPainters::cinderChariot);
        CustomPainters.register("fallen_sun", FusionPainters::fallenSun);
        CustomPainters.register("total_eclipse", FusionPainters::totalEclipse);
        CustomPainters.register("tectonic_seam", FusionPainters::tectonicSeam);
        CustomPainters.register("tectonic_plate", FusionPainters::tectonicPlate);
    }
}
