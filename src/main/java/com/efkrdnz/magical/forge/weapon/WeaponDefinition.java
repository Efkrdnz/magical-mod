package com.efkrdnz.magical.forge.weapon;

import java.util.Optional;
import java.util.Set;

import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.chain.ForgeMaterial;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ToolMaterial;

/**
 * One weapon in the catalogue: what it hits for, what shape it swings in, and which glyphs only it
 * can draw.
 *
 * <p>These are the first stats-carrying items in the mod. Everything before them ran on vanilla
 * swords and axes, so {@link #attack} is deliberately the <em>displayed</em> number - the value the
 * tooltip shows and the value {@code ForgeStrikeMath.baseHit} multiplies through - rather than the
 * raw modifier, which is that number less the player's own base and the material's bonus. Declaring
 * the modifier instead would mean every entry in the catalogue is off by a different amount from
 * what it says.
 *
 * @param id              registry path, also the lang key and asset name
 * @param archetype       the swing shape, which decides reach, knockback and recovery deltas
 * @param attack          total attack damage as the tooltip shows it
 * @param attackSpeed     attacks per second, as the tooltip shows it
 * @param gradeCap        the highest grade this weapon accepts, via {@link ForgeMaterial#maxGrade()}
 * @param themeElement    the element this weapon is about, or empty for one that is about none
 * @param signatureGlyphs bare glyph ids only this weapon may draw, on top of its archetype's pool
 */
public record WeaponDefinition(ResourceLocation id, WeaponClass archetype, float attack, float attackSpeed,
        ForgeMaterial gradeCap, Optional<ResourceLocation> themeElement, Set<String> signatureGlyphs) {

    public WeaponDefinition {
        signatureGlyphs = Set.copyOf(signatureGlyphs);
    }

    /**
     * Durability, repair material and enchantability come from vanilla's table rather than from
     * seven more columns here. The grade cap is what the catalogue actually chooses; the rest
     * follows from it, and following from it is why the two can never disagree.
     */
    public ToolMaterial toolMaterial() {
        return switch (gradeCap) {
            case WOOD -> ToolMaterial.WOOD;
            case STONE -> ToolMaterial.STONE;
            case IRON, UNKNOWN -> ToolMaterial.IRON;
            case GOLD -> ToolMaterial.GOLD;
            case DIAMOND -> ToolMaterial.DIAMOND;
            case NETHERITE -> ToolMaterial.NETHERITE;
        };
    }

    /**
     * What to hand {@code ToolMaterial.applySwordProperties} so the tooltip reads {@link #attack}.
     *
     * <p>Vanilla adds the modifier to the player's base attack damage of 1 and then adds the
     * material's own bonus on top, so both come back off here.
     */
    public float attackModifier() {
        return attack - 1.0f - toolMaterial().attackDamageBonus();
    }

    /** Vanilla's attack-speed modifier is an offset from the base rate of four swings a second. */
    public float attackSpeedModifier() {
        return attackSpeed - 4.0f;
    }

    public String path() {
        return id.getPath();
    }

    public String nameKey() {
        return "item.magical." + path();
    }

    public String descKey() {
        return nameKey() + ".desc";
    }
}
