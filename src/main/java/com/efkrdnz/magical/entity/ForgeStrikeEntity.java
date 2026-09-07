package com.efkrdnz.magical.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.HitShape;
import com.efkrdnz.magical.entity.forge.HitShapes;
import com.efkrdnz.magical.entity.forge.StrikeTravel;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.ForgeModifierKind;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.FormDefinition;
import com.efkrdnz.magical.forge.FormFamily;
import com.efkrdnz.magical.forge.StrikeImpact;
import java.util.Optional;

import com.efkrdnz.magical.forge.StrikeLoadout;
import com.efkrdnz.magical.forge.ForgePayloadService;
import com.efkrdnz.magical.forge.chain.Payload;
import com.efkrdnz.magical.forge.chain.TriggerKind;
import com.efkrdnz.magical.forge.strike.ForgeStrikeMath;
import com.efkrdnz.magical.forge.strike.ShapeMath;
import com.efkrdnz.magical.forge.strike.StrikeSpec;
import com.efkrdnz.magical.forge.strike.StrikeTally;
import com.efkrdnz.magical.magic.ForgeComboService;
import com.efkrdnz.magical.registry.MagicalEntities;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One forged-weapon strike. Every left click on a forged sword or axe spawns exactly one of these,
 * and the form family decides how it behaves: anchored forms sit on the wielder for two ticks, a
 * flurry re-pulses on a schedule, a wave travels, a slam drops onto the ground below.
 *
 * <p>The arithmetic arrives pre-resolved in a {@link StrikeSpec}, the geometry lives in the
 * {@code entity.forge} hit shapes, and the consequences of a hit live in {@code StrikeImpact}, so
 * all that is left here is deciding <em>when</em> to test for targets.</p>
 */
public final class ForgeStrikeEntity extends Entity {

    private static final EntityDataAccessor<Integer> FORM_ORDINAL = defineInt();
    private static final EntityDataAccessor<Integer> PRIMARY = defineInt();
    private static final EntityDataAccessor<Integer> SECONDARY = defineInt();
    private static final EntityDataAccessor<Integer> EDGE = defineInt();
    private static final EntityDataAccessor<Integer> LIFE = defineInt();
    private static final EntityDataAccessor<Integer> COMBO_INDEX = defineInt();
    private static final EntityDataAccessor<Integer> OWNER_ID = defineInt();
    private static final EntityDataAccessor<Integer> ELEMENT_ORDINAL = defineInt();
    private static final EntityDataAccessor<Boolean> HEAVY = defineBool();
    private static final EntityDataAccessor<Boolean> ECHO = defineBool();
    private static final EntityDataAccessor<Float> HALF_WIDTH = defineFloat();
    private static final EntityDataAccessor<Float> ARC = defineFloat();
    private static final EntityDataAccessor<Float> REACH = defineFloat();
    private static final EntityDataAccessor<Float> DIR_X = defineFloat();
    private static final EntityDataAccessor<Float> DIR_Y = defineFloat();
    private static final EntityDataAccessor<Float> DIR_Z = defineFloat();

    private static final int NO_ID = -1;
    private static final int ANCHORED_HIT_TICKS = 2;
    private static final int SLAM_SECOND_RING_TICK = 4;
    private static final float SLAM_SECOND_RING_RADIUS = 1.6f;
    private static final float SLAM_SECOND_RING_DAMAGE = 0.5f;
    private static final double SLAM_GROUND_SEARCH = 3.0;
    private static final int PIERCE_TARGETS = 3;
    private static final int SLAM_EFFECT_LIFE = 8;

    private final Map<UUID, Long> hitTicks = new HashMap<>();
    private final StrikeTally tally = new StrikeTally();
    private StrikeLoadout loadout;

    /** A payload fires once, whichever of its triggers comes due first. */
    private boolean payloadFired;
    private UUID ownerUuid;
    private int pulseIndex;
    private int waveHits;

    public ForgeStrikeEntity(EntityType<? extends ForgeStrikeEntity> entityType, Level level) {
        super(entityType, level);
        noPhysics = true;
    }

    public static ForgeStrikeEntity spawn(ServerLevel level, ServerPlayer owner, StrikeSpec spec, ForgedWeapon weapon,
            ElementDefinition element, FormDefinition form, Vec3 origin, Vec3 direction, boolean echo) {
        return spawn(level, owner, spec, weapon, element, form, origin, direction, echo,
                ForgeComboService.primaryTargetId(owner));
    }

