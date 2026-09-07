package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.forge.chain.ForgeGrade;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Gives a mob's ordinary melee swing the identity of the forged weapon it is holding.
 *
 * <p>The player's forge combat is player-gated four times over - the input is a client keybind, the
 * packet handler is {@code ServerPlayer}-typed, every {@code ForgeComboService} method takes a
 * {@code ServerPlayer}, and {@code ForgeStrikeEntity.spawn} demands one as its owner. A mob holding
 * a {@code magical:forged_weapon} therefore deals plain vanilla damage: the element, the grade and
 * the whole program are inert on it.
 *
 * <p>Rather than widen that chain - a large refactor of the system, for a mob that has no combo
 * cursor, no charge and no left click - this gives a mob the two things that actually read as the
 * weapon: the bite of its grade, and its element landing on the target. The program, the forms and
 * the modifiers stay dormant, which is honest: a boss swings, it does not combo.
 */
public final class ForgeMobStrike {

    /**
     * Quality scales the grade's bite, so a badly forged blade is worth less than a clean one - the
     * same relationship the player's own strike math applies.
     */
    private static final float QUALITY_SCALE = 0.01F;

    private ForgeMobStrike() {}

    /** Extra melee damage the held weapon is worth, or 0 when the mob holds nothing forged. */
    public static float bonusDamage(LivingEntity attacker) {
        return weaponOf(attacker)
                .map(weapon -> gradeBite(weapon.grade()) * (weapon.quality() * QUALITY_SCALE))
                .orElse(0.0F);
    }

    /**
     * Lands the held weapon's element rider on something the mob just hit.
     *
     * <p>Called after the damage itself, so the rider sees a target that has already taken the
     * swing - the same order the player's own path uses.
     */
    public static void onMeleeHit(ServerLevel level, LivingEntity attacker, LivingEntity target, float dealt) {
        Optional<ForgedWeapon> held = weaponOf(attacker);
        if (held.isEmpty()) {
            return;
        }
        ForgedWeapon weapon = held.get();
        Optional<ElementDefinition> element = ForgeElements.get(weapon.element());
        if (element.isEmpty()) {
            return;
        }
        ForgeRiderService.apply(level, attacker, target, weapon, element.get(), contextFor(attacker, target, dealt));
    }

    /**
     * A single swing, described the way the rider expects.
     *
     * <p>Marked {@code heavy} so the rider fires every time rather than rolling its proc chance. A
     * mob swings far less often than a player mid-combo, and an element that only showed up on one
     * hit in three would read as no element at all.
     */
    private static StrikeContext contextFor(LivingEntity attacker, LivingEntity target, float dealt) {
        Vec3 direction = target.position().subtract(attacker.position());
        direction = direction.lengthSqr() < 1.0E-6D ? attacker.getLookAngle() : direction.normalize();
        return new StrikeContext(
                null,
                FormFamily.SLASH,
                true,
                0,
                false,
                dealt,
                direction,
                1,
                1,
                1,
                new StrikeContext.TargetState(target.isOnFire(), target.getTicksFrozen() > 0));
    }

    private static float gradeBite(ForgeGrade grade) {
        return (float) grade.baseDamage();
    }

    private static Optional<ForgedWeapon> weaponOf(LivingEntity attacker) {
        return ForgedWeapons.get(attacker.getMainHandItem());
    }
}
