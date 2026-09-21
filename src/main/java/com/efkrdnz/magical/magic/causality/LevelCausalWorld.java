package com.efkrdnz.magical.magic.causality;

import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * The one class that stands between the pure {@link Weaver} and a running server.
 *
 * <p>Every question the engine can ask, answered off a player and their state, and nothing else in
 * the Authority knows what a level is. It is built fresh for each dispatch, which is cheap and is
 * what keeps the answers consistent within one walk: the health a chain reads at its third pin is
 * the health it read at its first, even if an earlier consequence in the same dispatch has since
 * landed. A rule should be evaluated against the world as it was when the cause fired.
 */
public final class LevelCausalWorld implements CausalWorld {

    private final ServerPlayer player;
    private final PlayerMagicState state;
    private final Map<Integer, Long> gates;
    private final long now;
    private final float health;
    private final float mana;

    public LevelCausalWorld(ServerPlayer player, PlayerMagicState state, Map<Integer, Long> gates) {
        this.player = player;
        this.state = state;
        this.gates = gates;
        this.now = player.level().getGameTime();
        float max = Math.max(1.0F, player.getMaxHealth());
        this.health = Math.max(0.0F, Math.min(1.0F, player.getHealth() / max));
        this.mana = state.mana();
    }

    @Override
    public long now() {
        return now;
    }

    @Override
    public float ledger() {
        return state.ledger().held();
    }

    @Override
    public float paradox() {
        return state.paradox().value();
    }

    @Override
    public float mana() {
        return mana;
    }

    @Override
    public boolean markAlive() {
        return marked() != null;
    }

    @Override
    public float selfHealth() {
        return health;
    }

    @Override
    public double distanceTo(int entityId) {
        Entity entity = player.level().getEntity(entityId);
        return entity == null ? Double.MAX_VALUE : Math.sqrt(player.distanceToSqr(entity));
    }

    @Override
    public boolean crouching() {
        return player.isShiftKeyDown();
    }

    @Override
    public long lastPassed(int nodeId) {
        Long last = gates.get(nodeId);
        return last == null ? Long.MIN_VALUE : last;
    }

    @Override
    public boolean crowded() {
        return !nearby(player, Scope.FIELD_RADIUS).isEmpty();
    }

    /** Whoever wears the mark, or null when it has run out, died or been left in another dimension. */
    public LivingEntity marked() {
        return marked(player, state);
    }

    /**
     * The same question as an ordinary lookup plus the three ways a mark stops counting.
     *
     * <p>Static because the service asks it constantly - to aim an anchored consequence, to flag an
     * event as having come from the marked, to draw the chip on the HUD - and every one of those
     * has to agree with what the engine was told, or a chain fires on a mark the effect cannot find.
     */
    public static LivingEntity marked(ServerPlayer player, PlayerMagicState state) {
        Anchor anchor = state.anchor();
        long now = player.level().getGameTime();
        if (!anchor.set(now) || !anchor.dimension().equals(player.level().dimension().location().toString())) {
            return null;
        }
        Entity entity = player.level().getEntity(anchor.entityId());
        return entity instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /** Every living thing but the wielder within that many blocks, for FIELD and for NEAREST. */
    public static java.util.List<LivingEntity> nearby(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(player.blockPosition()).inflate(radius),
                living -> living.isAlive() && living != player);
    }
}
