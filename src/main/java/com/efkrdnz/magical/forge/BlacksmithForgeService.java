package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.forge.chain.ForgeChainGrammar;
import com.efkrdnz.magical.forge.chain.ForgeError;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.chain.ForgeMaterial;
import com.efkrdnz.magical.forge.chain.ForgeRecipe;
import com.efkrdnz.magical.forge.chain.ForgeRules;
import com.efkrdnz.magical.forge.chain.RecognizedGlyph;
import com.efkrdnz.magical.forge.chain.StrokeQuantizer;
import com.efkrdnz.magical.forge.chain.ForgeValidation;
import com.efkrdnz.magical.forge.glyph.ForgeGlyphLibrary;
import com.efkrdnz.magical.forge.glyph.GlyphPoint;
import com.efkrdnz.magical.forge.glyph.GlyphQuality;
import com.efkrdnz.magical.forge.glyph.GlyphRecognizer;
import com.efkrdnz.magical.forge.glyph.GlyphTemplate;
import com.efkrdnz.magical.forge.glyph.RecognitionResult;
import com.efkrdnz.magical.forge.menu.BlacksmithForgeMenu;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.ForgeResultPayload;
import com.efkrdnz.magical.network.ForgeSubmitPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side pipeline for the Runeforge: opens the menu and turns a submitted rune chain into a
 * {@link ForgedWeapon} on the weapon in the forge slot. Every rejection is validated and replied to
 * with a {@link ForgeResultPayload}; a malformed client payload is never allowed to throw.
 */
public final class BlacksmithForgeService {

    /**
     * Minimum ticks between two submissions from the same player, a basic spam guard.
     *
     * <p>Half a second is far below anything a person can draw a chain in, so the legitimate path
     * never feels it, and it caps the recognizer at two runs a second per player. One accepted
     * submit runs the point-cloud matcher twelve times over thirty-five templates at N=32 - about
     * three million distance calls on the server thread - so this is the only thing bounding it.</p>
     */
    private static final int SUBMIT_COOLDOWN_TICKS = 10;

    /** Minimum ticks between two Runeforge opens from the same player. */
    private static final int OPEN_COOLDOWN_TICKS = 5;

    /** Remembered players above which the throttle maps sweep their stale entries. */
    private static final int THROTTLE_SWEEP_SIZE = 256;

    /** How long a remembered tick stays interesting: a minute is far past every cooldown above. */
    private static final long THROTTLE_STALE_TICKS = 20L * 60L;

    /**
     * Last submit and last open, per player.
     *
     * <p>The submit throttle used to live on the {@code BlacksmithForgeMenu} instance, which does
     * not outlive the menu: {@code open()} builds a fresh one with no history at all, so a modified
     * client could loop {@code OpenForgePayload}, one container click to re-seat the weapon, then a
     * maximum-size {@code ForgeSubmitPayload}, at packet rate, and pay no cooldown for any of it.
     * Keyed by player instead, the throttle survives the menu - and opening the screen is itself
     * rate-limited, so the menu-building half of that loop is bounded too.</p>
     */
    private static final Map<UUID, Long> LAST_SUBMIT_TICK = new HashMap<>();

    private static final Map<UUID, Long> LAST_OPEN_TICK = new HashMap<>();

    private BlacksmithForgeService() {
    }

