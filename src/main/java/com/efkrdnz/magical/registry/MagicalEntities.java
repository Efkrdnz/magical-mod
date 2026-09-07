package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.DivineDividerWaveEntity;
import com.efkrdnz.magical.entity.FlareTriangleEntity;
import com.efkrdnz.magical.entity.MagicBarrageBeamEntity;
import com.efkrdnz.magical.entity.MagicBarrageFieldEntity;
import com.efkrdnz.magical.entity.MagicBarrageShotEntity;
import com.efkrdnz.magical.entity.AbyssalDischargeEntity;
import com.efkrdnz.magical.entity.BlackFlameArcEntity;
import com.efkrdnz.magical.entity.BlackFlameBrandEntity;
import com.efkrdnz.magical.entity.BlackFlameFieldEntity;
import com.efkrdnz.magical.entity.BlackFlameProjectileEntity;
import com.efkrdnz.magical.entity.DimensionalGuillotineEntity;
import com.efkrdnz.magical.entity.GabrielHolyFieldEntity;
import com.efkrdnz.magical.entity.JudgementBeamEntity;
import com.efkrdnz.magical.entity.MagicCircleEffectEntity;
import com.efkrdnz.magical.entity.MagicOpponentEntity;
import com.efkrdnz.magical.entity.ForgeEffectEntity;
import com.efkrdnz.magical.entity.ForgeStrikeEntity;
import com.efkrdnz.magical.entity.ForgeZoneEntity;
import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.entity.SingularityEntity;
import com.efkrdnz.magical.entity.SoulBondEntity;
import com.efkrdnz.magical.entity.SovereignAegisEntity;
import com.efkrdnz.magical.entity.SpacePocketPortalEntity;
import com.efkrdnz.magical.entity.SpacePocketRoomEffectEntity;
import com.efkrdnz.magical.entity.SpacePortalEntity;
import com.efkrdnz.magical.entity.SpaceSummonEntity;
import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.entity.TowerAuraEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalEntities {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MagicalMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<MagicOpponentEntity>> MAGIC_OPPONENT = ENTITY_TYPES.register(
            "magic_opponent",
            () -> EntityType.Builder.<MagicOpponentEntity>of(MagicOpponentEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(2)
                    .build(key("magic_opponent")));

    public static final DeferredHolder<EntityType<?>, EntityType<BlackFlameProjectileEntity>> BLACK_FLAME_PROJECTILE = ENTITY_TYPES.register(
            "black_flame_projectile",
            () -> EntityType.Builder.<BlackFlameProjectileEntity>of(BlackFlameProjectileEntity::new, MobCategory.MISC)
                    .sized(0.75F, 0.75F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("black_flame_projectile")));

    public static final DeferredHolder<EntityType<?>, EntityType<BlackFlameFieldEntity>> BLACK_FLAME_FIELD = ENTITY_TYPES.register(
            "black_flame_field",
            () -> EntityType.Builder.<BlackFlameFieldEntity>of(BlackFlameFieldEntity::new, MobCategory.MISC)
                    .sized(10.0F, 5.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("black_flame_field")));

    public static final DeferredHolder<EntityType<?>, EntityType<BlackFlameArcEntity>> BLACK_FLAME_ARC = ENTITY_TYPES.register(
            "black_flame_arc",
            () -> EntityType.Builder.<BlackFlameArcEntity>of(BlackFlameArcEntity::new, MobCategory.MISC)
                    .sized(3.8F, 2.8F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("black_flame_arc")));

    public static final DeferredHolder<EntityType<?>, EntityType<BlackFlameBrandEntity>> BLACK_FLAME_BRAND = ENTITY_TYPES.register(
            "black_flame_brand",
            () -> EntityType.Builder.<BlackFlameBrandEntity>of(BlackFlameBrandEntity::new, MobCategory.MISC)
                    .sized(2.4F, 2.4F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("black_flame_brand")));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicCircleEffectEntity>> MAGIC_CIRCLE_EFFECT = ENTITY_TYPES.register(
            "magic_circle_effect",
            () -> EntityType.Builder.<MagicCircleEffectEntity>of(MagicCircleEffectEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .noSummon()
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("magic_circle_effect")));

    public static final DeferredHolder<EntityType<?>, EntityType<JudgementBeamEntity>> JUDGEMENT_BEAM = ENTITY_TYPES.register(
            "judgement_beam",
            () -> EntityType.Builder.<JudgementBeamEntity>of(JudgementBeamEntity::new, MobCategory.MISC)
                    .sized(34.0F, 70.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("judgement_beam")));

    public static final DeferredHolder<EntityType<?>, EntityType<GabrielHolyFieldEntity>> GABRIEL_HOLY_FIELD = ENTITY_TYPES.register(
            "gabriel_holy_field",
            () -> EntityType.Builder.<GabrielHolyFieldEntity>of(GabrielHolyFieldEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .noSummon()
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("gabriel_holy_field")));

    public static final DeferredHolder<EntityType<?>, EntityType<SkillClashEffectEntity>> SKILL_CLASH_EFFECT = ENTITY_TYPES.register(
            "skill_clash_effect",
            () -> EntityType.Builder.<SkillClashEffectEntity>of(SkillClashEffectEntity::new, MobCategory.MISC)
                    .sized(10.0F, 8.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("skill_clash_effect")));

    public static final DeferredHolder<EntityType<?>, EntityType<AbyssalDischargeEntity>> ABYSSAL_DISCHARGE = ENTITY_TYPES.register(
            "abyssal_discharge",
            () -> EntityType.Builder.<AbyssalDischargeEntity>of(AbyssalDischargeEntity::new, MobCategory.MISC)
                    .sized(26.0F, 7.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("abyssal_discharge")));

    public static final DeferredHolder<EntityType<?>, EntityType<SpaceSummonEntity>> SPACE_SUMMON = ENTITY_TYPES.register(
            "space_summon",
            () -> EntityType.Builder.<SpaceSummonEntity>of(SpaceSummonEntity::new, MobCategory.MISC)
                    .sized(3.0F, 3.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .noSummon()
                    .build(key("space_summon")));

    public static final DeferredHolder<EntityType<?>, EntityType<SpacePortalEntity>> SPACE_PORTAL = ENTITY_TYPES.register(
            "space_portal",
            () -> EntityType.Builder.<SpacePortalEntity>of(SpacePortalEntity::new, MobCategory.MISC)
                    .sized(1.8F, 2.8F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .noSummon()
                    .build(key("space_portal")));

    public static final DeferredHolder<EntityType<?>, EntityType<SpaceSubspaceEntity>> SPACE_SUBSPACE = ENTITY_TYPES.register(
            "space_subspace",
            () -> EntityType.Builder.<SpaceSubspaceEntity>of(SpaceSubspaceEntity::new, MobCategory.MISC)
                    .sized(32.0F, 32.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("space_subspace")));

    public static final DeferredHolder<EntityType<?>, EntityType<SpacePocketPortalEntity>> SPACE_POCKET_PORTAL = ENTITY_TYPES.register(
            "space_pocket_portal",
            () -> EntityType.Builder.<SpacePocketPortalEntity>of(SpacePocketPortalEntity::new, MobCategory.MISC)
                    .sized(4.0F, 5.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("space_pocket_portal")));

    public static final DeferredHolder<EntityType<?>, EntityType<SpacePocketRoomEffectEntity>> SPACE_POCKET_ROOM_EFFECT = ENTITY_TYPES.register(
            "space_pocket_room_effect",
            () -> EntityType.Builder.<SpacePocketRoomEffectEntity>of(SpacePocketRoomEffectEntity::new, MobCategory.MISC)
                    .sized(38.0F, 16.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(5)
                    .build(key("space_pocket_room_effect")));

    public static final DeferredHolder<EntityType<?>, EntityType<SingularityEntity>> SINGULARITY = ENTITY_TYPES.register(
            "singularity",
            () -> EntityType.Builder.<SingularityEntity>of(SingularityEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .noSummon()
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("singularity")));

    public static final DeferredHolder<EntityType<?>, EntityType<DimensionalGuillotineEntity>> DIMENSIONAL_GUILLOTINE = ENTITY_TYPES.register(
            "dimensional_guillotine",
            () -> EntityType.Builder.<DimensionalGuillotineEntity>of(DimensionalGuillotineEntity::new, MobCategory.MISC)
                    .sized(14.0F, 16.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("dimensional_guillotine")));

    public static final DeferredHolder<EntityType<?>, EntityType<SoulBondEntity>> SOUL_BOND = ENTITY_TYPES.register(
            "soul_bond",
            () -> EntityType.Builder.<SoulBondEntity>of(SoulBondEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .noSummon()
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("soul_bond")));

    public static final DeferredHolder<EntityType<?>, EntityType<SovereignAegisEntity>> SOVEREIGN_AEGIS = ENTITY_TYPES.register(
            "sovereign_aegis",
            () -> EntityType.Builder.<SovereignAegisEntity>of(SovereignAegisEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .noSummon()
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("sovereign_aegis")));

    public static final DeferredHolder<EntityType<?>, EntityType<TowerAuraEntity>> TOWER_AURA = ENTITY_TYPES.register(
            "tower_aura",
            () -> EntityType.Builder.<TowerAuraEntity>of(TowerAuraEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .build(key("tower_aura")));

    public static final DeferredHolder<EntityType<?>, EntityType<ForgeStrikeEntity>> FORGE_STRIKE = ENTITY_TYPES.register(
            "forge_strike",
            () -> EntityType.Builder.<ForgeStrikeEntity>of(ForgeStrikeEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("forge_strike")));

    public static final DeferredHolder<EntityType<?>, EntityType<ForgeEffectEntity>> FORGE_EFFECT = ENTITY_TYPES.register(
            "forge_effect",
            () -> EntityType.Builder.<ForgeEffectEntity>of(ForgeEffectEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(10)
                    .build(key("forge_effect")));

    public static final DeferredHolder<EntityType<?>, EntityType<ForgeZoneEntity>> FORGE_ZONE = ENTITY_TYPES.register(
            "forge_zone",
            () -> EntityType.Builder.<ForgeZoneEntity>of(ForgeZoneEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(10)
                    .build(key("forge_zone")));

    private MagicalEntities() {}

    // ---- profile-driven entity families (new roster) ----
    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.SpellEffectEntity>> SPELL_EFFECT = ENTITY_TYPES.register(
            "spell_effect",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.SpellEffectEntity>of(com.efkrdnz.magical.entity.fx.SpellEffectEntity::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("spell_effect")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.SolidConstructEntity>> SOLID_CONSTRUCT = ENTITY_TYPES.register(
            "solid_construct",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.SolidConstructEntity>of(com.efkrdnz.magical.entity.fx.SolidConstructEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("solid_construct")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.ThrownSpellEntity>> THROWN_SPELL = ENTITY_TYPES.register(
            "thrown_spell",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.ThrownSpellEntity>of(com.efkrdnz.magical.entity.fx.ThrownSpellEntity::new, MobCategory.MISC)
                    .sized(0.35F, 0.35F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("thrown_spell")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.RollingBodyEntity>> ROLLING_BODY = ENTITY_TYPES.register(
            "rolling_body",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.RollingBodyEntity>of(com.efkrdnz.magical.entity.fx.RollingBodyEntity::new, MobCategory.MISC)
                    .sized(1.2F, 1.2F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("rolling_body")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.EffigyEntity>> EFFIGY = ENTITY_TYPES.register(
            "effigy",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.EffigyEntity>of(com.efkrdnz.magical.entity.fx.EffigyEntity::new, MobCategory.MISC)
                    .sized(0.7F, 2.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("effigy")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.SpiritWolfEntity>> SPIRIT_WOLF = ENTITY_TYPES.register(
            "spirit_wolf",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.SpiritWolfEntity>of(com.efkrdnz.magical.entity.fx.SpiritWolfEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.9F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(2)
                    .build(key("spirit_wolf")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.PolymorphShellEntity>> POLYMORPH_SHELL = ENTITY_TYPES.register(
            "polymorph_shell",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.PolymorphShellEntity>of(com.efkrdnz.magical.entity.fx.PolymorphShellEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(2)
                    .build(key("polymorph_shell")));

    public static final DeferredHolder<EntityType<?>, EntityType<com.efkrdnz.magical.entity.fx.WrenchedItemEntity>> WRENCHED_ITEM = ENTITY_TYPES.register(
            "wrenched_item",
            () -> EntityType.Builder.<com.efkrdnz.magical.entity.fx.WrenchedItemEntity>of(com.efkrdnz.magical.entity.fx.WrenchedItemEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(4)
                    .build(key("wrenched_item")));


    public static final DeferredHolder<EntityType<?>, EntityType<DivineDividerWaveEntity>> DIVINE_DIVIDER_WAVE = ENTITY_TYPES.register(
            "divine_divider_wave",
            () -> EntityType.Builder.<DivineDividerWaveEntity>of(DivineDividerWaveEntity::new, MobCategory.MISC)
                    .sized(18.0F, 100.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("divine_divider_wave")));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicBarrageFieldEntity>> MAGIC_BARRAGE_FIELD = ENTITY_TYPES.register(
            "magic_barrage_field",
            () -> EntityType.Builder.<MagicBarrageFieldEntity>of(MagicBarrageFieldEntity::new, MobCategory.MISC)
                    .sized(48.0F, 48.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("magic_barrage_field")));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicBarrageShotEntity>> MAGIC_BARRAGE_SHOT = ENTITY_TYPES.register(
            "magic_barrage_shot",
            () -> EntityType.Builder.<MagicBarrageShotEntity>of(MagicBarrageShotEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("magic_barrage_shot")));

    public static final DeferredHolder<EntityType<?>, EntityType<MagicBarrageBeamEntity>> MAGIC_BARRAGE_BEAM = ENTITY_TYPES.register(
            "magic_barrage_beam",
            () -> EntityType.Builder.<MagicBarrageBeamEntity>of(MagicBarrageBeamEntity::new, MobCategory.MISC)
                    .sized(4.5F, 4.5F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("magic_barrage_beam")));


    public static final DeferredHolder<EntityType<?>, EntityType<FlareTriangleEntity>> FLARE_TRIANGLE = ENTITY_TYPES.register(
            "flare_triangle",
            () -> EntityType.Builder.<FlareTriangleEntity>of(FlareTriangleEntity::new, MobCategory.MISC)
                    .sized(34.0F, 8.0F)
                    .clientTrackingRange(SpellEntityVisibility.TRACKING_RANGE_CHUNKS)
                    .updateInterval(1)
                    .build(key("flare_triangle")));

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }

    private static ResourceKey<EntityType<?>> key(String path) {
        return ResourceKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path));
    }
}
