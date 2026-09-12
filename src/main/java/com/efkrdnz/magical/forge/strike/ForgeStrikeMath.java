package com.efkrdnz.magical.forge.strike;

import java.util.Arrays;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.chain.ForgeGrade;

/**
 * The pure arithmetic of a forged-weapon strike: damage, hit-shape, recovery, combo window and
 * modifier procs. Every method is total and side-effect free, so client and server agree.
 */
public final class ForgeStrikeMath {

    public static final float MAX_REACH = 5.5f;
    public static final float SPIN_RADIUS_CAP = 3.5f;
    public static final float HEAVY_SIZE_SCALE = 1.3f;
    public static final int HEAVY_RECOVERY = 8;
    public static final float FINISHER_SCALE = 1.35f;
    public static final float CRIT_MULTIPLIER = 1.5f;
    public static final int MIN_RECOVERY = 5;
    public static final int BASE_WINDOW = 14;
    public static final int MAX_WINDOW_BONUS = 24;
    public static final int BASE_CHARGE_THRESHOLD = 10;
    public static final int MAX_CHARGE_TICKS = 30;
    public static final float ECHO_SCALE = 0.40f;
    public static final int ECHO_DELAY_TICKS = 6;
    public static final float PIERCE_FRACTION = 0.20f;
    public static final float LEECH_FRACTION = 0.08f;
    public static final float LEECH_CAP = 2.0f;
    public static final int BRAND_MAX_STACKS = 3;
    public static final float BRAND_PER_STACK = 0.15f;
    public static final float SHATTER_BONUS = 1.30f;
    public static final float FINISHER_BONUS_CAP = 1.8f;
    public static final float MAX_PROC = 0.98f;
    public static final int RESET_AFTER_FINISHER = 4;
    public static final int FLURRY_PULSE_CAP_WITH_ECHO = 5;
    public static final float GALE_STEP_REACH_BONUS = 1.0f;

    private ForgeStrikeMath() {
    }

    /**
     * The value a rune is worth at {@code stacks} copies, from a three-rung ladder.
     *
     * <p>Every ladder here bends the same way: the second copy is worth clearly less than the
     * first and the third less again. A rune that scaled linearly would make stacking the only
     * sensible chain; one that barely scaled would make it pointless. Zero copies is zero, and
     * anything past the third rung holds there.
     */
    private static float rung(int stacks, float one, float two, float three) {
        if (stacks <= 0) {
            return 0f;
        }
        return stacks == 1 ? one : stacks == 2 ? two : three;
    }

    private static int rung(int stacks, int one, int two, int three) {
        if (stacks <= 0) {
            return 0;
        }
        return stacks == 1 ? one : stacks == 2 ? two : three;
    }