    public static void open(ServerPlayer player) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasClass(MagicalClasses.BLACKSMITH)) {
            MagicalMod.LOGGER.debug("{} tried to open the Runeforge without the Blacksmith class",
                    player.getGameProfile().getName());
            return;
        }
        if (throttled(LAST_OPEN_TICK, player, OPEN_COOLDOWN_TICKS)) {
            // No menu exists yet for a ForgeResultPayload flash to land on, so this gets the same
            // action-bar toast every other server-side gate with nothing open to reply into uses
            // (ArcaneCastingService, BlackFlamesService, ...) rather than looking like a dead block.
            player.displayClientMessage(Component.translatable("message.magical.runeforge_locked"), true);
            return;
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new BlacksmithForgeMenu(containerId, inventory),
                Component.translatable("screen.magical.runeforge")));
    }

    public static void submit(ServerPlayer player, ForgeSubmitPayload payload) {
        if (!(player.containerMenu instanceof BlacksmithForgeMenu menu) || menu.containerId != payload.containerId()) {
            MagicalMod.LOGGER.debug("Forge submit from {} ignored: no matching Runeforge menu open",
                    player.getGameProfile().getName());
            return;
        }
        if (throttled(LAST_SUBMIT_TICK, player, SUBMIT_COOLDOWN_TICKS)) {
            return;
        }
        MagicalNetwork.sendForgeResult(player,
                resolve(player, menu, payload, player.serverLevel().getGameTime()));
    }

    /** Drops a player's throttle bookkeeping. Called when they log out. */
    public static void forget(ServerPlayer player) {
        LAST_SUBMIT_TICK.remove(player.getUUID());
        LAST_OPEN_TICK.remove(player.getUUID());
    }

    /**
     * Whether this action is still inside its cooldown; stamps the map when it is not. A clock that
     * has moved backwards - a dimension keeping its own game time - reads as stale rather than as a
     * cooldown that could never expire.
     */
    private static boolean throttled(Map<UUID, Long> lastAt, ServerPlayer player, int cooldownTicks) {
        long now = player.serverLevel().getGameTime();
        Long last = lastAt.get(player.getUUID());
        if (last != null && now >= last && now - last < cooldownTicks) {
            return true;
        }
        lastAt.put(player.getUUID(), now);
        sweep(lastAt, now);
        return false;
    }

    /** Drops entries for players who have not touched the forge in a minute, once the map is big. */
    private static void sweep(Map<UUID, Long> lastAt, long now) {
        if (lastAt.size() <= THROTTLE_SWEEP_SIZE) {
            return;
        }
        lastAt.values().removeIf(tick -> tick > now || now - tick > THROTTLE_STALE_TICKS);
    }

    // --- pipeline ---------------------------------------------------------------------------

    private static ForgeResultPayload resolve(
            ServerPlayer player, BlacksmithForgeMenu menu, ForgeSubmitPayload payload, long now) {
        Optional<ForgeResultPayload> structureFailure = validateStructure(payload);
        if (structureFailure.isPresent()) {
            return structureFailure.get();
        }
        ItemStack weapon = menu.weaponStack();
        Optional<ForgeResultPayload> weaponFailure = validateWeapon(weapon);
        if (weaponFailure.isPresent()) {
            return weaponFailure.get();
        }
        RecognizeOutcome recognized = recognizeGlyphs(payload);
        if (recognized.failure().isPresent()) {
            return recognized.failure().get();
        }
        ForgeValidation validation = ForgeChainGrammar.validate(recognized.glyphs());
        if (validation instanceof ForgeValidation.Invalid invalid) {
            return ForgeResultPayload.fail(invalid.error(), invalid.argument());
        }
        ForgeRecipe recipe = ((ForgeValidation.Valid) validation).recipe();
        return applyRecipe(player, menu, weapon, recipe, now);
    }

    // --- steps 6-11: class/material/cooldown/mana gates, then apply --------------------------

    private static ForgeResultPayload applyRecipe(
            ServerPlayer player, BlacksmithForgeMenu menu, ItemStack weapon, ForgeRecipe recipe, long now) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        Optional<ForgeResultPayload> classFailure = checkClassGate(state, recipe.grade());
        if (classFailure.isPresent()) {
            return classFailure.get();
        }

        Optional<ForgedWeapon> existing = ForgedWeapons.getOrMigrate(weapon);
        ForgeMaterial material = ForgeMaterials.detect(weapon);
        Optional<ForgeResultPayload> gradeFailure = checkGradeCap(material, recipe.grade(), existing);
        if (gradeFailure.isPresent()) {
            return gradeFailure.get();
        }
        Optional<ForgeResultPayload> cooldownFailure = checkReforgeCooldown(existing, now);
        if (cooldownFailure.isPresent()) {
            return cooldownFailure.get();
        }

        Optional<CostTotals> totals = computeCostTotals(recipe);
        if (totals.isEmpty()) {
            MagicalMod.LOGGER.warn("Forge submit from {} referenced an unknown modifier or temper definition",
                    player.getGameProfile().getName());
            return ForgeResultPayload.fail(ForgeError.BAD_PAYLOAD, 0);
        }
        int cost = ForgeRules.manaCost(recipe.grade(), totals.get().manaSum(), state.maxMana());
        if (state.mana() < cost) {
            return ForgeResultPayload.fail(ForgeError.NO_MANA, cost);
        }

        int quality = ForgeRules.quality(recipe.meanGlyphQuality(), totals.get().stabilitySum());
        if (ForgeRules.isMisfire(quality)) {
            return misfire(player, state, cost, quality);
        }
        return succeed(player, state, menu, weapon, recipe, existing, cost, quality, now);
    }

    // --- step 2: structure ------------------------------------------------------------------

    private static Optional<ForgeResultPayload> validateStructure(ForgeSubmitPayload payload) {
        List<ForgeSubmitPayload.Glyph> glyphs = payload.glyphs();
        if (glyphs.isEmpty() || glyphs.size() > ForgeRules.MAX_GLYPHS) {
            return Optional.of(ForgeResultPayload.fail(ForgeError.BAD_PAYLOAD, 0));
        }
        for (ForgeSubmitPayload.Glyph glyph : glyphs) {
            if (invalidGlyphStructure(glyph)) {
                return Optional.of(ForgeResultPayload.fail(ForgeError.BAD_PAYLOAD, 0));
            }
        }
        return Optional.empty();
    }

    private static boolean invalidGlyphStructure(ForgeSubmitPayload.Glyph glyph) {
        List<ForgeSubmitPayload.Stroke> strokes = glyph.strokes();
        if (strokes.isEmpty() || strokes.size() > ForgeRules.MAX_STROKES_PER_GLYPH) {
            return true;
        }
        for (ForgeSubmitPayload.Stroke stroke : strokes) {
            List<ForgeSubmitPayload.Point> points = stroke.points();
            if (points.isEmpty() || points.size() > ForgeRules.MAX_POINTS_PER_STROKE) {
                return true;
            }
            for (ForgeSubmitPayload.Point point : points) {
                if (!StrokeQuantizer.inRange(point.x()) || !StrokeQuantizer.inRange(point.y())) {
                    return true;
                }
            }
        }
        return false;
    }

    // --- step 3: weapon -----------------------------------------------------------------------

    private static Optional<ForgeResultPayload> validateWeapon(ItemStack weapon) {
        if (weapon.isEmpty()) {
            return Optional.of(ForgeResultPayload.fail(ForgeError.NO_WEAPON, 0));
        }
        if (!ForgeMaterials.isForgeable(weapon)) {
            return Optional.of(ForgeResultPayload.fail(ForgeError.NOT_FORGEABLE, 0));
        }
        return Optional.empty();
    }

    // --- step 4: recognition --------------------------------------------------------------

    private record RecognizeOutcome(List<RecognizedGlyph> glyphs, Optional<ForgeResultPayload> failure) {
        static RecognizeOutcome ok(List<RecognizedGlyph> glyphs) {
            return new RecognizeOutcome(glyphs, Optional.empty());
        }

        static RecognizeOutcome fail(ForgeError error, int argument) {
            return new RecognizeOutcome(List.of(), Optional.of(ForgeResultPayload.fail(error, argument)));
        }
    }

    private static RecognizeOutcome recognizeGlyphs(ForgeSubmitPayload payload) {
        List<List<List<GlyphPoint>>> strokesByGlyph = payload.toStrokes();
        GlyphRecognizer recognizer = ForgeGlyphLibrary.recognizer();
        List<RecognizedGlyph> recognized = new ArrayList<>(strokesByGlyph.size());
        for (int index = 0; index < strokesByGlyph.size(); index++) {
            RecognitionResult result = recognizer.recognize(strokesByGlyph.get(index));
            if (result.status() == RecognitionResult.Status.AMBIGUOUS) {
                return RecognizeOutcome.fail(ForgeError.AMBIGUOUS_GLYPH, index);
            }
            if (result.status() != RecognitionResult.Status.ACCEPTED) {
                return RecognizeOutcome.fail(ForgeError.UNRECOGNIZED_GLYPH, index);
            }
            GlyphTemplate best = result.best().orElseThrow();
            recognized.add(new RecognizedGlyph(best.id(), best.category(), GlyphQuality.toQuality(result.bestScore())));
        }
        return RecognizeOutcome.ok(List.copyOf(recognized));
    }

    // --- step 6: class gate -----------------------------------------------------------------

    private static Optional<ForgeResultPayload> checkClassGate(PlayerMagicState state, ForgeGrade grade) {
        if (!state.hasClass(MagicalClasses.BLACKSMITH)) {
            return Optional.of(ForgeResultPayload.fail(ForgeError.CLASS_REQUIRED, 0));
        }
        if (grade == ForgeGrade.DIVINE && !state.hasClass(MagicalClasses.DIVINESMITH)) {
            return Optional.of(ForgeResultPayload.fail(ForgeError.DIVINESMITH_REQUIRED, 0));
        }
        return Optional.empty();
    }

    // --- step 7: material and grade cap ------------------------------------------------------

    private static Optional<ForgeResultPayload> checkGradeCap(
            ForgeMaterial material, ForgeGrade grade, Optional<ForgedWeapon> existing) {
        Optional<ForgeError> error = ForgeRules.checkGrade(material, grade, existing.map(ForgedWeapon::grade));
        if (error.isEmpty()) {
            return Optional.empty();
        }
        int argument = error.get() == ForgeError.MATERIAL_CAP ? material.maxGrade().ordinal() : 0;
        return Optional.of(ForgeResultPayload.fail(error.get(), argument));
    }

    // --- step 8: reforge cooldown -----------------------------------------------------------

    private static Optional<ForgeResultPayload> checkReforgeCooldown(Optional<ForgedWeapon> existing, long now) {
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        ForgedWeapon weapon = existing.get();
        long remainingTicks = ForgeRules.remainingCooldown(weapon.forgedAtGameTime(), weapon.grade(), now);
        if (remainingTicks <= 0) {
            return Optional.empty();
        }
        int seconds = (int) Math.ceil(remainingTicks / 20.0);
        return Optional.of(ForgeResultPayload.fail(ForgeError.COOLDOWN, seconds));
    }

    // --- step 9: mana and stability ------------------------------------------------------------

    private record CostTotals(int manaSum, int stabilitySum) {
    }

    private static Optional<CostTotals> computeCostTotals(ForgeRecipe recipe) {
        int manaSum = 0;
        int stabilitySum = 0;
        for (String modifierId : recipe.modifiers()) {
            Optional<ModifierDefinition> definition = ForgeModifiers.get(ForgeIds.id(modifierId));
            if (definition.isEmpty()) {
                return Optional.empty();
            }
            manaSum += definition.get().manaDelta();
            stabilitySum += definition.get().stabilityDelta();
        }
        if (recipe.temper().isPresent()) {
            Optional<TemperDefinition> temper = ForgeTempers.get(ForgeIds.id(recipe.temper().get()));
            if (temper.isEmpty()) {
                return Optional.empty();
            }
            stabilitySum += temper.get().stabilityDelta();
        }
        return Optional.of(new CostTotals(manaSum, stabilitySum));
    }

    // --- step 10: misfire -------------------------------------------------------------------

    private static ForgeResultPayload misfire(ServerPlayer player, PlayerMagicState state, int cost, int quality) {
        state.spendMana(cost / 2);
        state.sync(player);
        player.serverLevel().playSound(
                null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.55f);
        return ForgeResultPayload.fail(ForgeError.MISFIRE, quality);
    }

    // --- step 11: success -------------------------------------------------------------------

    private static ForgeResultPayload succeed(
            ServerPlayer player, PlayerMagicState state, BlacksmithForgeMenu menu, ItemStack weapon,
            ForgeRecipe recipe, Optional<ForgedWeapon> existing, int cost, int quality, long now) {
        state.spendMana(cost);
        ForgedWeapon forged = new ForgedWeapon(
                ForgeIds.id(recipe.element()),
                recipe.grade(),
                recipe.temper().map(ForgeIds::id),
                recipe.forms().stream().map(ForgeIds::id).toList(),
                recipe.modifiers().stream().map(ForgeIds::id).toList(),
                quality,
                now);
        ForgedWeapons.write(weapon, forged);
        menu.weaponSlot().setChanged();
        menu.broadcastChanges();

        ResourceLocation classId = recipe.grade() == ForgeGrade.DIVINE
                ? MagicalClasses.DIVINESMITH
                : MagicalClasses.BLACKSMITH;
        state.addClassXp(classId, ForgeRules.classXp(recipe.grade(), existing.map(ForgedWeapon::grade)));
        state.sync(player);

        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS,
                0.85f, recipe.grade() == ForgeGrade.DIVINE ? 1.4f : 1.0f);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.6f, 1.2f);
        return ForgeResultPayload.ok(quality);
    }
}
