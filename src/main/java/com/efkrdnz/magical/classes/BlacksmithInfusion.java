package com.efkrdnz.magical.classes;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

/**
 * Runeforging: Blacksmiths inscribe a weapon with an attribute glyph, a binding knot, and a
 * grade sigil, all hand-drawn in the forge. The attribute decides the on-hit identity, the
 * binding tier decides how reliably it procs, the grade decides raw power, and the temper
 * quality (how cleanly the runes were drawn) scales everything in between. Divinesmiths can
 * re-forge an inscribed weapon up to Divine grade.
 */
public final class BlacksmithInfusion {
    public static final String INFUSION_KEY = MagicalMod.MODID + "_infusion";
    private static final String ATTRIBUTE = "attribute";
    private static final String GRADE = "grade";
    private static final String BINDING = "binding";
    private static final String QUALITY = "quality";
    private static final String TEMPER = "temper";
    private static final int MISFIRE_QUALITY = 25;

    public enum RuneAttribute {
        FIRE(0xFF6A2A, ChatFormatting.GOLD),
        VOID(0x7B4DFF, ChatFormatting.DARK_PURPLE),
        STORM(0x7FE7FF, ChatFormatting.AQUA),
        FROST(0xBDF3FF, ChatFormatting.BLUE),
        RADIANT(0xFFE9A0, ChatFormatting.YELLOW),
        VENOM(0x8FE04B, ChatFormatting.GREEN);

        private final int color;
        private final ChatFormatting chatColor;

        RuneAttribute(int color, ChatFormatting chatColor) {
            this.color = color;
            this.chatColor = chatColor;
        }

        public int color() {
            return color;
        }

        public ChatFormatting chatColor() {
            return chatColor;
        }

