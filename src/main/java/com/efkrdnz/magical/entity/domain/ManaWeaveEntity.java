package com.efkrdnz.magical.entity.domain;

import com.efkrdnz.magical.magic.DomainPass;
import com.efkrdnz.magical.magic.mana.WeaveAspect;
import com.efkrdnz.magical.magic.mana.WeaveOperation;
import com.efkrdnz.magical.magic.mana.WeaveSubject;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The Weave: the local mana field, claimed, with the rules of magic written onto it.
 *
 * <p>The second {@link DomainEntity}, and the one that proves the first was worth extracting. It
 * inherits the owner, the radius, the lifetime, the sweep and the {@link DomainPass} split, and
 * adds exactly one thing of its own - six aspects of magic, each carrying an operation and a
 * subject, packed the way the subspace packs its twelve laws.
 *
 * <p><b>It does not act on bodies, and that is deliberate.</b> Its {@code applyLaws} is empty. A
 * subspace legislates physics, so it must touch everything standing inside it every tick; a Weave
 * legislates magic, and magic happens at the moment of a cast. So the Weave is <em>read</em> from
 * where casting is charged rather than pushed onto the things standing in it. The inherited sweep
 * costs it nothing and buys it the lifetime and follow behaviour it wanted anyway, and the hook is
 * where a future rule about loose mana moving through bodies will go.
 */
public final class ManaWeaveEntity extends DomainEntity {

    /** A Weave is wider than a subspace: it claims a field rather than shaping a room. */
    public static final float MIN_RADIUS = 8.0F;
    public static final float MAX_RADIUS = 24.0F;
    private static final int NO_RULE = -1;

    private static final EntityDataAccessor<Integer> COST_OPERATION =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COST_SUBJECT =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COOLDOWN_OPERATION =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COOLDOWN_SUBJECT =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DURATION_OPERATION =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DURATION_SUBJECT =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCHOOL_OPERATION =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> SCHOOL_SUBJECT =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLOW_OPERATION =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLOW_SUBJECT =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MANIFESTATION_OPERATION =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MANIFESTATION_SUBJECT =
            SynchedEntityData.defineId(ManaWeaveEntity.class, EntityDataSerializers.INT);

    public ManaWeaveEntity(EntityType<? extends ManaWeaveEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static ManaWeaveEntity create(ServerLevel level, LivingEntity owner, float radius, boolean followOwner) {
        ManaWeaveEntity entity = new ManaWeaveEntity(MagicalEntities.MANA_WEAVE.get(), level);
        entity.setPos(owner.getX(), owner.getY() + owner.getBbHeight() * 0.5D, owner.getZ());
        entity.setOwner(owner);
        entity.setRadius(Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS));
        entity.setFollowOwner(followOwner);
        return entity;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        for (WeaveAspect aspect : WeaveAspect.values()) {
            builder.define(operationAccessor(aspect), NO_RULE);
            builder.define(subjectAccessor(aspect), WeaveSubject.THEIRS.ordinal());
        }
    }

    // ----------------------------------------------------------------------------- the grammar

    /** Writes one rule. RESTORE erases rather than stores, so the aspect behaves normally again. */
    public void inscribe(WeaveAspect aspect, WeaveOperation operation, WeaveSubject subject) {
        entityData.set(operationAccessor(aspect), operation.clears() ? NO_RULE : operation.ordinal());
        entityData.set(subjectAccessor(aspect), subject.ordinal());
    }

    /** The operation standing on this aspect, or null when the aspect is unwritten. */
    public WeaveOperation operationOn(WeaveAspect aspect) {
        int ordinal = entityData.get(operationAccessor(aspect));
        WeaveOperation[] operations = WeaveOperation.values();
        return ordinal >= 0 && ordinal < operations.length ? operations[ordinal] : null;
    }

    public WeaveSubject subjectOn(WeaveAspect aspect) {
        int ordinal = entityData.get(subjectAccessor(aspect));
        WeaveSubject[] subjects = WeaveSubject.values();
        return ordinal >= 0 && ordinal < subjects.length ? subjects[ordinal] : WeaveSubject.THEIRS;
    }

    /** True when a rule on this aspect is written about this caster in particular. */
    public boolean binds(WeaveAspect aspect, UUID caster) {
        if (operationOn(aspect) == null) {
            return false;
        }
        boolean wielder = caster != null && caster.equals(ownerUuid());
        return switch (subjectOn(aspect)) {
            case ALL -> true;
            case MINE -> wielder;
            case THEIRS -> !wielder;
        };
    }

    /** How many of the six aspects carry a rule - the Weave's weight, for anything that spends it. */
    public int written() {
        int count = 0;
        for (WeaveAspect aspect : WeaveAspect.values()) {
            if (operationOn(aspect) != null) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------------------ the domain

    @Override
    protected double searchRadius() {
        return radius();
    }

    @Override
    protected boolean contains(Entity entity) {
        return entityBoundaryPoint(entity).distanceToSqr(position()) <= radius() * radius();
    }

    /**
     * Nothing per-tick and per-body. A Weave rules on casting, and casting asks the Weave rather
     * than being told by it - see {@code WeaveLaw}, which is read from the cast path.
     */
    @Override
    protected void applyLaws(Entity owner, Entity subject, DomainPass pass) {
    }

    @Override
    protected void releaseOwnerHandle(ServerPlayer player) {
        var state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (state.activeWeaveEntityId() == getId()) {
            state.setActiveWeaveEntityId(-1);
            state.sync(player);
        }
    }

    // ------------------------------------------------------------------------------------ save

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        for (WeaveAspect aspect : WeaveAspect.values()) {
            // Guarded, because an unguarded read of a missing key answers 0, and 0 is RAISE - a
            // real rule. The subspace learned that the hard way; see its own read.
            if (tag.contains(aspect.name() + "Operation")) {
                entityData.set(operationAccessor(aspect), tag.getInt(aspect.name() + "Operation"));
            }
            if (tag.contains(aspect.name() + "Subject")) {
                entityData.set(subjectAccessor(aspect), tag.getInt(aspect.name() + "Subject"));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        for (WeaveAspect aspect : WeaveAspect.values()) {
            tag.putInt(aspect.name() + "Operation", entityData.get(operationAccessor(aspect)));
            tag.putInt(aspect.name() + "Subject", entityData.get(subjectAccessor(aspect)));
        }
    }

    private static EntityDataAccessor<Integer> operationAccessor(WeaveAspect aspect) {
        return switch (aspect) {
            case COST -> COST_OPERATION;
            case COOLDOWN -> COOLDOWN_OPERATION;
            case DURATION -> DURATION_OPERATION;
            case SCHOOL -> SCHOOL_OPERATION;
            case FLOW -> FLOW_OPERATION;
            case MANIFESTATION -> MANIFESTATION_OPERATION;
        };
    }

    private static EntityDataAccessor<Integer> subjectAccessor(WeaveAspect aspect) {
        return switch (aspect) {
            case COST -> COST_SUBJECT;
            case COOLDOWN -> COOLDOWN_SUBJECT;
            case DURATION -> DURATION_SUBJECT;
            case SCHOOL -> SCHOOL_SUBJECT;
            case FLOW -> FLOW_SUBJECT;
            case MANIFESTATION -> MANIFESTATION_SUBJECT;
        };
    }
}
