package com.efkrdnz.magical.client.screen.forge;

import com.efkrdnz.magical.forge.chain.ForgeRecipe;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Supplies the derived combat numbers the preview panel shows underneath the recipe summary, so the
 * panel only has to lay lines out and never has to work any of them out. {@code ForgeStatPreview}
 * is the implementation the forge screen wires in.
 */
@FunctionalInterface
public interface ForgePreviewText {

    /** Extra lines describing what {@code recipe} would produce at {@code quality}. */
    List<Component> statLines(ForgeRecipe recipe, int quality);
}
