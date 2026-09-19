package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.boss.unwaking.UnwakingCapabilities;
import com.efkrdnz.magical.entity.SpellEntityVisibility;
import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicAttribute;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.HitEffect;
import com.efkrdnz.magical.magic.incantation.PayloadKind;
import com.efkrdnz.magical.magic.incantation.ProjectilePlan;
import com.efkrdnz.magical.magic.incantation.ShotState;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.VersePrototypes;
import com.efkrdnz.magical.magic.incantation.Wake;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One body of a recited shot, and the Authority of Mana's only entity. It reads everything it is
 * from a {@link ProjectilePlan}: the prototype (what it is), the stamped {@link ShotState} (what the
 * verses before it wrote), and the payload it releases. What the renderer needs is synced - the
 * prototype, the school, the radius, the life, the behaviours, the wakes, the direction, a seed;
 * the plan itself is server-only NBT so a body in an unloaded chunk keeps its payload.
 *
 * <p>A body ends one way and releases the payload of that way: a hit ends it with its Latch, its
 * fuse with its Fuse, its expiry with its Epitaph. Every end fires the explosion it carries and
 * carries the caster if the prototype does. A bounce is not an end. Damage funnels through
 * {@link MagicDamageService} under the recite skill's id, which is also what a counter reads:
 * Sovereign Aegis refuses authority skills by id, so it refuses these.
 */
public final class VerseBodyEntity extends Entity implements CounterableSkillThreat {
    private static final EntityDataAccessor<Integer> PROTOTYPE = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> SCHOOL = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BEHAVIOURS = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> WAKES = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(VerseBodyEntity.class, EntityDataSerializers.INT);

    /** Noita's critical hit. */
    public static final double CRIT_MULTIPLIER = 5.0D;
    /** An Undying body still ends here, because a server tick is not free. */
    public static final int UNDYING_CAP_TICKS = 1200;
    public static final double SEEK_RANGE = 12.0D;
    /** A bursting ricochet with no explosion of its own bursts this wide with the body's damage. */
    public static final double BURST_RADIUS = 1.5D;
    static final int OWNER_GRACE_TICKS = 5;
    static final int WAKE_INTERVAL = 4;
    static final double WAKE_REACH = 0.4D;
    static final float WAKE_BURN_SECONDS = 2.0F;
    static final int WAKE_FROST_TICKS = 20;
    static final double PIT_PULL = 0.08D;
    static final int COUNTER_WINDOW_TICKS = 13;

    private UUID ownerUuid;
    private ResourceLocation skillId = MagicContent.STARTER_SKILL;
    private ProjectilePlan plan;
    /** The flight vector in blocks per tick; the direction synced for the renderer follows it. */
    private Vec3 velocity = Vec3.ZERO;
    /** The heading at spawn, which a Gyre turns about. */
    private Vec3 launch = new Vec3(0.0D, 0.0D, 1.0D);
    private double speed;
    private int bouncesLeft;
    private boolean released;
    private boolean relayed;
    private final Set<Integer> struck = new HashSet<>();
    private VersePrototype prototypeCache;
    private int prototypeCacheIndex = -1;

