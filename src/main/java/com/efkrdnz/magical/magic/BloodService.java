package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.blood.BloodPrices;
import com.efkrdnz.magical.magic.passive.PassiveHooks;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * What blood magic costs, and how much harder it hits for having cost it.
 *
 * <p>Two fuels, and the second is the point. A blood cost is drawn from the Crimson Vessel first
 * and from the caster's own body only for what the Vessel cannot cover, so the fantasy is legible
 * without a tutorial: fight well and you spend their blood, fight badly and you spend yours.
 */
public final class BloodService {

    /**
     * How much of a blood cost one point of health buys. Twenty health is five hundred blood, so a
     * heart is worth fifty and the whole bar is worth five full Vessels.
     *
     * <p>It used to be eight, matched to {@code WarPassives.PAYMENT_MANA_PER_HEALTH}. The two are no
     * longer the same number and no longer should be: Red Payment buys mana, this buys blood, and
     * this one is now charged as true damage that no barrier soaks. A cheaper bill nothing can
     * absorb is a harder bill than an expensive one a full barrier pays for you.
     */
    public static final int COST_PER_HEALTH = 25;

    /** How long healing stays suppressed after a payment in health. */
    public static final int OPEN_WOUND_TICKS = 100;

    /**
     * Blood magic never finishes you off. A cast that would leave you below this is refused
     * outright rather than clamped: clamping would mean the spell went off and the last of your
     * health quietly vanished, which is the same as dying to your own button.
     */
    public static final float MIN_HEALTH_AFTER_PAYMENT = 1.0F;

    /** At full health a blood spell is ordinary; at death's door it is this many times as strong. */
    public static final float MAX_POTENCY = 2.2F;

    private BloodService() {
    }

    /**
     * True when the player holds any blood skill at all.
     *
     * <p>This gates the school's whole economy, so that someone who has never touched Blood is not
     * quietly accruing a resource they can neither see nor spend - and it gates the HUD bar, so the
     * Vessel only takes up room on screen for players it means something to.
     */
    public static boolean isBloodMage(PlayerMagicState state) {
        for (ResourceLocation id : state.unlockedSkills()) {
            MagicSkillDefinition skill = MagicContent.get(id);
            if (skill != null && skill.school() == MagicSchool.BLOOD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Charges a blood cost: Vessel first, then health for the shortfall.
     *
     * <p>Returns false and takes nothing at all when the caster cannot survive the bill, so a
     * refused cast leaves the Vessel exactly as full as it was.
     */
    public static boolean pay(ServerPlayer player, PlayerMagicState state, int cost) {
        if (MagicPrice.waived(player) || cost <= 0) {
            return true;
        }
        int shortfall = Math.max(0, cost - state.bloodVessel());
        float healthCost = shortfall / (float) COST_PER_HEALTH;
        if (!canSurvive(player, healthCost)) {
            refuse(player);
            return false;
        }
        state.drawFromVessel(cost);
        bleed(player, state, healthCost);
        return true;
    }

    /**
     * Charges a cost the Vessel is not allowed to cover. This is the desperation price: a skill
     * that asks for it wants the caster weaker for having cast it, and letting harvested blood
     * stand in would remove the only thing holding that skill in check.
     */
    public static boolean payInHealthOnly(ServerPlayer player, PlayerMagicState state, int cost) {
        if (MagicPrice.waived(player) || cost <= 0) {
            return true;
        }
        float healthCost = cost / (float) COST_PER_HEALTH;
        if (!canSurvive(player, healthCost)) {
            refuse(player);
            return false;
        }
        bleed(player, state, healthCost);
        return true;
    }

    /**
     * Charges a cost the body is not allowed to cover: the mirror of {@link #payInHealthOnly}.
     *
     * <p>A ritual wants proof the caster has been taking blood off other people. Letting them open a
     * vein for the difference would turn "a full Vessel" into "two hearts", which is neither the
     * same requirement nor the same fantasy.
     *
     * <p>Returns false and takes nothing when the Vessel is short, so a refused ritual leaves it
     * exactly as full as it was.
     */
    public static boolean payFromVesselOnly(ServerPlayer player, PlayerMagicState state, int cost) {
        if (MagicPrice.waived(player) || cost <= 0) {
            return true;
        }
        if (state.bloodVessel() < cost) {
            player.displayClientMessage(Component.translatable("message.magical.vessel_not_full"), true);
            return false;
        }
        state.drawFromVessel(cost);
        return true;
    }

    /**
     * The multiplier a blood skill's damage and size are scaled by: 1.0 at full health, rising to
     * {@link #MAX_POTENCY} at the edge of death.
     *
     * <p>This is what makes the school worth its price rather than merely expensive. It reads off
     * current health, so paying for one spell in blood immediately makes the next one stronger.
     */
    public static float potency(LivingEntity caster) {
        float max = caster.getMaxHealth();
        if (max <= 0.0F) {
            return 1.0F;
        }
        float missing = 1.0F - Math.min(1.0F, Math.max(0.0F, caster.getHealth() / max));
        return 1.0F + missing * (MAX_POTENCY - 1.0F);
    }

    /**
     * What a cast of this skill costs in blood with the points applied: its base price from
     * {@link BloodPrices}, through {@link #scale}.
     */
    public static int cost(MagicSkillResolvedStats stats) {
        return scale(stats, BloodPrices.base(stats.definition().id()));
    }

    /**
     * A blood price with the points applied.
     *
     * <p>The factor is the one mana is billed at - points spent on the other stats raise it, Thrift
     * lowers it, and it never goes below a quarter - so the budget pulls on blood exactly as it
     * pulls on mana. A positive price never rounds away to nothing.
     */
    public static int scale(MagicSkillResolvedStats stats, int base) {
        if (base <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * stats.costScale()));
    }

    private static boolean canSurvive(ServerPlayer player, float healthCost) {
        return player.getHealth() - healthCost >= MIN_HEALTH_AFTER_PAYMENT;
    }

    private static void bleed(ServerPlayer player, PlayerMagicState state, float healthCost) {
        if (healthCost <= 0.0F) {
            return;
        }
        // The bespoke type, not vanilla magic. Slipping past magic resistance, Gluttony and Wrath is
        // now the point: a price you can resist is not a price, and paying your own bill is not
        // being attacked. Between the bypass tags and the early-out in MagicGameplayEvents, the
        // caster's own flesh pays - never armour, and never the barrier.
        player.hurt(com.efkrdnz.magical.magic.blood.BloodDamageTypes.price(player), healthCost);
        state.openWound(OPEN_WOUND_TICKS);
        PassiveHooks.puff(player, ParticleTypes.DAMAGE_INDICATOR, 6, 0.22D);
    }

    private static void refuse(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.magical.not_enough_blood"), true);
    }
}
