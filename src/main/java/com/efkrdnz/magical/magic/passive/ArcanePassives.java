package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * The Mystic line: the well, the ward and the blade.
 *
 * <p>Cadence is the class fantasy written as a rule — alternate blade and spell and both halves
 * pay, repeat either one and neither does. Overchannel is the only passive in the mod the player
 * triggers deliberately, by sneaking as they cast.</p>
 */
public final class ArcanePassives implements ClassPassiveHandler {
    private static final float WELL_THRESHOLD = 1.0F / 3.0F;
    private static final int WELL_REGEN = 2;
    private static final int CLEAN_CASTING_EVERY = 5;
    private static final float REBOUND_SHARE = 0.3F;
    /** How long each half of the Bladesinger's rhythm stays live, in ticks. */
    private static final long CADENCE_WINDOW = 40L;
    private static final float CADENCE_MELEE_BONUS = 0.4F;
    private static final float CADENCE_MANA_DISCOUNT = 0.3F;
    private static final float OVERCHANNEL_COST = 2.0F;
    private static final float OVERCHANNEL_POWER = 1.6F;
    private static final double NULL_FIELD_RADIUS = 5.0D;
    private static final float NULL_FIELD_REDUCTION = 0.15F;
    private static final long TITHE_MOTE_LIFETIME = 300L;
    private static final double TITHE_PICKUP_RANGE = 1.6D;
    private static final int TITHE_MANA = 15;
    private static final float TITHE_HEAL = 2.0F;
    private static final float MANIFOLD_CHANCE = 0.2F;
    private static final float MANIFOLD_DAMAGE = 2.0F;
    private static final float MANIFOLD_SIZE = 1.4F;
    private static final float THOUGHT_SHARE = 0.1F;
    private static final int THOUGHT_COST = 5;

    private final Map<UUID, Arcane> scratch = new HashMap<>();

    private static final class Arcane {
        long lastCastTick = Long.MIN_VALUE;
        long lastMeleeTick = Long.MIN_VALUE;
        final List<Mote> motes = new ArrayList<>();
    }

