package com.efkrdnz.magical.magic.sword.rack;

import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.ForgeRiderService;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.StrikeContext;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.SwordArray;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/**
 * The rack in the fight: which weapon each sword is, and what it does when it lands.
 *
 * <p><b>The swords are the rack.</b> Sword <i>j</i> of the formation is the <i>j</i>-th socket
 * holding a weapon the wielder may fly ({@link SwordArray#socketOf}), so a formation is exactly as
 * many swords as the rack holds and a gap on the ring is never a gap round the wielder. An empty
 * socket is nothing - there is no plain steel. {@link #rackedMask} is that set read off the rack as
 * it stands, and {@code SwordService.refreshRack} copies it onto the Array, which is what the count,
 * the save and the client read. Every read here is off the rack itself, so a weapon swapped this
 * tick is the one that flies this tick.
 *
 * <p><b>A sword hits for its weapon.</b> The kit's number for a move is written in units of one
 * sword landing, and {@link #strike} replaces the unit with each weapon's own hit through
 * {@link ArmDamage}: its attack as its tooltip reads it, the forge's own hit if it was forged, and
 * what its enchantments add against this body. Its enchantments' after-hit effects then run once,
 * off the first enchanted weapon in the hit, exactly as vanilla runs them for an arrow - the source
 * handed to them is a direct attack by the wielder, because Fire Aspect and its kind ask for one and
 * a sword the wielder threw is the wielder's attack. A forged weapon's element rides the hit the way
 * it rides the forge's own strikes, rolled rather than guaranteed, and a wound made of many swords
 * carries each element in it once rather than once a sword.
 *
 * <p>Only a hit that landed brings its effects: a wound the barrier ate, a shield took or an
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

    /**
     * Bit <i>s</i> set means socket <i>s</i> holds a weapon this wielder may fly: a socket the rung
     * has opened, holding what Weapon God - or its absence - allows. The swords, as a mask.
     */
    public static int rackedMask(Player player, PlayerMagicState state) {
        // Asked of every player on login and on every class taken, and getData would give each
        // of them an empty rack to carry round in their save for good.
        if (!player.hasData(MagicalAttachments.SWORD_RACK)) {
            return 0;
        }
        SwordRack rack = rack(player);
        boolean weaponGod = weaponGod(state);
        int open = unlocked(state);
        int mask = 0;
        for (int socket = 0; socket < open; socket++) {
            ItemStack stack = rack.getItem(socket);
            if (!stack.isEmpty() && SwordRackRules.accepts(stack, weaponGod)) {
                mask |= 1 << socket;
            }
        }
        return mask;
    }

    /**
     * The weapon sword {@code sword} is, or empty when the rack holds no such sword. The rack's own
     * stack: read it, never change it.
     */
    public static ItemStack arm(Player player, PlayerMagicState state, int sword) {
        return armOf(rack(player), rackedMask(player, state), sword);
    }

    /** The weapons of every sword in {@code mask}, any the rack no longer holds left out. */
    public static List<ItemStack> arms(Player player, PlayerMagicState state, int mask) {
        SwordRack rack = rack(player);
        int racked = rackedMask(player, state);
        List<ItemStack> out = new ArrayList<>();
        for (int sword = 0; sword < SwordRack.SIZE; sword++) {
            if ((mask & (1 << sword)) == 0) {
                continue;
            }
            ItemStack arm = armOf(rack, racked, sword);
            if (!arm.isEmpty()) {
                out.add(arm);
            }
        }
        return out;
    }

    /** Every sword's weapon in formation order, empty past the last: what the formation is drawn with. */
    public static ItemStack[] allArms(Player player, PlayerMagicState state) {
        SwordRack rack = rack(player);
        int racked = rackedMask(player, state);
        ItemStack[] out = new ItemStack[SwordRack.SIZE];
        for (int sword = 0; sword < SwordRack.SIZE; sword++) {
            out[sword] = armOf(rack, racked, sword);
        }
        return out;
    }

    private static ItemStack armOf(SwordRack rack, int racked, int sword) {
        int socket = SwordArray.socketOf(racked, sword);
        return socket < 0 ? ItemStack.EMPTY : rack.getItem(socket);
    }

    public static List<ItemStack> one(ItemStack arm) {
        return arm == null || arm.isEmpty() ? List.of() : List.of(arm);
    }

    // ---- the hit ------------------------------------------------------------------------------

    /**
     * One wound made of {@code swords} swords, {@code arms} of which carry a weapon, billed at
     * {@code amount} in the kit's own units.
     *
     * <p>An attacker that is not a living thing - a blade whose wielder has gone - or a wound with no
     * weapon in it lands the kit's number as it stands and nothing else.
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
            scales[i] = ArmDamage.scale(weaponHit(level, arms.get(i), wielder, victim, direct));
        }
        float dealt = (float) (amount * ArmDamage.mean(scales, swords));
        StrikeContext.TargetState before = new StrikeContext.TargetState(victim.isOnFire(), victim.getTicksFrozen() > 0);
        float health = victim.getHealth() + victim.getAbsorptionAmount();
        MagicDamageService.hurt(victim, source, dealt, skillId);
        boolean landed = victim.isDeadOrDying() || victim.getHealth() + victim.getAbsorptionAmount() < health;
        if (landed) {
            afterHit(level, wielder, victim, arms, direct, dealt, before);
        }
    }

    /**
     * What this weapon lands on this body when it is swung: its attack as its tooltip reads it, the
     * forge's own hit ({@code ForgeStrikeMath.baseHit}, exactly what a forged swing starts from) if
     * it was forged, and what its enchantments add against this body - Sharpness, Smite on the
     * undead - the way a melee hit adds them.
     */
    public static double weaponHit(ServerLevel level, ItemStack arm, LivingEntity wielder, LivingEntity victim,
            DamageSource direct) {
        double attack = attack(arm, baseAttack(wielder));
        double hit = ForgedWeapons.get(arm)
                .map(weapon -> (double) ForgeStrikeMath.baseHit((float) attack, weapon.grade(), weapon.quality()))
                .orElse(attack);
        return hit + EnchantmentHelper.modifyDamage(level, arm, victim, direct, 0.0F);
    }

    /**
     * The weapon's main-hand attack on top of {@code base}: the {@code N Attack Damage} line on its
     * tooltip, where {@code base} is the wielder's own attack before anything is held - one, for a
     * player.
     */
    public static double attack(ItemStack stack, double base) {
        double[] add = {0.0D};
        double[] multiplyBase = {0.0D};
        double[] multiplyTotal = {1.0D};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.value() != Attributes.ATTACK_DAMAGE.value()) {
                return;
            }
            switch (modifier.operation()) {
                case ADD_VALUE -> add[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> multiplyBase[0] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> multiplyTotal[0] *= 1.0D + modifier.amount();
                default -> {
                }
            }
        });
        return ArmDamage.attack(base, add[0], multiplyBase[0], multiplyTotal[0]);
    }

    /** The wielder's attack before anything is held: what a tooltip adds a weapon onto. */
    private static double baseAttack(LivingEntity wielder) {
        AttributeInstance instance = wielder.getAttribute(Attributes.ATTACK_DAMAGE);
        return instance == null ? 1.0D : instance.getBaseValue();
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
