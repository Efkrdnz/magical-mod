package com.efkrdnz.magical.entity;

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
    private PlayerMagicState magicState = new PlayerMagicState();
    private UUID copiedPlayerUuid;
    private String copiedPlayerName = "Mage Clone";
    private int difficulty = 2;
    private int castDelay;

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

    public void copyFromPlayer(ServerPlayer player, int difficulty) {
        this.magicState = player.getData(MagicalAttachments.MAGIC_STATE).copy();
        this.magicState.clearAuthority();
        this.magicState.clearCooldowns();
        this.magicState.refillMana();
        this.magicState.setBarrier(this.magicState.maxBarrier());
        this.copiedPlayerUuid = player.getUUID();
        this.copiedPlayerName = player.getGameProfile().getName() + "'s Clone";
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
        if (tickCount % 20 == 0 && magicState.mana() < magicState.maxMana()) {
            magicState.addMana(2 + difficulty);
        }
        for (var skillId : magicState.unlockedSkills()) {
            int cooldown = magicState.skillCooldown(skillId);
            if (cooldown > 0) {
                magicState.setSkillCooldown(skillId, cooldown - 1);
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
        if (MagicMobCastingService.cast(this, magicState, skill, target)) {
            castDelay = nextCastDelay(skill);
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

        boolean hasRanged = MagicMobCastingService.hasUsableProjectile(this, magicState);
        boolean hasCloseBurst = MagicMobCastingService.hasUsableCloseBurst(this, magicState);
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
        if (hurt && entity instanceof LivingEntity living && magicState.isPassiveEnabled(MagicPassiveContent.SIN_WRATH.id())) {
            living.knockback(0.22D + difficulty * 0.04D, getX() - living.getX(), getZ() - living.getZ());
        }
        return hurt;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        float adjusted = amount;
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
        tag.putInt("CastDelay", castDelay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("MagicState")) {
            magicState = PlayerMagicState.load(tag.getCompound("MagicState"));
            magicState.clearAuthority();
        }
        if (tag.hasUUID("CopiedPlayer")) {
            copiedPlayerUuid = tag.getUUID("CopiedPlayer");
        }
        copiedPlayerName = tag.contains("CopiedPlayerName") ? tag.getString("CopiedPlayerName") : "Mage Clone";
        difficulty = Mth.clamp(tag.getInt("OpponentDifficulty"), 0, 5);
        castDelay = Math.max(0, tag.getInt("CastDelay"));
        setCustomName(Component.literal(copiedPlayerName));
        setCustomNameVisible(true);
        if (MagicMobCastingService.needsWeapon(magicState) && getMainHandItem().isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
        }
    }
}