    public VerseBodyEntity(EntityType<? extends VerseBodyEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static VerseBodyEntity spawn(ServerLevel level, LivingEntity caster, ProjectilePlan body, Vec3 position, Vec3 direction, ResourceLocation skillId) {
        VerseBodyEntity entity = new VerseBodyEntity(MagicalEntities.VERSE_BODY.get(), level);
        entity.ownerUuid = caster == null ? null : caster.getUUID();
        entity.skillId = skillId;
        entity.entityData.set(SEED, level.random.nextInt(64));
        entity.bind(body);
        entity.setPos(position.x, position.y, position.z);
        entity.aim(direction);
        level.addFreshEntity(entity);
        return entity;
    }

    /** Every body this owner has in the box, for tests and readouts. */
    public static List<VerseBodyEntity> ownedBy(ServerLevel level, LivingEntity owner, AABB box) {
        return level.getEntitiesOfClass(VerseBodyEntity.class, box, body -> owner.getUUID().equals(body.ownerUuid));
    }

    /** The synced picture and the server numbers, off the plan. */
    private void bind(ProjectilePlan body) {
        plan = body;
        VersePrototype prototype = body.prototype();
        ShotState s = body.stamped();
        entityData.set(PROTOTYPE, VersePrototypes.all().indexOf(prototype));
        entityData.set(SCHOOL, (byte) (s.school() != null ? s.school() : prototype.school()).ordinal());
        entityData.set(RADIUS, prototype.radius());
        entityData.set(LIFE, prototype.isStatic()
                ? Math.max(1, prototype.durationTicks())
                : Math.max(1, prototype.lifetimeTicks() + s.lifetimeAddTicks()));
        entityData.set(BEHAVIOURS, VerseBehaviours.mask(s.behaviours()));
        entityData.set(WAKES, (byte) wakeMask(s.wakes()));
        speed = prototype.speed() * s.speedMultiplier();
        bouncesLeft = s.bounces();
    }

    private void aim(Vec3 direction) {
        Vec3 heading = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : direction.normalize();
        launch = heading;
        velocity = heading.scale(speed);
        setDirection(heading);
    }

    private static int wakeMask(List<Wake> wakes) {
        int mask = 0;
        for (Wake wake : wakes) {
            mask |= 1 << wake.ordinal();
        }
        return mask;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PROTOTYPE, 0);
        builder.define(SCHOOL, (byte) MagicSchool.ARCANE.ordinal());
        builder.define(RADIUS, 0.15F);
        builder.define(LIFE, 40);
        builder.define(BEHAVIOURS, 0);
        builder.define(WAKES, (byte) 0);
        builder.define(DIR_X, 0.0F);
        builder.define(DIR_Y, 0.0F);
        builder.define(DIR_Z, 1.0F);
        builder.define(SEED, 0);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        if (plan == null) {
            discard();
            return;
        }
        if (has(Behaviour.NAUGHT)) {
            end(level, position(), PayloadKind.EPITAPH);
            return;
        }
        if (plan.prototype().isStatic()) {
            tickStanding(level);
        } else {
            tickFlying(level);
        }
    }

    private void tickStanding(ServerLevel level) {
        int interval = Math.max(1, plan.prototype().pulseIntervalTicks());
        if (tickCount % interval == 0) {
            pulse(level);
        }
        if (isRemoved()) {
            return;
        }
        if (fuseDue()) {
            end(level, position(), PayloadKind.FUSE);
        } else if (lifeOver()) {
            end(level, position(), PayloadKind.EPITAPH);
        }
    }

