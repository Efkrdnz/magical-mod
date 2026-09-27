package com.efkrdnz.magical.magic.sword.rack;

import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeRiderService;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.StrikeContext;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * The rack in the fight: which weapon each sword carries, and what that weapon does when its sword
 * lands.
 *
 * <p><b>Socket <i>i</i> is sword <i>i</i>.</b> The same index the away mask, the formation and
 * every blade's {@code Slot} already use, so a weapon follows its sword out of the formation, into
 * a body and home again without anything having to remember which weapon went where. A socket the
 * rung has not opened, an empty one, or one holding something the wielder may no longer fly is
 * plain steel.
 *
 * <p><b>What a weapon brings, once a hit.</b> Its hit is scaled by {@link ArmFlavour} - a little,
 * by design. Its enchantments' after-hit effects run once, off the first enchanted weapon in the
 * hit, exactly as vanilla runs them for an arrow; the source handed to them is a direct attack by
 * the wielder, because Fire Aspect and its kind ask for one and a sword the wielder threw is the
 * wielder's attack. A forged weapon's element rides the hit the way it rides the forge's own
 * strikes, rolled rather than guaranteed, and a wound made of many swords carries each element in
 * it once rather than once a sword.
 *
 * <p>Only a hit that landed brings any of it: a wound the barrier ate, a shield took or an
 * invulnerable body shrugged off sets nothing on fire.
 */
public final class SwordArms {

    /** The colour of a weapon that burns without being forged to: Fire Aspect, Flame. */
    public static final int FIRE_ACCENT = 0xFF8A2E;

    private SwordArms() {
    }

    public static SwordRack rack(Player player) {
        return player.getData(MagicalAttachments.SWORD_RACK);
    }

