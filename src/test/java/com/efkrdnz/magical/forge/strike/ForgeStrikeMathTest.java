package com.efkrdnz.magical.forge.strike;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ModifierStack;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.WeaponClass;
import com.efkrdnz.magical.forge.chain.ForgeGrade;

class ForgeStrikeMathTest {

    private static final float DELTA = 1e-4f;

    // Mirrors ForgeForms.SLASH.
    private static final FormStats SLASH =
            new FormStats(FormFamily.SLASH, 1.00f, 1.70f, 3.5f, 1.6f, 150f, 0f, 4, 0.35f, 8);

    // Mirrors ForgeForms.THRUST.
    private static final FormStats THRUST =
            new FormStats(FormFamily.THRUST, 1.15f, 1.90f, 4.5f, 0.6f, 0f, 0f, 6, 0.45f, 9);

    // Mirrors ForgeForms.SPIN.
    private static final FormStats SPIN =
            new FormStats(FormFamily.SPIN, 0.90f, 1.50f, 2.75f, 2.75f, 360f, 0f, 5, 0.50f, 14);

    // Mirrors ForgeForms.SLAM.
    private static final FormStats SLAM =
            new FormStats(FormFamily.SLAM, 1.40f, 2.40f, 2.5f, 2.5f, 360f, 0f, 3, 0.60f, 16);

    // Mirrors ForgeForms.FLURRY.
    private static final FormStats FLURRY =
            new FormStats(FormFamily.FLURRY, 0.45f, 0.40f, 3.0f, 0.7f, 40f, 0f, 6, 0.10f, 12);

    // Mirrors ForgeForms.WAVE.
    private static final FormStats WAVE =
            new FormStats(FormFamily.WAVE, 1.00f, 1.60f, 12f, 1.4f, 140f, 1.10f, 20, 0.30f, 10);

    // Mirrors ForgeTempers.KEEN.
    private static final TemperStats KEEN = new TemperStats(1.0f, 0.85f, 1.15f, 1.0f, 0.30f, 0, 0, 0);

    // Mirrors ForgeTempers.HEAVY.
    private static final TemperStats HEAVY = new TemperStats(0f, 1.35f, 0.85f, 1.7f, 0f, 4, 3, -4);

    // Mirrors ForgeTempers.SWIFT.
    private static final TemperStats SWIFT = new TemperStats(-0.5f, 0.90f, 1.20f, 0.8f, 0f, 6, -3, 0);

    private static ModifierStack mods(ForgeModifierKind... kinds) {
        return ModifierStack.of(Set.of(kinds));
    }

    @Test
    void qualityScaleInterpolatesFromSeventyFiveToOneTwentyFive() {
        assertEquals(0.75f, ForgeStrikeMath.qualityScale(0), DELTA);
        assertEquals(1.05f, ForgeStrikeMath.qualityScale(60), DELTA);
        assertEquals(1.25f, ForgeStrikeMath.qualityScale(100), DELTA);
    }

    @Test
    void baseHitAddsGradeDamageScaledByQuality() {
        assertEquals(10.1f, ForgeStrikeMath.baseHit(8f, ForgeGrade.CRUDE, 60), DELTA);
    }

