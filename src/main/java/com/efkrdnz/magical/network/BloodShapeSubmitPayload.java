package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.blood.shape.BloodShape;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One edited shape, on its way to the server.
 *
 * <p>One slot per packet rather than the whole book. The editor only ever dirties the slot being
 * drawn in, and sending nine would let a hostile client push nine shapes' worth of validation per
 * packet instead of one.
 *
 * <p>Points travel in the packed form they are already stored in, so there is no second encoding
 * between here and disk to disagree with the first. Worst case is 128 points and four stroke ends,
 * a little over 600 bytes against the 32767-byte serverbound limit, and both list caps are enforced
 * by the codec itself - an over-length list is refused while it is being read, before a byte of it
 * reaches any of this mod's code.
 */
public record BloodShapeSubmitPayload(int slot, int heightPercent, int spreadPercent,
        int flags, List<Integer> points, List<Integer> ends) implements CustomPacketPayload {

    private static final int MAX_POINTS =
            BloodShapeRules.MAX_STROKES_PER_SHAPE * BloodShapeRules.MAX_POINTS_PER_STROKE;

    public static final Type<BloodShapeSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "blood_shape_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BloodShapeSubmitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BloodShapeSubmitPayload::slot,
                    ByteBufCodecs.VAR_INT, BloodShapeSubmitPayload::heightPercent,
                    // Signed, so VAR_INT's zigzag-free encoding costs five bytes at the down end.
                    // Five bytes once per edit is not worth a second encoding to get wrong.
                    ByteBufCodecs.VAR_INT, BloodShapeSubmitPayload::spreadPercent,
                    ByteBufCodecs.VAR_INT, BloodShapeSubmitPayload::flags,
                    ByteBufCodecs.INT.apply(ByteBufCodecs.list(MAX_POINTS)),
                    BloodShapeSubmitPayload::points,
                    ByteBufCodecs.INT.apply(ByteBufCodecs.list(BloodShapeRules.MAX_STROKES_PER_SHAPE)),
                    BloodShapeSubmitPayload::ends,
                    BloodShapeSubmitPayload::new);

    /** Flattens a shape for the wire. */
    public static BloodShapeSubmitPayload of(int slot, BloodShape shape) {
        List<Integer> points = new ArrayList<>(shape.pointCount());
        for (int packed : shape.pointsCopy()) {
            points.add(packed);
        }
        List<Integer> ends = new ArrayList<>(shape.strokeCount());
        for (int end : shape.strokeEndsCopy()) {
            ends.add(end);
        }
        return new BloodShapeSubmitPayload(slot, shape.heightPercent(), shape.spreadPercent(),
                shape.flags(), List.copyOf(points), List.copyOf(ends));
    }

    /**
     * Rebuilds the shape.
     *
     * <p>Every cap, every coordinate bound and the whole stroke table are re-applied on the way in,
     * because {@link BloodShape#ofFlat} is written to distrust what it is handed. Nothing here
     * believes the numbers it was sent - and none of it is the reach check, which happens at cast
     * time against the reach the caster has then.
     */
    public BloodShape toShape() {
        int[] packed = new int[points.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = points.get(i);
        }
        int[] strokeEnds = new int[ends.size()];
        for (int i = 0; i < strokeEnds.length; i++) {
            strokeEnds[i] = ends.get(i);
        }
        return BloodShape.ofFlat(packed, strokeEnds, heightPercent, spreadPercent, flags);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