        public String nameKey() {
            return "screen.magical.forge_rune." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * The weapon's temperament - how the broad slash behaves. Keen strikes far and can crit,
     * Heavy sweeps wide and hits like a truck, Swift trades range for a snappier recovery.
     */
    public enum RuneTemper {
        KEEN(1.6F, -10.0F, 1.0F, 0.30F, 0),
        HEAVY(0.0F, 46.0F, 1.7F, 0.0F, 0),
        SWIFT(-0.5F, -18.0F, 0.9F, 0.0F, 3);

        private final float reachBonus;
        private final float arcBonus;
        private final float knockbackScale;
        private final float critChance;
        private final int cooldownTrim;

        RuneTemper(float reachBonus, float arcBonus, float knockbackScale, float critChance, int cooldownTrim) {
            this.reachBonus = reachBonus;
            this.arcBonus = arcBonus;
            this.knockbackScale = knockbackScale;
            this.critChance = critChance;
            this.cooldownTrim = cooldownTrim;
        }

        public float reachBonus() {
            return reachBonus;
        }

        public float arcBonus() {
            return arcBonus;
        }

        public float knockbackScale() {
            return knockbackScale;
        }

        public float critChance() {
            return critChance;
        }

        public int cooldownTrim() {
            return cooldownTrim;
        }

        public String nameKey() {
            return "screen.magical.forge_rune." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Immutable snapshot of a weapon's infusion, handed to the melee service for the elemental cleave. */
    public record Infusion(RuneAttribute attribute, RuneGrade grade, int binding, int quality, RuneTemper temper) {
        public float temperMultiplier() {
            return 0.75F + quality * 0.005F;
        }
    }

    public enum RuneGrade {
        CRUDE(2.5F, 20, 8),
        FINE(4.5F, 45, 15),
        HIGH(7.5F, 90, 25),
        DIVINE(14.0F, 200, 40);

        private final float baseDamage;
        private final int manaCost;
        private final int classXp;

        RuneGrade(float baseDamage, int manaCost, int classXp) {
            this.baseDamage = baseDamage;
            this.manaCost = manaCost;
            this.classXp = classXp;
        }

        public float baseDamage() {
            return baseDamage;
        }

        public int manaCost() {
            return manaCost;
        }

        public String nameKey() {
            return "screen.magical.forge_rune." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private BlacksmithInfusion() {}

    public static void forge(ServerPlayer player, ItemStack weapon, RuneAttribute attribute, RuneGrade grade, int bindingTier, int quality, RuneTemper temper) {
        if (weapon.isEmpty() || !isWeapon(weapon)) {
            player.displayClientMessage(Component.translatable("message.magical.infusion_requires_forge_weapon"), true);
            return;
        }
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        bindingTier = Mth.clamp(bindingTier, 1, 3);
        quality = Mth.clamp(quality, 0, 100);

        if (grade == RuneGrade.DIVINE) {
            if (!state.hasClass(MagicalClasses.DIVINESMITH)) {
                player.displayClientMessage(Component.translatable("message.magical.divinesmith_required"), true);
                return;
            }
            if (!hasInfusion(weapon)) {
                player.displayClientMessage(Component.translatable("message.magical.divine_requires_infused"), true);
                return;
            }
        } else if (!state.hasClass(MagicalClasses.BLACKSMITH)) {
            player.displayClientMessage(Component.translatable("message.magical.blacksmith_required"), true);
            return;
        }

        if (state.mana() < grade.manaCost()) {
            player.displayClientMessage(Component.translatable("message.magical.forge_no_mana", grade.manaCost()), true);
            return;
        }
        if (quality < MISFIRE_QUALITY) {
            state.setMana(state.mana() - grade.manaCost() / 2);
            state.sync(player);
            player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.55F);
            player.displayClientMessage(Component.translatable("message.magical.forge_misfire"), true);
            return;
        }

        state.setMana(state.mana() - grade.manaCost());
        writeInfusion(weapon, attribute, grade, bindingTier, quality, temper);
        if (grade == RuneGrade.DIVINE) {
            state.addClassXp(MagicalClasses.DIVINESMITH, grade.classXp);
        } else {
            state.addClassXp(MagicalClasses.BLACKSMITH, grade.classXp);
        }
        state.sync(player);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.85F, grade == RuneGrade.DIVINE ? 1.4F : 1.0F);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7F, 0.7F);
        player.displayClientMessage(Component.translatable("message.magical.weapon_runeforged",
                Component.translatable(grade.nameKey()), Component.translatable(attribute.nameKey()), roman(bindingTier), quality,
                Component.translatable(temper.nameKey())), true);
    }

    public static void applyWeaponEffects(ServerPlayer attacker, LivingEntity target, ItemStack weapon) {
        CompoundTag infusion = infusionTag(weapon);
        if (infusion == null || !(attacker.level() instanceof ServerLevel level)) {
            return;
        }
        RuneAttribute attribute = attributeOf(infusion);
        RuneGrade grade = gradeOf(infusion);
        int binding = infusion.contains(BINDING) ? Mth.clamp(infusion.getInt(BINDING), 1, 3) : 2;
        int quality = infusion.contains(QUALITY) ? Mth.clamp(infusion.getInt(QUALITY), 0, 100) : 60;
        if (attribute == null || grade == null) {
            return;
        }

        float temper = 0.75F + quality * 0.005F;
        float bonus = grade.baseDamage() * temper;
        boolean divine = grade == RuneGrade.DIVINE;
        boolean proc = attacker.getRandom().nextFloat() < 0.20F + binding * 0.25F + (divine ? 0.05F : 0.0F);

        // Synergy shatter/skewer bonuses are checked before the hit so pre-existing states count.
        if (proc && attribute == RuneAttribute.FIRE && target.isOnFire()) {
            bonus *= 1.5F;
        }
        if (proc && attribute == RuneAttribute.FROST && target.getTicksFrozen() > 0) {
            bonus *= 1.5F;
        }
        target.hurt(attacker.damageSources().magic(), bonus);

        if (proc) {
            applyElement(level, attacker, target, attribute, grade.ordinal(), divine, bonus);
            if (divine) {
                attacker.heal(1.0F);
                level.sendParticles(ParticleTypes.END_ROD, attacker.getX(), attacker.getY(0.8D), attacker.getZ(), 4, 0.25D, 0.35D, 0.25D, 0.02D);
            }
        }
        // The ranged element now travels on the flying slash thrown by every swing
        // (see MeleeCombatService), so a direct hit no longer spits a separate projectile.
    }

    /**
     * Applies the bound element's on-hit effect to a single target. Shared by the direct weapon
     * proc and the elemental broad-slash cleave ({@code bonus} is the reference damage the element
     * scales its secondary hits from).
     */
    public static void applyElement(ServerLevel level, ServerPlayer attacker, LivingEntity target, RuneAttribute attribute, int gradeIndex, boolean divine, float bonus) {
        switch (attribute) {
            case FIRE -> {
                target.igniteForSeconds(2.0F + gradeIndex * 2.0F);
                level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY(0.5D), target.getZ(), 8 + gradeIndex * 4, 0.35D, 0.35D, 0.35D, 0.05D);
            }
            case VOID -> {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40 + gradeIndex * 25, divine ? 2 : gradeIndex >= 2 ? 1 : 0), attacker);
                siphonMana(attacker, 2 + gradeIndex * 2);
                level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY(0.6D), target.getZ(), 12, 0.35D, 0.45D, 0.35D, 0.3D);
            }
            case STORM -> chainLightning(level, attacker, target, bonus * 0.6F, gradeIndex);
            case FROST -> {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50 + gradeIndex * 20, gradeIndex >= 2 ? 2 : 1), attacker);
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 60, target.getTicksFrozen() + 60 + gradeIndex * 30));
                level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.6D), target.getZ(), 10 + gradeIndex * 4, 0.35D, 0.4D, 0.35D, 0.04D);
            }
            case RADIANT -> {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60 + gradeIndex * 20, 0), attacker);
                if (target.getType().is(EntityTypeTags.UNDEAD)) {
                    target.hurt(attacker.damageSources().magic(), 2.0F + gradeIndex * 2.0F); // smite
                }
                attacker.heal(0.5F + gradeIndex * 0.5F);
                level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6D), target.getZ(), 10 + gradeIndex * 4, 0.35D, 0.45D, 0.35D, 0.04D);
            }
            case VENOM -> {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + gradeIndex * 30, gradeIndex >= 2 ? 1 : 0), attacker);
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60 + gradeIndex * 20, 0), attacker);
                level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY(0.6D), target.getZ(), 8 + gradeIndex * 3, 0.35D, 0.4D, 0.35D, 0.02D);
            }
        }
    }

    private static void chainLightning(ServerLevel level, ServerPlayer attacker, LivingEntity target, float damage, int gradeIndex) {
        LivingEntity chained = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : level.getEntities(target, target.getBoundingBox().inflate(5.0D),
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != attacker)) {
            double distance = entity.distanceToSqr(target);
            if (distance < bestDistance) {
                bestDistance = distance;
                chained = (LivingEntity) entity;
            }
        }
        if (chained == null) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6D), target.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.12D);
            return;
        }
        chained.hurt(attacker.damageSources().magic(), damage);
        Vec3 from = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        Vec3 to = chained.position().add(0.0D, chained.getBbHeight() * 0.5D, 0.0D);
        int points = Math.max(4, (int) (from.distanceTo(to) * 6.0D));
        for (int i = 0; i <= points; i++) {
            Vec3 point = from.lerp(to, i / (double) points);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0.04D, 0.04D, 0.04D, 0.0D);
        }
        level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25F, 1.7F + gradeIndex * 0.1F);
    }

    private static void siphonMana(ServerPlayer attacker, int amount) {
        PlayerMagicState state = attacker.getData(MagicalAttachments.MAGIC_STATE);
        state.setMana(Math.min(state.mana() + amount, state.maxMana()));
        state.sync(attacker);
    }

    public static boolean hasInfusion(ItemStack stack) {
        return infusionTag(stack) != null;
    }

    public static Component tooltip(ItemStack stack) {
        CompoundTag infusion = infusionTag(stack);
        if (infusion == null) {
            return null;
        }
        RuneAttribute attribute = attributeOf(infusion);
        RuneGrade grade = gradeOf(infusion);
        if (attribute == null || grade == null) {
            return null;
        }
        int binding = infusion.contains(BINDING) ? infusion.getInt(BINDING) : 2;
        int quality = infusion.contains(QUALITY) ? infusion.getInt(QUALITY) : 60;
        return Component.translatable("tooltip.magical.runeforge",
                        Component.translatable(grade.nameKey()),
                        Component.translatable(attribute.nameKey()),
                        roman(binding), quality,
                        Component.translatable(temperOf(infusion).nameKey()))
                .withStyle(attribute.chatColor());
    }

    private static String roman(int tier) {
        return switch (Mth.clamp(tier, 1, 3)) {
            case 1 -> "I";
            case 2 -> "II";
            default -> "III";
        };
    }

    /** Full infusion snapshot for the melee cleave, or null if the weapon isn't runeforged. */
    public static Infusion infusionFor(ItemStack stack) {
        CompoundTag infusion = infusionTag(stack);
        if (infusion == null) {
            return null;
        }
        RuneAttribute attribute = attributeOf(infusion);
        RuneGrade grade = gradeOf(infusion);
        if (attribute == null || grade == null) {
            return null;
        }
        int binding = infusion.contains(BINDING) ? Mth.clamp(infusion.getInt(BINDING), 1, 3) : 2;
        int quality = infusion.contains(QUALITY) ? Mth.clamp(infusion.getInt(QUALITY), 0, 100) : 60;
        return new Infusion(attribute, grade, binding, quality, temperOf(infusion));
    }

    private static void writeInfusion(ItemStack stack, RuneAttribute attribute, RuneGrade grade, int bindingTier, int quality, RuneTemper temper) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag infusion = new CompoundTag();
            infusion.putString(ATTRIBUTE, attribute.name());
            infusion.putString(GRADE, grade.name());
            infusion.putInt(BINDING, bindingTier);
            infusion.putInt(QUALITY, quality);
            infusion.putString(TEMPER, temper.name());
            tag.put(INFUSION_KEY, infusion);
        });
    }

    private static RuneTemper temperOf(CompoundTag infusion) {
        if (infusion.contains(TEMPER)) {
            try {
                return RuneTemper.valueOf(infusion.getString(TEMPER).toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // fall through to the default for legacy weapons
            }
        }
        return RuneTemper.KEEN;
    }

    private static RuneAttribute attributeOf(CompoundTag infusion) {
        String raw = infusion.getString(ATTRIBUTE);
        // Legacy weapons stored a resource location such as "magical:fire".
        String name = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        try {
            return RuneAttribute.valueOf(name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static RuneGrade gradeOf(CompoundTag infusion) {
        try {
            return RuneGrade.valueOf(infusion.getString(GRADE).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static CompoundTag infusionTag(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        if (!tag.contains(INFUSION_KEY)) {
            return null;
        }
        CompoundTag infusion = tag.getCompound(INFUSION_KEY);
        return infusion.contains(ATTRIBUTE) && infusion.contains(GRADE) ? infusion : null;
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem || stack.getItem() instanceof AxeItem;
    }
}
