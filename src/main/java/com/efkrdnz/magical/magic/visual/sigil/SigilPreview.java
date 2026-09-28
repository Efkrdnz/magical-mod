package com.efkrdnz.magical.magic.visual.sigil;

import com.efkrdnz.magical.magic.visual.sigil.SigilPlacement.Spawn;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /magical-debug sigils [ink]}: the library hung in the air in front of the player, as
 * hovering sigils sent past the particle setting so a capture always shows them. One ink is a
 * block of every symbol; no ink is every ink, one row each, top to bottom in declaration order.
 */
public final class SigilPreview {
    private static final int BLOCK_COLUMNS = 11;
    private static final double BLOCK_SPACING = 0.42D;
    private static final double BLOCK_DISTANCE = 3.5D;
    private static final float BLOCK_SCALE = 2.0F;
    /** Forty-three columns 0.6 apart is 26 blocks: inside a 16:9 frame at nine blocks, symbols about 22 pixels tall. */
    private static final double WALL_SPACING = 0.6D;
    private static final double WALL_DISTANCE = 9.0D;
    private static final float WALL_SCALE = 2.6F;

    private SigilPreview() {
    }

    /** Hangs the preview and returns how many sigils it sent. */
    public static int show(ServerPlayer player, Optional<SigilInk> only) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        List<SigilInk> inks = only.map(List::of).orElseGet(() -> new ArrayList<>(SigilInk.named().values()));
        boolean block = inks.size() == 1;
        int columns = block ? BLOCK_COLUMNS : Sigil.COUNT;
        int rows = block ? (Sigil.COUNT + BLOCK_COLUMNS - 1) / BLOCK_COLUMNS : inks.size();
        double spacing = block ? BLOCK_SPACING : WALL_SPACING;
        double distance = block ? BLOCK_DISTANCE : WALL_DISTANCE;
        float scale = block ? BLOCK_SCALE : WALL_SCALE;
        Vec3 centre = player.getEyePosition().add(flat.scale(distance));
        List<Spawn> cells = SigilPlacement.wall(centre.x, centre.y, centre.z, flat.x, flat.z, columns, rows, spacing);
        int sent = 0;
        for (int i = 0; i < cells.size(); i++) {
            int symbol = block ? i : i % columns;
            if (symbol >= Sigil.COUNT) {
                break;
            }
            SigilInk ink = block ? inks.get(0) : inks.get(i / columns);
            SigilMark mark = SigilMark.of(Sigil.values()[symbol]).ink(ink).motion(SigilMotion.HOVER).scale(scale);
            Spawn cell = cells.get(i);
            player.serverLevel().sendParticles(player, Sigils.options(mark, 0), true, true, cell.x(), cell.y(), cell.z(), 0, 0.0D, 0.0D, 0.0D, 0.0D);
            sent++;
        }
        return sent;
    }
}
