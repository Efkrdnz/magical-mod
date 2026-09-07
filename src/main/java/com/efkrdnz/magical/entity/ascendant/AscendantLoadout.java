package com.efkrdnz.magical.entity.ascendant;

import com.efkrdnz.magical.forge.ForgeIds;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphCategory;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The blade each Ascendant carries, inscribed the way no player could inscribe it.
 *
 * <p>These are built straight from the {@link ForgedWeapon} constructor rather than through the
 * Runeforge, so they skip every gate the forge applies: the Blacksmith class, the Divinesmith
 * requirement on Divine, the material floor, the mana cost, the reforge cooldown and the fusion
 * skill check. Nothing below the chain grammar re-tests any of that, which is what makes a tier 10
 * blade possible at all - a Divine black-flame inscription needs Divinesmith, the Black Flames
 * sorcery and an already High-forged base, and the boss is not a smith.
 *
 * <p>They drop. That is the point of them: the reward for the fight is a weapon you could not have
 * made, and every one of them re-enters the forge as a legal chain afterwards.
 */
public final class AscendantLoadout {

    /** Inscribed perfectly. A boss's blade is not a botched draw. */
    private static final int QUALITY = 100;

    private AscendantLoadout() {}

    /**
     * The weapon for a tier, freshly built.
     *
     * <p>Each one is chosen to match how its tier fights rather than to be uniformly bigger: the
     * mid tiers carry a rider that punishes a duel, and the top two carry the fork-and-payload
     * chains the operator runes were added for.
     */
    public static ItemStack weaponFor(AscendantTier tier) {
        return switch (tier) {
            // A skirmisher's axe: chain lightning off a heavy swing.
            case ECHO -> forged(Items.NETHERITE_AXE, ForgeGrade.MASTER, "storm", "heavy",
                    List.of("slash", "pierce", "cleave"));
            // A duelist's blade: poison that outlasts the exchange, and life drawn back out of it.
            case SIN_EATER -> forged(Items.NETHERITE_SWORD, ForgeGrade.MASTER, "venom", "keen",
                    List.of("pierce", "slash", "leech", "thrust"));
            // Artillery: two crescents on one swing, each detonating where it lands.
            case FALLEN -> forged(Items.NETHERITE_SWORD, ForgeGrade.MYTHIC, "explosion", "swift",
                    List.of("fork", "wave", "wave", "shatter", "slam"));
            // An executioner's chain: a black-flame wave that births a slam on the first body.
            case BLACK_FLAME -> forged(Items.NETHERITE_SWORD, ForgeGrade.MYTHIC, "black_flame", "keen",
                    List.of("trigger", "wave", "slam", "pierce", "brand"));
            // Three forked black-flame waves, each birthing a slam where its flight ends.
            case AUTHORITY -> forged(Items.NETHERITE_SWORD, ForgeGrade.DIVINE, "black_flame", "heavy",
                    List.of("fork", "fork", "wake", "wave", "wave", "wave", "slam", "shatter", "pierce"));
        };
    }

    private static ItemStack forged(Item item, ForgeGrade grade, String element, String temper,
            List<String> program) {
        ItemStack stack = new ItemStack(item);
        ForgedWeapons.write(stack, new ForgedWeapon(
                ForgeIds.id(element),
                grade,
                Optional.of(ForgeIds.id(temper)),
                idsOf(program, GlyphCategory.FORM),
                idsOf(program, GlyphCategory.MODIFIER),
                program.stream().map(ForgeIds::id).toList(),
                QUALITY,
                0L));
        return stack;
    }

    /**
     * The flat form and modifier views the tooltip and the kept-rune strip read.
     *
     * <p>Derived from the program rather than written out twice, so the two can never disagree - a
     * weapon whose flat lists disagreed with its program would show the player one chain and fire
     * another.
     */
    private static List<ResourceLocation> idsOf(List<String> program, GlyphCategory category) {
        return program.stream()
                .filter(id -> ForgeGlyphLibrary.byId(id)
                        .map(template -> template.category() == category)
                        .orElse(false))
                .map(ForgeIds::id)
                .toList();
    }
}
