package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPrice;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodDamageTypes;
import com.efkrdnz.magical.magic.blood.SacrificeBudget;
import java.util.Random;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What the sixteen prices of a Blood Sacrifice actually charge.
 *
 * <p>Every number that can be made worse runs through {@link SacrificeBudget}, which is where a
 * Hellbroker's interest is applied and where each entry's own ceiling stops it. Two forms, and the
 * difference matters: a chance or a count is a magnitude and scales whole, while a multiplier
 * amplifies its distance from one, so a blow meant to land a fifth harder never becomes one that
 * lands twice as hard.
 *
 * <p>Split from {@link SacrificeBoons} because between them they are more than a file's worth.
 * Public where that one is not, and only for {@link #fizzles}: the cast pipeline has to ask
 * before it builds a context, and that pipeline lives in another package.
 */
public final class SacrificeCurses {

    static final float FIZZLE = 0.15F;
    static final float FIZZLE_CAP = 0.3375F;
    static final float GLASS_BONES = 1.20F;
    static final float GLASS_BONES_CAP = 1.45F;
    static final float BRITTLE = 1.35F;
    static final float BRITTLE_CAP = 1.80F;
    static final float DROUGHT = 1.50F;
    static final float DROUGHT_CAP = 2.10F;
    static final float CHAINS = 1.50F;
    static final float CHAINS_CAP = 2.10F;
    static final float OPEN_WOUND = 0.50F;
    static final float OPEN_WOUND_CAP = 0.15F;
    static final float DULLED = 1.60F;
    static final float DULLED_CAP = 2.50F;
    static final float MISERY = 0.15F;
    static final float MISERY_CAP = 0.3375F;
    static final float LIFE_TAX = 1.0F;
    static final float LIFE_TAX_CAP = 2.25F;
    static final float HEMORRHAGE_BLOOD = 4.0F;
    static final float HEMORRHAGE_BLOOD_CAP = 9.0F;
    /** What a dry Vessel bleeds instead, per half second. */
    static final float HEMORRHAGE_HEALTH = 0.5F;
    static final float HEMORRHAGE_HEALTH_CAP = 1.1F;
    static final double THIN_SKIN = -6.0D;
    static final double THIN_SKIN_FLOOR = -16.0D;
    static final double SLOW_BLOOD = -0.20D;
    static final double SLOW_BLOOD_FLOOR = -0.45D;
    static final double LEADEN_JUMP = -0.40D;
    static final double LEADEN_JUMP_FLOOR = -0.70D;
    static final double LEADEN_FALL = 1.0D;
    static final double LEADEN_FALL_CEILING = 2.0D;

    /** Blood magic never finishes you off, prices included: a tax stops at the last point of health. */
    private static final float MIN_HEALTH = 1.0F;

    private static final ResourceLocation THIN_SKIN_MODIFIER = modifier("thin_skin");
    private static final ResourceLocation SLOW_SPEED_MODIFIER = modifier("slow_blood_speed");
    private static final ResourceLocation SLOW_SWING_MODIFIER = modifier("slow_blood_swing");
    private static final ResourceLocation LEADEN_JUMP_MODIFIER = modifier("leaden_step_jump");
    private static final ResourceLocation LEADEN_FALL_MODIFIER = modifier("leaden_step_fall");

    private SacrificeCurses() {
    }

    private static ResourceLocation modifier(String name) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sacrifice/" + name);
    }

    /** True while the pact is brokered, which is the only thing that makes prices feed each other. */
    private static boolean broker(PlayerMagicState state) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.HELLBROKER.id());
    }

    private static float worse(PlayerMagicState state, float base, float clamp) {
        return SacrificeBudget.amplifyMultiplier(base, clamp, state.activeRitualPriceCount(), broker(state));
    }

    private static float more(PlayerMagicState state, float base, float clamp) {
        return SacrificeBudget.amplify(base, clamp, state.activeRitualPriceCount(), broker(state));
    }

    // ---- the cast pipeline ----------------------------------------------------------------------

    /**
     * The chance a cast dies after paying for itself. Public because the cast pipeline asks.
     *
     * <p>{@code hellbroker} is passed rather than read so a test can pin both halves of the curve
     * without building a player who owns the passive.
     */
    public static float fizzleChance(PlayerMagicState state, boolean hellbroker) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.SPELL_FIZZLE.id())) {
            return 0.0F;
        }
        return SacrificeBudget.amplify(FIZZLE, FIZZLE_CAP, state.activeRitualPriceCount(), hellbroker);
    }

    /**
     * Whether this cast dies in the caster's throat.
     *
     * <p>Seeded from the cast's own seed rather than a fresh {@link Random}, so a game test can
     * force either outcome and a lag spike cannot reroll it.
     */
    public static boolean fizzles(ServerPlayer player, PlayerMagicState state,
            MagicSkillDefinition definition, long seed) {
        if (MagicContent.BLOOD_SACRIFICE.id().equals(definition.id()) || MagicPrice.waived(player)) {
            // The ritual is exempt. One that failed to open its own screen after taking a hundred
            // blood would be indistinguishable from a crash.
            return false;
        }
        float chance = fizzleChance(state, broker(state));
        return chance > 0.0F && new Random(seed).nextFloat() < chance;
    }

    /** Mana Drought and Binding Chains, folded into the cast about to resolve. */
    static void adjustCast(PlayerMagicState state, CastAdjustment out) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.MANA_DROUGHT.id())) {
            out.mana *= worse(state, DROUGHT, DROUGHT_CAP);
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BINDING_CHAINS.id())) {
            out.cooldown *= worse(state, CHAINS, CHAINS_CAP);
        }
    }

    /** Life Tax: a point of health per cast, in flesh, after the cast has already been paid for. */
    static void afterCast(ServerPlayer player, PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.LIFE_TAX.id())) {
            return;
        }
        bleed(player, more(state, LIFE_TAX, LIFE_TAX_CAP));
    }

    // ---- damage ---------------------------------------------------------------------------------

    /** Damage about to land on the player, sharpened by Glass Bones and by a brittle barrier. */
    static float incoming(PlayerMagicState state, float amount) {
        float scaled = amount;
        if (ClassPassiveEffects.on(state, MagicPassiveContent.GLASS_BONES.id())) {
            scaled *= worse(state, GLASS_BONES, GLASS_BONES_CAP);
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BRITTLE_BARRIER.id()) && state.barrier() > 0) {
            // Expressed as a bigger blow rather than a weaker shield, because the barrier soaks a
            // flat number: a hit that counts for a third more drains a third more of it, and more
            // leaks through when it runs out, which is what a brittle shield does.
            scaled *= worse(state, BRITTLE, BRITTLE_CAP);
        }
        return scaled;
    }

    /** Echoing Misery: a share of what the caster just dealt comes back through them. */
    static void recoil(ServerPlayer player, PlayerMagicState state, float dealt) {
        if (dealt <= 0.0F || !ClassPassiveEffects.on(state, MagicPassiveContent.ECHOING_MISERY.id())) {
            return;
        }
        bleed(player, dealt * more(state, MISERY, MISERY_CAP));
    }

    /** Open Wound, on everything that would put health back. */
    static float heal(PlayerMagicState state, float amount) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.OPEN_WOUND.id())
                ? amount * worse(state, OPEN_WOUND, OPEN_WOUND_CAP)
                : amount;
    }

    /** Dulled Senses, on anything the world is trying to hold the player with. */
    static float statusScale(PlayerMagicState state) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.DULLED_SENSES.id())
                ? worse(state, DULLED, DULLED_CAP)
                : 1.0F;
    }

    /** True when a Weeping Vessel is refusing the kill the player just made. */
    static boolean refusesTheKill(PlayerMagicState state) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.WEEPING_VESSEL.id());
    }

    // ---- the slow tick --------------------------------------------------------------------------

    /** Hemorrhage: the Vessel leaks, and once it is dry the leak comes out of the body instead. */
    static void hemorrhage(ServerPlayer player, PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.HEMORRHAGE.id())) {
            return;
        }
        if (state.bloodVessel() > 0) {
            state.addBloodVessel(-Math.round(more(state, HEMORRHAGE_BLOOD, HEMORRHAGE_BLOOD_CAP)));
            return;
        }
        bleed(player, more(state, HEMORRHAGE_HEALTH, HEMORRHAGE_HEALTH_CAP));
    }

    /**
     * The attribute-driven prices, re-applied every slow tick for the same reason the boons are.
     *
     * <p>Their amounts are amplified too, so a Hellbroker's second price makes the player slower as
     * well as everything else - which is the whole deal the broker is offering.
     */
    static void attributes(ServerPlayer player, PlayerMagicState state) {
        SacrificeBoons.applyOrClear(player, Attributes.ARMOR, THIN_SKIN_MODIFIER,
                penalty(state, THIN_SKIN, THIN_SKIN_FLOOR), AttributeModifier.Operation.ADD_VALUE,
                ClassPassiveEffects.on(state, MagicPassiveContent.THIN_SKIN.id()));

        boolean slow = ClassPassiveEffects.on(state, MagicPassiveContent.SLOW_BLOOD.id());
        double slowAmount = penalty(state, SLOW_BLOOD, SLOW_BLOOD_FLOOR);
        SacrificeBoons.applyOrClear(player, Attributes.MOVEMENT_SPEED, SLOW_SPEED_MODIFIER, slowAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, slow);
        SacrificeBoons.applyOrClear(player, Attributes.ATTACK_SPEED, SLOW_SWING_MODIFIER, slowAmount,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, slow);

        boolean leaden = ClassPassiveEffects.on(state, MagicPassiveContent.LEADEN_STEP.id());
        SacrificeBoons.applyOrClear(player, Attributes.JUMP_STRENGTH, LEADEN_JUMP_MODIFIER,
                penalty(state, LEADEN_JUMP, LEADEN_JUMP_FLOOR),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, leaden);
        SacrificeBoons.applyOrClear(player, Attributes.FALL_DAMAGE_MULTIPLIER, LEADEN_FALL_MODIFIER,
                penalty(state, LEADEN_FALL, LEADEN_FALL_CEILING),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, leaden);
    }

    /**
     * An attribute delta, amplified the way a penalty should be.
     *
     * <p>These are already distances from zero rather than multipliers around one, so the magnitude
     * form is the right one; the sign is carried through so a negative delta only gets more so.
     */
    private static double penalty(PlayerMagicState state, double base, double clamp) {
        float scaled = SacrificeBudget.amplify((float) Math.abs(base), (float) Math.abs(clamp),
                state.activeRitualPriceCount(), broker(state));
        return base < 0.0D ? -scaled : scaled;
    }

    /**
     * Charges a price against the body in true damage, never past the last point of health.
     *
     * <p>Blood magic does not finish you off, and a price that could would make every cast a coin
     * flip on dying to your own button.
     */
    private static void bleed(ServerPlayer player, float health) {
        if (health <= 0.0F || MagicPrice.waived(player)) {
            return;
        }
        float payable = Math.min(health, Math.max(0.0F, player.getHealth() - MIN_HEALTH));
        if (payable <= 0.0F) {
            return;
        }
        player.hurt(BloodDamageTypes.price(player), payable);
    }
}