    /**
     * As above, but with the primary target stated rather than read off the combo state.
     *
     * <p>A forked press spawns several strikes at once, and only one of them may claim the single
     * vanilla hit the press came with. Every other member is spawned with
     * {@link StrikeLoadout#NO_PRIMARY_TARGET}: if they each subtracted the weapon attack from the
     * same body, a wide fork would deal almost nothing to the thing it was aimed at.
     */
    public static ForgeStrikeEntity spawn(ServerLevel level, ServerPlayer owner, StrikeSpec spec, ForgedWeapon weapon,
            ElementDefinition element, FormDefinition form, Vec3 origin, Vec3 direction, boolean echo,
            int primaryTargetId) {
        return spawn(level, owner, spec, weapon, element, form, origin, direction, echo, primaryTargetId,
                Optional.empty());
    }

    /** As above, carrying a nested step to fire when its trigger comes due. */
    public static ForgeStrikeEntity spawn(ServerLevel level, ServerPlayer owner, StrikeSpec spec, ForgedWeapon weapon,
            ElementDefinition element, FormDefinition form, Vec3 origin, Vec3 direction, boolean echo,
            int primaryTargetId, Optional<Payload> payload) {
        ForgeStrikeEntity strike = new ForgeStrikeEntity(MagicalEntities.FORGE_STRIKE.get(), level);
        Vec3 dir = direction.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : direction.normalize();
        strike.setPos(origin.x, origin.y, origin.z);
        strike.ownerUuid = owner.getUUID();
        // The primary target is snapshotted now: the combo state advances the moment this press
        // resolves, which clears it, and an anchored strike does not collect until the next tick.
        strike.loadout = StrikeLoadout.of(spec, weapon, element, form,
                (float) owner.getAttributeValue(Attributes.ATTACK_DAMAGE), echo, primaryTargetId, payload);
        strike.entityData.set(FORM_ORDINAL, spec.family().ordinal());
        strike.entityData.set(PRIMARY, element.primaryColor());
        strike.entityData.set(SECONDARY, element.secondaryColor());
        strike.entityData.set(EDGE, element.edgeColor());
        strike.entityData.set(LIFE, lifeFor(spec, echo));
        strike.entityData.set(COMBO_INDEX, spec.comboIndex());
        strike.entityData.set(OWNER_ID, owner.getId());
        strike.entityData.set(ELEMENT_ORDINAL, element.kind().ordinal());
        strike.entityData.set(HEAVY, spec.heavy());
        strike.entityData.set(ECHO, echo);
        strike.entityData.set(HALF_WIDTH, spec.halfWidth());
        strike.entityData.set(ARC, spec.arcDegrees());
        strike.entityData.set(REACH, spec.reach());
        strike.setDirection(dir);
        level.addFreshEntity(strike);
        return strike;
    }

