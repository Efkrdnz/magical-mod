package com.efkrdnz.magical.magic;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

public record AuthorityDefinition(ResourceLocation id, int color, List<ResourceLocation> skillIds) {
    public String nameKey() {
        return "authority.magical." + id.getPath();
    }

    public String descriptionKey() {
        return nameKey() + ".desc";
    }
}