    /** Extra blocks of reach the REACH rune buys. A WAVE is thrown twice as far per copy. */
    public static float reachBonus(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.REACH), 1.0f, 1.6f, 2.0f);
    }

    /** The share of a hit PIERCE deals again, ignoring armour. */
    public static float pierceFraction(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.PIERCE), 0.20f, 0.32f, 0.40f);
    }

    /** The share of damage dealt that LEECH returns as health. */
    public static float leechFraction(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.LEECH), 0.08f, 0.13f, 0.16f);
    }

    /** Most health one press may leech, however many bodies it touched. */
    public static float leechCap(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.LEECH), 2.0f, 3.0f, 3.5f);
    }

    /** How far SHATTER multiplies a finisher against a target already carrying a status. */
    public static float shatterBonus(ModifierStack mods) {
        int stacks = mods.stacks(ForgeModifierKind.SHATTER);
        return stacks <= 0 ? 1f : rung(stacks, 1.30f, 1.45f, 1.55f);
    }

    /**
     * The share of the blow CHORUS repeats for each <em>other</em> body this press has already
     * opened, and the most bodies it will count.
     *
     * <p>The cap is the whole reason it is safe on a spin. Without it a 360-degree form into a mob
     * farm multiplies by the crowd, and the rune stops being "hits harder in a fight" and becomes
     * "delete everything standing together".
     */
    public static float chorusFraction(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.CHORUS), 0.35f, 0.50f, 0.50f);
    }

    public static int chorusMaxEchoes(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.CHORUS), 3, 5, 5);
    }

    /** Barrier TITHE spends on a press, and the damage multiplier that buys. */
    public static int titheCost(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.TITHE), 4, 7, 9);
    }

    public static float titheBonus(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.TITHE), 1.50f, 1.75f, 1.90f);
    }

    /**
     * The share of a strike's damage CARRY banks into the next link of the chain, and the ceiling
     * on what the bank may add.
     *
     * <p>The cap breaks the feedback loop: each strike banks off the damage it dealt, which already
     * includes the last bank, so an uncapped CARRY compounds without limit over a long chain.
     */
    public static float carryFraction(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.CARRY), 0.25f, 0.35f, 0.40f);
    }

    public static float carryCap(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.CARRY), 0.60f, 0.85f, 1.00f);
    }

    /** Most brand stacks a target may carry. */
    public static int brandMaxStacks(ModifierStack mods) {
        int stacks = mods.stacks(ForgeModifierKind.BRAND);
        return stacks <= 0 ? 0 : rung(stacks, 3, 4, 5);
    }

    /**
     * What each form of a forked press is worth, as a share of firing alone.
     *
     * <p>Without this a fork is pure profit. A Divine heavy slam is about 84 damage; four of them
     * on one press is 336, which kills anything in the game outright and makes every other chain a
     * mistake. Dividing by {@code 1 + 0.25(width - 1)} leaves a four-way fork worth roughly 2.3
     * single slams - clearly strong, worth the four operator slots and three extra glyphs, and not
     * an instant win.
     */
    public static float forkScale(int width) {
        return width <= 1 ? 1f : 1f / (1f + 0.25f * (width - 1));
    }

    /** Radians per tick a SEEKING projectile bends toward the nearest body ahead of it. */
    public static double seekingTurnRadians(ModifierStack mods) {
        return rung(mods.stacks(ForgeModifierKind.SEEKING), 0.12f, 0.18f, 0.22f);
    }

    public static float qualityScale(int quality) {
        return 0.75f + 0.005f * quality;
    }

    public static float baseHit(float weaponAttack, ForgeGrade grade, int quality) {
        return weaponAttack + grade.baseDamage() * qualityScale(quality);
    }

    public static float strikeDamage(float baseHit, FormStats form, boolean heavy, float chargeFraction,
            boolean finisher) {
        float scale = heavy ? form.heavyScale() * (1 + 0.25f * chargeFraction) : form.lightScale();
        return baseHit * scale * (finisher ? FINISHER_SCALE : 1f);
    }

    public static float primaryTargetDamage(float hit, float weaponAttack) {
        return Math.max(hit - weaponAttack, 0.25f * hit);
    }

    public static float finisherBonus(boolean shatterApplies, int brandStacks) {
        return finisherBonus(shatterApplies, brandStacks, ModifierStack.EMPTY);
    }

    /**
     * The finisher multiplier, reading SHATTER and BRAND off the stack the strike carries.
     *
     * <p>The two multiply, and the product is still capped: stacking both to the top would
     * otherwise turn one press into a bigger multiplier than any grade was priced for.
     */
    public static float finisherBonus(boolean shatterApplies, int brandStacks, ModifierStack mods) {
        int cap = mods.has(ForgeModifierKind.BRAND) ? brandMaxStacks(mods) : BRAND_MAX_STACKS;
        int stacks = clamp(brandStacks, 0, cap);
        float shatter = shatterApplies ? shatterBonusOrDefault(mods) : 1f;
        return Math.min(FINISHER_BONUS_CAP, shatter * (1 + BRAND_PER_STACK * stacks));
    }

    private static float shatterBonusOrDefault(ModifierStack mods) {
        return mods.has(ForgeModifierKind.SHATTER) ? shatterBonus(mods) : SHATTER_BONUS;
    }

    public static float reach(FormStats form, TemperStats temper, WeaponClass weaponClass, ModifierStack mods) {
        return reach(form, temper, weaponClass, mods, 0f);
    }

    /**
     * The strike's reach, with {@code artBonus} folded in before the family's cap so an Art's extra
     * reach competes with the cap exactly as the REACH rune does rather than stepping over it.
     */
    public static float reach(FormStats form, TemperStats temper, WeaponClass weaponClass, ModifierStack mods,
            float artBonus) {
        float bonus = reachBonus(mods);
        float value;
        if (form.family() == FormFamily.WAVE) {
            // A thrown crescent travels, so the rune buys it twice the ground a melee arc gets.
            value = form.reach() + bonus * 2f + artBonus;
        } else if (form.family() == FormFamily.SPIN) {
            value = Math.min(SPIN_RADIUS_CAP, form.reach() + temper.reachDelta() + bonus + artBonus);
        } else {
            value = Math.min(MAX_REACH,
                    form.reach() + temper.reachDelta() + weaponClass.reachDelta() + bonus + artBonus);
        }
        return Math.max(1.0f, value);
    }

    /**
     * The reach an Art adds to the strike it rides on. Only GALE's Gale Step has one: the table
     * gives its light flurry "+1 reach" alongside the half-block dash per pulse.
     *
     * <p>It has to be decided here, when the press resolves, rather than by the Art itself. An Art
     * runs on impact, and by then the strike's reach has already chosen what it could touch -
     * widening it afterwards would widen nothing. A heavy flurry does not fire Gale Step
     * ({@code ArtTrigger.LIGHT}) and so gets no extra reach either.</p>
     */
    public static float artReachBonus(ForgeElementKind element, FormFamily family, boolean heavy) {
        return element == ForgeElementKind.GALE && family == FormFamily.FLURRY && !heavy
                ? GALE_STEP_REACH_BONUS
                : 0f;
    }

    public static float halfWidth(FormStats form, TemperStats temper, boolean heavy, ModifierStack mods) {
        // Width rides the same ladder as reach, at a third of the size.
        return form.halfWidth() * temper.widthScale() * (heavy ? HEAVY_SIZE_SCALE : 1f)
                + reachBonus(mods) * 0.3f;
    }

    public static float arcDegrees(FormStats form, boolean heavy) {
        return Math.min(360f, form.arcDegrees() * (heavy ? HEAVY_SIZE_SCALE : 1f));
    }

    public static float knockback(FormStats form, TemperStats temper, WeaponClass weaponClass) {
        return form.knockback() * temper.knockbackScale() * weaponClass.knockbackScale();
    }

    public static float speed(FormStats form, TemperStats temper) {
        return form.speed() * temper.speedScale();
    }

    public static int recovery(FormStats form, TemperStats temper, WeaponClass weaponClass, boolean heavy,
            ModifierStack mods) {
        int haste = rung(mods.stacks(ForgeModifierKind.HASTE), 3, 5, 6);
        int raw = form.recoveryTicks() + (heavy ? HEAVY_RECOVERY : 0) + weaponClass.recoveryDelta()
                + temper.recoveryDelta() - haste;
        return Math.max(MIN_RECOVERY, raw);
    }

    public static long windowEnd(long strikeTick, int recovery, TemperStats temper, ModifierStack mods) {
        int haste = rung(mods.stacks(ForgeModifierKind.HASTE), 4, 7, 9);
        int bonus = Math.min(MAX_WINDOW_BONUS, temper.comboWindowDelta() + haste);
        return strikeTick + recovery + BASE_WINDOW + bonus;
    }

    public static int chargeThreshold(TemperStats temper) {
        return Math.max(1, BASE_CHARGE_THRESHOLD + temper.chargeThresholdDelta());
    }

    public static float chargeFraction(int heldTicks, int threshold) {
        int span = MAX_CHARGE_TICKS - threshold;
        if (span <= 0) {
            // threshold at or above the charge cap: nothing to interpolate over, so the charge is
            // either not yet reached (0) or already saturated (1). Keeps this method total instead
            // of dividing by zero/negative and silently poisoning strikeDamage's heavy branch with
            // NaN/Infinity.
            return heldTicks >= threshold ? 1f : 0f;
        }
        return clamp((heldTicks - threshold) / (float) span, 0f, 1f);
    }

    /**
     * Whether a guard armed to run until {@code untilTick} is still soaking damage at {@code now}.
     * Mirrors the same {@code now < untilTick} test the guard's own active-reduction check uses, so
     * a caller deciding whether an interruption arrived early (guard still running) or late (guard
     * already ran its full course) always agrees with what the guard itself was doing at that tick.
     */
    public static boolean guardStillRunning(long untilTick, long now) {
        return now < untilTick;
    }

    public static float procChance(float procBase, ForgeGrade grade, ModifierStack mods) {
        float binding = rung(mods.stacks(ForgeModifierKind.BINDING), 0.25f, 0.40f, 0.50f);
        return Math.min(MAX_PROC, procBase + 0.05f * grade.ordinal() + binding);
    }

    public static int[] flurryPulseTicks(boolean heavy) {
        return heavy ? new int[] {0, 2, 4, 6, 8} : new int[] {0, 3, 6};
    }

    /**
     * The pulses one flurry strike actually gets. An echo is a second whole flurry six ticks behind
     * the first, so left alone an ECHO weapon would land three pulses plus three more - six cuts off
     * one press. The echo's share is trimmed so the pair never exceeds
     * {@link #FLURRY_PULSE_CAP_WITH_ECHO}; a heavy flurry never gets an echo at all
     * ({@link #echoAllowed}), so its five pulses stand.
     */
    public static int[] flurryPulseTicks(boolean heavy, boolean echo) {
        int[] pulses = flurryPulseTicks(heavy);
        if (!echo) {
            return pulses;
        }
        int budget = Math.max(0, FLURRY_PULSE_CAP_WITH_ECHO - pulses.length);
        return Arrays.copyOf(pulses, Math.min(pulses.length, budget));
    }

    public static boolean echoAllowed(FormFamily family, boolean heavy) {
        return !(family == FormFamily.FLURRY && heavy);
    }

    public static float leechHeal(float dealt, float healedSoFarThisPress) {
        return Math.min(LEECH_FRACTION * dealt, Math.max(0f, LEECH_CAP - healedSoFarThisPress));
    }

    /** Leech for a strike carrying {@code mods}: a bigger share, into a bigger pool, per copy. */
    public static float leechHeal(float dealt, float healedSoFarThisPress, ModifierStack mods) {
        return Math.min(leechFraction(mods) * dealt,
                Math.max(0f, leechCap(mods) - healedSoFarThisPress));
    }

    public static StrikeSpec resolve(FormStats form, TemperStats temper, WeaponClass weaponClass, ModifierStack mods,
            ForgeGrade grade, int quality, float weaponAttack, boolean heavy, float chargeFraction,
            boolean finisher, int comboIndex, ForgeElementKind element) {
        float hit = baseHit(weaponAttack, grade, quality);
        float damage = strikeDamage(hit, form, heavy, chargeFraction, finisher);
        float artBonus = artReachBonus(element, form.family(), heavy);
        return new StrikeSpec(form.family(), heavy, finisher, comboIndex, damage,
                reach(form, temper, weaponClass, mods, artBonus), halfWidth(form, temper, heavy, mods),
                arcDegrees(form, heavy), speed(form, temper), form.lifeTicks(), knockback(form, temper, weaponClass),
                temper.critChance(), recovery(form, temper, weaponClass, heavy, mods), mods, chargeFraction);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