    /** The form's life, stretched when a heavy variant still has a pulse or ring scheduled past it. */
    private static int lifeFor(StrikeSpec spec, boolean echo) {
        int life = Math.max(1, spec.lifeTicks());
        if (spec.family() == FormFamily.FLURRY) {
            int[] pulses = ForgeStrikeMath.flurryPulseTicks(spec.heavy(), echo);
            return pulses.length == 0 ? life : Math.max(life, pulses[pulses.length - 1] + 2);
        }
        if (spec.family() == FormFamily.SLAM && spec.heavy()) {
            return Math.max(life, SLAM_SECOND_RING_TICK + 2);
        }
        return life;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FORM_ORDINAL, FormFamily.SLASH.ordinal());
        builder.define(PRIMARY, 0xD8E4FF);
        builder.define(SECONDARY, 0xD8E4FF);
        builder.define(EDGE, 0xFFFFFF);
        builder.define(LIFE, 4);
        builder.define(COMBO_INDEX, 0);
        builder.define(OWNER_ID, NO_ID);
        builder.define(ELEMENT_ORDINAL, ForgeElementKind.FIRE.ordinal());
        builder.define(HEAVY, false);
        builder.define(ECHO, false);
        builder.define(HALF_WIDTH, 1.6F);
        builder.define(ARC, 150.0F);
        builder.define(REACH, 3.5F);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (tickCount > life()) {
            // The late trigger. A carrier that expires having touched nothing still delivers, which
            // is what turns a thrown crescent into a way to put a strike somewhere downrange.
            firePayload(TriggerKind.EXPIRY);
            discard();
            return;
        }
        firePayloadOnTimer();
        switch (family()) {
            case WAVE -> tickWave();
            case SLAM -> tickSlam();
            default -> tickAnchored();
        }
    }

    /**
     * Fires the nested step if it is waiting on {@code kind}, and only once.
     *
     * <p>The flag matters: a wave that is blocked and then ticks past its life would otherwise
     * deliver its payload twice.
     */
    public void firePayload(TriggerKind kind) {
        if (payloadFired || loadout == null || !(level() instanceof ServerLevel server)) {
            return;
        }
        Optional<Payload> payload = loadout.payload();
        if (payload.isEmpty() || payload.get().kind() != kind) {
            return;
        }
        if (!(ownerEntity() instanceof ServerPlayer owner)) {
            return;
        }
        payloadFired = true;
        ForgePayloadService.fire(server, owner, loadout, position(), direction());
    }

    private void firePayloadOnTimer() {
        if (payloadFired || loadout == null) {
            return;
        }
        loadout.payload()
                .filter(payload -> payload.kind() == TriggerKind.TIMER && tickCount >= payload.delayTicks())
                .ifPresent(payload -> firePayload(TriggerKind.TIMER));
    }

    /** Anchored families ride the wielder; on the client that is the whole of their behaviour. */
    private void tickAnchored() {
        Entity owner = ownerEntity();
        if (owner != null) {
            setPos(owner.getX(), owner.getY(), owner.getZ());
        }
        if (level().isClientSide()) {
            return;
        }
        if (family() == FormFamily.FLURRY) {
            tickFlurry();
        } else if (tickCount <= ANCHORED_HIT_TICKS) {
            Vec3 origin = strikeOrigin();
            collect(origin, origin, reach(), 1.0f, Integer.MAX_VALUE);
        }
    }

    /** Every pulse is a fresh cut, so the dedupe map is cleared and the same body takes them all. */
    private void tickFlurry() {
        int age = tickCount - 1;
        for (int pulse : ForgeStrikeMath.flurryPulseTicks(heavy(), echo())) {
            if (pulse != age) {
                continue;
            }
            hitTicks.clear();
            tally.beginPass();
            pulseIndex++;
            Vec3 origin = strikeOrigin();
            collect(origin, origin, reach(), 1.0f, Integer.MAX_VALUE);
            return;
        }
    }

    private void tickWave() {
        Vec3 from = position();
        Vec3 dir = direction();
        float speed = loadout == null ? 0.0f : loadout.speed();
        if (!(level() instanceof ServerLevel server)) {
            Vec3 drift = from.add(dir.scale(speed));
            setPos(drift.x, drift.y, drift.z);
            return;
        }
        if (loadout != null && loadout.has(ForgeModifierKind.SEEKING)) {
            dir = StrikeTravel.steerToward(server, this, ownerEntity(), from, dir,
                    ForgeStrikeMath.seekingTurnRadians(loadout.mods()));
            setDirection(dir);
        }
        StrikeTravel.Step step = StrikeTravel.advance(server, this, from, dir, speed);
        int budget = waveBudget() - waveHits;
        if (budget > 0) {
            waveHits += collect(step.to(), from, reach(), 1.0f, budget);
        }
        setPos(step.to().x, step.to().y, step.to().z);
        if (step.blocked() || waveHits >= waveBudget()) {
            firePayload(TriggerKind.EXPIRY);
            discard();
        }
    }

    /** A light wave stops on the first body, PIERCE threads three, a heavy sweeps until it expires. */
    private int waveBudget() {
        int budget = heavy() ? Integer.MAX_VALUE : 1;
        boolean pierce = loadout != null && loadout.has(ForgeModifierKind.PIERCE);
        return pierce ? Math.max(budget, PIERCE_TARGETS) : budget; // penetration only ever adds
    }

    private void tickSlam() {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        if (tickCount == 1) {
            Vec3 ground = StrikeTravel.groundUnder(server, this, position(), SLAM_GROUND_SEARCH);
            if (ground == null) {
                discard(); // nothing under the blow: a slam needs ground to break
                return;
            }
            setPos(ground.x, ground.y, ground.z);
            ForgeEffectEntity.impact(server, ground, ForgeEffectStyle.SLAM_CRACK, primaryColor(), edgeColor(),
                    reach(), SLAM_EFFECT_LIFE).withHeavy(heavy());
            collect(position(), position(), reach(), 1.0f, Integer.MAX_VALUE);
            return;
        }
        if (heavy() && tickCount == SLAM_SECOND_RING_TICK) {
            hitTicks.clear();
            tally.beginPass();
            collect(position(), position(), reach() * SLAM_SECOND_RING_RADIUS, SLAM_SECOND_RING_DAMAGE,
                    Integer.MAX_VALUE);
        }
    }

    /** Broad-phase by the shape's own inflation, narrowed by the shape itself. Returns hits made. */
    private int collect(Vec3 origin, Vec3 previous, double reach, float damageScale, int budget) {
        if (!(level() instanceof ServerLevel server) || loadout == null) {
            return 0;
        }
        HitShape shape = HitShapes.forFamily(family());
        HitShape.Inflation pad = shape.broadInflation(halfWidth(), reach);
        AABB area = new AABB(previous, origin).inflate(pad.x(), pad.y(), pad.z());
        Vec3 dir = direction();
        ShapeMath.Basis basis = ShapeMath.Basis.fromForward(dir.x, dir.y, dir.z);
        Entity owner = ownerEntity();
        long now = server.getGameTime();
        int hits = 0;
        for (Entity entity : server.getEntities(this, area,
                candidate -> candidate instanceof LivingEntity living && living.isAlive() && candidate != owner)) {
            if (hits >= budget) {
                break;
            }
            LivingEntity target = (LivingEntity) entity;
            if (hitTicks.containsKey(target.getUUID()) || !shape.hits(query(origin, previous, basis, target, reach))) {
                continue;
            }
            hitTicks.put(target.getUUID(), now);
            StrikeImpact.apply(server, this, owner, target, loadout, dir, damageScale, now, random, tally);
            // The on-impact trigger, at the first body this strike opens up and no other.
            if (tally.impacts() == 1) {
                firePayload(TriggerKind.IMPACT);
            }
            hits++;
        }
        return hits;
    }

    private HitShape.Query query(Vec3 origin, Vec3 previous, ShapeMath.Basis basis, LivingEntity target,
            double reach) {
        AABB box = target.getBoundingBox();
        Vec3 centre = box.getCenter();
        return new HitShape.Query(origin.x, origin.y, origin.z, previous.x, previous.y, previous.z, basis,
                centre.x, centre.y, centre.z, box.minY, target.getBbWidth(), reach, halfWidth(), arc());
    }

    private Vec3 strikeOrigin() {
        Entity owner = ownerEntity();
        if (owner instanceof LivingEntity living) {
            return living.getEyePosition();
        }
        return owner == null ? position() : owner.position();
    }

    private Entity ownerEntity() {
        int id = entityData.get(OWNER_ID);
        Entity byId = id == NO_ID ? null : level().getEntity(id);
        if (byId != null || ownerUuid == null || !(level() instanceof ServerLevel server)) {
            return byId;
        }
        return server.getEntity(ownerUuid);
    }

    private void setDirection(Vec3 dir) {
        entityData.set(DIR_X, (float) dir.x);
        entityData.set(DIR_Y, (float) dir.y);
        entityData.set(DIR_Z, (float) dir.z);
    }

    public Vec3 direction() {
        Vec3 dir = new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
        return dir.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : dir.normalize();
    }

    public FormFamily family() {
        int ordinal = entityData.get(FORM_ORDINAL);
        FormFamily[] families = FormFamily.values();
        return ordinal >= 0 && ordinal < families.length ? families[ordinal] : FormFamily.SLASH;
    }

    /** The wielder, so an anchored strike can be drawn riding them rather than a tick behind. */
    public int ownerId() {
        return entityData.get(OWNER_ID);
    }

    /** Drives the renderer's element accent; the colours alone cannot tell FROST from GALE. */
    public ForgeElementKind element() {
        int ordinal = entityData.get(ELEMENT_ORDINAL);
        ForgeElementKind[] kinds = ForgeElementKind.values();
        return ordinal >= 0 && ordinal < kinds.length ? kinds[ordinal] : ForgeElementKind.FIRE;
    }

    public int primaryColor() {
        return entityData.get(PRIMARY);
    }

    public int secondaryColor() {
        return entityData.get(SECONDARY);
    }

    public int edgeColor() {
        return entityData.get(EDGE);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int comboIndex() {
        return entityData.get(COMBO_INDEX);
    }

    public boolean heavy() {
        return entityData.get(HEAVY);
    }

    public boolean echo() {
        return entityData.get(ECHO);
    }

    public float halfWidth() {
        return entityData.get(HALF_WIDTH);
    }

    public float arc() {
        return entityData.get(ARC);
    }

    public float reach() {
        return entityData.get(REACH);
    }

    public int pulseIndex() {
        return pulseIndex;
    }

    /** Lives a handful of ticks and is respawned by the next press: never worth persisting. */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < SpellEntityVisibility.RENDER_DISTANCE_SQR;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource damageSource, float amount) {
        return false;
    }

    private static EntityDataAccessor<Integer> defineInt() {
        return SynchedEntityData.defineId(ForgeStrikeEntity.class, EntityDataSerializers.INT);
    }

    private static EntityDataAccessor<Float> defineFloat() {
        return SynchedEntityData.defineId(ForgeStrikeEntity.class, EntityDataSerializers.FLOAT);
    }

    private static EntityDataAccessor<Boolean> defineBool() {
        return SynchedEntityData.defineId(ForgeStrikeEntity.class, EntityDataSerializers.BOOLEAN);
    }
}
