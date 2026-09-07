package com.efkrdnz.magical.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/**
 * BRAND's bookkeeping: every non-finisher hit from a branding weapon leaves a mark on the target,
 * and the finisher cashes all of them in at once. Stacks are short lived, so a combo dropped
 * halfway through loses its build-up.
 */
public final class ForgeBrandService {

    private static final int LIFE_TICKS = 100;

    private record Brand(int stacks, long expiryTick) {}

    private static final Map<UUID, Brand> BRANDS = new HashMap<>();

    private ForgeBrandService() {}

    public static void addStack(LivingEntity target, long now) {
        Brand current = BRANDS.get(target.getUUID());
        int stacks = current == null || now >= current.expiryTick() ? 0 : current.stacks();
        BRANDS.put(target.getUUID(),
                new Brand(Math.min(ForgeStrikeMath.BRAND_MAX_STACKS, stacks + 1), now + LIFE_TICKS));
    }

    /** The stacks standing on {@code target}, cleared in the same breath. */
    public static int consume(LivingEntity target, long now) {
        Brand current = BRANDS.remove(target.getUUID());
        return current == null || now >= current.expiryTick() ? 0 : current.stacks();
    }

    public static int stacks(LivingEntity target, long now) {
        Brand current = BRANDS.get(target.getUUID());
        return current == null || now >= current.expiryTick() ? 0 : current.stacks();
    }

    public static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        BRANDS.entrySet().removeIf(entry -> now >= entry.getValue().expiryTick());
    }
}