    private void tickFlying(ServerLevel level) {
        if (fuseDue()) {
            end(level, position(), PayloadKind.FUSE);
            return;
        }
        Vec3 from = position();
        Vec3 step = flightStep(level);
        Vec3 to = from.add(step);
        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            if (bouncesLeft > 0) {
                bounce(level, blockHit);
                return;
            }
            end(level, blockHit.getLocation(), PayloadKind.LATCH);
            return;
        }
        offerApproachCounters(from, to);
        LivingEntity hit = firstEntityHit(from, to);
        if (hit != null) {
            if (hit instanceof ServerPlayer player && MagicCounterService.hasActivePrompt(player, this)) {
                MagicCounterService.expirePrompt(player, this);
            }
            strike(level, hit);
            if (!has(Behaviour.PUNCTURE)) {
                end(level, new Vec3(hit.getX(), hit.getY(0.55D), hit.getZ()), PayloadKind.LATCH);
                return;
            }
        }
        setPos(to.x, to.y, to.z);
        setDirection(velocity);
        if (tickCount % WAKE_INTERVAL == 0) {
            wake(level);
        }
        if (lifeOver()) {
            end(level, position(), PayloadKind.EPITAPH);
        }
    }

    /**
     * Where the body goes this tick, in a fixed order: a Gyre is placed on its orbit and nothing
     * else applies; otherwise gravity, then Seeker and Errant on the flight vector, then Serpentine
     * as an offset riding on top of it.
     */
    private Vec3 flightStep(ServerLevel level) {
        Entity owner = ownerEntity();
        if (has(Behaviour.GYRE) && owner != null) {
            Vec3 wanted = owner.position().add(VerseBehaviours.gyreOffset(launch, tickCount));
            Vec3 step = wanted.subtract(position());
            if (step.lengthSqr() > 1.0E-6D) {
                velocity = step.normalize().scale(Math.max(speed, 1.0E-3D));
            }
            return step;
        }
        Vec3 toTarget = null;
        if (has(Behaviour.SEEKER)) {
            LivingEntity target = nearestHostile(level, owner);
            if (target != null) {
                toTarget = target.getBoundingBox().getCenter().subtract(position());
            }
        }
        velocity = VerseBehaviours.steer(velocity, plan.stamped().gravity(), toTarget, has(Behaviour.SEEKER), has(Behaviour.ERRANT), tickCount, seed());
        Vec3 step = velocity;
        if (has(Behaviour.SERPENTINE)) {
            step = step.add(VerseBehaviours.serpentineOffset(velocity, tickCount));
        }
        return step;
    }

    private LivingEntity nearestHostile(ServerLevel level, Entity owner) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : SkillTargets.hostilesWithin(level, owner != null ? owner : this, position(), SEEK_RANGE)) {
            double distance = candidate.distanceToSqr(position());
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private void bounce(ServerLevel level, BlockHitResult hit) {
        Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
        bouncesLeft--;
        velocity = VerseBehaviours.bounce(velocity, normal);
        Vec3 at = hit.getLocation().add(normal.scale(0.05D));
        setPos(at.x, at.y, at.z);
        setDirection(velocity);
        if (has(Behaviour.BOUNCE_BURST)) {
            explode(level, at, Math.max(explosionRadius(), BURST_RADIUS), Math.max(explosionDamage(), damage()));
        }
    }

    private LivingEntity firstEntityHit(Vec3 from, Vec3 to) {
        Entity owner = ownerEntity();
        boolean ownerFair = plan.stamped().friendlyFire() && tickCount > OWNER_GRACE_TICKS;
        AABB path = getBoundingBox().expandTowards(to.subtract(from)).inflate(0.3D + radius());
        LivingEntity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : level().getEntities(this, path, target -> target instanceof LivingEntity living && living.isAlive()
                && !struck.contains(target.getId()) && (target != owner || ownerFair))) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(radius()).clip(from, to);
            if (hit.isPresent()) {
                double distance = from.distanceToSqr(hit.get());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = (LivingEntity) entity;
                }
            }
        }
        return closest;
    }

    /** The hit: healing, damage with its crit roll, knockback, then the effects, the body's own first. */
    private void strike(ServerLevel level, LivingEntity target) {
        struck.add(target.getId());
        Entity owner = ownerEntity();
        Entity source = owner == null ? this : owner;
        ShotState s = plan.stamped();
        double healing = plan.prototype().healing() + s.healingAdd();
        if (healing > 0.0D) {
            target.heal((float) healing);
        }
        double damage = damage();
        if (damage > 0.0D) {
            if (s.critChance() > 0.0D && random.nextDouble() * 100.0D < s.critChance()) {
                damage *= CRIT_MULTIPLIER;
            }
            MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) damage, skillId);
        }
        if (s.knockback() > 0.0D) {
            SkillTargets.shove(target, position(), 0.25D * s.knockback(), 0.05D);
        }
        for (HitEffect effect : VerseHitEffects.effectsOf(plan)) {
            VerseHitEffects.apply(effect, target, source, position(), random);
        }
    }

    /** A static's pulse: its effect over its radius. The caster stands in their own ring unharmed unless friendly fire is on, but is healed by it. */
    private void pulse(ServerLevel level) {
        Entity owner = ownerEntity();
        Entity source = owner == null ? this : owner;
        ShotState s = plan.stamped();
        double radius = radius();
        double healing = plan.prototype().healing() + s.healingAdd();
        double damage = damage();
        List<HitEffect> effects = VerseHitEffects.pulseEffectsOf(plan);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), LivingEntity::isAlive)) {
            if (target.distanceToSqr(position()) > radius * radius) {
                continue;
            }
            if (healing > 0.0D) {
                target.heal((float) healing);
            }
            if (target == owner && !s.friendlyFire()) {
                continue;
            }
            if (damage > 0.0D) {
                MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) damage, skillId);
            }
            for (HitEffect effect : effects) {
                VerseHitEffects.apply(effect, target, source, position(), random);
            }
            if (plan.prototype().look() == VersePrototype.Look.PIT && !(target instanceof ServerPlayer)) {
                Vec3 pull = position().subtract(target.position());
                if (pull.lengthSqr() > 1.0E-4D) {
                    pull = pull.normalize().scale(PIT_PULL);
                    target.push(pull.x, pull.y * 0.5D, pull.z);
                    target.hurtMarked = true;
                }
            }
        }
        if (plan.prototype().look() == VersePrototype.Look.PIT) {
            for (VerseBodyEntity other : level.getEntitiesOfClass(VerseBodyEntity.class, getBoundingBox().inflate(radius), body -> body != this && !body.plan().prototype().isStatic())) {
                Vec3 pull = position().subtract(other.position());
                if (pull.lengthSqr() > 1.0E-4D) {
                    other.velocity = other.velocity.add(pull.normalize().scale(PIT_PULL)).normalize().scale(Math.max(other.speed, 1.0E-3D));
                }
            }
        }
    }

    /** What the body leaves beside its line: fire sets alight, water puts out, frost slows. Never a block. */
    private void wake(ServerLevel level) {
        ShotState s = plan.stamped();
        if (s.wakes().isEmpty()) {
            return;
        }
        Entity owner = ownerEntity();
        double reach = radius() + WAKE_REACH + s.wakeAmount() * 0.04D;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(reach), living -> living.isAlive() && living != owner)) {
            for (Wake wakeKind : s.wakes()) {
                switch (wakeKind) {
                    case FIRE -> target.igniteForSeconds(WAKE_BURN_SECONDS);
                    case WATER -> target.clearFire();
                    case FROST -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WAKE_FROST_TICKS, 0, false, true), owner);
                }
            }
        }
    }

    // ------------------------------------------------------------------ ends

    private void end(ServerLevel level, Vec3 at, PayloadKind reason) {
        if (isRemoved()) {
            return;
        }
        explode(level, at);
        if (plan.payloadKind() == reason) {
            release(level, at);
        }
        if (reason == PayloadKind.LATCH && has(Behaviour.RELAY) && !relayed) {
            VerseBodySpawner.relay(level, this, at);
        }
        carryCaster(level, at);
        discard();
    }

    private void release(ServerLevel level, Vec3 at) {
        if (released || !plan.hasPayload()) {
            return;
        }
        released = true;
        VerseBodySpawner.release(level, livingOwner(), plan.payload(), at, direction(), skillId);
    }

    private void explode(ServerLevel level, Vec3 at) {
        double radius = explosionRadius();
        if (radius > 0.0D) {
            explode(level, at, radius, explosionDamage());
        }
    }

    /** Everything living in the radius, the caster included, with falloff. */
    private void explode(ServerLevel level, Vec3 at, double radius, double damage) {
        Entity owner = ownerEntity();
        Entity source = owner == null ? this : owner;
        MagicSkillDefinition skill = MagicContent.get(skillId);
        if (skill != null) {
            SpellFx.impact(level, skill, at, new Vec3(0.0D, 1.0D, 0.0D), null, owner, (float) Math.max(0.5D, radius * 0.5D));
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius), LivingEntity::isAlive)) {
            double distance = Math.sqrt(target.distanceToSqr(at));
            if (distance > radius) {
                continue;
            }
            double falloff = Math.max(0.25D, 1.0D - distance / radius);
            if (damage > 0.0D) {
                MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) (damage * falloff), skillId);
            }
            SkillTargets.shove(target, at, 0.3D * falloff, 0.12D * falloff);
        }
    }

    /** A Blink or a Step Word: the caster to where the body ended, through the one placement path the mod has. */
    private void carryCaster(ServerLevel level, Vec3 at) {
        if (!plan.prototype().carriesCaster() || !(ownerEntity() instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof ServerPlayer player && UnwakingCapabilities.refuseMovement(player)) {
            return;
        }
        Vec3 feet = placeNear(level, at, living);
        if (feet == null) {
            // A block hit ends on the face it struck, and that point floors into the struck block
            // itself, so both searches are looking up and down a column of solid wall. The last
            // point the body stood in is the same impact one step back, and it is in open air.
            feet = placeNear(level, position(), living);
        }
        if (feet == null) {
            return;
        }
        SafeSpotSearch.place(living, feet, living.getYRot(), living.getXRot(), false);
        level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    /** Somewhere a body of this size can stand at {@code wanted}: on the ground under it, else lifted clear of it. */
    private static Vec3 placeNear(ServerLevel level, Vec3 wanted, LivingEntity living) {
        Vec3 feet = SafeSpotSearch.standableNear(level, wanted, 2, 3, living.getBbWidth(), living.getBbHeight());
        return feet != null ? feet : SafeSpotSearch.liftClear(level, wanted, living.getBbWidth(), living.getBbHeight(), 2.0D);
    }

    // ------------------------------------------------------------------ counters

    private void offerApproachCounters(Vec3 from, Vec3 to) {
        if (damage() <= 0.0D || velocity.lengthSqr() < 1.0E-6D) {
            return;
        }
        Entity owner = ownerEntity();
        Vec3 heading = velocity.normalize();
        AABB warningPath = new AABB(from, from.add(heading.scale(9.0D))).inflate(2.0D + radius());
        for (Entity entity : level().getEntities(this, warningPath, target -> target instanceof ServerPlayer player && player.isAlive() && target != owner)) {
            ServerPlayer player = (ServerPlayer) entity;
            if (player.getEyePosition().subtract(from).dot(heading) < 0.0D) {
                continue;
            }
            MagicCounterService.offerCounter(player, this, player.getEyePosition().add(0.0D, -0.25D, 0.0D), COUNTER_WINDOW_TICKS);
        }
    }

    @Override
    public Entity counterEntity() {
        return this;
    }

    @Override
    public ResourceLocation counterSkillId() {
        return skillId;
    }

    @Override
    public MagicAttribute counterAttribute() {
        MagicSkillDefinition skill = MagicContent.get(skillId);
        return skill == null ? MagicAttribute.ARCANE : skill.attribute();
    }

    @Override
    public Entity counterOwner() {
        return ownerEntity();
    }

    @Override
    public void onCountered(ServerLevel level, ServerPlayer defender, MagicSkillDefinition counterSkill, Vec3 clashPosition) {
        MagicSkillDefinition skill = MagicContent.get(skillId);
        MagicCounterService.spawnClash(level, clashPosition, skill == null ? 0xF0F4FF : skill.color(), counterSkill.color());
        discard();
    }

    // ------------------------------------------------------------------ numbers

    private boolean fuseDue() {
        return plan.payloadKind() == PayloadKind.FUSE && tickCount >= Math.max(1, plan.fuseTicks());
    }

    private boolean lifeOver() {
        if (tickCount >= UNDYING_CAP_TICKS) {
            return true;
        }
        return !has(Behaviour.UNDYING) && tickCount >= life();
    }

    /** {@code prototype.damage + damageAdd}, nothing under Blunt, never negative. */
    public double damage() {
        ShotState s = plan.stamped();
        return s.nullsDamage() ? 0.0D : Math.max(0.0D, plan.prototype().damage() + s.damageAdd());
    }

    private double explosionRadius() {
        return Math.max(0.0D, plan.prototype().explosionRadius() + plan.stamped().explosionRadius());
    }

    private double explosionDamage() {
        return plan.stamped().nullsDamage() ? 0.0D : Math.max(0.0D, plan.prototype().explosionDamage() + plan.stamped().explosionDamageAdd());
    }

    // ------------------------------------------------------------------ accessors

    public ProjectilePlan plan() {
        return plan;
    }

    public ResourceLocation skillId() {
        return skillId;
    }

    /** From the synced index, cached, because {@code VersePrototypes.all()} copies the table. */
    public VersePrototype prototype() {
        int index = entityData.get(PROTOTYPE);
        if (index != prototypeCacheIndex || prototypeCache == null) {
            List<VersePrototype> all = VersePrototypes.all();
            prototypeCache = index >= 0 && index < all.size() ? all.get(index) : VersePrototypes.NEEDLE;
            prototypeCacheIndex = index;
        }
        return prototypeCache;
    }

    public MagicSchool school() {
        int ordinal = entityData.get(SCHOOL);
        MagicSchool[] schools = MagicSchool.values();
        return ordinal >= 0 && ordinal < schools.length ? schools[ordinal] : MagicSchool.ARCANE;
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int life() {
        return entityData.get(LIFE);
    }

    public int behaviourMask() {
        return entityData.get(BEHAVIOURS);
    }

    public int wakeMask() {
        return entityData.get(WAKES);
    }

    public int seed() {
        return entityData.get(SEED);
    }

    public boolean has(Behaviour behaviour) {
        return VerseBehaviours.has(behaviourMask(), behaviour);
    }

    public Vec3 direction() {
        return new Vec3(entityData.get(DIR_X), entityData.get(DIR_Y), entityData.get(DIR_Z));
    }

    private void setDirection(Vec3 v) {
        if (v.lengthSqr() < 1.0E-6D) {
            return;
        }
        Vec3 n = v.normalize();
        entityData.set(DIR_X, (float) n.x);
        entityData.set(DIR_Y, (float) n.y);
        entityData.set(DIR_Z, (float) n.z);
    }

    public void markRelayed() {
        relayed = true;
    }

    private Entity ownerEntity() {
        if (ownerUuid == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(ownerUuid);
    }

    public LivingEntity livingOwner() {
        return ownerEntity() instanceof LivingEntity living ? living : null;
    }

    // ------------------------------------------------------------------ nbt and entity contract

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerUuid != null) {
            tag.putUUID("Owner", ownerUuid);
        }
        tag.putString("Skill", skillId.toString());
        if (plan != null) {
            tag.put("Body", ShotPlanCodec.saveBody(plan));
        }
        tag.putDouble("VelX", velocity.x);
        tag.putDouble("VelY", velocity.y);
        tag.putDouble("VelZ", velocity.z);
        tag.putDouble("LaunchX", launch.x);
        tag.putDouble("LaunchY", launch.y);
        tag.putDouble("LaunchZ", launch.z);
        tag.putInt("Age", tickCount);
        tag.putInt("Bounces", bouncesLeft);
        tag.putBoolean("Released", released);
        tag.putBoolean("Relayed", relayed);
        tag.putInt("Seed", seed());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerUuid = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Skill"));
        if (id != null) {
            skillId = id;
        }
        entityData.set(SEED, tag.getInt("Seed"));
        ProjectilePlan body = tag.contains("Body") ? ShotPlanCodec.loadBody(tag.getCompound("Body")) : null;
        if (body == null) {
            plan = null;
            return;
        }
        bind(body);
        velocity = new Vec3(tag.getDouble("VelX"), tag.getDouble("VelY"), tag.getDouble("VelZ"));
        launch = new Vec3(tag.getDouble("LaunchX"), tag.getDouble("LaunchY"), tag.getDouble("LaunchZ"));
        tickCount = tag.getInt("Age");
        bouncesLeft = tag.getInt("Bounces");
        released = tag.getBoolean("Released");
        relayed = tag.getBoolean("Relayed");
        setDirection(velocity);
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
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
