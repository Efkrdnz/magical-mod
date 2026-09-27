package com.efkrdnz.magical.magic.sword.rack;

import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;
import java.util.Optional;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.neoforged.neoforge.common.Tags;

/**
 * What a socket will take: a sword, and with Weapon God any weapon at all.
 *
 * <p><b>The catalogue is asked first.</b> A scythe and a greatsword are both built on vanilla's
 * sword properties, so both are in {@code ItemTags.SWORDS} and both extend {@link SwordItem}; only
 * the catalogue row says which is which, the same reason {@code ForgeMaterials.weaponClass} reads
 * it before any tag. A greatsword is a sword. A scythe, a spear, claws, a dagger and an axe are
 * weapons, and wait for the apex.
 *
 * <p><b>Classes before tags.</b> A tag check on a registry that was never bound throws rather than
 * answering, which is every unit test and the first moments of a load, so every vanilla weapon is
 * recognised by what it is before anything is asked of a tag, and a tag that cannot be read yet
 * reads as not carrying the item. Tags are there for the weapons of other mods, which is what the
 * common {@code c:tools/melee_weapon} and {@code c:tools/ranged_weapon} tags are for.
 */
public final class SwordRackRules {

    private SwordRackRules() {
    }

    /** Whether this stack may be racked by a wielder who does or does not have Weapon God. */
    public static boolean accepts(ItemStack stack, boolean weaponGod) {
        return weaponGod ? isWeapon(stack) : isSword(stack);
    }

    public static boolean isSword(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Optional<WeaponDefinition> catalogue = ForgeMaterials.catalogue(stack);
        if (catalogue.isPresent()) {
            WeaponClass archetype = catalogue.get().archetype();
            return archetype == WeaponClass.SWORD || archetype == WeaponClass.GREATSWORD;
        }
        return stack.getItem() instanceof SwordItem || tagged(stack, ItemTags.SWORDS);
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isSword(stack) || ForgeMaterials.catalogue(stack).isPresent()) {
            return true;
        }
        Item item = stack.getItem();
        if (item instanceof AxeItem || item instanceof MaceItem || item instanceof TridentItem
                || item instanceof ProjectileWeaponItem) {
            return true;
        }
        return tagged(stack, ItemTags.WEAPON_ENCHANTABLE)
                || tagged(stack, Tags.Items.MELEE_WEAPON_TOOLS)
                || tagged(stack, Tags.Items.RANGED_WEAPON_TOOLS);
    }

    /** A tag that has not been bound yet carries nothing, rather than throwing. See the class note. */
    private static boolean tagged(ItemStack stack, TagKey<Item> tag) {
        try {
            return stack.is(tag);
        } catch (IllegalStateException unbound) {
            return false;
        }
    }
}