    @Test
    void strikeDamageAppliesLightHeavyChargeAndFinisherScales() {
        FormStats slashLike = new FormStats(FormFamily.SLASH, 1.0f, 1.7f, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(10f, ForgeStrikeMath.strikeDamage(10f, slashLike, false, 0f, false), DELTA);
        assertEquals(17f, ForgeStrikeMath.strikeDamage(10f, slashLike, true, 0f, false), DELTA);
        assertEquals(21.25f, ForgeStrikeMath.strikeDamage(10f, slashLike, true, 1.0f, false), DELTA);
        assertEquals(13.5f, ForgeStrikeMath.strikeDamage(10f, slashLike, false, 0f, true), DELTA);
    }

    @Test
    void primaryTargetDamageIsFlooredAtAQuarterOfHit() {
        assertEquals(2.525f, ForgeStrikeMath.primaryTargetDamage(10.1f, 8f), DELTA);
        assertEquals(22f, ForgeStrikeMath.primaryTargetDamage(30f, 8f), DELTA);
    }

    @Test
    void finisherBonusAppliesShatterAndBrandCappedAtTheCap() {
        assertEquals(1f, ForgeStrikeMath.finisherBonus(false, 0), DELTA);
        assertEquals(1.3f, ForgeStrikeMath.finisherBonus(true, 0), DELTA);
        assertEquals(1.45f, ForgeStrikeMath.finisherBonus(false, 3), DELTA);
        assertEquals(1.8f, ForgeStrikeMath.finisherBonus(true, 3), DELTA);
        assertEquals(1.45f, ForgeStrikeMath.finisherBonus(false, 9), DELTA);
    }

    @Test
    void reachForSlashCombinesTemperAndWeaponClassAndCapsAtMaxReach() {
        assertEquals(4.2f, ForgeStrikeMath.reach(SLASH, KEEN, WeaponClass.AXE, ModifierStack.EMPTY), DELTA);
        assertEquals(5.2f, ForgeStrikeMath.reach(SLASH, KEEN, WeaponClass.AXE, mods(ForgeModifierKind.REACH)),
                DELTA);
    }

    @Test
    void reachForThrustCapsAtMaxReach() {
        assertEquals(5.5f, ForgeStrikeMath.reach(THRUST, KEEN, WeaponClass.SWORD, mods(ForgeModifierKind.REACH)),
                DELTA);
    }

    @Test
    void reachForWaveIgnoresTemperAndClassAndHasNoCap() {
        assertEquals(12f, ForgeStrikeMath.reach(WAVE, KEEN, WeaponClass.SWORD, ModifierStack.EMPTY), DELTA);
        assertEquals(14f, ForgeStrikeMath.reach(WAVE, KEEN, WeaponClass.SWORD, mods(ForgeModifierKind.REACH)),
                DELTA);
    }

    @Test
    void reachForSpinCapsAtTheSpinRadiusCap() {
        assertEquals(3.5f, ForgeStrikeMath.reach(SPIN, KEEN, WeaponClass.SWORD, mods(ForgeModifierKind.REACH)),
                DELTA);
    }

    @Test
    void halfWidthAppliesWidthScaleHeavySizeAndReachBonus() {
        assertEquals(2.16f, ForgeStrikeMath.halfWidth(SLASH, HEAVY, false, ModifierStack.EMPTY), DELTA);
        assertEquals(2.808f, ForgeStrikeMath.halfWidth(SLASH, HEAVY, true, ModifierStack.EMPTY), DELTA);
        assertEquals(3.108f, ForgeStrikeMath.halfWidth(SLASH, HEAVY, true, mods(ForgeModifierKind.REACH)), DELTA);
    }

    @Test
    void arcDegreesScalesWithHeavyAndCapsAtThreeSixty() {
        assertEquals(360f, ForgeStrikeMath.arcDegrees(SLAM, true), DELTA);
        assertEquals(195f, ForgeStrikeMath.arcDegrees(SLASH, true), DELTA);
    }

    @Test
    void knockbackMultipliesFormTemperAndWeaponClass() {
        assertEquals(0.74375f, ForgeStrikeMath.knockback(SLASH, HEAVY, WeaponClass.AXE), DELTA);
    }

    @Test
    void speedMultipliesFormSpeedByTemperSpeedScale() {
        FormStats fastForm = new FormStats(FormFamily.WAVE, 0, 0, 0, 0, 0, 2.0f, 0, 0, 0);
        TemperStats fastTemper = new TemperStats(0, 1, 1.5f, 1, 0, 0, 0, 0);
        assertEquals(3.0f, ForgeStrikeMath.speed(fastForm, fastTemper), DELTA);
    }

    @Test
    void recoveryCombinesDeltasHeavyAndHasteAndFloorsAtMinimum() {
        assertEquals(13, ForgeStrikeMath.recovery(SLASH, SWIFT, WeaponClass.AXE, true,
                mods(ForgeModifierKind.HASTE)));
        assertEquals(5, ForgeStrikeMath.recovery(SLASH, SWIFT, WeaponClass.SWORD, false,
                mods(ForgeModifierKind.HASTE)));
    }

    @Test
    void windowEndAddsBaseWindowAndCappedComboBonus() {
        assertEquals(132L, ForgeStrikeMath.windowEnd(100L, 8, SWIFT, mods(ForgeModifierKind.HASTE)));
        TemperStats bigWindowTemper = new TemperStats(0, 1, 1, 1, 0, 30, 0, 0);
        assertEquals(100L + 8 + ForgeStrikeMath.BASE_WINDOW + ForgeStrikeMath.MAX_WINDOW_BONUS,
                ForgeStrikeMath.windowEnd(100L, 8, bigWindowTemper, mods(ForgeModifierKind.HASTE)));
    }

    @Test
    void chargeThresholdFloorsAtOneAndAppliesTemperDelta() {
        assertEquals(6, ForgeStrikeMath.chargeThreshold(HEAVY));
        assertEquals(10, ForgeStrikeMath.chargeThreshold(TemperStats.NONE));
    }

    @Test
    void chargeFractionClampsBetweenZeroAndOne() {
        assertEquals(0f, ForgeStrikeMath.chargeFraction(10, 10), DELTA);
        assertEquals(0.5f, ForgeStrikeMath.chargeFraction(20, 10), DELTA);
        assertEquals(1f, ForgeStrikeMath.chargeFraction(40, 10), DELTA);
        assertEquals(0f, ForgeStrikeMath.chargeFraction(6, 6), DELTA);
    }

    @Test
    void chargeFractionIsTotalWhenThresholdReachesOrExceedsTheChargeCap() {
        // threshold == MAX_CHARGE_TICKS: span is zero, must not divide by zero / return NaN.
        assertEquals(1f, ForgeStrikeMath.chargeFraction(30, 30), DELTA);
        assertEquals(0f, ForgeStrikeMath.chargeFraction(29, 30), DELTA);
        // threshold above MAX_CHARGE_TICKS: span is negative, still must not divide by a
        // non-positive number or return NaN.
        assertEquals(1f, ForgeStrikeMath.chargeFraction(35, 35), DELTA);
        assertEquals(0f, ForgeStrikeMath.chargeFraction(34, 35), DELTA);
    }

    @Test
    void procChanceScalesWithGradeAndBindingAndCapsAtMaxProc() {
        assertEquals(0.60f, ForgeStrikeMath.procChance(0.60f, ForgeGrade.CRUDE, ModifierStack.EMPTY), DELTA);
        assertEquals(0.98f, ForgeStrikeMath.procChance(0.60f, ForgeGrade.DIVINE, mods(ForgeModifierKind.BINDING)),
                DELTA);
        assertEquals(0.65f, ForgeStrikeMath.procChance(0.50f, ForgeGrade.MASTER, ModifierStack.EMPTY), DELTA);
    }

    @Test
    void flurryPulseTicksDependOnHeavy() {
        assertArrayEquals(new int[] {0, 2, 4, 6, 8}, ForgeStrikeMath.flurryPulseTicks(true));
        assertArrayEquals(new int[] {0, 3, 6}, ForgeStrikeMath.flurryPulseTicks(false));
    }

    /**
     * ECHO plus FLURRY: the press and its echo together never land more than five cuts. A light
     * flurry spends three, so its echo is trimmed to the remaining two.
     */
    @Test
    void anEchoedFlurryIsTrimmedSoThePairNeverExceedsFivePulses() {
        int[] press = ForgeStrikeMath.flurryPulseTicks(false, false);
        int[] echo = ForgeStrikeMath.flurryPulseTicks(false, true);

        assertArrayEquals(new int[] {0, 3, 6}, press);
        assertArrayEquals(new int[] {0, 3}, echo);
        assertEquals(ForgeStrikeMath.FLURRY_PULSE_CAP_WITH_ECHO, press.length + echo.length);
    }

    @Test
    void aHeavyFlurryKeepsAllFivePulsesBecauseItNeverEchoes() {
        assertArrayEquals(new int[] {0, 2, 4, 6, 8}, ForgeStrikeMath.flurryPulseTicks(true, false));
        assertFalse(ForgeStrikeMath.echoAllowed(FormFamily.FLURRY, true));
        assertEquals(0, ForgeStrikeMath.flurryPulseTicks(true, true).length);
    }

    @Test
    void echoAllowedIsFalseOnlyForHeavyFlurry() {
        assertFalse(ForgeStrikeMath.echoAllowed(FormFamily.FLURRY, true));
        assertTrue(ForgeStrikeMath.echoAllowed(FormFamily.FLURRY, false));
        assertTrue(ForgeStrikeMath.echoAllowed(FormFamily.SLASH, true));
    }

    @Test
    void leechHealIsFractionCappedByRemainingLeechPool() {
        assertEquals(1.6f, ForgeStrikeMath.leechHeal(20f, 0f), DELTA);
        assertEquals(0.5f, ForgeStrikeMath.leechHeal(20f, 1.5f), DELTA);
        assertEquals(0f, ForgeStrikeMath.leechHeal(20f, 2.0f), DELTA);
    }

    @Test
    void resolveAssemblesEveryFieldFromTheIndividualFunctions() {
        ModifierStack mods = mods(ForgeModifierKind.REACH);
        ForgeGrade grade = ForgeGrade.HIGH;
        int quality = 50;
        float weaponAttack = 7f;
        boolean heavy = true;
        float chargeFraction = 0.4f;
        boolean finisher = false;
        int comboIndex = 1;

        StrikeSpec spec = ForgeStrikeMath.resolve(SLASH, KEEN, WeaponClass.AXE, mods, grade, quality, weaponAttack,
                heavy, chargeFraction, finisher, comboIndex, ForgeElementKind.FIRE);

        float expectedHit = ForgeStrikeMath.baseHit(weaponAttack, grade, quality);
        float expectedDamage = ForgeStrikeMath.strikeDamage(expectedHit, SLASH, heavy, chargeFraction, finisher);

        assertEquals(SLASH.family(), spec.family());
        assertEquals(heavy, spec.heavy());
        assertEquals(finisher, spec.finisher());
        assertEquals(comboIndex, spec.comboIndex());
        assertEquals(expectedDamage, spec.damage(), DELTA);
        assertEquals(ForgeStrikeMath.reach(SLASH, KEEN, WeaponClass.AXE, mods), spec.reach(), DELTA);
        assertEquals(ForgeStrikeMath.halfWidth(SLASH, KEEN, heavy, mods), spec.halfWidth(), DELTA);
        assertEquals(ForgeStrikeMath.arcDegrees(SLASH, heavy), spec.arcDegrees(), DELTA);
        assertEquals(ForgeStrikeMath.speed(SLASH, KEEN), spec.speed(), DELTA);
        assertEquals(SLASH.lifeTicks(), spec.lifeTicks());
        assertEquals(ForgeStrikeMath.knockback(SLASH, KEEN, WeaponClass.AXE), spec.knockback(), DELTA);
        assertEquals(KEEN.critChance(), spec.critChance(), DELTA);
        assertEquals(ForgeStrikeMath.recovery(SLASH, KEEN, WeaponClass.AXE, heavy, mods), spec.recoveryTicks());
        assertEquals(mods, spec.mods());
        assertEquals(chargeFraction, spec.chargeFraction(), DELTA);
        assertTrue(spec.has(ForgeModifierKind.REACH));
        assertFalse(spec.has(ForgeModifierKind.HASTE));
    }

    @Test
    void asEchoScalesDamageAndClearsTheEchoFlag() {
        ModifierStack mods = mods(ForgeModifierKind.ECHO, ForgeModifierKind.PIERCE);
        StrikeSpec spec = new StrikeSpec(FormFamily.SLASH, false, false, 0, 20f, 4f, 1f, 150f, 0f, 4, 0.3f, 0.1f, 8,
                mods, 0f);

        StrikeSpec echo = spec.asEcho();

        assertEquals(8f, echo.damage(), DELTA);
        assertFalse(echo.has(ForgeModifierKind.ECHO));
        assertTrue(echo.has(ForgeModifierKind.PIERCE));
        assertEquals(spec.family(), echo.family());
        assertEquals(spec.heavy(), echo.heavy());
        assertEquals(spec.finisher(), echo.finisher());
        assertEquals(spec.comboIndex(), echo.comboIndex());
        assertEquals(spec.reach(), echo.reach(), DELTA);
        assertEquals(spec.halfWidth(), echo.halfWidth(), DELTA);
        assertEquals(spec.arcDegrees(), echo.arcDegrees(), DELTA);
        assertEquals(spec.speed(), echo.speed(), DELTA);
        assertEquals(spec.lifeTicks(), echo.lifeTicks());
        assertEquals(spec.knockback(), echo.knockback(), DELTA);
        assertEquals(spec.critChance(), echo.critChance(), DELTA);
        assertEquals(spec.recoveryTicks(), echo.recoveryTicks());
        assertEquals(spec.chargeFraction(), echo.chargeFraction(), DELTA);
    }

    /**
     * Gale Step's "+1 reach". Only a light GALE flurry gets it: a heavy flurry never fires the Art
     * ({@code ArtTrigger.LIGHT}), and no other element/form pair has a reach-changing Art at all.
     */
    @Test
    void onlyALightGaleFlurryEarnsTheArtReachBonus() {
        assertEquals(ForgeStrikeMath.GALE_STEP_REACH_BONUS,
                ForgeStrikeMath.artReachBonus(ForgeElementKind.GALE, FormFamily.FLURRY, false), DELTA);
        assertEquals(0f, ForgeStrikeMath.artReachBonus(ForgeElementKind.GALE, FormFamily.FLURRY, true), DELTA);
        assertEquals(0f, ForgeStrikeMath.artReachBonus(ForgeElementKind.GALE, FormFamily.SLASH, false), DELTA);
        assertEquals(0f, ForgeStrikeMath.artReachBonus(ForgeElementKind.FROST, FormFamily.FLURRY, false), DELTA);
    }

    @Test
    void theArtReachBonusIsFoldedInBeforeTheFamilyCap() {
        ModifierStack noMods = mods();
        float plain = ForgeStrikeMath.reach(FLURRY, KEEN, WeaponClass.SWORD, noMods);

        assertEquals(plain + ForgeStrikeMath.GALE_STEP_REACH_BONUS,
                ForgeStrikeMath.reach(FLURRY, KEEN, WeaponClass.SWORD, noMods,
                        ForgeStrikeMath.GALE_STEP_REACH_BONUS), DELTA);
        // The cap still governs: a huge bonus cannot push a non-wave form past MAX_REACH.
        assertEquals(ForgeStrikeMath.MAX_REACH,
                ForgeStrikeMath.reach(FLURRY, KEEN, WeaponClass.SWORD, noMods, 99f), DELTA);
    }

    @Test
    void guardStillRunningIsTrueStrictlyBeforeItsUntilTickAndFalseFromThatTickOnward() {
        long untilTick = 130L;

        assertTrue(ForgeStrikeMath.guardStillRunning(untilTick, 100L), "well before expiry");
        assertTrue(ForgeStrikeMath.guardStillRunning(untilTick, 129L), "the tick right before expiry");
        // ForgeComboService#activeReduction gates the guard's own mitigation on `now < untilTick`, so
        // the cancel-vs-expired split here must land on the same tick that check does, or a cancel at
        // the guard's own expiry tick could disagree with whether the guard was still reducing damage.
        assertFalse(ForgeStrikeMath.guardStillRunning(untilTick, 130L), "the expiry tick itself");
        assertFalse(ForgeStrikeMath.guardStillRunning(untilTick, 200L), "well after expiry");
    }

    @Test
    void aGaleFlurryResolvesOneBlockLongerThanTheSameFlurryOnAnotherElement() {
        ModifierStack noMods = mods();
        StrikeSpec gale = ForgeStrikeMath.resolve(FLURRY, KEEN, WeaponClass.SWORD, noMods, ForgeGrade.HIGH, 50,
                7f, false, 0f, false, 0, ForgeElementKind.GALE);
        StrikeSpec frost = ForgeStrikeMath.resolve(FLURRY, KEEN, WeaponClass.SWORD, noMods, ForgeGrade.HIGH, 50,
                7f, false, 0f, false, 0, ForgeElementKind.FROST);
        StrikeSpec heavyGale = ForgeStrikeMath.resolve(FLURRY, KEEN, WeaponClass.SWORD, noMods, ForgeGrade.HIGH, 50,
                7f, true, 0f, false, 0, ForgeElementKind.GALE);

        assertEquals(frost.reach() + ForgeStrikeMath.GALE_STEP_REACH_BONUS, gale.reach(), DELTA);
        assertEquals(frost.damage(), gale.damage(), DELTA);
        assertEquals(ForgeStrikeMath.reach(FLURRY, KEEN, WeaponClass.SWORD, noMods), heavyGale.reach(), DELTA);
    }
}
