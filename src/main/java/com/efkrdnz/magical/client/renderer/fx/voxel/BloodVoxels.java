package com.efkrdnz.magical.client.renderer.fx.voxel;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.MagicVertex;
import com.efkrdnz.magical.client.renderer.fx.MagicalFxRenderTypes;
import com.efkrdnz.magical.magic.blood.BloodFieldData;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a field of blood voxels. One call, and the entire client half of the framework.
 *
 * <p>A new blood ability needs three things to use this: sync a {@link BloodFieldData} from its
 * behaviour, give its profile a custom silhouette, and register a painter that calls this with its
 * own {@link VoxelStyle}. No new entity type, no new renderer, no new render type - the shared
 * effect entity and the body render type already do all of that.
 */
public final class BloodVoxels {

    /** Launch height as a share of the caster's height: roughly the chest. */
    private static final double LAUNCH_HEIGHT = 0.6D;

    private BloodVoxels() {
    }

    /**
     * @return how many quads were drawn, so a caller can tell a field that skipped from one that
     *         drew nothing
     */
    public static int paint(FxContext ctx, VisualProfile profile, Silhouette silhouette,
            VoxelStyle style) {
        VoxelField field = VoxelFields.of(ctx.data, style, profile.budgetClass());
        if (field == null) {
            return 0;
        }

        float keep = VoxelMotion.lodKeep(ctx.cameraPos.length());
        if (keep <= 0.0F) {
            return 0;
        }
        int wanted = Math.max(1, Math.round(field.count() * keep));
        int granted = FxBudget.claimVoxels(wanted);
        if (granted <= 0) {
            return 0;
        }
        if (granted < wanted) {
            // Losing the race thins the field further rather than truncating it, so the shape stays
            // recognisable instead of ending halfway along itself.
            keep *= granted / (float) wanted;
        }

        BloodFieldData source = field.source();
        Entity owner = ownerOf(source.ownerId());
        float yaw = source.baseYaw();
        float pitch = source.basePitch();
        if (source.keepRotating() && owner instanceof LivingEntity living) {
            // Head yaw rather than body yaw, because that is what a player aims with, and
            // getViewYRot interpolates it the wrap-safe way - a remote player crossing 180 would
            // otherwise whip the whole field the long way round inside one frame.
            yaw = living.getViewYRot(ctx.partialTick);
            pitch = living.getViewXRot(ctx.partialTick);
        }
        double[] basis = BloodShapeGeometry.basis(yaw, pitch);

        // The effect entity's position only moves when a packet arrives, so a field meant to sit on
        // the caster lags a tick of their motion - a fifth of a block at a sprint, and visibly a
        // stutter. Re-anchoring on the owner every frame is the other half of drawing this smoothly:
        // the partial tick alone is not enough.
        Vec3 shift = Vec3.ZERO;
        double launchHeight = 1.0D;
        if (owner != null) {
            shift = owner.getPosition(ctx.partialTick).subtract(ctx.origin);
            launchHeight = owner.getBbHeight() * LAUNCH_HEIGHT;
        }

        // cameraPos is the camera relative to the entity origin, not the camera. Every extra
        // translate has to come back out of it, or the face mask picks the wrong three faces
        // whenever the camera is near a cube's plane.
        float cameraX = (float) (ctx.cameraPos.x - shift.x);
        float cameraY = (float) (ctx.cameraPos.y - shift.y - silhouette.offsetY());
        float cameraZ = (float) (ctx.cameraPos.z - shift.z);

        VertexConsumer consumer = ctx.buffers.getBuffer(MagicalFxRenderTypes.shardBody());
        VoxelEmitter emitter = new VoxelEmitter(consumer, ctx.pose.last(), cameraX, cameraY, cameraZ);

        int basePacked = MagicVertex.pack(style.kind().id(), style.shaderCount(),
                style.shaderParamB(), 0.0F, ctx.seed, 0);
        int alpha = Mth.clamp(Math.round(silhouette.opacity() * 255.0F), 1, 255);
        int rgb = profile.color(style.role());

        float pitchSize = source.pitch();
        float anchorX = (float) shift.x;
        float anchorY = (float) (shift.y + launchHeight);
        float anchorZ = (float) shift.z;

        double[] projected = new double[3];
        float[] position = new float[3];

        for (int i = 0; i < field.count; i++) {
            float edge = VoxelMotion.lodEdge(field.lodKey[i], keep);
            if (edge <= 0.0F) {
                continue;
            }
            float rank = field.rank[i];
            float delay = VoxelMotion.delayFor(style.timing(), source.formTicks(), rank, i, ctx.seed);
            float erosion = VoxelMotion.erosion(style.timing(), rank, delay, ctx.age, ctx.life);
            float half = VoxelMotion.halfExtent(style, pitchSize, i, ctx.seed, ctx.age, delay,
                    erosion, edge);
            if (half <= 0.0F) {
                continue;
            }

            BloodShapeGeometry.project(field.targets[i * 3], field.targets[i * 3 + 1],
                    field.targets[i * 3 + 2], source.heightOffset(), basis, projected);
            float progress = VoxelMotion.progress(style.timing(), delay, ctx.age);
            VoxelMotion.place(style, i, ctx.seed, ctx.age, progress,
                    (float) (projected[0] + shift.x), (float) (projected[1] + shift.y),
                    (float) (projected[2] + shift.z), anchorX, anchorY, anchorZ, position);

            // 0.7 is where the body shader starts its dissolve, so the erosion channel maps onto the
            // window it already has rather than needing one of its own.
            int packed = MagicVertex.withPhase(basePacked, 0.7F + 0.3F * erosion);
            emitter.cube(position[0], position[1], position[2], half, rgb, alpha, packed);
        }

        int quads = emitter.faces();
        FxBudget.countQuads(quads);
        return quads;
    }

    private static Entity ownerOf(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (id <= 0 || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getEntity(id);
    }
}
