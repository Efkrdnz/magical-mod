package com.efkrdnz.magical.entity;

import com.efkrdnz.magical.entity.ascendant.AscendantLoadout;
import com.efkrdnz.magical.entity.ascendant.AscendantTier;
import com.efkrdnz.magical.forge.ForgeMobStrike;
import com.efkrdnz.magical.entity.ascendant.OpponentKind;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicMobCastingService;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class MagicOpponentEntity extends Monster {
    private static final int BASE_CAST_INTERVAL = 26;

    /** Damage multiplier against an Ascendant caught between bursts. */
    private static final float RECOVERY_VULNERABILITY = 1.25F;

    /** Damage multiplier against one whose verdict was answered, and how long it lasts. */
    private static final float STAGGER_VULNERABILITY = 1.5F;
    private static final int STAGGER_TICKS = 60;
    private PlayerMagicState magicState = new PlayerMagicState();
    private UUID copiedPlayerUuid;
    private String copiedPlayerName = "Mage Clone";
    private int difficulty = 2;
    private int castDelay;
    private OpponentKind kind = OpponentKind.CLONE;
    private int burstRemaining;
    private int recoveryLeft;
    private boolean staggered;

    public MagicOpponentEntity(EntityType<? extends MagicOpponentEntity> entityType, Level level) {
        super(entityType, level);
        xpReward = 18;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 42.0D)
                .add(Attributes.ATTACK_DAMAGE, 7.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.31D)
                .add(Attributes.FOLLOW_RANGE, 42.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.18D);
    }

    public static MagicOpponentEntity cloneFrom(ServerPlayer player, int difficulty) {
        MagicOpponentEntity clone = new MagicOpponentEntity(com.efkrdnz.magical.registry.MagicalEntities.MAGIC_OPPONENT.get(), player.level());
        clone.copyFromPlayer(player, difficulty);
        return clone;
    }

    /**
     * Spawns an authored enemy rather than a copy of anyone.
     *
     * <p>A clone is only ever as dangerous as the player who cast it, which is the right shape for a
     * sparring partner and the wrong one for a boss - a fresh character's level 10 would be
     * harmless. An Ascendant reads {@link AscendantTier} instead, so tier 8 is the same fight for
     * everyone who meets it.
     */
    public static MagicOpponentEntity ascendant(Level level, int tier) {
        MagicOpponentEntity opponent = new MagicOpponentEntity(
                com.efkrdnz.magical.registry.MagicalEntities.MAGIC_OPPONENT.get(), level);
        opponent.becomeAscendant(AscendantTier.byTier(tier).orElse(AscendantTier.ECHO));
        return opponent;
    }

    private void becomeAscendant(AscendantTier tier) {
        this.kind = OpponentKind.ASCENDANT;
        this.difficulty = tier.tier();
        this.copiedPlayerUuid = null;
        this.copiedPlayerName = "Ascendant";
        this.magicState = new PlayerMagicState();
        // maxMana() is a config base plus two bonuses; the class pool bonus is the one that is
        // never saved, which is exactly right for a state that is rebuilt on load.
        this.magicState.setClassPoolBonuses(
                Math.max(0, tier.maxMana() - this.magicState.maxMana()), 0);
        grantRoster(tier);
        this.magicState.refillMana();
        this.magicState.setBarrier(this.magicState.maxBarrier());
        setCustomName(Component.translatable(tier.nameKey()));
        setCustomNameVisible(true);
        equipAscendantWeapon(tier);
        applyAscendantAttributes(tier);
        setHealth(getMaxHealth());
    }

    /**
     * The Ascendant's own spell list.
     *
     * <p>Every id here is already mob-castable: they carry usable {@code MobCastProfile}s and sit
     * outside the sets {@code canMobUse} refuses. Higher tiers reach past that, which is a later
     * phase and a change to the gate rather than to this list.
     */
    private void grantRoster(AscendantTier tier) {
        magicState.unlock(MagicContent.CRUCIBLE.id());
        magicState.unlock(MagicContent.LEVIATHAN_COIL.id());
        magicState.unlock(MagicContent.HEAVENS_GAZE.id());
        magicState.unlock(MagicContent.ECHOES_OF_PASSAGE.id());
        if (tier.tier() >= 7) {
            magicState.unlock(MagicContent.DIASPORA.id());
            magicState.unlock(MagicContent.FALLEN_FIRMAMENT.id());
        }
        if (tier.tier() >= 8) {
            // These three already declare usable mob profiles and were held back only by the
            // created-skill check, which an Ascendant is now allowed past.
            magicState.unlock(MagicContent.FALLEN_SUN.id());
            magicState.unlock(MagicContent.TOTAL_ECLIPSE.id());
            magicState.unlock(MagicContent.TECTONIC_VERDICT.id());
        }
    }

    /**
     * Whether the boss is in the opening between bursts.
     *
     * <p>This is the fight's rhythm: it casts a burst, then stands exposed. Making a boss cast ever
     * faster only produces noise - what a player can actually read and answer is a pause.
     */
    public boolean isRecovering() {
        return recoveryLeft > 0;
    }

    /** Spends one spell of the current burst, and opens the recovery window when it runs out. */
    private void advanceBurst(AscendantTier tier) {
        if (burstRemaining <= 0) {
            burstRemaining = tier.burstSpells();
        }
        burstRemaining--;
        if (burstRemaining > 0) {
            castDelay = tier.burstGapTicks();
            return;
        }
        castDelay = 0;
        recoveryLeft = tier.recoveryTicks();
        burstRemaining = tier.burstSpells();
        plantVerdict(tier);
    }

    /**
     * Plants the telegraph that closes a burst.
     *
     * <p>A fresh entity every time: the counter service remembers one prompt per threat entity for
     * that entity's whole life, so a reused one would ask once and never again.
     */
    private void plantVerdict(AscendantTier tier) {
        if (!(level() instanceof ServerLevel level) || !(getTarget() instanceof LivingEntity target)) {
            return;
        }
        if (!target.isAlive()) {
            return;
        }
        level.addFreshEntity(AscendantVerdictEntity.create(level, this, target, tier));
    }

    /**
     * Opens the punish window early, and widens it.
     *
     * <p>Called when a player answers a verdict. Reading the tell has to buy something, or the
     * telegraph is only a tax on inattention.
     */
    public void stagger() {
        recoveryLeft = Math.max(recoveryLeft, STAGGER_TICKS);
        staggered = true;
        castDelay = 0;
    }

    /**
     * Hands the tier its blade, and guarantees it drops.
     *
     * <p>The weapon is the reward for the fight - an inscription the forge would refuse to make for
     * the player who kills it - so it is not left to a vanilla drop roll.
     */
    private void equipAscendantWeapon(AscendantTier tier) {
        setItemSlot(EquipmentSlot.MAINHAND, AscendantLoadout.weaponFor(tier));
        setDropChance(EquipmentSlot.MAINHAND, 1.0F);
    }

    private void applyAscendantAttributes(AscendantTier tier) {
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(tier.health());
        // The blade's own bite, on top of the tier's. Read after equipping for that reason.
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(tier.attack() + ForgeMobStrike.bonusDamage(this));
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(tier.speed());
        // Both of these are flat on a clone at every difficulty, which is most of why level 5 melts.
        getAttribute(Attributes.ARMOR).setBaseValue(tier.armour());
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(tier.knockbackResistance());
    }

    /** The tier this opponent is, or empty when it is a clone. */
    public java.util.Optional<AscendantTier> ascendantTier() {
        return kind == OpponentKind.ASCENDANT ? AscendantTier.byTier(difficulty) : java.util.Optional.empty();
    }

    public OpponentKind kind() {
        return kind;
    }

    public int difficulty() {
        return difficulty;
    }

    public void copyFromPlayer(ServerPlayer player, int difficulty) {
        this.magicState = player.getData(MagicalAttachments.MAGIC_STATE).copy();
        this.magicState.clearAuthority();
        this.magicState.clearCooldowns();
        this.magicState.refillMana();
        this.magicState.setBarrier(this.magicState.maxBarrier());
        this.copiedPlayerUuid = player.getUUID();
        this.copiedPlayerName = player.getGameProfile().getName() + "'s Clone";
        // Deliberately still 0..5: a clone above 5 would wear boss stats over a roster that is only
        // ever as good as the player who spawned it. Six and up go through ascendant() instead.
        this.kind = OpponentKind.CLONE;
        this.difficulty = Mth.clamp(difficulty, 0, 5);
        setCustomName(Component.literal(copiedPlayerName));
        setCustomNameVisible(true);
        if (MagicMobCastingService.needsWeapon(magicState)) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(34.0D + this.difficulty * 8.0D);
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(5.5D + this.difficulty * 1.15D);
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.29D + this.difficulty * 0.012D);
        setHealth(getMaxHealth());
    }

    public UUID copiedPlayerUuid() {
        return copiedPlayerUuid;
    }

    public String copiedPlayerName() {
        return copiedPlayerName;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.12D, true));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 14.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide()) {
            tickMagicState();
            tickCombatCasting();
        }
    }

    private void tickMagicState() {
        java.util.Optional<AscendantTier> tier = ascendantTier();
        if (tickCount % 20 == 0 && magicState.mana() < magicState.maxMana()) {
            magicState.addMana(tier.map(AscendantTier::manaPerSecond).orElse(2 + difficulty));
        }
        int rate = tier.map(AscendantTier::cooldownRate).orElse(1);
        for (var skillId : magicState.unlockedSkills()) {
            int cooldown = magicState.skillCooldown(skillId);
            if (cooldown > 0) {
                magicState.setSkillCooldown(skillId, Math.max(0, cooldown - rate));
            }
        }
    }

    private void tickCombatCasting() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || !(level() instanceof ServerLevel)) {
            return;
        }
        getLookControl().setLookAt(target, 30.0F, 30.0F);
        updateTacticalMovement(target);
        if (recoveryLeft > 0) {
            // The opening. A clone never enters one, so its cadence is exactly what it always was.
            recoveryLeft--;
            if (recoveryLeft == 0) {
                staggered = false;
            }
            return;
        }
        if (castDelay > 0) {
            castDelay--;
            return;
        }
        if (difficulty <= 1 && random.nextFloat() < (difficulty == 0 ? 0.18F : 0.08F)) {
            castDelay = 8 + random.nextInt(18);
            return;
        }
        MagicSkillDefinition skill = MagicMobCastingService.chooseSkill(this, magicState, target, difficulty);
        if (skill == null) {
            castDelay = Math.max(6, 18 - difficulty * 2);
            return;
        }
        if (MagicMobCastingService.cast(this, magicState, skill, target, difficulty)) {
            java.util.Optional<AscendantTier> tier = ascendantTier();
            if (tier.isPresent()) {
                advanceBurst(tier.get());
            } else {
                castDelay = nextCastDelay(skill);
            }
        } else {
            castDelay = Math.max(7, 16 - difficulty);
        }
    }

    private int nextCastDelay(MagicSkillDefinition skill) {
        int intelligenceDelay = switch (difficulty) {
            case 0 -> 18 + random.nextInt(26);
            case 1 -> 12 + random.nextInt(20);
            case 2 -> 8 + random.nextInt(16);
            case 3 -> 5 + random.nextInt(13);
            case 4 -> 3 + random.nextInt(10);
            default -> 2 + random.nextInt(8);
        };
        int typeDelay = skill.type().name().equals("BARRIER") ? 12 : skill.type().name().equals("BURST") ? 8 : 4;
        return Math.max(4, BASE_CAST_INTERVAL + typeDelay - difficulty * 4 + intelligenceDelay / 2);
    }

    private void updateTacticalMovement(LivingEntity target) {
        double distance = distanceTo(target);
        if (difficulty <= 1) {
            if (random.nextInt(difficulty == 0 ? 34 : 52) == 0) {
                getNavigation().stop();
                return;
            }
            if (distance > 3.0D) {
                getNavigation().moveTo(target, 0.92D);
            }
            return;
        }

        boolean hasRanged = MagicMobCastingService.hasUsableProjectile(this, magicState, difficulty);
        boolean hasCloseBurst = MagicMobCastingService.hasUsableCloseBurst(this, magicState, difficulty);
        double preferredMin = hasRanged && !hasCloseBurst ? 7.5D : hasRanged ? 5.5D : 2.4D;
        double preferredMax = hasRanged ? 12.0D + difficulty * 0.75D : 5.0D;

        if (distance < preferredMin && hasRanged) {
            moveAwayFrom(target, 1.02D + difficulty * 0.02D);
            return;
        }
        if (distance > preferredMax || (!hasRanged && distance > 2.6D)) {
            getNavigation().moveTo(target, 1.0D + difficulty * 0.025D);
            return;
        }
        if (difficulty >= 4 && tickCount % 18 == 0) {
            strafeAround(target);
        }
    }

    private void moveAwayFrom(LivingEntity target, double speed) {
        Vec3 away = position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D);
        if (away.lengthSqr() < 1.0E-4D) {
            away = new Vec3(random.nextDouble() - 0.5D, 0.0D, random.nextDouble() - 0.5D);
        }
        away = away.normalize();
        Vec3 destination = position().add(away.scale(5.0D + difficulty));
        getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
    }

    private void strafeAround(LivingEntity target) {
        Vec3 toTarget = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
        if (toTarget.lengthSqr() < 1.0E-4D) {
            return;
        }
        Vec3 side = new Vec3(-toTarget.z, 0.0D, toTarget.x).normalize().scale(random.nextBoolean() ? 3.4D : -3.4D);
        Vec3 destination = position().add(side);
        getNavigation().moveTo(destination.x, destination.y, destination.z, 1.02D + difficulty * 0.02D);
    }

    @Override
    public boolean doHurtTarget(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        boolean hurt = super.doHurtTarget(level, entity);
        if (hurt && entity instanceof LivingEntity struck) {
            // A mob gets no combo, no charge and no echo, but the element on its blade still lands.
            ForgeMobStrike.onMeleeHit(level, this, struck, (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
        }
        if (hurt && entity instanceof LivingEntity living && magicState.isPassiveEnabled(MagicPassiveContent.SIN_WRATH.id())) {
            living.knockback(0.22D + difficulty * 0.04D, getX() - living.getX(), getZ() - living.getZ());
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        float adjusted = amount;
        if (isRecovering()) {
            // The opening has to be worth taking, or the right play is simply to keep running.
            adjusted *= staggered ? STAGGER_VULNERABILITY : RECOVERY_VULNERABILITY;
        }
        if (difficulty >= 3 && getTarget() instanceof LivingEntity target && getHealth() / Math.max(1.0F, getMaxHealth()) < 0.62F && random.nextFloat() < 0.16F + difficulty * 0.035F) {
            MagicMobCastingService.castBestDefense(this, magicState, target, difficulty);
        }
        if (damageSource.is(net.minecraft.tags.DamageTypeTags.IS_FIRE)) {
            adjusted *= 1.0F - Math.min(0.8F, magicState.passiveReduction(MagicPassiveContent.HEAT_RESISTANCE.id()));
        }
        if (damageSource.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            adjusted *= 1.0F - Math.min(0.6F, magicState.passiveReduction(MagicPassiveContent.MAGIC_RESISTANCE.id()));
        }
        adjusted = magicState.absorbDamage(adjusted);
        if (adjusted <= 0.0F) {
            return false;
        }
        return super.hurtServer(level, damageSource, adjusted);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("MagicState", magicState.save());
        if (copiedPlayerUuid != null) {
            tag.putUUID("CopiedPlayer", copiedPlayerUuid);
        }
        tag.putString("CopiedPlayerName", copiedPlayerName);
        tag.putInt("OpponentDifficulty", difficulty);
        tag.putString("OpponentKind", kind.name());
        tag.putInt("BurstRemaining", burstRemaining);
        tag.putInt("RecoveryLeft", recoveryLeft);
        tag.putInt("CastDelay", castDelay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // An opponent saved before Ascendants existed carries no kind tag and a difficulty of at
        // most 5, so it loads back as exactly the clone it was.
        kind = OpponentKind.byName(tag.getString("OpponentKind"));
        difficulty = Mth.clamp(tag.getInt("OpponentDifficulty"), 0, AscendantTier.MAX_TIER);
        if (tag.contains("MagicState")) {
            magicState = PlayerMagicState.load(tag.getCompound("MagicState"));
            if (kind == OpponentKind.CLONE) {
                magicState.clearAuthority();
            }
        }
        if (tag.hasUUID("CopiedPlayer")) {
            copiedPlayerUuid = tag.getUUID("CopiedPlayer");
        }
        copiedPlayerName = tag.contains("CopiedPlayerName") ? tag.getString("CopiedPlayerName") : "Mage Clone";
        castDelay = Math.max(0, tag.getInt("CastDelay"));
        burstRemaining = Math.max(0, tag.getInt("BurstRemaining"));
        recoveryLeft = Math.max(0, tag.getInt("RecoveryLeft"));
        java.util.Optional<AscendantTier> tier = ascendantTier();
        if (tier.isPresent()) {
            // The mana pool bonus is derived rather than saved, so it has to be rebuilt or a
            // reloaded Ascendant would come back with a config-default pool it cannot cast from.
            magicState.setClassPoolBonuses(
                    Math.max(0, tier.get().maxMana() - new PlayerMagicState().maxMana()), 0);
            magicState.setMana(Math.min(magicState.mana(), magicState.maxMana()));
            setCustomName(Component.translatable(tier.get().nameKey()));
        } else {
            setCustomName(Component.literal(copiedPlayerName));
        }
        setCustomNameVisible(true);
        if (MagicMobCastingService.needsWeapon(magicState) && getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        }
    }
}
