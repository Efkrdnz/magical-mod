package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public final class AuthorityContent {
    private static final Map<ResourceLocation, AuthorityDefinition> AUTHORITIES = new LinkedHashMap<>();

    public static final ResourceLocation SPACE = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "authority_of_space");
    public static final ResourceLocation SOUL = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "authority_of_soul");
    public static final ResourceLocation MANA = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "authority_of_mana");
    public static final ResourceLocation CHAOS = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "authority_of_chaos");

    public static final AuthorityDefinition AUTHORITY_OF_SPACE = register(
            SPACE,
            0x88DFFF,
            List.of(MagicContent.CREATE_SUBSPACE.id(), MagicContent.MANIPULATE_SPACE.id(), MagicContent.POCKET_DIMENSION.id(), MagicContent.SPATIAL_ARSENAL.id()));

    public static final AuthorityDefinition AUTHORITY_OF_SOUL = register(
            SOUL,
            0xD8F0FF,
            List.of(MagicContent.SOUL_VOW.id()));

    public static final AuthorityDefinition AUTHORITY_OF_MANA = register(
            MANA,
            0xF0F4FF,
            List.of(MagicContent.INCANTATION_1.id(), MagicContent.INCANTATION_2.id(),
                    MagicContent.INCANTATION_3.id(), MagicContent.INCANTATION_4.id(),
                    MagicContent.GRIMOIRE.id()));

    public static final AuthorityDefinition AUTHORITY_OF_CHAOS = register(
            CHAOS,
            0xFF4FD8,
            List.of(MagicContent.BURDEN.id(), MagicContent.FRACTURE.id(), MagicContent.LAST_GRAIN.id(),
                    MagicContent.CRITICALITY.id()));

    private AuthorityContent() {}

    private static AuthorityDefinition register(ResourceLocation id, int color, List<ResourceLocation> skillIds) {
        AuthorityDefinition definition = new AuthorityDefinition(id, color, List.copyOf(skillIds));
        AUTHORITIES.put(id, definition);
        return definition;
    }

    public static AuthorityDefinition get(ResourceLocation id) {
        return AUTHORITIES.get(id);
    }

    public static List<AuthorityDefinition> all() {
        return List.copyOf(AUTHORITIES.values());
    }

    public static List<String> commandIds() {
        return AUTHORITIES.keySet().stream().map(ResourceLocation::getPath).toList();
    }
}
