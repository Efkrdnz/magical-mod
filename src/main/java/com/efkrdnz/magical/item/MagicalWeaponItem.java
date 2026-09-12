package com.efkrdnz.magical.item;

import java.util.List;

import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeElements;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;

/**
 * One catalogue weapon. The class is shared; the row it carries is what makes each one different.
 *
 * <p>Its stats are ordinary vanilla attribute modifiers, built by
 * {@link #propertiesFor(WeaponDefinition)} at registration and stored on the item, which is why a
 * weapon hitting for two hundred needs no change anywhere in the strike pipeline:
 * {@code ForgeComboService.resolve} already reads the wielder's ATTACK_DAMAGE off the held stack
 * and {@code ForgeStrikeMath.baseHit} already multiplies through it.
 */
public class MagicalWeaponItem extends Item {

    /** Arcane violet, for the handful of weapons that lean on no element at all. */
    private static final int UNTHEMED_ACCENT = 0x9B6BFF;

    private final WeaponDefinition definition;

    public MagicalWeaponItem(WeaponDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    /**
     * Durability, repair, enchantability, the sword tool rules and the attribute modifiers, all
     * from vanilla's own sword path so a catalogue weapon behaves like a weapon everywhere this mod
     * does not reach - anvils, grindstones, enchanting tables, the F3+H tooltip.
     *
     * <p>Applied onto the properties DeferredRegister hands the factory rather than onto a fresh
     * object, for two reasons: that one already carries the registry id, and this call reaches
     * {@code acquireBootstrapRegistrationLookup(BLOCK)}, which throws if it runs any earlier than
     * the registry being filled. Building the properties in a static initialiser instead takes the
     * whole mod down before FML's JUnit launcher can even open a class loader.
     *
     * <p>It also settles the material the forge detects: {@code applySwordProperties} sets the
     * REPAIRABLE component, which is one of the three things {@code ForgeMaterials.detect} already
     * reads. The explicit branch there is the authority; this just means the two agree even if that
     * branch is ever removed.
     */
    public static Properties propertiesFor(WeaponDefinition definition, Properties properties) {
        return definition.toolMaterial()
                .applySwordProperties(properties, definition.attackModifier(), definition.attackSpeedModifier())
                .rarity(rarityFor(definition));
    }

    /**
     * The colour the weapon's own name is written in, taken from how far it can be forged.
     *
     * <p>The grade ceiling is the one number that actually separates these weapons in the long run -
     * a netherite-capped blade reaches MYTHIC and an iron one stops at HIGH - so it is the honest
     * thing for the name colour to be reporting. It also means the armoury tab sorts itself into
     * three legible bands on sight, which thirty-two recoloured vanilla sword sprites otherwise do
     * not.
     */
    private static Rarity rarityFor(WeaponDefinition definition) {
        return switch (definition.gradeCap()) {
            case NETHERITE -> Rarity.EPIC;
            case DIAMOND -> Rarity.RARE;
            case IRON, GOLD -> Rarity.UNCOMMON;
            case WOOD, STONE, UNKNOWN -> Rarity.COMMON;
        };
    }

    public WeaponDefinition definition() {
        return definition;
    }

    /**
     * One line naming the swing shape, and one of flavour. The damage and speed numbers are already
     * on the vanilla attribute lines below, so repeating them here would only give a player two
     * places to read the same thing and one chance for them to disagree.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        int accent = accentColor();
        lines.add(Component.translatable("tooltip.magical.weapon.archetype",
                Component.translatable(definition.archetype().nameKey())).withColor(accent));
        definition.themeElement().flatMap(ForgeElements::get).ifPresent(element -> lines.add(
                Component.translatable("tooltip.magical.weapon.theme",
                                Component.translatable("forge.magical.glyph." + element.id().getPath()))
                        .withColor(element.edgeColor())));
        lines.add(Component.translatable(definition.descKey())
                .withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The weapon's own colour, for the lines this class writes and for nothing else.
     *
     * <p>Only the declared theme, never what is currently inscribed: {@code appendHoverText} runs on
     * whichever side asks for the lines, and reading a data component here to recolour a line would
     * make the tooltip differ between the two for no gain. The panel behind the box does follow the
     * inscription, and it is client-side where that is free.
     */
    private int accentColor() {
        return definition.themeElement()
                .flatMap(ForgeElements::get)
                .map(ElementDefinition::primaryColor)
                .orElse(UNTHEMED_ACCENT);
    }
}
