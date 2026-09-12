package com.efkrdnz.magical.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.forge.ForgeMaterials;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.ForgedWeapons;
import com.efkrdnz.magical.forge.chain.ForgeMaterial;
import com.efkrdnz.magical.forge.chain.ForgeRules;
import com.efkrdnz.magical.forge.glyph.ForgeVocabulary;
import com.efkrdnz.magical.forge.weapon.WeaponDefinition;
import com.mojang.datafixers.util.Either;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * What a smith sees in a weapon that nobody else does.
 *
 * <p>The forge's rules are all knowable before you walk to an anvil - which grade a piece of iron
 * will take, which shapes this weapon has somewhere to put, whether it has cooled down enough to be
 * worked again - and before this, the only way to learn any of it was to open the forge and be told
 * no. A Blacksmith reads it off the item in hand instead.
 *
 * <p>Gated on the class rather than shown to everyone on purpose: it is the class's whole payoff,
 * and a list of every weapon's secret sigils printed for any passer-by would spend the catalogue's
 * surprises in an afternoon.
 *
 * <p>Client-side only, because {@code ClientMagicState} is the only place the class is known on this
 * side - the attachment itself never leaves the server. {@code RenderTooltipEvent.GatherComponents}
 * is the right seam for that: it fires nowhere else.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class SmithsRead {

    private static final int HEADER_COLOR = 0xFFD68A;
    private static final int BOUND_COLOR = 0xFFB13C;
    private static final int POOL_COLOR = 0x7FD8FF;
    private static final int TICKS_PER_SECOND = 20;

    private SmithsRead() {}

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !ForgeMaterials.isForgeable(stack)) {
            return;
        }
        if (!ClientMagicState.get().hasClass(MagicalClasses.BLACKSMITH)) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        for (Component line : lines(stack)) {
            elements.add(Either.left(line));
        }
    }

    private static List<Component> lines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("tooltip.magical.smith.header").withColor(HEADER_COLOR));

        ForgeMaterial material = ForgeMaterials.detect(stack);
        lines.add(Component.translatable("tooltip.magical.smith.ceiling",
                        Component.translatable("forge.magical.material." + material.serializedName()),
                        Component.translatable("forge.magical.grade." + material.maxGrade().serializedName()))
                .withStyle(ChatFormatting.GRAY));

        anvilLine(stack).ifPresent(lines::add);
        sigilLines(stack, lines);
        return lines;
    }

    /**
     * Whether the weapon can be worked right now.
     *
     * <p>The remaining time comes from {@link ForgeRules#remainingCooldown}, the same call the
     * server makes when it refuses a reforge, so the tooltip cannot promise a window the anvil will
     * then deny. Without a level there is no game time to compare against and the line is dropped
     * rather than guessed at.
     */
    private static Optional<Component> anvilLine(ItemStack stack) {
        Optional<ForgedWeapon> forged = ForgedWeapons.get(stack);
        if (forged.isEmpty()) {
            return Optional.of(Component.translatable("tooltip.magical.smith.unforged")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (Minecraft.getInstance().level == null) {
            return Optional.empty();
        }
        long remaining = ForgeRules.remainingCooldown(forged.get().forgedAtGameTime(), forged.get().grade(),
                Minecraft.getInstance().level.getGameTime());
        if (remaining <= 0L) {
            return Optional.of(Component.translatable("tooltip.magical.smith.ready")
                    .withStyle(ChatFormatting.GREEN));
        }
        int seconds = (int) Math.ceil(remaining / (double) TICKS_PER_SECOND);
        return Optional.of(Component.translatable("tooltip.magical.smith.cooling", seconds)
                .withStyle(ChatFormatting.RED));
    }

    /**
     * The shapes this weapon can be given that a weapon in general cannot.
     *
     * <p>Two lines, because the two kinds are not the same promise. The archetype pool is what every
     * weapon of this shape can draw and is worth knowing when choosing between two daggers; the
     * bound sigils are this one weapon's alone and are the reason to keep it.
     */
    private static void sigilLines(ItemStack stack, List<Component> lines) {
        ForgeMaterials.weaponClass(stack).ifPresent(archetype -> {
            List<String> pool = ForgeVocabulary.archetypeGlyphs(archetype);
            if (!pool.isEmpty()) {
                lines.add(Component.translatable("tooltip.magical.smith.pool",
                                Component.translatable(archetype.nameKey()),
                                join(pool, POOL_COLOR))
                        .withStyle(ChatFormatting.GRAY));
            }
        });

        List<String> bound = ForgeMaterials.catalogue(stack)
                .map(WeaponDefinition::signatureGlyphs)
                .map(signature -> signature.stream().sorted().toList())
                .orElse(List.of());
        lines.add(bound.isEmpty()
                ? Component.translatable("tooltip.magical.smith.no_bound").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.magical.smith.bound", join(bound, BOUND_COLOR))
                        .withStyle(ChatFormatting.GRAY));
    }

    private static Component join(List<String> glyphIds, int color) {
        MutableComponent joined = null;
        for (String glyphId : glyphIds) {
            MutableComponent name = Component.translatable("forge.magical.glyph." + glyphId).withColor(color);
            joined = joined == null
                    ? name
                    : joined.append(Component.translatable("tooltip.magical.smith.sep")).append(name);
        }
        return joined == null ? Component.empty() : joined;
    }
}
