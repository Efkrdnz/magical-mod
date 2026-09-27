package com.efkrdnz.magical.client.renderer.fx.paint.custom;

import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.paint.CustomPainters;
import com.efkrdnz.magical.client.renderer.sword.SwordBladeRenderer;
import com.efkrdnz.magical.client.renderer.sword.SwordBladeRenderer.Geometry;
import com.efkrdnz.magical.magic.skill.sword.BelowSkill;
import com.efkrdnz.magical.magic.sword.stance.Pattern;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * The two pieces of Sword steel that are not a sword of the formation: One Blade's greatsword and
 * Below's risers. Both are Duskfall through {@link SwordBladeRenderer} and nothing else - the
 * school draws its swords and no light, mark or shape round them.
 */
public final class SwordPainters {

    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);

    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);

    /**
     * How far a riser leans out from the middle of the eruption: the flown blade's own cant. Every
     * bound {@code BelowSilhouetteTest} holds the risers to is measured on the canted blade, and a
     * lean of the same size stays inside it.
     */
    private static final double RISER_LEAN = Math.toRadians(Geometry.CANT_YAW);

    /** Duskfall's whole length at 1:1, in blocks. */
    private static final double MODEL_LENGTH = (Geometry.MODEL_MAX_Y - Geometry.MODEL_MIN_Y) / Geometry.MODEL_UNITS;

    private SwordPainters() {
    }

    public static void register() {
        CustomPainters.register("one_blade", SwordPainters::oneBlade);
        CustomPainters.register("below", SwordPainters::below);
    }

    /**
     * One Blade: one Duskfall, {@code sizeA} blocks from pommel to point, the pommel in the hand and
     * the point down the look. It grows out of the hand over the fuse and is whole once formed.
     */
    public static void oneBlade(FxContext ctx, VisualProfile profile, Silhouette s) {
        Vec3 heading = ctx.direction.lengthSqr() > 1.0E-6D ? ctx.direction.normalize() : FORWARD;
        // VALUE runs from 1 to 0 over the fuse and sits at 0 once the blade has formed.
        float formed = 1.0F - Math.max(0.0F, Math.min(1.0F, ctx.extra));
        double length = s.sizeA() * formed * (2.0F - formed);
        if (length <= 1.0E-3D) {
            return;
        }
        ctx.pose.pushPose();
        // steel() draws about the middle of the length, so the middle goes half a length out and
        // the pommel lands on the hand.
        ctx.pose.translate(heading.x * length * 0.5D, heading.y * length * 0.5D, heading.z * length * 0.5D);
        SwordBladeRenderer.steel(ctx, heading, (float) (length / MODEL_LENGTH));
        ctx.pose.popPose();
    }

    /**
     * Below: one Duskfall per sword that went under, coming up through the committed point in the
     * stance's pattern, each leaning out from the middle. Where they stand and how high they are is
     * {@link BelowSkill}'s arithmetic, and the test holds it inside the cylinder that catches.
     */
    public static void below(FxContext ctx, VisualProfile profile, Silhouette s) {
        CompoundTag data = ctx.data;
        if (data == null || !data.contains(BelowSkill.DATA_SWORDS)) {
            return;
        }
        int count = Math.min(Geometry.MAX_BLADES, data.getInt(BelowSkill.DATA_SWORDS));
        if (count <= 0) {
            return;
        }
        Pattern[] patterns = Pattern.values();
        Pattern pattern = patterns[Math.floorMod(data.getInt(BelowSkill.DATA_PATTERN), patterns.length)];
        float yaw = data.getFloat(BelowSkill.DATA_YAW);
        double height = BelowSkill.riseHeight(ctx.age, Geometry.axialReach());
        double sin = Math.sin(RISER_LEAN);
        double cos = Math.cos(RISER_LEAN);
        for (int i = 0; i < count; i++) {
            double[] offset = BelowSkill.riseOffset(pattern, i, count, yaw);
            double out = Math.hypot(offset[0], offset[1]);
            Vec3 heading = out < 1.0E-6D ? UP : new Vec3(offset[0] / out * sin, cos, offset[1] / out * sin);
            ctx.pose.pushPose();
            ctx.pose.translate(offset[0], height, offset[1]);
            SwordBladeRenderer.steel(ctx, heading, Geometry.SCALE);
            ctx.pose.popPose();
        }
    }
}
