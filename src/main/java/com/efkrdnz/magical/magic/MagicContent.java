package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

public final class MagicContent {
    /** Z, X, C, V. Sized here because the keybind array and the cooldown array both follow it. */
    public static final int LOADOUT_SIZE = 4;

    /** How many named loadouts a player may keep. Capped so the state payload stays small. */
    public static final int MAX_LOADOUTS = 6;

    /**
     * Ticks after a successful cast during which the loadout may not be switched.
     *
     * <p>Casting locks the switch, not the other way round: the chain worth stopping is fire
     * everything, switch, fire everything. Switching when you have not cast stays free.
     */
    public static final int LOADOUT_SWAP_LOCK_TICKS = 20;

    private static final Map<ResourceLocation, MagicSkillDefinition> SKILLS = new LinkedHashMap<>();
    private static final List<ResourceLocation> ORDERED_IDS = new ArrayList<>();

    public static final MagicSkillDefinition GABRIEL = register("gabriel", MagicSchool.LIGHT, MagicSkillType.BURST, 4, 5, 640.0F, 0.0F, 3.4F, 116, 4200, 80, 0.0F, 0, 0xFFD700, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition GABRIEL_ULTIMATE_PROTECTION = register("gabriel_ultimate_protection", MagicSchool.LIGHT, MagicSkillType.BARRIER, 4, 5, 0.0F, 0.0F, 1.15F, 58, 320, 20, 0.0F, 0, 0xFFF8D6, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition GABRIEL_JUDGEMENT = register("gabriel_judgement", MagicSchool.LIGHT, MagicSkillType.BURST, 4, 5, 640.0F, 0.0F, 3.4F, 116, 4200, 80, 0.0F, 0, 0xFFD700, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition GABRIEL_PERFECT_SEAL = register("gabriel_perfect_seal", MagicSchool.LIGHT, MagicSkillType.BARRIER, 4, 5, 0.0F, 0.0F, 3.0F, 98, 1500, 20, 0.0F, 0, 0xFFE27A, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition GABRIEL_HOLY_FIELD = register("gabriel_holy_field", MagicSchool.LIGHT, MagicSkillType.BURST, 4, 5, 34.0F, 0.0F, 3.6F, 132, 2800, 170, 0.45F, 0, 0xFFF1A8, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition SPACE_WALKER = register("space_walker", MagicSchool.SPATIAL, MagicSkillType.BURST, 3, 3, 0.0F, 0.0F, 1.0F, 8, 30, 8, 0.0F, 0, 0x88DFFF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition BLACK_FLAMES = register("black_flames", MagicSchool.DARK, MagicSkillType.BURST, -2, 3, 16.0F, 1.18F, 1.35F, 46, 92, 80, 0.28F, 0, 0x1A071F, MagicAttribute.DARK);
    public static final MagicSkillDefinition BLACK_FLAMES_CAST = register("black_flames_cast", MagicSchool.DARK, MagicSkillType.PROJECTILE, -2, 3, 16.0F, 1.48F, 1.45F, 48, 108, 118, 0.25F, 0, 0x1A071F, MagicAttribute.DARK);
    public static final MagicSkillDefinition BLACK_FLAMES_IMBUE = register("black_flames_imbue", MagicSchool.DARK, MagicSkillType.BURST, -2, 3, 7.5F, 1.0F, 1.0F, 34, 220, 220, 0.35F, 0, 0x2A061E, MagicAttribute.DARK);
    public static final MagicSkillDefinition BLACK_FLAMES_BRAND = register("black_flames_brand", MagicSchool.DARK, MagicSkillType.BURST, -2, 3, 13.0F, 1.0F, 1.35F, 42, 260, 220, 0.15F, 0, 0x5A083A, MagicAttribute.DARK);

    public static final MagicSkillDefinition ABYSSAL_DISCHARGE = register("abyssal_discharge", MagicSchool.DARK, MagicSkillType.BURST, -2, 0, 18.5F, 0.0F, 4.7F, 58, 260, 38, 0.55F, 0, 0x341052, MagicAttribute.DARK);

    // BLOOD, layer -1. Every one of these costs zero mana on purpose: they are billed in blood by
    // their own handler through BloodService, and a mana cost here would charge the player twice.
    public static final MagicSkillDefinition CRIMSON_TITHE = register("crimson_tithe", MagicSchool.BLOOD, MagicSkillType.BURST, -1, 0, 0.0F, 0.0F, 1.0F, 0, 320, 200, 0.0F, 0, 0xC4122B);
    public static final MagicSkillDefinition HEMORRHAGE = register("hemorrhage", MagicSchool.BLOOD, MagicSkillType.BURST, -1, 0, 6.0F, 0.0F, 5.0F, 0, 180, 140, 0.1F, 0, 0xA8142E);
    public static final MagicSkillDefinition SCARLET_LANCE = register("scarlet_lance", MagicSchool.BLOOD, MagicSkillType.PROJECTILE, -1, 0, 15.0F, 1.7F, 1.0F, 0, 140, 60, 0.45F, 0, 0xE8425E);
    public static final MagicSkillDefinition SECOND_HEART = register("second_heart", MagicSchool.BLOOD, MagicSkillType.BARRIER, -1, 0, 0.0F, 0.0F, 1.2F, 0, 700, 400, 0.0F, 0, 0x8A0B1E);
    public static final MagicSkillDefinition VEIN_WALK = register("vein_walk", MagicSchool.BLOOD, MagicSkillType.BURST, -1, 0, 0.0F, 0.0F, 1.0F, 0, 160, 20, 0.0F, 0, 0xD62839);
    public static final MagicSkillDefinition EXSANGUINATE = register("exsanguinate", MagicSchool.BLOOD, MagicSkillType.BURST, -1, 0, 4.0F, 0.0F, 1.0F, 0, 240, 80, 0.0F, 0, 0x6E0B1A);

    // DARK, layer -2, joining black_flames and abyssal_discharge above. Zero mana again, for the
    // opposite reason to Blood's: these cost nothing at the moment of casting. DarkService writes
    // the price down as Corruption instead, and nothing but Purification ever writes it off.
    public static final MagicSkillDefinition EFFIGY = register("effigy", MagicSchool.DARK, MagicSkillType.BURST, -2, 0, 0.0F, 0.0F, 1.4F, 0, 420, 400, 0.0F, 0, 0x4A2D63, MagicAttribute.DARK);
    public static final MagicSkillDefinition UMBRAL_TENANCY = register("umbral_tenancy", MagicSchool.DARK, MagicSkillType.BURST, -2, 0, 0.0F, 0.0F, 1.0F, 0, 360, 120, 0.0F, 0, 0x2E1B40, MagicAttribute.DARK);
    public static final MagicSkillDefinition LONG_DEBT = register("long_debt", MagicSchool.DARK, MagicSkillType.BURST, -2, 0, 0.0F, 0.0F, 1.2F, 0, 900, 300, 0.0F, 0, 0x7A52A3, MagicAttribute.DARK);
    public static final MagicSkillDefinition SEVER_THE_THREAD = register("sever_the_thread", MagicSchool.DARK, MagicSkillType.PROJECTILE, -2, 0, 2.0F, 1.9F, 0.7F, 0, 200, 60, 0.0F, 0, 0x9B6FD4, MagicAttribute.DARK);

    public static final MagicSkillDefinition VAULT_OF_AVARICE = register("vault_of_avarice", MagicSchool.VOID, MagicSkillType.BURST, -4, 0, 0.0F, 0.0F, 1.0F, 0, 0, 20, 0.0F, 0, 0xD7F75B, MagicAttribute.DARK);
    public static final MagicSkillDefinition CREATE_SUBSPACE = register("create_subspace", MagicSchool.ARCANE, MagicSkillType.BURST, -5, 0, 0.0F, 0.0F, 1.0F, 22, 40, 20, 0.0F, 0, 0x88DFFF, MagicAttribute.ARCANE);
    public static final MagicSkillDefinition MANIPULATE_SPACE = register("manipulate_space", MagicSchool.ARCANE, MagicSkillType.BURST, -5, 0, 0.0F, 0.0F, 1.0F, 8, 8, 20, 0.0F, 0, 0xA9ECFF, MagicAttribute.ARCANE);
    public static final MagicSkillDefinition POCKET_DIMENSION = register("pocket_dimension", MagicSchool.SPATIAL, MagicSkillType.BURST, -5, 0, 0.0F, 0.0F, 1.0F, 36, 180, 20, 0.0F, 0, 0x5DA8FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition SPATIAL_ARSENAL = register("spatial_arsenal", MagicSchool.SPATIAL, MagicSkillType.BURST, -5, 0, 0.0F, 0.0F, 1.0F, 18, 80, 20, 0.0F, 0, 0x6ABEFF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition SINGULARITY = register("singularity", MagicSchool.SPATIAL, MagicSkillType.BURST, -5, 0, 32.0F, 0.82F, 2.0F, 84, 900, 160, 1.2F, 0, 0x050714, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition DIMENSIONAL_GUILLOTINE = register("dimensional_guillotine", MagicSchool.SPATIAL, MagicSkillType.BURST, -5, 0, 42.0F, 1.0F, 2.3F, 72, 520, 42, 1.4F, 0, 0x82E8FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition SOUL_VOW = register("soul_vow", MagicSchool.SOUL, MagicSkillType.BURST, -5, 0, 0.0F, 0.0F, 1.0F, 18, 80, 20, 0.0F, 0, 0xD8F0FF, MagicAttribute.SOUL);
    public static final MagicSkillDefinition SOUL_VALLEY = register("soul_valley", MagicSchool.SOUL, MagicSkillType.BURST, -5, 0, 0.0F, 0.0F, 1.0F, 74, 1600, 20, 0.0F, 0, 0xD8F0FF, MagicAttribute.SOUL);

    // --- Fusion outputs (Spell Creator / Magic Originator combination spells) ---
    // Created skills: not random-rewardable, not usable as fusion inputs. They fall through the
    // caster's id-ladder to the generic PROJECTILE/BURST path, colored and scaled by these stats.

    // =====================================================================================
    // NEW ROSTER: 36 base skills (per school T0 x2, T1, T2, T3, T4), 16 class rewards, 7 fusion outputs.
    // register(path, school, type, tier, requiredLevel, dmg, speed, size, mana, cooldown, duration, knockback, barrierRestore, color[, attribute])
    // =====================================================================================
    // --- ARCANE ---
    public static final MagicSkillDefinition ARCANE_SNAP = register("arcane_snap", MagicSchool.ARCANE, MagicSkillType.BURST, 0, 0, 4.0F, 0.0F, 1.0F, 8, 40, 20, 0.0F, 0, 0x9FB4FF);
    public static final MagicSkillDefinition LODESTONE = register("lodestone", MagicSchool.ARCANE, MagicSkillType.BURST, 0, 0, 0.0F, 1.0F, 1.0F, 12, 180, 100, 0.0F, 0, 0x6E7CE6);
    public static final MagicSkillDefinition RETROGRADE = register("retrograde", MagicSchool.ARCANE, MagicSkillType.BURST, 1, 1, 3.0F, 0.0F, 1.0F, 22, 200, 60, 0.0F, 0, 0x9C8CFF);
    public static final MagicSkillDefinition ARCANE_EXILE = register("arcane_exile", MagicSchool.ARCANE, MagicSkillType.BURST, 2, 3, 12.0F, 1.0F, 1.0F, 30, 90, 60, 0.3F, 0, 0xA9B3FF);
    public static final MagicSkillDefinition PUPPET_SIGIL = register("puppet_sigil", MagicSchool.ARCANE, MagicSkillType.BURST, 3, 5, 12.0F, 1.35F, 1.0F, 48, 220, 60, 0.3F, 0, 0x9C8CFF);
    public static final MagicSkillDefinition ECHOES_OF_PASSAGE = register("echoes_of_passage", MagicSchool.ARCANE, MagicSkillType.BURST, 4, 5, 20.0F, 0.0F, 1.4F, 96, 1300, 140, 0.7F, 0, 0xC9A6FF);
    public static final MagicSkillDefinition CIRCLE_ARSENAL = register("circle_arsenal", MagicSchool.ARCANE, MagicSkillType.BURST, 4, 5, 8.0F, 1.35F, 1.0F, 28, 900, 20, 0.15F, 0, 0x8E7BFF, MagicAttribute.ARCANE);
    // --- FIRE ---
    public static final MagicSkillDefinition SMOKESTACK = register("smokestack", MagicSchool.FIRE, MagicSkillType.BURST, 0, 0, 1.0F, 0.0F, 3.0F, 12, 140, 100, 0.0F, 0, 0x4A2E24);
    public static final MagicSkillDefinition SLAG_ROLLER = register("slag_roller", MagicSchool.FIRE, MagicSkillType.PROJECTILE, 0, 0, 5.0F, 0.55F, 1.2F, 14, 70, 80, 0.6F, 0, 0xD8471A);
    public static final MagicSkillDefinition WILDFIRE = register("wildfire", MagicSchool.FIRE, MagicSkillType.BURST, 1, 1, 2.5F, 0.2F, 4.0F, 24, 100, 120, 0.0F, 0, 0xFF6A1F);
    public static final MagicSkillDefinition MAGMA_VENT = register("magma_vent", MagicSchool.FIRE, MagicSkillType.BURST, 2, 3, 12.0F, 1.15F, 1.6F, 34, 140, 80, 0.4F, 0, 0xFF5A1C);
    public static final MagicSkillDefinition ASH_EFFIGY = register("ash_effigy", MagicSchool.FIRE, MagicSkillType.PROJECTILE, 3, 5, 19.0F, 1.1F, 2.2F, 48, 240, 100, 0.6F, 0, 0x9A8A80);
    public static final MagicSkillDefinition CRUCIBLE = register("crucible", MagicSchool.FIRE, MagicSkillType.BURST, 4, 5, 30.0F, 0.0F, 6.0F, 96, 1200, 200, 1.2F, 0, 0xFF3C10);
    public static final MagicSkillDefinition FLARE_RING = register("flare_ring", MagicSchool.FIRE, MagicSkillType.BURST, 1, 1, 7.8F, 0.0F, 2.0F, 24, 36, 12, 0.5F, 0, 0xFFB15A);
    // --- WATER ---
    public static final MagicSkillDefinition RIP_CURRENT = register("rip_current", MagicSchool.WATER, MagicSkillType.BURST, 0, 0, 1.5F, 0.28F, 1.8F, 14, 24, 60, 0.0F, 0, 0x3FB8E8);
    public static final MagicSkillDefinition RIME_SNAP = register("rime_snap", MagicSchool.WATER, MagicSkillType.BURST, 0, 0, 5.0F, 0.0F, 2.4F, 14, 30, 25, 0.0F, 0, 0xCFF4FF);
    public static final MagicSkillDefinition DROWNING_BELL = register("drowning_bell", MagicSchool.WATER, MagicSkillType.BURST, 1, 1, 3.0F, 5.0F, 1.1F, 24, 140, 80, 0.0F, 0, 0x5FB8D8);
    public static final MagicSkillDefinition ICE_RAMPART = register("ice_rampart", MagicSchool.WATER, MagicSkillType.BARRIER, 2, 3, 7.0F, 0.0F, 5.0F, 34, 70, 160, 0.9F, 0, 0x7FD0F0);
    public static final MagicSkillDefinition DELUGE_JET = register("deluge_jet", MagicSchool.WATER, MagicSkillType.PROJECTILE, 3, 5, 2.6F, 0.38F, 0.9F, 44, 80, 24, 0.4F, 0, 0x2A8FD8);
    public static final MagicSkillDefinition LEVIATHAN_COIL = register("leviathan_coil", MagicSchool.WATER, MagicSkillType.BURST, 4, 5, 22.0F, 0.0F, 10.0F, 96, 900, 200, 0.9F, 0, 0x155F7A);
    // --- LIGHT ---
    public static final MagicSkillDefinition GLINT = register("glint", MagicSchool.LIGHT, MagicSkillType.PROJECTILE, 0, 0, 4.5F, 0.9F, 1.0F, 12, 26, 14, 0.0F, 0, 0xFFF7C2, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition LANTERN_BRAND = register("lantern_brand", MagicSchool.LIGHT, MagicSkillType.BURST, 0, 0, 1.8F, 1.0F, 1.0F, 12, 50, 80, 0.35F, 0, 0xFFCF6E, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition REVELATION = register("revelation", MagicSchool.LIGHT, MagicSkillType.BURST, 1, 1, 5.0F, 15.0F, 24.0F, 18, 160, 100, 0.0F, 0, 0xFFE2A0, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition CLEANSING_RAY = register("cleansing_ray", MagicSchool.LIGHT, MagicSkillType.BURST, 2, 3, 7.0F, 0.0F, 2.0F, 28, 80, 100, 0.2F, 0, 0xFFF1B8, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition PRISM_CASCADE = register("prism_cascade", MagicSchool.LIGHT, MagicSkillType.BURST, 3, 5, 22.0F, 0.0F, 1.1F, 48, 120, 30, 0.4F, 0, 0xFFD873, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition HEAVENS_GAZE = register("heavens_gaze", MagicSchool.LIGHT, MagicSkillType.BURST, 4, 5, 6.0F, 0.35F, 2.6F, 96, 1200, 120, 0.3F, 0, 0xFFE04A, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition DIVINE_DIVIDER = register("divine_divider", MagicSchool.LIGHT, MagicSkillType.BURST, 3, 4, 36.0F, 1.35F, 3.1F, 78, 1200, 52, 1.0F, 0, 0xF8FCFF);
    public static final MagicSkillDefinition SOVEREIGN_AEGIS = register("sovereign_aegis", MagicSchool.LIGHT, MagicSkillType.BARRIER, 4, 5, 0.0F, 0.0F, 1.0F, 32, 220, 60, 0.0F, 0, 0xFFF4B2, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition AEGIS_ULTIMATE_PROTECTION = register("aegis_ultimate_protection", MagicSchool.LIGHT, MagicSkillType.BARRIER, 4, 5, 0.0F, 0.0F, 1.0F, 44, 260, 20, 0.0F, 0, 0xFFF8D6, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition AEGIS_SANCTUARY = register("aegis_sanctuary", MagicSchool.LIGHT, MagicSkillType.BARRIER, 4, 5, 0.0F, 0.0F, 3.0F, 58, 460, 180, 0.0F, 0, 0xAEEBFF, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition AEGIS_PERFECT_SEAL = register("aegis_perfect_seal", MagicSchool.LIGHT, MagicSkillType.BARRIER, 4, 5, 0.0F, 0.0F, 2.8F, 86, 1200, 20, 0.0F, 0, 0xFFE27A, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition JUDGEMENT = register("judgement", MagicSchool.LIGHT, MagicSkillType.BURST, 4, 5, 500.0F, 0.0F, 3.0F, 90, 3600, 80, 0.0F, 0, 0xFFDC38);
    // --- VOID ---
    public static final MagicSkillDefinition HOLLOW_MAW = register("hollow_maw", MagicSchool.VOID, MagicSkillType.BURST, 0, 0, 5.0F, 0.0F, 1.0F, 14, 40, 12, 0.25F, 0, 0x5A1C6E, MagicAttribute.DARK);
    public static final MagicSkillDefinition HUSHWING = register("hushwing", MagicSchool.VOID, MagicSkillType.PROJECTILE, 0, 0, 0.45F, 0.32F, 1.0F, 16, 90, 120, 0.0F, 0, 0x7B6AA8, MagicAttribute.DARK);
    public static final MagicSkillDefinition GRAVEMOONS = register("gravemoons", MagicSchool.VOID, MagicSkillType.BARRIER, 1, 1, 6.2F, 1.0F, 1.0F, 24, 160, 200, 0.6F, 0, 0x3B1F55, MagicAttribute.DARK);
    public static final MagicSkillDefinition ANTITHESIS = register("antithesis", MagicSchool.VOID, MagicSkillType.BURST, 2, 3, 8.5F, 0.0F, 1.0F, 30, 120, 160, 0.9F, 0, 0x9C7BE6, MagicAttribute.DARK);
    public static final MagicSkillDefinition GULLET_OF_THE_DEEP = register("gullet_of_the_deep", MagicSchool.VOID, MagicSkillType.BURST, 3, 5, 7.5F, 0.0F, 1.45F, 56, 260, 30, 1.1F, 0, 0x24102E, MagicAttribute.DARK);
    public static final MagicSkillDefinition FALLEN_FIRMAMENT = register("fallen_firmament", MagicSchool.VOID, MagicSkillType.BURST, 4, 5, 9.0F, 0.35F, 3.0F, 98, 1400, 120, 0.0F, 0, 0x2E1042, MagicAttribute.DARK);
    // --- SPATIAL ---
    public static final MagicSkillDefinition TRANSPOSITION = register("transposition", MagicSchool.SPATIAL, MagicSkillType.BURST, 0, 0, 4.0F, 1.0F, 1.0F, 14, 100, 12, 0.15F, 0, 0xB4F1FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition SHORTREACH = register("shortreach", MagicSchool.SPATIAL, MagicSkillType.BURST, 0, 0, 4.0F, 0.0F, 1.0F, 14, 90, 60, 0.0F, 0, 0xB4D8E6, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition COMPRESSION = register("compression", MagicSchool.SPATIAL, MagicSkillType.BURST, 1, 1, 5.0F, 1.0F, 1.0F, 20, 90, 80, 0.0F, 0, 0x9AE6FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition RIGID_FRAME = register("rigid_frame", MagicSchool.SPATIAL, MagicSkillType.BURST, 2, 3, 11.0F, 0.0F, 6.0F, 36, 140, 80, 1.0F, 0, 0xD9C58E, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition CREASE_FOLD = register("crease_fold", MagicSchool.SPATIAL, MagicSkillType.BURST, 3, 5, 20.0F, 0.0F, 2.5F, 52, 220, 12, 0.0F, 0, 0x8FE0FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition DIASPORA = register("diaspora", MagicSchool.SPATIAL, MagicSkillType.BURST, 4, 5, 36.0F, 0.0F, 2.5F, 96, 1200, 60, 0.4F, 0, 0x8ED9FF, MagicAttribute.SPATIAL);
    // --- CLASS REWARDS ---
    public static final MagicSkillDefinition HEATED_IRON = register("heated_iron", MagicSchool.FIRE, MagicSkillType.BURST, 0, 0, 0.5F, 6.0F, 0.55F, 12, 120, 80, 0.0F, 0, 0xFF7A2E);
    public static final MagicSkillDefinition DAWNHAMMER = register("dawnhammer", MagicSchool.LIGHT, MagicSkillType.BURST, 2, 0, 14.0F, 1.0F, 1.3F, 30, 70, 10, 0.9F, 0, 0xFFD86A, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition GILDED_CHAIN = register("gilded_chain", MagicSchool.LIGHT, MagicSkillType.BURST, 3, 0, 5.0F, 0.0F, 4.0F, 44, 220, 120, 0.6F, 0, 0xFFD27A, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition IRON_CHARGE = register("iron_charge", MagicSchool.ARCANE, MagicSkillType.BURST, 0, 0, 5.5F, 1.15F, 0.9F, 12, 60, 6, 0.8F, 0, 0x7CE0FF);
    public static final MagicSkillDefinition SUNDER_GRIP = register("sunder_grip", MagicSchool.ARCANE, MagicSkillType.BURST, 1, 0, 6.0F, 1.1F, 1.0F, 16, 100, 60, 0.3F, 0, 0xA9B9CF);
    public static final MagicSkillDefinition BLOODSHOUT = register("bloodshout", MagicSchool.FIRE, MagicSkillType.BURST, 2, 0, 6.0F, 0.0F, 1.0F, 30, 180, 100, 0.0F, 0, 0xD7301A);
    public static final MagicSkillDefinition LIVING_BULWARK = register("living_bulwark", MagicSchool.ARCANE, MagicSkillType.BARRIER, 2, 0, 5.0F, 0.65F, 1.0F, 30, 220, 120, 0.0F, 12, 0x9AA7B8);
    public static final MagicSkillDefinition HAIL_VOLLEY = register("hail_volley", MagicSchool.WATER, MagicSkillType.BURST, 1, 0, 1.5F, 1.4F, 2.0F, 22, 140, 60, 0.35F, 0, 0xA9DDF5);
    public static final MagicSkillDefinition SPIRIT_WOLF = register("spirit_wolf", MagicSchool.WATER, MagicSkillType.BURST, 2, 0, 6.0F, 0.42F, 1.0F, 34, 200, 240, 0.3F, 0, 0x8FE3E0);
    public static final MagicSkillDefinition LEAD_SHOT = register("lead_shot", MagicSchool.SPATIAL, MagicSkillType.PROJECTILE, 2, 0, 24.0F, 0.0F, 1.5F, 30, 100, 10, 0.8F, 0, 0xE0F6FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition ARCANE_GRASP = register("arcane_grasp", MagicSchool.ARCANE, MagicSkillType.BURST, 1, 0, 9.0F, 0.6F, 1.0F, 18, 70, 36, 0.0F, 0, 0x9AD8FF);
    public static final MagicSkillDefinition ARCANUM_HARE = register("arcanum_hare", MagicSchool.ARCANE, MagicSkillType.BURST, 3, 0, 6.0F, 0.42F, 0.6F, 55, 240, 80, 0.0F, 0, 0xB8E8FF);
    public static final MagicSkillDefinition LEECH_CUT = register("leech_cut", MagicSchool.VOID, MagicSkillType.BURST, 2, 0, 14.0F, 1.0F, 0.9F, 22, 90, 12, 0.0F, 12, 0x9C2C5E, MagicAttribute.DARK);
    public static final MagicSkillDefinition SOMNOLENT_DRAUGHT = register("somnolent_draught", MagicSchool.WATER, MagicSkillType.BURST, 0, 0, 1.5F, 1.0F, 3.0F, 12, 140, 40, 0.0F, 0, 0xB9A6E8);
    public static final MagicSkillDefinition CONTAGION = register("contagion", MagicSchool.VOID, MagicSkillType.BURST, 2, 0, 2.4F, 0.0F, 1.5F, 34, 160, 200, 0.0F, 0, 0x552B78, MagicAttribute.DARK);
    public static final MagicSkillDefinition UNSTABLE_COMPOUND = register("unstable_compound", MagicSchool.LIGHT, MagicSkillType.PROJECTILE, 3, 0, 16.0F, 0.0F, 1.75F, 54, 260, 60, 0.6F, 0, 0xF0B843, MagicAttribute.DIVINE);
    // --- CLASS TREE SIGNATURES (one per line, plus the Warsmith apex that granted nothing) ---
    public static final MagicSkillDefinition ANVIL_FALL = register("anvil_fall", MagicSchool.FIRE, MagicSkillType.BURST, 3, 0, 18.0F, 0.0F, 2.2F, 48, 200, 30, 0.9F, 10, 0xFF6A2A);
    public static final MagicSkillDefinition SIGIL_FORGE = register("sigil_forge", MagicSchool.SPATIAL, MagicSkillType.BURST, 2, 0, 12.0F, 0.0F, 1.4F, 26, 140, 400, 0.4F, 0, 0xC9E8FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition WAR_HORN = register("war_horn", MagicSchool.LIGHT, MagicSkillType.BURST, 2, 0, 7.0F, 0.0F, 3.0F, 30, 160, 80, 0.7F, 6, 0xFFE3A0, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition SIGHT_LINE = register("sight_line", MagicSchool.SPATIAL, MagicSkillType.BURST, 2, 0, 10.0F, 0.0F, 1.0F, 28, 120, 12, 0.2F, 0, 0xDCF2FF, MagicAttribute.SPATIAL);
    public static final MagicSkillDefinition MANA_BLOOM = register("mana_bloom", MagicSchool.ARCANE, MagicSkillType.BURST, 2, 0, 11.0F, 0.0F, 2.6F, 34, 170, 60, 0.0F, 0, 0x9CD4FF);
    public static final MagicSkillDefinition VIAL_BREAK = register("vial_break", MagicSchool.WATER, MagicSkillType.BURST, 2, 0, 6.0F, 0.9F, 2.0F, 24, 130, 160, 0.2F, 0, 0x8FD46F, MagicAttribute.WATER);

    // --- FUSION OUTPUTS ---
    public static final MagicSkillDefinition SCALDING_GEYSER = register("scalding_geyser", MagicSchool.WATER, MagicSkillType.BURST, 2, 0, 9.0F, 1.3F, 1.2F, 40, 220, 200, 0.5F, 0, 0xE8D7C0, MagicAttribute.WATER);
    public static final MagicSkillDefinition DAWNWELL = register("dawnwell", MagicSchool.LIGHT, MagicSkillType.BURST, 2, 0, 1.5F, 0.0F, 4.0F, 36, 300, 100, 0.0F, 8, 0xDDF6FF, MagicAttribute.DIVINE);
    public static final MagicSkillDefinition BLACK_ORRERY = register("black_orrery", MagicSchool.VOID, MagicSkillType.BURST, 3, 0, 8.0F, 0.0F, 4.0F, 60, 320, 100, 0.0F, 0, 0x5B3A8C, MagicAttribute.DARK);
    public static final MagicSkillDefinition CINDER_CHARIOT = register("cinder_chariot", MagicSchool.FIRE, MagicSkillType.BURST, 3, 0, 8.0F, 0.6F, 2.2F, 56, 360, 100, 0.8F, 0, 0xE0521C);
    public static final MagicSkillDefinition FALLEN_SUN = register("fallen_sun", MagicSchool.FIRE, MagicSkillType.BURST, 4, 0, 12.0F, 0.0F, 3.0F, 100, 1400, 200, 0.6F, 0, 0xFFB330);
    public static final MagicSkillDefinition TOTAL_ECLIPSE = register("total_eclipse", MagicSchool.VOID, MagicSkillType.BURST, 4, 0, 20.0F, 0.3F, 9.0F, 100, 1400, 120, 0.5F, 0, 0x1A0B2E, MagicAttribute.DARK);
    public static final MagicSkillDefinition TECTONIC_VERDICT = register("tectonic_verdict", MagicSchool.SPATIAL, MagicSkillType.BURST, 4, 0, 40.0F, 0.35F, 7.0F, 100, 1400, 60, 1.0F, 0, 0xB0532A, MagicAttribute.SPATIAL);

    public static final ResourceLocation STARTER_SKILL = ARCANE_SNAP.id();
    private static final float STARTER_TIER_4_CHANCE = 0.18F;
    private static final int STARTER_SPACE_WALKER_LUCKY_WEIGHT = 6;
    public static final Set<ResourceLocation> STARTER_UNLOCKS = Set.of(STARTER_SKILL);
    public static final Set<ResourceLocation> AUTHORITY_SKILLS = Set.of(CREATE_SUBSPACE.id(), MANIPULATE_SPACE.id(), POCKET_DIMENSION.id(), SPATIAL_ARSENAL.id(), SOUL_VOW.id());
    public static final Set<ResourceLocation> CLASS_REWARD_SKILLS = Set.of(
            HEATED_IRON.id(),
            DAWNHAMMER.id(),
            GILDED_CHAIN.id(),
            IRON_CHARGE.id(),
            SUNDER_GRIP.id(),
            BLOODSHOUT.id(),
            LIVING_BULWARK.id(),
            HAIL_VOLLEY.id(),
            SPIRIT_WOLF.id(),
            LEAD_SHOT.id(),
            ARCANE_GRASP.id(),
            ARCANUM_HARE.id(),
            LEECH_CUT.id(),
            SOMNOLENT_DRAUGHT.id(),
            CONTAGION.id(),
            UNSTABLE_COMPOUND.id(),
            ANVIL_FALL.id(),
            SIGIL_FORGE.id(),
            WAR_HORN.id(),
            SIGHT_LINE.id(),
            MANA_BLOOM.id(),
            VIAL_BREAK.id());
    public static final Set<ResourceLocation> SUB_SKILLS = Set.of(
            AEGIS_ULTIMATE_PROTECTION.id(),
            AEGIS_SANCTUARY.id(),
            AEGIS_PERFECT_SEAL.id(),
            GABRIEL_ULTIMATE_PROTECTION.id(),
            GABRIEL_JUDGEMENT.id(),
            GABRIEL_PERFECT_SEAL.id(),
            GABRIEL_HOLY_FIELD.id(),
            BLACK_FLAMES_CAST.id(),
            BLACK_FLAMES_IMBUE.id(),
            BLACK_FLAMES_BRAND.id(),
            SINGULARITY.id(),
            DIMENSIONAL_GUILLOTINE.id(),
            SOUL_VALLEY.id());
    public static final Set<ResourceLocation> CREATED_SKILLS = Set.of(
            BLACK_FLAMES.id(),
            GABRIEL.id(),
            SCALDING_GEYSER.id(),
            DAWNWELL.id(),
            BLACK_ORRERY.id(),
            CINDER_CHARIOT.id(),
            FALLEN_SUN.id(),
            TOTAL_ECLIPSE.id(),
            TECTONIC_VERDICT.id());
    public static final Set<ResourceLocation> ALL_SKILLS = SKILLS.keySet().stream()
            .filter(id -> !SUB_SKILLS.contains(id))
            .filter(id -> !CREATED_SKILLS.contains(id))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private MagicContent() {}

    private static MagicSkillDefinition register(String path, MagicSchool school, MagicSkillType type, int tier, int requiredLevel,
            float baseDamage, float baseSpeed, float baseSize, int baseManaCost, int baseCooldownTicks, int baseDurationTicks,
            float baseKnockback, int barrierRestore, int color) {
        return register(path, school, type, tier, requiredLevel, baseDamage, baseSpeed, baseSize, baseManaCost, baseCooldownTicks, baseDurationTicks,
                baseKnockback, barrierRestore, color, MagicAttribute.fromSchool(school));
    }

    private static MagicSkillDefinition register(String path, MagicSchool school, MagicSkillType type, int tier, int requiredLevel,
            float baseDamage, float baseSpeed, float baseSize, int baseManaCost, int baseCooldownTicks, int baseDurationTicks,
            float baseKnockback, int barrierRestore, int color, MagicAttribute attribute) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
        MagicSkillDefinition definition = new MagicSkillDefinition(id, school, type, tier, requiredLevel, baseDamage, baseSpeed, baseSize,
                baseManaCost, baseCooldownTicks, baseDurationTicks, baseKnockback, barrierRestore, color, attribute);
        SKILLS.put(id, definition);
        ORDERED_IDS.add(id);
        return definition;
    }

    public static MagicSkillDefinition get(ResourceLocation id) {
        return SKILLS.get(id);
    }

    public static List<MagicSkillDefinition> allSkills() {
        return SKILLS.values().stream().filter(skill -> !isSubSkill(skill.id())).toList();
    }

    public static List<MagicSkillDefinition> skillsForTier(int tier) {
        return SKILLS.values().stream().filter(skill -> skill.tier() == tier && !isSubSkill(skill.id())).toList();
    }


    public static List<MagicSkillDefinition> sovereignAegisSubSkills() {
        return List.of(AEGIS_ULTIMATE_PROTECTION, AEGIS_SANCTUARY, AEGIS_PERFECT_SEAL);
    }

    public static List<MagicSkillDefinition> gabrielSubSkills() {
        return List.of(GABRIEL_ULTIMATE_PROTECTION, GABRIEL_JUDGEMENT, GABRIEL_PERFECT_SEAL, GABRIEL_HOLY_FIELD);
    }

    public static List<MagicSkillDefinition> blackFlamesSubSkills() {
        return List.of(BLACK_FLAMES_CAST, BLACK_FLAMES_IMBUE, BLACK_FLAMES_BRAND);
    }

    public static List<MagicSkillDefinition> spatialArsenalSubSkills() {
        return List.of(SINGULARITY, DIMENSIONAL_GUILLOTINE);
    }

    public static List<MagicSkillDefinition> soulVowSubSkills() {
        return List.of(SOUL_VALLEY);
    }

    public static List<MagicSkillDefinition> authoritySkills() {
        return SKILLS.values().stream().filter(skill -> AUTHORITY_SKILLS.contains(skill.id())).toList();
    }

    public static List<ResourceLocation> orderedSkillIds() {
        return List.copyOf(ORDERED_IDS);
    }

    public static List<String> commandIds() {
        return ORDERED_IDS.stream().filter(id -> !isSubSkill(id) && !isCreatedSkill(id)).map(ResourceLocation::getPath).toList();
    }

    public static ResourceLocation skillIdByIndex(int index) {
        return ORDERED_IDS.get(Math.floorMod(index, ORDERED_IDS.size()));
    }

    public static int skillIndex(ResourceLocation id) {
        return ORDERED_IDS.indexOf(id);
    }

    public static int maxTier() {
        return SKILLS.values().stream().filter(skill -> !isAuthoritySkill(skill.id())).mapToInt(MagicSkillDefinition::tier).max().orElse(0);
    }

    public static int minTier() {
        return SKILLS.values().stream().filter(skill -> !isAuthoritySkill(skill.id())).mapToInt(MagicSkillDefinition::tier).min().orElse(0);
    }

    public static boolean isAuthoritySkill(ResourceLocation skillId) {
        return AUTHORITY_SKILLS.contains(skillId);
    }

    public static boolean isClassRewardSkill(ResourceLocation skillId) {
        return CLASS_REWARD_SKILLS.contains(skillId);
    }

    public static boolean isSubSkill(ResourceLocation skillId) {
        return SUB_SKILLS.contains(skillId);
    }

    public static boolean isCreatedSkill(ResourceLocation skillId) {
        return CREATED_SKILLS.contains(skillId);
    }

    public static ResourceLocation randomProficiencyReward(Set<ResourceLocation> alreadyUnlocked, RandomSource random) {
        List<Integer> tiers = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int totalWeight = 0;
        for (int tier = 0; tier <= maxTier(); tier++) {
            int currentTier = tier;
            boolean hasCandidate = SKILLS.values().stream()
                    .anyMatch(skill -> skill.tier() == currentTier
                            && !STARTER_SKILL.equals(skill.id())
                            && !isAuthoritySkill(skill.id())
                            && !isClassRewardSkill(skill.id())
                            && !isSubSkill(skill.id())
                            && !isCreatedSkill(skill.id())
                            && !alreadyUnlocked.contains(skill.id()));
            if (!hasCandidate) {
                continue;
            }
            int weight = Math.max(3, 64 / ((tier + 1) * (tier + 1)));
            tiers.add(tier);
            weights.add(weight);
            totalWeight += weight;
        }
        if (tiers.isEmpty()) {
            return null;
        }

        int roll = random.nextInt(totalWeight);
        int selectedTier = tiers.getLast();
        for (int i = 0; i < tiers.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) {
                selectedTier = tiers.get(i);
                break;
            }
        }

        int finalTier = selectedTier;
        List<MagicSkillDefinition> candidates = SKILLS.values().stream()
                .filter(skill -> skill.tier() == finalTier
                        && !STARTER_SKILL.equals(skill.id())
                        && !isAuthoritySkill(skill.id())
                        && !isClassRewardSkill(skill.id())
                        && !isSubSkill(skill.id())
                        && !isCreatedSkill(skill.id())
                        && !alreadyUnlocked.contains(skill.id()))
                .toList();
        return candidates.get(random.nextInt(candidates.size())).id();
    }

    public static ResourceLocation randomStarterBonusSkill(Set<ResourceLocation> alreadyUnlocked, RandomSource random) {
        boolean lucky = random.nextFloat() < STARTER_TIER_4_CHANCE;
        ResourceLocation selected = lucky
                ? weightedStarterSkill(alreadyUnlocked, random, 4, true)
                : weightedStarterSkill(alreadyUnlocked, random, 3, false);
        return selected != null ? selected : weightedStarterSkill(alreadyUnlocked, random, 3, false);
    }

    private static ResourceLocation weightedStarterSkill(Set<ResourceLocation> alreadyUnlocked, RandomSource random, int tier, boolean includeLuckySpaceWalker) {
        List<StarterSkillWeight> weights = new ArrayList<>();
        for (MagicSkillDefinition skill : SKILLS.values()) {
            if (skill.tier() == tier && isStarterBonusCandidate(skill.id(), alreadyUnlocked)) {
                weights.add(new StarterSkillWeight(skill.id(), 1));
            }
        }
        if (includeLuckySpaceWalker && isStarterBonusCandidate(SPACE_WALKER.id(), alreadyUnlocked)) {
            weights.add(new StarterSkillWeight(SPACE_WALKER.id(), STARTER_SPACE_WALKER_LUCKY_WEIGHT));
        }
        int totalWeight = weights.stream().mapToInt(StarterSkillWeight::weight).sum();
        if (totalWeight <= 0) {
            return null;
        }
        int roll = random.nextInt(totalWeight);
        for (StarterSkillWeight weight : weights) {
            roll -= weight.weight();
            if (roll < 0) {
                return weight.skillId();
            }
        }
        return weights.getLast().skillId();
    }

    private static boolean isStarterBonusCandidate(ResourceLocation skillId, Set<ResourceLocation> alreadyUnlocked) {
        return !STARTER_SKILL.equals(skillId)
                && !isAuthoritySkill(skillId)
                && !isClassRewardSkill(skillId)
                && !isSubSkill(skillId)
                && !isCreatedSkill(skillId)
                && !alreadyUnlocked.contains(skillId);
    }

    public static int levelForXp(int xp) {
        if (xp >= 480) {
            if (xp >= 720) {
                return 5;
            }
            return 4;
        }
        if (xp >= 300) {
            return 3;
        }
        if (xp >= 170) {
            return 2;
        }
        if (xp >= 70) {
            return 1;
        }
        return 0;
    }

    public static int xpIntoLevel(int xp) {
        int level = levelForXp(xp);
        return xp - requiredXpForLevel(level);
    }

    public static int xpForNextLevel(int xp) {
        int level = levelForXp(xp);
        int next = requiredXpForLevel(Math.min(5, level + 1));
        if (next <= xp) {
            return 0;
        }
        return next - xp;
    }

    public static int requiredXpForLevel(int level) {
        return switch (level) {
            case 5 -> 720;
            case 4 -> 480;
            case 3 -> 300;
            case 2 -> 170;
            case 1 -> 70;
            default -> 0;
        };
    }

    private record StarterSkillWeight(ResourceLocation skillId, int weight) {}
}
