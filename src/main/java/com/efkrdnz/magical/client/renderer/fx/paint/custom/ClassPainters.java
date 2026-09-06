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
import com.efkrdnz.magical.magic.skill.classes.HailVolleySkill;
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

/** Bespoke painters for the class rewards. */
public final class ClassPainters {
    private ClassPainters() {}

    private static Vec3 dir(FxContext ctx) {
        return ctx.direction.lengthSqr() > 1.0E-6D ? ctx.direction.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
    }

    /** A gold hammer head on a haft, raised over the shoulder and swung through a bright arc (ctx.extra = swing 0..1). */
    public static void dawnhammer(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = dir(ctx);
        float swing = Mth.clamp(ctx.extra, 0.0F, 1.0F);
        // raised (-70 deg behind) to slammed (+80 deg ahead), fast through the middle
        float angle = Mth.lerp(swing * swing * (3.0F - 2.0F * swing), -70.0F, 80.0F);
        int gold = profile.color(ColorRole.BASE);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.GOLD.id(), 8, 6, 0.0F, ctx.seed, 0);
        ctx.pose.pushPose();
        FilamentPainter.orientAlong(ctx.pose, f);
        // pivot at the shoulder; the haft goes out along +Y then rotates about local X (across the swing)
        ctx.pose.mulPose(Axis.XP.rotationDegrees(angle));
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.column(), 0.07F, 3.0F, 0.07F, gold, 1.0F, packed);
        ctx.pose.translate(0.0F, 3.0F, 0.0F);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        float[] head = FxMesh.prism(8);
        FxMesh.emit(solid, ctx.pose.last().pose(), head, 0.45F, 0.9F, 0.45F, gold, 1.0F, packed);
        ctx.pose.popPose();
        // the swept arc
        if (swing > 0.25F) {
            VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
            int arcPacked = MagicVertex.pack(FxKinds.Filament.RIBBON.id(), 6, 0, swing, ctx.seed, 0);
            float sweep = Mth.clamp((angle + 70.0F), 0.0F, 150.0F);
            float[] arc = FxMesh.arc(12, 0.86F, -70.0F, sweep);
            ctx.pose.pushPose();
            FilamentPainter.orientAlong(ctx.pose, f);
            ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F));
            float fadeOut = Math.max(0.0F, 1.0F - Math.max(0.0F, swing - 0.7F) * 3.0F);
            FxMesh.emit(beam, ctx.pose.last().pose(), arc, 3.3F, 3.3F, 1.0F, hot, 0.8F * fadeOut, arcPacked);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(30);
    }

    /** Thick gold chain links between the two ends (and the stake), white-hot when taut. */
    public static void gildedChain(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        if (d == null || !d.contains("AX")) {
            return;
        }
        Vec3 a = new Vec3(d.getDouble("AX"), d.getDouble("AY"), d.getDouble("AZ")).subtract(ctx.origin);
        Vec3 b = new Vec3(d.getDouble("BX"), d.getDouble("BY"), d.getDouble("BZ")).subtract(ctx.origin);
        boolean taut = d.getBoolean("Taut");
        int rgb = profile.color(taut ? ColorRole.HOT : ColorRole.BASE);
        chain(ctx, a, b, rgb, taut);
        if (d.contains("SX")) {
            Vec3 stake = new Vec3(d.getDouble("SX"), d.getDouble("SY"), d.getDouble("SZ")).subtract(ctx.origin);
            chain(ctx, a.add(b).scale(0.5D), stake, rgb, taut);
        }
    }

    private static void chain(FxContext ctx, Vec3 from, Vec3 to, int rgb, boolean taut) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.2D) {
            return;
        }
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.GOLD.id(), 4, 8, 0.0F, ctx.seed, 0);
        int links = Math.max(2, (int) Math.round(len / 0.45D));
        float[] ring = FxMesh.ring(10, 0.3F);
        float sag = taut ? 0.0F : (float) Math.min(0.8D, len * 0.12D);
        for (int i = 0; i < links; i++) {
            double t = (i + 0.5D) / links;
            Vec3 p = from.add(d.scale(t)).add(0.0D, -sag * 4.0D * t * (1.0D - t), 0.0D);
            ctx.pose.pushPose();
            ctx.pose.translate(p.x, p.y, p.z);
            FilamentPainter.orientAlong(ctx.pose, d);
            ctx.pose.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? 0.0F : 90.0F));
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), ring, 0.22F, 0.22F, 1.0F, rgb, 1.0F, packed);
            ctx.pose.popPose();
        }
        // cuff rings at both ends
        for (Vec3 p : new Vec3[] {from, to}) {
            ctx.pose.pushPose();
            ctx.pose.translate(p.x, p.y, p.z);
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.ring(16, 0.12F), 0.45F, 0.45F, 1.0F, rgb, 1.0F, packed);
            ctx.pose.popPose();
        }
        FxBudget.countQuads(links * 10 + 32);
    }

    /** A tattered ember-crust war banner on a pole strapped to the caster's back, streaming behind. */
    public static void bloodshout(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 back = dir(ctx);
        int base = profile.color(ColorRole.BASE);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int pole = MagicVertex.pack(FxKinds.Body.METAL_BANDS.id(), 4, 4, 0.0F, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.translate(back.x * 0.35D, -0.6D, back.z * 0.35D);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.column(), 0.05F, 3.2F, 0.05F, base, 1.0F, pole);
        // the banner sheet hangs from the top of the pole, along the back direction, waving
        VertexConsumer field = ctx.buffers.getBuffer(MagicalFxRenderTypes.surfaceField());
        int sheet = MagicVertex.pack(FxKinds.Field.EMBER_CRUST.id(), 6, 4, 0.5F + 0.5F * Mth.sin(ctx.age * 0.15F), ctx.seed, 0);
        ctx.pose.translate(0.0F, 3.2F, 0.0F);
        FilamentPainter.orientAlong(ctx.pose, back);
        ctx.pose.mulPose(Axis.YP.rotationDegrees(90.0F));
        ctx.pose.translate(0.0F, -0.8F, 0.0F);
        FxMesh.emit(field, ctx.pose.last().pose(), FxMesh.square(), 0.55F, 0.8F, 1.0F, hot, 0.9F, sheet);
        ctx.pose.popPose();
        FxBudget.countQuads(3);
    }

    /** The caster re-rendered as fitted ashlar masonry under a crenellated stone mantle. */
    public static void livingBulwark(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = dir(ctx);
        int stone = profile.color(ColorRole.DIM);
        int seam = profile.color(ColorRole.BASE);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.STONE.id(), 5, 6, 0.0F, ctx.seed, 0);
        float in = Mth.clamp(ctx.age / 6.0F, 0.0F, 1.0F);
        ctx.pose.pushPose();
        FilamentPainter.orientAlong(ctx.pose, f);
        // legs
        for (int side = -1; side <= 1; side += 2) {
            ctx.pose.pushPose();
            ctx.pose.translate(side * 0.16F, 0.0F, 0.0F);
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.slab(), 0.14F, 0.75F * in, 0.16F, stone, 1.0F, packed);
            ctx.pose.popPose();
        }
        // torso
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.75F, 0.0F);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.slab(), 0.36F, 0.7F * in, 0.22F, stone, 1.0F, packed);
        ctx.pose.popPose();
        // arms
        for (int side = -1; side <= 1; side += 2) {
            ctx.pose.pushPose();
            ctx.pose.translate(side * 0.5F, 0.8F, 0.0F);
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.slab(), 0.12F, 0.65F * in, 0.14F, stone, 1.0F, packed);
            ctx.pose.popPose();
        }
        // keystone head
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 1.45F, 0.0F);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.prism(5), 0.24F, 0.4F * in, 0.24F, seam, 1.0F, packed);
        ctx.pose.popPose();
        // the mantle: seven merlons around the shoulders
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 1.35F, 0.0F);
        float[] merlons = FxMesh.plateFan(7);
        FxMesh.emit(solid, ctx.pose.last().pose(), merlons, 0.7F * in, 0.35F, 0.7F * in, stone, 1.0F, packed);
        ctx.pose.popPose();
        ctx.pose.popPose();
        FxBudget.countQuads(60);
    }

    /** Sky ring-sigil over a ground reticle with the scheduled ice needles streaking down between them. */
    public static void hailVolley(FxContext ctx, VisualProfile profile, Silhouette s) {
        float radius = s.sizeA();
        float skyH = Math.max(2.0F, ctx.extra);
        int base = profile.color(ColorRole.BASE);
        int bright = profile.color(ColorRole.BRIGHT);
        float warn = Mth.clamp(ctx.age / HailVolleySkill.WARNING, 0.0F, 1.0F);
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        // sky ring (inverted sigil stand-in) and ground reticle ring
        int ring = MagicVertex.pack(FxKinds.Filament.DASH_TRAIN.id(), 30, 0, warn, ctx.seed, 0);
        for (int k = 0; k < 2; k++) {
            ctx.pose.pushPose();
            ctx.pose.translate(0.0F, k == 0 ? skyH : 0.05F, 0.0F);
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            ctx.pose.mulPose(Axis.ZP.rotationDegrees(ctx.age * (k == 0 ? -1.5F : 1.0F)));
            FxMesh.emit(beam, ctx.pose.last().pose(), FxMesh.ring(40, 0.03F), radius * warn, radius * warn, 1.0F, base, 0.8F, ring);
            ctx.pose.popPose();
        }
        int quads = 80;
        // bolts in flight: the same schedule the server uses
        int rainEnd = ctx.life > 0.0F ? (int) ctx.life - 8 : HailVolleySkill.WARNING + HailVolleySkill.RAIN;
        int fallTicks = Math.max(1, Math.round(skyH / HailVolleySkill.BOLT_SPEED));
        VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
        int bolt = MagicVertex.pack(FxKinds.Filament.CRYSTAL_SHARD.id(), 2, 0, 0.5F, ctx.seed, 1);
        int firstTick = Math.max(HailVolleySkill.WARNING, (int) ctx.age - fallTicks);
        for (int t = firstTick; t <= (int) ctx.age && t < rainEnd; t++) {
            if ((t - HailVolleySkill.WARNING) % HailVolleySkill.BOLT_SPACING != 0) {
                continue;
            }
            float fallen = (ctx.age - t) * HailVolleySkill.BOLT_SPEED;
            if (fallen > skyH) {
                continue;
            }
            for (int i = 0; i < HailVolleySkill.BOLTS_PER_WAVE; i++) {
                Vec3 off = HailVolleySkill.boltOffset(ctx.seed, t, i, radius);
                ctx.pose.pushPose();
                ctx.pose.translate(off.x, skyH - fallen, off.z);
                ctx.pose.mulPose(Axis.XP.rotationDegrees(180.0F));
                FxMesh.emit(dark, ctx.pose.last().pose(), FxMesh.column(), 0.05F, 1.2F, 0.05F, bright, 0.95F, bolt);
                ctx.pose.popPose();
                quads += 2;
            }
        }
        FxBudget.countQuads(quads);
    }

    /** A translucent teal wolf: six ribbon strands nose to tail, liquid-rope legs, two slit eyes, mist off the tail. */
    public static void spiritWolf(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = dir(ctx);
        float leap = Mth.clamp(ctx.extra, 0.0F, 1.0F);
        int base = profile.color(ColorRole.BASE);
        int bright = profile.color(ColorRole.BRIGHT);
        int hot = profile.color(ColorRole.HOT);
        VertexConsumer beam = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentBeam());
        int ribbon = MagicVertex.pack(FxKinds.Filament.RIBBON.id(), 6, 1, 0.5F, ctx.seed, 0);
        int rope = MagicVertex.pack(FxKinds.Filament.LIQUID_ROPE.id(), 4, 0, 0.5F, ctx.seed, 1);
        VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
        ctx.pose.pushPose();
        FilamentPainter.orientAlong(ctx.pose, f);
        float stretch = 1.0F + 0.35F * leap;
        float bob = Mth.sin(ctx.age * 0.5F) * 0.04F;
        // body strands: from tail (z=-0.7) to nose (z=+0.75), fanned around the spine
        for (int i = 0; i < 6; i++) {
            float a = i / 6.0F * Mth.TWO_PI;
            float rx = Mth.cos(a) * 0.16F;
            float ry = Mth.sin(a) * 0.14F;
            Vec3 tail = new Vec3(rx * 0.6F, 0.55F + ry * 0.6F + bob, -0.7F * stretch);
            Vec3 nose = new Vec3(rx * 0.35F, 0.62F + ry * 0.4F + bob + 0.1F * leap, 0.75F * stretch);
            FilamentPainter.link(ctx, beam, tail, nose, 0.05F, i % 2 == 0 ? base : bright, 0.75F, ribbon);
        }
        // legs: four short liquid ropes swinging with the gait
        for (int i = 0; i < 4; i++) {
            float z = i < 2 ? 0.4F : -0.4F;
            float x = (i % 2 == 0 ? -0.14F : 0.14F);
            float swing = Mth.sin(ctx.age * 0.6F + (i % 2 == 0 ? 0.0F : Mth.PI) + (i < 2 ? 0.0F : Mth.PI)) * 0.2F * (1.0F - leap);
            Vec3 hip = new Vec3(x, 0.5F, z * stretch);
            Vec3 paw = new Vec3(x, 0.02F, z * stretch + swing + (leap > 0.3F ? (i < 2 ? 0.25F : -0.25F) * leap : 0.0F));
            FilamentPainter.link(ctx, dark, hip, paw, 0.045F, base, 0.85F, rope);
        }
        // head: two slit eyes
        for (int side = -1; side <= 1; side += 2) {
            ctx.pose.pushPose();
            ctx.pose.translate(side * 0.09F, 0.72F + bob, 0.68F * stretch);
            OrbPainter.billboard(ctx, FxKinds.Orb.EYE_SLIT, 0.06F, hot, 1.0F, 0.5F, 2, 0);
            ctx.pose.popPose();
        }
        // tail mist
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.5F, -0.85F * stretch);
        OrbPainter.billboard(ctx, FxKinds.Orb.PLASMA, 0.18F, base, 0.35F, ctx.phase, 3, 2);
        ctx.pose.popPose();
        ctx.pose.popPose();
        FxBudget.countQuads(6 * 2 + 4 * 2 + 3);
    }

    /** The lifted block: a stone prism inside the refraction bubble. */
    public static void arcaneGrasp(FxContext ctx, VisualProfile profile, Silhouette s) {
        if (ctx.data == null || !ctx.data.getBoolean("Rock")) {
            return;
        }
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.STONE.id(), 4, 6, 0.0F, ctx.seed, 0);
        ctx.pose.pushPose();
        ctx.pose.mulPose(Axis.YP.rotationDegrees(ctx.age * 2.0F));
        ctx.pose.mulPose(Axis.XP.rotationDegrees(15.0F));
        ctx.pose.translate(0.0F, -0.45F, 0.0F);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.slab(), 0.45F, 0.9F, 0.45F, profile.color(ColorRole.DIM), 1.0F, packed);
        ctx.pose.popPose();
        FxBudget.countQuads(6);
    }

    /** A glazed porcelain hare: hunched torso, two ear blades, stub legs; crackle veins spread with ctx.extra (cracks 0..1). */
    public static void arcanumHare(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 f = dir(ctx);
        float cracks = Mth.clamp(ctx.extra, 0.0F, 1.0F);
        float hop = ctx.data != null ? ctx.data.getFloat("Hop") : 0.0F;
        int ivory = profile.color(ColorRole.BRIGHT);
        int vein = profile.color(ColorRole.BASE);
        VertexConsumer solid = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        int packed = MagicVertex.pack(FxKinds.Body.BONE_IVORY.id(), 6, 6, cracks, ctx.seed, 0);
        float in = Mth.clamp(ctx.age / 6.0F, 0.0F, 1.0F);
        ctx.pose.pushPose();
        FilamentPainter.orientAlong(ctx.pose, f);
        ctx.pose.translate(0.0F, 0.12F * hop, 0.0F);
        // torso (hunched sphere), head, ears, legs
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.0F, -0.05F);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.sphere(12, 6), 0.26F * in, 0.24F * in, 0.32F * in, ivory, 1.0F, packed);
        ctx.pose.popPose();
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, 0.3F, 0.28F);
        FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.sphere(10, 5), 0.16F * in, 0.16F * in, 0.18F * in, ivory, 1.0F, packed);
        ctx.pose.popPose();
        for (int side = -1; side <= 1; side += 2) {
            ctx.pose.pushPose();
            ctx.pose.translate(side * 0.07F, 0.4F, 0.26F);
            ctx.pose.mulPose(Axis.ZP.rotationDegrees(side * 12.0F));
            ctx.pose.mulPose(Axis.XP.rotationDegrees(-15.0F));
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.column(), 0.045F, 0.42F * in, 0.02F, ivory, 1.0F, packed);
            ctx.pose.popPose();
        }
        for (int i = 0; i < 4; i++) {
            ctx.pose.pushPose();
            ctx.pose.translate((i % 2 == 0 ? -0.14F : 0.14F), -0.2F, i < 2 ? 0.18F : -0.2F);
            FxMesh.emit(solid, ctx.pose.last().pose(), FxMesh.slab(), 0.05F, 0.14F * in, 0.08F, ivory, 1.0F, packed);
            ctx.pose.popPose();
        }
        ctx.pose.popPose();
        // crackle veins as a faint dark filament ring at the torso, brighter as cracks grow
        if (cracks > 0.0F) {
            VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
            int veins = MagicVertex.pack(FxKinds.Filament.VEIN.id(), 4, 0, cracks, ctx.seed, 1);
            ctx.pose.pushPose();
            ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
            FxMesh.emit(dark, ctx.pose.last().pose(), FxMesh.ring(16, 0.12F), 0.3F, 0.3F, 1.0F, vein, cracks, veins);
            ctx.pose.popPose();
        }
        // the heptagon sigil it hops on
        ctx.pose.pushPose();
        ctx.pose.translate(0.0F, -0.28F, 0.0F);
        ctx.pose.mulPose(Axis.XP.rotationDegrees(90.0F));
        MarkPainter.mark(ctx, FxKinds.Mark.HEX_CELLS, 0.5F, vein, 0.6F, 0.5F, 7, 4);
        ctx.pose.popPose();
        FxBudget.countQuads(140);
    }

    /** Wet black veins rooted in each carrier's shadow crawling up the body, strung body to body. */
    public static void contagion(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag d = ctx.data;
        Level level = Minecraft.getInstance().level;
        if (d == null || level == null) {
            return;
        }
        int[] ids = d.getIntArray("C");
        int ink = profile.color(ColorRole.INK);
        int heart = profile.color(ColorRole.HOT);
        VertexConsumer dark = ctx.buffers.getBuffer(MagicalFxRenderTypes.filamentDark());
        int vein = MagicVertex.pack(FxKinds.Filament.VEIN.id(), 5, 0, 0.5F + 0.5F * Mth.sin(ctx.age * 0.3F), ctx.seed, 1);
        Vec3[] centres = new Vec3[ids.length];
        int quads = 0;
        for (int i = 0; i < ids.length; i++) {
            Entity e = level.getEntity(ids[i]);
            if (e == null) {
                continue;
            }
            Vec3 feet = e.position().subtract(ctx.origin);
            Vec3 chest = feet.add(0.0D, e.getBbHeight() * 0.7D, 0.0D);
            centres[i] = chest;
            float w = e.getBbWidth() * 0.5F;
            for (int k = 0; k < 3; k++) {
                float a = k / 3.0F * Mth.TWO_PI + ctx.seed;
                Vec3 root = feet.add(Mth.cos(a) * w * 1.3F, 0.02D, Mth.sin(a) * w * 1.3F);
                FilamentPainter.link(ctx, dark, root, chest, 0.05F, ink, 0.9F, vein);
                quads += 2;
            }
            ctx.pose.pushPose();
            ctx.pose.translate(chest.x, chest.y, chest.z);
            OrbPainter.billboard(ctx, FxKinds.Orb.VOID_CORE, 0.14F + 0.03F * Mth.sin(ctx.age * 0.3F), heart, 0.9F, 0.5F, 3, 0);
            ctx.pose.popPose();
            quads++;
        }
        // sagging veins between carriers close to each other
        for (int i = 0; i < centres.length; i++) {
            for (int j = i + 1; j < centres.length; j++) {
                if (centres[i] == null || centres[j] == null || centres[i].distanceTo(centres[j]) > 6.0D) {
                    continue;
                }
                Vec3 mid = centres[i].add(centres[j]).scale(0.5D).add(0.0D, -0.4D, 0.0D);
                FilamentPainter.link(ctx, dark, centres[i], mid, 0.04F, ink, 0.8F, vein);
                FilamentPainter.link(ctx, dark, mid, centres[j], 0.04F, ink, 0.8F, vein);
                quads += 4;
            }
        }
        FxBudget.countQuads(quads);
    }

    public static void register() {
        CustomPainters.register("dawnhammer", ClassPainters::dawnhammer);
        CustomPainters.register("gilded_chain", ClassPainters::gildedChain);
        CustomPainters.register("bloodshout", ClassPainters::bloodshout);
        CustomPainters.register("living_bulwark", ClassPainters::livingBulwark);
        CustomPainters.register("hail_volley", ClassPainters::hailVolley);
        CustomPainters.register("spirit_wolf", ClassPainters::spiritWolf);
        CustomPainters.register("arcane_grasp", ClassPainters::arcaneGrasp);
        CustomPainters.register("arcanum_hare", ClassPainters::arcanumHare);
        CustomPainters.register("contagion", ClassPainters::contagion);
    }
}
