package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;

import java.util.Optional;

/**
 * A creature kind's own numbers, read off its vanilla attributes: what a figment of it weighs and how
 * hard it bites once it is real. No level is needed, so the Playbill can ask on the client.
 */
public final class KindStats {
    private static final float ORDINARY_HEALTH = 20.0F;

    private KindStats() {}

    public static float maxHealth(String creatureId) {
        return base(creatureId, Attributes.MAX_HEALTH, ORDINARY_HEALTH);
    }

    public static float attack(String creatureId) {
        return base(creatureId, Attributes.ATTACK_DAMAGE, 0.0F);
    }

    @SuppressWarnings("unchecked")
    private static float base(String creatureId, Holder<Attribute> attribute, float fallback) {
        ResourceLocation id = ResourceLocation.tryParse(creatureId);
        if (id == null) {
            return fallback;
        }
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty() || !DefaultAttributes.hasSupplier(type.get())) {
            return fallback;
        }
        AttributeSupplier supplier = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type.get());
        return supplier.hasAttribute(attribute) ? (float) supplier.getBaseValue(attribute) : fallback;
    }
}
