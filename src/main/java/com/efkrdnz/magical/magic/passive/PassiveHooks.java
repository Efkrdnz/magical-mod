package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** Small shared predicates and lookups the class-passive handlers all need. */
public final class PassiveHooks {

    private PassiveHooks() {}

    public static boolean isSpellDamage(DamageSource source) {
        return source != null && (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC));
    }

    /** A kill counts as a spell kill when the killing blow was magic rather than a swing. */
    public static boolean isSpellKill(DamageSource source) {
        return isSpellDamage(source);
    }

    /** Offensive casts only: barriers and zero-damage utility should not feed damage passives. */
    public static boolean isOffensive(MagicSkillDefinition definition) {
        return definition != null && definition.type() != MagicSkillType.BARRIER && definition.baseDamage() > 0.0F;
    }

    /** The hostile that actually swung or cast, when there is one and it is not the player. */
    public static LivingEntity attacker(ServerPlayer player, DamageSource source) {
        if (source == null || !(source.getEntity() instanceof LivingEntity living) || living == player) {
            return null;
        }
        return living;
    }

    /** Every animal this player has tamed within {@code radius} blocks. */
    public static List<TamableAnimal> ownedPets(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(TamableAnimal.class,
                new AABB(player.blockPosition()).inflate(radius),
                pet -> pet.isAlive() && pet.isTame() && player.getUUID().equals(pet.getOwnerUUID()));
    }

    /** True when the entity is one of the player's own tamed animals. */
    public static boolean isOwnedPet(ServerPlayer player, Entity entity) {
        return entity instanceof TamableAnimal pet && pet.isTame() && player.getUUID().equals(pet.getOwnerUUID());
    }

    /** Hostiles near the player, excluding the player, their pets and other players. */
    public static List<LivingEntity> hostilesNear(ServerPlayer player, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(player.blockPosition()).inflate(radius),
                other -> other.isAlive() && other != player && !isOwnedPet(player, other)
                        && !(other instanceof net.minecraft.world.entity.player.Player));
    }

    public static void puff(ServerPlayer player, ParticleOptions particle, int count, double spread) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(particle, player.getX(), player.getY() + 1.0D, player.getZ(), count, spread, spread, spread, 0.02D);
        }
    }
}
