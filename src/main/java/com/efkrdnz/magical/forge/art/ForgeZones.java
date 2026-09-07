package com.efkrdnz.magical.forge.art;

import java.util.List;

import com.efkrdnz.magical.entity.ForgeZoneEntity;
import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * What each kind of lingering Art does on a tick. Looked up from the zone's synced
 * {@link ForgeZoneKind} ordinal every tick, so nothing about a zone's behaviour has to survive a
 * save and reload except that one number.
 *
 * <p>A zone whose owner has logged out or changed level simply idles: no damage is dealt with
 * nobody to credit it to, and the zone still expires on schedule.</p>
 */
public final class ForgeZones {

    private static final int PYRE_PERIOD = 5;
    private static final int EMBER_PERIOD = 10;
    private static final float EMBER_DAMAGE = 1.0f;
    private static final float EMBER_IGNITE_SECONDS = 2.0f;
    private static final int GLACIAL_SLOW_TICKS = 40;
    private static final int GLACIAL_SLOW_AMPLIFIER = 2;
    private static final int GLACIAL_FREEZE_TICKS = 80;
    private static final double NULL_PULL = 0.25;
    private static final int NULL_SIPHON_PERIOD = 10;
    private static final int NULL_SIPHON_MANA = 1;
    private static final int RIFT_DELAY = 10;
    private static final int SANCTIFIED_PERIOD = 10;
    private static final float SANCTIFIED_HEAL = 1.0f;
    private static final float SANCTIFIED_SMITE = 3.0f;
    private static final int MIASMA_PERIOD = 20;
    private static final int MIASMA_POISON_TICKS = 60;
    private static final int CYCLONE_PULL_TICKS = 10;
    private static final double CYCLONE_PULL = 0.35;
    private static final double CYCLONE_THROW = 0.8;
    private static final int SKYFALL_DELAY = 10;
    private static final double SKYFALL_HEIGHT = 6.0;
    private static final int BOLT_LIFE = 6;

    private ForgeZones() {}

    /** The whole table. One switch, no stored lambdas, no state outside the zone entity itself. */
    public static void tick(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age) {
        ElementDefinition element = elementOf(zone);
        switch (zone.kind()) {
            case PYRE_WHEEL -> pyreWheel(level, zone, owner, age, element);
            case EMBER_ERUPTION -> emberEruption(level, zone, owner, age, element);
            case GLACIAL_HALO -> glacialHalo(level, zone, owner, age, element);
            case NULL_ORBIT -> nullOrbit(level, zone, owner, age);
            case ABYSS_RIFT -> abyssRift(level, zone, owner, age, element);
            case SANCTIFIED_RING -> sanctifiedRing(level, zone, owner, age, element);
            case MIASMA_CLOUD -> miasmaCloud(level, zone, owner, age);
            case CYCLONE -> cyclone(level, zone, owner, age);
            case SKYFALL_BOLT -> skyfallBolt(level, zone, owner, age, element);
        }
    }

