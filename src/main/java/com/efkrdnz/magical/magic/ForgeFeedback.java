package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.network.MagicalNetwork;

import net.minecraft.server.level.ServerPlayer;

/**
 * What a landed forged strike does to the wielder's own view. Each family gets its own weight -
 * a flurry barely registers, a slam shoves the whole camera - and the heavy variant of any of them
 * hits noticeably harder.
 */
public final class ForgeFeedback {

    private static final int HEAVY_OVERLAY_BONUS = 4;
    private static final float HEAVY_ALPHA_SCALE = 1.6f;
    private static final float HEAVY_SHAKE_SCALE = 1.5f;
    private static final int HEAVY_FREEZE_BONUS = 1;
    private static final float HEAVY_FOV_SCALE = 1.5f;

    private ForgeFeedback() {}

    public record Kick(int overlayTicks, float alpha, int shakeTicks, float shake, int freeze, float fov) {

        public Kick heavy() {
            return new Kick(overlayTicks + HEAVY_OVERLAY_BONUS, alpha * HEAVY_ALPHA_SCALE, shakeTicks,
                    shake * HEAVY_SHAKE_SCALE, freeze + HEAVY_FREEZE_BONUS, fov * HEAVY_FOV_SCALE);
        }
    }

    public static Kick kick(FormFamily family, boolean heavy) {
        Kick base = switch (family) {
            case SLASH -> new Kick(6, 0.30f, 4, 0.12f, 0, 0.02f);
            case CLEAVE -> new Kick(7, 0.34f, 5, 0.14f, 0, 0.02f);
            case THRUST -> new Kick(5, 0.26f, 3, 0.10f, 0, 0.03f);
            case SPIN -> new Kick(8, 0.30f, 6, 0.16f, 0, 0.02f);
            case SLAM -> new Kick(10, 0.42f, 8, 0.22f, 1, 0.04f);
            case WAVE -> new Kick(4, 0.20f, 2, 0.08f, 0, 0.01f);
            case RISING -> new Kick(7, 0.30f, 5, 0.14f, 0, 0.03f);
            case FLURRY -> new Kick(4, 0.18f, 3, 0.08f, 0, 0.01f);
        };
        return heavy ? base.heavy() : base;
    }

    /** Sent when a strike actually connects - a swing at empty air kicks nothing. */
    public static void send(ServerPlayer player, FormFamily family, boolean heavy, int color) {
        Kick kick = kick(family, heavy);
        MagicalNetwork.playFirstPersonImpact(player, color, kick.overlayTicks(), kick.alpha(), kick.shakeTicks(),
                kick.shake(), kick.freeze(), kick.fov());
    }
}
