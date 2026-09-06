package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The porcelain hare a transmuted creature is hidden behind. Mobs are hidden (invisible, no AI,
 * silent) and dragged along; the shell hops away from the nearest attacker and forwards every point
 * of damage to the original as a crack. Players keep their body (shrunk and disarmed by status)
 * and the shell follows them. Six cracks or the timer end it.
 */
public class PolymorphShellEntity extends LivingEntity implements ProfiledEffect {
    private static final EntityDataAccessor<Integer> SKILL_INDEX = SynchedEntityData.defineId(PolymorphShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(PolymorphShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(PolymorphShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CRACKS = SynchedEntityData.defineId(PolymorphShellEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HOP = SynchedEntityData.defineId(PolymorphShellEntity.class, EntityDataSerializers.FLOAT);
    public static final int MAX_CRACKS = 6;

    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private UUID victimUuid;
    private UUID ownerUuid;
    private boolean victimIsPlayer;
    private boolean ended;
    private int hopCooldown;

    public PolymorphShellEntity(EntityType<? extends PolymorphShellEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1000.0D).add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    public static PolymorphShellEntity create(ServerLevel level, MagicSkillDefinition definition, Entity owner, LivingEntity victim, int life, int seed) {
        PolymorphShellEntity shell = new PolymorphShellEntity(MagicalEntities.POLYMORPH_SHELL.get(), level);
        shell.skillId = definition.id();
        shell.entityData.set(SKILL_INDEX, MagicContent.skillIndex(definition.id()));
        shell.entityData.set(LIFE, life);
        shell.entityData.set(SEED, seed & 63);
        shell.victimUuid = victim.getUUID();
        shell.ownerUuid = owner != null ? owner.getUUID() : null;
        shell.victimIsPlayer = victim instanceof Player;
        shell.setPos(victim.getX(), victim.getY(), victim.getZ());
        shell.setYRot(victim.getYRot());
        shell.setHealth(shell.getMaxHealth());
        shell.hide(victim);
        return shell;
    }

    private void hide(LivingEntity victim) {
        MagicStatusService.apply(victim, MagicStatus.POLYMORPHED, life(), skillId, null);
        if (victim instanceof Player) {
            MagicStatusService.apply(victim, MagicStatus.COMPRESSED, life(), 3, 0.3F, skillId, null);
            return;
        }
        victim.setInvisible(true);
        victim.setSilent(true);
        victim.setInvulnerable(false);
        if (victim instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setTarget(null);
        }
    }

    private void restore(LivingEntity victim) {
        MagicStatusService.clear(victim, MagicStatus.POLYMORPHED);
        MagicStatusService.clear(victim, MagicStatus.COMPRESSED);
        if (victim instanceof Player) {
            return;
        }
        victim.setInvisible(false);
        victim.setSilent(false);
        if (victim instanceof Mob mob) {
            mob.setNoAi(false);
        }
        victim.setPos(getX(), getY(), getZ());
        victim.hurtMarked = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SKILL_INDEX, -1);
        builder.define(LIFE, 80);
        builder.define(SEED, 0);
        builder.define(CRACKS, 0);
        builder.define(HOP, 0.0F);
    }

    private LivingEntity victim(ServerLevel level) {
        return victimUuid != null && level.getEntity(victimUuid) instanceof LivingEntity living ? living : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity victim = victim(level);
        if (victim == null || !victim.isAlive() || tickCount >= life() || cracks() >= MAX_CRACKS) {
            end();
            return;
        }
        entityData.set(HOP, Math.max(0.0F, entityData.get(HOP) - 0.1F));
        if (victimIsPlayer) {
            // the player keeps their own body; the shell rides on them
            setPos(victim.getX(), victim.getY(), victim.getZ());
            setYRot(victim.getYRot());
            return;
        }
        victim.setPos(getX(), getY(), getZ());
        victim.setDeltaMovement(Vec3.ZERO);
        victim.hurtMarked = true;
        if (hopCooldown > 0) {
            hopCooldown--;
        }
        if (onGround() && hopCooldown <= 0) {
            Entity owner = ownerUuid != null ? level.getEntity(ownerUuid) : null;
            List<LivingEntity> threats = SkillTargets.hostilesWithin(level, victim, position(), 8.0D);
            Vec3 away = Vec3.ZERO;
            for (LivingEntity threat : threats) {
                if (threat != this) {
                    away = position().subtract(threat.position());
                    break;
                }
            }
            if (owner instanceof LivingEntity && away.lengthSqr() < 1.0E-4D && distanceTo(owner) < 8.0D) {
                away = position().subtract(owner.position());
            }
            if (away.lengthSqr() > 1.0E-4D) {
                away = new Vec3(away.x, 0.0D, away.z).normalize().scale(0.42D).add(0.0D, 0.38D, 0.0D);
                setDeltaMovement(away);
                hurtMarked = true;
                float yaw = (float) Math.toDegrees(Math.atan2(-away.x, away.z));
                setYRot(yaw);
                yBodyRot = yaw;
                entityData.set(HOP, 1.0F);
                hopCooldown = 10;
            }
        }
    }

    private void end() {
        if (ended || !(level() instanceof ServerLevel level)) {
            return;
        }
        ended = true;
        LivingEntity victim = victim(level);
        if (victim != null) {
            restore(victim);
        }
        SpellFx.impact(level, definition(), position().add(0.0D, 0.5D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, ownerUuid != null ? level.getEntity(ownerUuid) : null, 1.2F);
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (ended) {
            return false;
        }
        LivingEntity victim = victim(level);
        if (victim != null && victim != source.getEntity()) {
            victim.invulnerableTime = 0;
            victim.hurt(source, amount);
        }
        entityData.set(CRACKS, cracks() + 1);
        if (cracks() >= MAX_CRACKS) {
            end();
        }
        return true;
    }

    @Override
    public void die(DamageSource source) {
        end();
    }

    public int cracks() {
        return entityData.get(CRACKS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public MagicSkillDefinition definition() {
        MagicSkillDefinition definition = MagicContent.get(skillId);
        return definition != null ? definition : MagicContent.get(MagicContent.STARTER_SKILL);
    }

    // ---- LivingEntity plumbing ----

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return NonNullList.withSize(4, ItemStack.EMPTY);
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public boolean isPushable() {
        return !victimIsPlayer;
    }

    @Override
    public boolean isPickable() {
        return !victimIsPlayer;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation parsed = ResourceLocation.tryParse(tag.getString("SkillId"));
        if (parsed != null && MagicContent.get(parsed) != null) {
            skillId = parsed;
            entityData.set(SKILL_INDEX, MagicContent.skillIndex(parsed));
        }
        if (tag.hasUUID("Victim")) {
            victimUuid = tag.getUUID("Victim");
        }
        if (tag.hasUUID("Owner")) {
            ownerUuid = tag.getUUID("Owner");
        }
        victimIsPlayer = tag.getBoolean("VictimPlayer");
        entityData.set(LIFE, tag.getInt("Life"));
        entityData.set(SEED, tag.getInt("Seed"));
        entityData.set(CRACKS, tag.getInt("Cracks"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SkillId", skillId.toString());
        if (victimUuid != null) {
            tag.putUUID("Victim", victimUuid);
        }
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putBoolean("VictimPlayer", victimIsPlayer);
        tag.putInt("Life", life());
        tag.putInt("Seed", entityData.get(SEED));
        tag.putInt("Cracks", cracks());
    }

    // ---- ProfiledEffect ----

    @Override
    public int skillIndex() {
        return entityData.get(SKILL_INDEX);
    }

    @Override
    public int effectAge() {
        return tickCount;
    }

    @Override
    public int effectLife() {
        return life();
    }

    @Override
    public int effectSeed() {
        return entityData.get(SEED);
    }

    @Override
    public Vec3 effectDirection() {
        double yaw = Math.toRadians(yBodyRot);
        return new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
    }

    /** cracks/MAX in the integer part... painters read cracks from {@link #effectValue} as 0..1 and hop from data. */
    @Override
    public float effectValue() {
        return cracks() / (float) MAX_CRACKS;
    }

    @Override
    public CompoundTag effectData() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Hop", entityData.get(HOP));
        return tag;
    }

    /** Draw mode 1: the hare body (mode 0 is the caster's hand windup). */
    @Override
    public int effectDrawMode() {
        return 1;
    }
}