    private static void pyreWheel(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age,
            ElementDefinition element) {
        if (age % PYRE_PERIOD != 0) {
            return;
        }
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.hurt(owner, caught, zone.power());
        }
        ArtSupport.burst(level, zone.position(), ForgeEffectStyle.FIRE_BLOOM, element, zone.radius());
    }

    private static void emberEruption(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age,
            ElementDefinition element) {
        if (age % EMBER_PERIOD != 0) {
            return;
        }
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.hurt(owner, caught, EMBER_DAMAGE);
            ArtSupport.ignite(owner, caught, EMBER_IGNITE_SECONDS);
        }
        ArtSupport.burst(level, zone.position(), ForgeEffectStyle.SLAM_CRACK, element, zone.radius());
    }

    /** One hard freeze the moment the halo opens; the rest of its life is the ring standing there. */
    private static void glacialHalo(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age,
            ElementDefinition element) {
        if (age != 1) {
            return;
        }
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.apply(owner, caught,
                    new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, GLACIAL_SLOW_TICKS, GLACIAL_SLOW_AMPLIFIER));
            ArtSupport.freeze(owner, caught, GLACIAL_FREEZE_TICKS);
        }
        ArtSupport.burst(level, zone.position(), ForgeEffectStyle.FROST_SHARDS, element, zone.radius());
    }

    private static void nullOrbit(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age) {
        Vec3 centre = zone.position();
        boolean siphons = age % NULL_SIPHON_PERIOD == 0;
        int siphoned = 0;
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.pullToward(owner, caught, centre, NULL_PULL);
            siphoned += siphons ? NULL_SIPHON_MANA : 0;
        }
        if (owner != null && siphoned > 0) {
            PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
            state.addMana(siphoned);
            state.sync(owner);
        }
    }

    private static void abyssRift(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age,
            ElementDefinition element) {
        if (age != RIFT_DELAY) {
            return;
        }
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.hurt(owner, caught, zone.power());
        }
        ArtSupport.burst(level, zone.position(), ForgeEffectStyle.VOID_IMPLOSION, element, zone.radius());
    }

    private static void sanctifiedRing(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age,
            ElementDefinition element) {
        if (age % SANCTIFIED_PERIOD != 0) {
            return;
        }
        if (owner != null) {
            owner.heal(SANCTIFIED_HEAL);
        }
        for (LivingEntity caught : inside(level, zone, owner)) {
            if (caught.getType().is(EntityTypeTags.UNDEAD)) {
                ArtSupport.hurt(owner, caught, SANCTIFIED_SMITE);
            } else if (caught instanceof Player) {
                caught.heal(SANCTIFIED_HEAL);
            }
        }
        ArtSupport.burst(level, zone.position(), ForgeEffectStyle.RADIANT_CROSS, element, zone.radius());
    }

    private static void miasmaCloud(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age) {
        if (age % MIASMA_PERIOD != 0) {
            return;
        }
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.apply(owner, caught, new MobEffectInstance(MobEffects.POISON, MIASMA_POISON_TICKS, 0));
        }
    }

    /**
     * Drags everything in for ten ticks, then throws the lot outward on the eleventh. The wielder is
     * excluded: the cyclone opens on top of them, so including them would only fling them out of it.
     */
    private static void cyclone(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age) {
        Vec3 centre = zone.position();
        if (age <= CYCLONE_PULL_TICKS) {
            for (LivingEntity caught : inside(level, zone, owner)) {
                ArtSupport.pullToward(owner, caught, centre, CYCLONE_PULL);
            }
            return;
        }
        if (age == CYCLONE_PULL_TICKS + 1) {
            for (LivingEntity caught : inside(level, zone, owner)) {
                ArtSupport.pushFrom(owner, caught, centre, CYCLONE_THROW);
            }
        }
    }

    private static void skyfallBolt(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner, int age,
            ElementDefinition element) {
        if (age != SKYFALL_DELAY) {
            return;
        }
        Vec3 centre = zone.position();
        ArtSupport.bolt(level, centre.add(0.0, SKYFALL_HEIGHT, 0.0), centre, element, BOLT_LIFE);
        for (LivingEntity caught : inside(level, zone, owner)) {
            ArtSupport.hurt(owner, caught, zone.power());
        }
    }

    private static List<LivingEntity> inside(ServerLevel level, ForgeZoneEntity zone, ServerPlayer owner) {
        return ArtSupport.around(level, zone.position(), zone.radius(), owner, zone);
    }

    /**
     * The zone carries its own colours, so it never has to resolve an element id that a later build
     * might have dropped. FIRE stands in only as the enum the visual helpers ask for.
     */
    private static ElementDefinition elementOf(ForgeZoneEntity zone) {
        return new ElementDefinition(ArtSupport.STRIKE_ID, ForgeElementKind.FIRE, zone.color(),
                zone.secondaryColor(), zone.secondaryColor(), 0.0f);
    }
}
