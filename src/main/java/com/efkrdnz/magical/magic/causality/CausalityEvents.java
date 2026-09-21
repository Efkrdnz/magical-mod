package com.efkrdnz.magical.magic.causality;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Where the world turns into a {@link CausalEvent}, and the only place in the Authority that has
 * ever heard of a damage source.
 *
 * <p>The two causes a wielder is most likely to build on - taking a hit and landing one - are
 * <b>not</b> here. Both of them have to be spliced into the exact rung of
 * {@code MagicGameplayEvents.onIncomingDamage} where they belong: a Store must run before the
 * barrier or storing a hit would not save the barrier from it, and a Break must run after, because
 * it is the barrier emptying that it is about. A subscriber of our own could not say where it sat
 * in that ladder, so those three call {@link #hurt}, {@link #strike} and {@link #broke} from inside
 * it and this class holds the ones whose ordering nobody can observe.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class CausalityEvents {

    private CausalityEvents() {}

    // ---- the ones spliced into the damage ladder ------------------------------------------------

    /** A hit, on its way in to a wielder, before the barrier has had a word. */
    public static float hurt(ServerPlayer victim, DamageSource source, float damage) {
        if (damage <= 0.0F) {
            return damage;
        }
        return CausalityService.dispatch(victim,
                new CausalEvent(Cause.HURT, damage, authorId(source), flags(source)));
    }

    /** A hit a wielder is about to land. Storing part of your own blow is a real thing to build. */
    public static float strike(ServerPlayer attacker, LivingEntity victim, DamageSource source, float damage) {
        if (damage <= 0.0F || victim == attacker) {
            return damage;
        }
        return CausalityService.dispatch(attacker,
                new CausalEvent(Cause.STRIKE, damage, victim.getId(), flags(source)));
    }

    /** The barrier has just been emptied, and this much got through it. */
    public static void broke(ServerPlayer victim, DamageSource source, float through) {
        if (through <= 0.0F) {
            return;
        }
        CausalityService.dispatch(victim, new CausalEvent(Cause.BREAK, through, authorId(source), flags(source)));
    }

    /** A wielder has cast something. The magnitude is what it cost them. */
    public static void invoked(ServerPlayer caster, int manaCost) {
        PlayerMagicState state = caster.getData(MagicalAttachments.MAGIC_STATE);
        if (!CausalityService.holds(state) || state.weave().empty()) {
            return;
        }
        CausalityService.dispatch(caster, new CausalEvent(Cause.INVOKE, Math.max(0, manaCost), -1, CausalEvent.MAGIC));
    }

    // ---- the ones whose ordering nobody can observe ---------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide()) {
            return;
        }
        float worth = dead.getMaxHealth();
        if (event.getSource().getEntity() instanceof ServerPlayer killer) {
            CausalityService.dispatch(killer, new CausalEvent(Cause.SLAY, worth, dead.getId(), flags(event.getSource())));
        }
        forEachWatcher(dead, watcher ->
                CausalityService.dispatch(watcher, new CausalEvent(Cause.MARKED_FALLS, worth, dead.getId(), 0)));
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) {
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!CausalityService.holds(state) || state.weave().empty()) {
            return;
        }
        event.setAmount(CausalityService.dispatch(player, new CausalEvent(Cause.MENDED, event.getAmount(), -1, 0)));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        CausalityService.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CausalityService.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        // A mark does not cross a dimension and neither does a consequence that was waiting to land.
        CausalityService.forget(event.getEntity().getUUID());
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getData(MagicalAttachments.MAGIC_STATE).anchor().clear();
        }
    }

    // ---- watching somebody else -----------------------------------------------------------------

    /**
     * A consequence on a body somebody has marked, handed to whoever marked it.
     *
     * <p>Spliced into the damage ladder for the same reason the other two are, and it is the whole
     * of what an anchor buys: one body, one wielder at a time, and a scan of the player list rather
     * than a reverse index, because a server has tens of players and a fight has one or two.
     */
    public static float markedHurt(LivingEntity victim, DamageSource source, float damage) {
        if (damage <= 0.0F || victim.level().isClientSide()) {
            return damage;
        }
        float[] carried = {damage};
        forEachWatcher(victim, watcher -> carried[0] = CausalityService.dispatch(watcher,
                new CausalEvent(Cause.MARKED_HURT, carried[0], victim.getId(), flags(source))));
        return carried[0];
    }

    /** The marked has hit something. Handed to whoever marked it, carrying what it dealt. */
    public static float markedStrikes(LivingEntity attacker, LivingEntity victim, DamageSource source, float damage) {
        if (damage <= 0.0F || attacker.level().isClientSide()) {
            return damage;
        }
        float[] carried = {damage};
        forEachWatcher(attacker, watcher -> carried[0] = CausalityService.dispatch(watcher,
                new CausalEvent(Cause.MARKED_STRIKES, carried[0], victim.getId(), flags(source))));
        return carried[0];
    }

    /** Every wielder whose mark is on this body, in player-list order. Usually none. */
    private static void forEachWatcher(LivingEntity body, java.util.function.Consumer<ServerPlayer> action) {
        if (body.getServer() == null) {
            return;
        }
        for (ServerPlayer player : body.getServer().getPlayerList().getPlayers()) {
            if (player == body) {
                continue;
            }
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            if (!CausalityService.holds(state) || state.weave().empty()) {
                continue;
            }
            LivingEntity marked = LevelCausalWorld.marked(player, state);
            if (marked != null && marked.getId() == body.getId()) {
                action.accept(player);
            }
        }
    }

    // ---- reading a damage source ----------------------------------------------------------------

    /** Whoever is answerable for a consequence: the thing that loosed it, not the thing that flew. */
    private static int authorId(DamageSource source) {
        Entity author = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
        return author == null ? -1 : author.getId();
    }

    /**
     * What kind of consequence this is, in the four terms a condition can ask about.
     *
     * <p>Melee is decided by there being an author standing close enough to have swung rather than
     * by a tag, because nothing in vanilla marks a hit as melee and the projectile that is not a
     * projectile is exactly what a wielder would want to catch.
     */
    private static int flags(DamageSource source) {
        int flags = 0;
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile) {
            flags |= CausalEvent.PROJECTILE;
        }
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypeTags.WITCH_RESISTANT_TO)) {
            flags |= CausalEvent.MAGIC;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            flags |= CausalEvent.FIRE;
        }
        if ((flags & CausalEvent.PROJECTILE) == 0 && source.getEntity() != null
                && source.getEntity() == source.getDirectEntity()) {
            flags |= CausalEvent.MELEE;
        }
        return flags;
    }
}
