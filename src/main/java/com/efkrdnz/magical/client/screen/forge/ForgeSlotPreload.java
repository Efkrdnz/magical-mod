package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.chain.ForgeChainBuilder;
import com.efkrdnz.magical.forge.chain.ForgeKeptChain;
import com.efkrdnz.magical.forge.chain.ForgeKeptGlyphs;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the rune chain in step with the weapon slot.
 *
 * <p>A forged weapon dropped into an empty chain preloads its own runes as kept glyphs, so
 * adjusting one rune no longer means redrawing all of them; taking that weapon out again drops the
 * cells that came off it. Work in progress is never wiped: a chain the player has already put a
 * glyph of their own into is left exactly as it is, and the preload waits for the next empty
 * chain.</p>
 */
final class ForgeSlotPreload {

    private ItemStack lastWeapon = ItemStack.EMPTY;

    /** Call once per client tick with whatever is in the forge slot right now. */
    void sync(ForgeChainBuilder builder, ItemStack weapon) {
        if (ItemStack.matches(weapon, lastWeapon)) {
            return;
        }
        lastWeapon = weapon.copy();
        builder.removeKept();
        if (!builder.committed().isEmpty()) {
            return;
        }
        ForgedWeapons.keptChain(ForgedWeapons.get(weapon))
                .ifPresent(chain -> builder.preloadKept(drawable(chain), chain.quality()));
    }

    /**
     * The kept glyphs of {@code chain} this build can draw and the server can back. A weapon
     * carrying a rune this version no longer defines would otherwise preload a blank cell that
     * every submit refuses.
     */
    private static List<ForgeKeptGlyphs.Kept> drawable(ForgeKeptChain chain) {
        return chain.preloadOrder().stream()
                .filter(kept -> ForgeGlyphLibrary.byId(kept.id())
                        .filter(template -> template.category() == kept.category())
                        .isPresent())
                .toList();
    }
}