    /** The apex's passive: any weapon, not only swords. */
    public static boolean weaponGod(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.WEAPON_GOD.id());
    }

    /** Sockets the rung has opened: four, seven, ten, twelve. */
    public static int unlocked(PlayerMagicState state) {
        return Math.max(0, Math.min(SwordRack.SIZE, SwordService.rulesFor(state).swords()));
    }

    /** The weapon sword {@code slot} flies as, or empty for plain steel. The rack's own stack: read it, never change it. */
    public static ItemStack arm(Player player, PlayerMagicState state, int slot) {
        if (slot < 0 || slot >= unlocked(state)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = rack(player).getItem(slot);
        return !stack.isEmpty() && SwordRackRules.accepts(stack, weaponGod(state)) ? stack : ItemStack.EMPTY;
    }

    /** The weapons of every sword in {@code mask}, plain steel left out. */
    public static List<ItemStack> arms(Player player, PlayerMagicState state, int mask) {
        List<ItemStack> out = new ArrayList<>();
        for (int slot = 0; slot < SwordRack.SIZE; slot++) {
            if ((mask & (1 << slot)) == 0) {
                continue;
            }
            ItemStack arm = arm(player, state, slot);
            if (!arm.isEmpty()) {
                out.add(arm);
            }
        }
        return out;
    }

    /** All twelve, in socket order, plain steel as empty stacks: what the formation is drawn with. */
    public static ItemStack[] allArms(Player player, PlayerMagicState state) {
        ItemStack[] out = new ItemStack[SwordRack.SIZE];
        for (int slot = 0; slot < SwordRack.SIZE; slot++) {
            out[slot] = arm(player, state, slot);
        }
        return out;
    }

    public static List<ItemStack> one(ItemStack arm) {
        return arm == null || arm.isEmpty() ? List.of() : List.of(arm);
    }

    // ---- the hit ------------------------------------------------------------------------------

    /**
     * One wound made of {@code swords} swords, {@code arms} of which carry a weapon.
     *
     * <p>Plain steel, or an attacker that is not a living thing - a blade whose wielder has gone -
     * is the plain hit and nothing else.
     */
    public static void strike(ServerLevel level, Entity attacker, LivingEntity victim, List<ItemStack> arms,
            int swords, DamageSource source, double amount, ResourceLocation skillId) {
        if (amount <= 0.0D) {
            return;
        }
        if (!(attacker instanceof LivingEntity wielder) || arms.isEmpty()) {
            MagicDamageService.hurt(victim, source, (float) amount, skillId);
            return;
        }
        DamageSource direct = direct(level, wielder);
        double[] scales = new double[arms.size()];
        for (int i = 0; i < scales.length; i++) {
            scales[i] = scaleOf(level, arms.get(i), victim, direct);
        }
        float dealt = (float) (amount * ArmFlavour.mean(scales, swords));
        StrikeContext.TargetState before = new StrikeContext.TargetState(victim.isOnFire(), victim.getTicksFrozen() > 0);
        float health = victim.getHealth() + victim.getAbsorptionAmount();
        MagicDamageService.hurt(victim, source, dealt, skillId);
        boolean landed = victim.isDeadOrDying() || victim.getHealth() + victim.getAbsorptionAmount() < health;
        if (landed) {
            afterHit(level, wielder, victim, arms, direct, dealt, before);
        }
    }

    /** What one weapon multiplies its sword's hit on this body by. */
    public static double scaleOf(ServerLevel level, ItemStack arm, LivingEntity victim, DamageSource direct) {
        double enchant = EnchantmentHelper.modifyDamage(level, arm, victim, direct, 0.0F);
        int grade = ForgedWeapons.get(arm).map(weapon -> weapon.grade().ordinal()).orElse(-1);
        return ArmFlavour.scale(attackBonus(arm), enchant, grade);
    }

    /** The weapon's main-hand attack bonus: the {@code +N Attack Damage} line on its tooltip. */
    public static double attackBonus(ItemStack stack) {
        double[] sum = {0.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() == Attributes.ATTACK_DAMAGE.value()
                    && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum[0] += modifier.amount();
            }
        });
        return sum[0];
    }

    private static void afterHit(ServerLevel level, LivingEntity wielder, LivingEntity victim, List<ItemStack> arms,
            DamageSource direct, float dealt, StrikeContext.TargetState before) {
        for (ItemStack arm : arms) {
            if (arm.isEnchanted()) {
                // A copy, so an effect that wears its item wears nothing in the rack.
                EnchantmentHelper.doPostAttackEffectsWithItemSource(level, victim, direct, arm.copy());
                break;
            }
        }
        Set<ResourceLocation> ridden = new HashSet<>();
        for (ItemStack arm : arms) {
            Optional<ForgedWeapon> forged = ForgedWeapons.get(arm);
            if (forged.isEmpty() || !ridden.add(forged.get().element())) {
                continue;
            }
            ForgedWeapon weapon = forged.get();
            ForgeElements.get(weapon.element()).ifPresent(element -> ForgeRiderService.apply(level, wielder, victim,
                    weapon, element, context(wielder, victim, dealt, before)));
        }
    }

    /** A slash that is not the forge's own: never heavy, never a finisher, so the rider rolls. */
    private static StrikeContext context(LivingEntity wielder, LivingEntity victim, float dealt,
            StrikeContext.TargetState before) {
        Vec3 direction = victim.position().subtract(wielder.position());
        direction = direction.lengthSqr() < 1.0E-6D ? wielder.getLookAngle() : direction.normalize();
        return new StrikeContext(null, FormFamily.SLASH, false, 0, false, dealt, direction, 1, 1, 1, before);
    }

    private static DamageSource direct(ServerLevel level, LivingEntity wielder) {
        return wielder instanceof Player player
                ? level.damageSources().playerAttack(player)
                : level.damageSources().mobAttack(wielder);
    }

    // ---- the colour ---------------------------------------------------------------------------

    /**
     * The colour a hit rings in: the first forged element's, else fire's for a weapon enchanted to
     * burn, else none (0). An element forged black is nudged off zero, which means none.
     */
    public static int accent(List<ItemStack> arms) {
        for (ItemStack arm : arms) {
            Optional<Integer> element = ForgedWeapons.get(arm)
                    .flatMap(weapon -> ForgeElements.get(weapon.element()))
                    .map(definition -> definition.primaryColor() & 0xFFFFFF);
            if (element.isPresent()) {
                return element.get() == 0 ? 0x010101 : element.get();
            }
        }
        for (ItemStack arm : arms) {
            for (Holder<Enchantment> enchantment : arm.getEnchantments().keySet()) {
                if (enchantment.is(Enchantments.FIRE_ASPECT) || enchantment.is(Enchantments.FLAME)) {
                    return FIRE_ACCENT;
                }
            }
        }
        return 0;
    }

    public static int accent(ItemStack arm) {
        return accent(one(arm));
    }
}