    /** An abyssal mote left where something died, waiting to be walked over. */
    private record Mote(Vec3 pos, long spawnedTick) {}

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of(
                MagicPassiveContent.DEEPENING_WELL.id(),
                MagicPassiveContent.CLEAN_CASTING.id(),
                MagicPassiveContent.WARD_REBOUND.id(),
                MagicPassiveContent.CADENCE.id(),
                MagicPassiveContent.OVERCHANNEL.id(),
                MagicPassiveContent.NULL_FIELD.id(),
                MagicPassiveContent.ABYSSAL_TITHE.id(),
                MagicPassiveContent.MANIFOLD.id(),
                MagicPassiveContent.EDGE_OF_THOUGHT.id());
    }

    private Arcane arcane(ServerPlayer player) {
        return scratch.computeIfAbsent(player.getUUID(), id -> new Arcane());
    }

    /** Null Field blanks the mod's own statuses outright; consulted by MagicStatusService. */
    public static boolean blocksStatus(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) {
            return false;
        }
        PlayerMagicState state = player.getData(com.efkrdnz.magical.registry.MagicalAttachments.MAGIC_STATE);
        return ClassPassiveEffects.on(state, MagicPassiveContent.NULL_FIELD.id());
    }

    @Override
    public void adjustCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition, CastAdjustment out) {
        Arcane arcane = arcane(player);
        long now = player.serverLevel().getGameTime();

        // Clean Casting: the counter is persisted, so logging out mid-rotation does not reset it.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.CLEAN_CASTING.id())
                && state.passiveCounter(MagicPassiveContent.CLEAN_CASTING.id()) >= CLEAN_CASTING_EVERY - 1) {
            out.mana = 0.0F;
            PassiveHooks.puff(player, ParticleTypes.ENCHANT, 8, 0.3D);
        }

        // Cadence: a spell that follows a swing is cheap.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.CADENCE.id()) && now - arcane.lastMeleeTick <= CADENCE_WINDOW) {
            out.mana *= 1.0F - CADENCE_MANA_DISCOUNT;
        }

        // Overchannel: the only passive you fire on purpose. Sneak and pay double.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.OVERCHANNEL.id()) && player.isShiftKeyDown()
                && PassiveHooks.isOffensive(definition)) {
            out.mana *= OVERCHANNEL_COST;
            out.damage *= OVERCHANNEL_POWER;
            out.size *= OVERCHANNEL_POWER;
        }

        // Manifold: now and then the working folds and both castings arrive together.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.MANIFOLD.id())
                && player.getRandom().nextFloat() < MANIFOLD_CHANCE) {
            out.damage *= MANIFOLD_DAMAGE;
            out.size *= MANIFOLD_SIZE;
            player.level().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4F, 1.5F);
        }
    }

    @Override
    public void afterCast(ServerPlayer player, PlayerMagicState state, MagicSkillDefinition definition) {
        Arcane arcane = arcane(player);
        arcane.lastCastTick = player.serverLevel().getGameTime();
        if (ClassPassiveEffects.on(state, MagicPassiveContent.CLEAN_CASTING.id())) {
            ResourceLocation id = MagicPassiveContent.CLEAN_CASTING.id();
            int next = state.passiveCounter(id) + 1;
            state.setPassiveCounter(id, next >= CLEAN_CASTING_EVERY ? 0 : next);
        }
    }

    @Override
    public void onMeleeHit(ServerPlayer player, PlayerMagicState state, Entity target) {
        Arcane arcane = arcane(player);
        long now = player.serverLevel().getGameTime();

        // Cadence: a swing that follows a spell lands with the spell still on it.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.CADENCE.id())
                && now - arcane.lastCastTick <= CADENCE_WINDOW && target instanceof LivingEntity living) {
            float bonus = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * CADENCE_MELEE_BONUS;
            MagicDamageService.hurt(living, player.damageSources().indirectMagic(player, player), bonus);
        }

        // Edge Of Thought: a Spellblade spends the well through the edge itself.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.EDGE_OF_THOUGHT.id())
                && state.mana() > THOUGHT_COST && target instanceof LivingEntity living) {
            float bonus = state.mana() * THOUGHT_SHARE;
            state.spendMana(THOUGHT_COST);
            MagicDamageService.hurt(living, player.damageSources().indirectMagic(player, player), bonus);
        }

        arcane.lastMeleeTick = now;
    }

    @Override
    public void onBarrierAbsorb(ServerPlayer player, PlayerMagicState state, float absorbed, DamageSource source) {
        // Ward Rebound: a warder's ward feeds the well it came from.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.WARD_REBOUND.id())) {
            state.addMana(Math.max(1, Math.round(absorbed * REBOUND_SHARE)));
        }
    }

    @Override
    public float incomingDamage(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        // Null Field: magic thins out near an Abjurer.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.NULL_FIELD.id()) && PassiveHooks.isSpellDamage(source)) {
            LivingEntity attacker = PassiveHooks.attacker(player, source);
            if (attacker != null && player.distanceTo(attacker) <= NULL_FIELD_RADIUS) {
                return amount * (1.0F - NULL_FIELD_REDUCTION);
            }
        }
        return amount;
    }

    @Override
    public void onKill(ServerPlayer player, PlayerMagicState state, LivingEntity victim, DamageSource source) {
        // Abyssal Tithe: the abyss leaves you something, but you have to go and stand in it.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.ABYSSAL_TITHE.id()) && PassiveHooks.isSpellKill(source)) {
            Arcane arcane = arcane(player);
            arcane.motes.add(new Mote(victim.position(), player.serverLevel().getGameTime()));
            if (arcane.motes.size() > 32) {
                arcane.motes.remove(0);
            }
        }
    }

    @Override
    public void slowTick(ServerPlayer player, PlayerMagicState state) {
        // Deepening Well: the well runs fastest when it looks empty.
        if (ClassPassiveEffects.on(state, MagicPassiveContent.DEEPENING_WELL.id())
                && state.mana() < state.maxMana() * WELL_THRESHOLD) {
            state.addMana(WELL_REGEN);
        }
        tickMotes(player, state);
    }

    private void tickMotes(ServerPlayer player, PlayerMagicState state) {
        Arcane arcane = arcane(player);
        if (arcane.motes.isEmpty()) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        ServerLevel level = player.serverLevel();
        Iterator<Mote> iterator = arcane.motes.iterator();
        while (iterator.hasNext()) {
            Mote mote = iterator.next();
            if (now - mote.spawnedTick() > TITHE_MOTE_LIFETIME) {
                iterator.remove();
                continue;
            }
            level.sendParticles(ParticleTypes.SCULK_SOUL, mote.pos().x, mote.pos().y + 0.4D, mote.pos().z, 2, 0.1D, 0.2D, 0.1D, 0.01D);
            if (player.position().distanceTo(mote.pos()) <= TITHE_PICKUP_RANGE) {
                iterator.remove();
                state.addMana(TITHE_MANA);
                player.heal(TITHE_HEAL);
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.35F, 0.7F);
            }
        }
    }

    @Override
    public void forget(UUID playerId) {
        scratch.remove(playerId);
    }
}
