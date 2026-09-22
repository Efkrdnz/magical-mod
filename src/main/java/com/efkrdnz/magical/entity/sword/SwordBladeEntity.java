package com.efkrdnz.magical.entity.sword;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 * One blade that has left its bearing: in the air, standing in a body, standing in the ground, or
 * cutting its line home.
 *
 * <p>Every one of those carries Edge, and Edge in any of them is <em>spent</em> - the third of the
 * three places conservation knows about. A blade never mints metal and never destroys it; it only
 * decides whether the metal it carries walks back on {@code SwordService}'s slow clock or comes
 * home all at once, which is Returning's business and not this class's.
 *
 * <p><b>Flight is an explicit raycast and not vanilla {@code Projectile} physics</b>, the way
 * {@code VerseBodyEntity} flies: the wall bounds the step and the first living body on the segment
 * <em>before</em> the wall is met first, so a blade thrown at somebody standing against a wall
 * hits the body rather than the stone behind them. A projectile that moves and then asks what it
 * is inside of has already passed through them at 1.8 blocks a tick.
 *
 * <p><b>{@link #wound} needs both halves and they fail in opposite directions.</b> Without
 * clearing {@code invulnerableTime}, six blades landing in one tick land as <em>one</em> hit -
 * vanilla takes the first and refuses the rest as no stronger - and Loose is quietly a sixth as
 * strong with full sound and full FX, which reads as a mechanics bug and not as a balance one.
 * With it cleared and no internal cooldown, one blade that can meet a body twice is twelve full
 * hits in a tick and an instant delete. So the i-frames go and a per-blade per-victim clock takes
 * their place, in this blade's own server scratch under {@code "icd_" + slot + "_" + victimId}.
 * A volley of six still lands six times, because six blades are six entities with six clocks -
 * which is exactly what the skill is priced at.
 *
 * <p>Mode bit 1 is set, so {@code SpellEffectEntity.offerCounters} returns on its first line:
 * twelve blades each running a player sweep every tick and throwing twelve competing counter
 * prompts at one defender is not a QTE, it is a denial of service. The consequence to remember is
 * that draw mode is {@code mode >> 1}, so no sword silhouette may ever be narrowed to
 * {@code forModes(0)}.
 */
public class SwordBladeEntity extends SpellEffectEntity {

    /** In the air, on its way out. */
    public static final byte STATE_FLYING = 0;

    /** Standing in a body it landed in. */
    public static final byte STATE_SPENT = 1;

    /** Standing in the world where it came down, and the enemy can break it. */
    public static final byte STATE_PLANTED = 2;

    /** Cutting the line back to the wielder. A shed, a recall, a miss, a wall. */
    public static final byte STATE_RETURNING = 3;

    /** How long a blade stands before it dissolves, in either of the two standing states. */
    public static final int LYING_TICKS = 600;

    /** Blades in the air per wielder. Conservation makes the worst case unreachable anyway. */
    public static final int MAX_IN_FLIGHT = 12;

    /**
     * Standing blades per wielder, oldest discarded at spawn.
     *
     * <p>The {@code BloodHarvestEntity} rule, and for its reason: the newest action is the one the
     * wielder is looking at, so a cap that refused the newest would make the skill they just
     * pressed the one that did nothing.
     */
    public static final int MAX_LYING = 16;

    /** How long one blade must wait before it may wound the same body again. */
    public static final int VICTIM_COOLDOWN_TICKS = 10;

    /** How fast a blade cuts its line home when it did not land. */
    public static final double RETURN_SPEED = 1.2D;

    /** Close enough to the wielder's chest to be back in their hand. */
    public static final double ARRIVE_RANGE = 1.0D;

    /** A blade whose wielder has gone will not orbit the world looking for them. */
    public static final int RETURN_CAP_TICKS = 200;

    /** How wide a sweep finds one wielder's blades. Comfortably past the Keel's own leash. */
    public static final double OWNER_SWEEP = 64.0D;

    /** Mode bit 1: never offered as a counter, never sneak-flipped. */
    public static final byte MODE_SILENT = 2;

    private static final String TAG_SLOT = "Slot";
    private static final String TAG_FLIGHT = "Flight";
    private static final String TAG_VICTIM = "Victim";
    private static final String ICD_PREFIX = "icd_";

    private static final EntityDataAccessor<Byte> STATE =
            SynchedEntityData.defineId(SwordBladeEntity.class, EntityDataSerializers.BYTE);

    /** How wide a net the segment sweep casts around the blade's own line. */
    private static final double SWEEP_SLACK = 0.3D;

    /** See {@link #behavior()}: a blade's tick is written out in full and dispatches to nothing. */
    private static final SpellBehavior NOTHING = entity -> { };

    /** Blocks a tick, server only. Never saved, because the blade never is. */
    private Vec3 velocity = Vec3.ZERO;

    /** Ticks in the state the blade is in now, which is what the renderer reads as its progress. */
    private int stateTicks;

    public SwordBladeEntity(EntityType<? extends SwordBladeEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    // ---- spawning ------------------------------------------------------------------------------

    /**
     * A blade leaving its bearing under its own power: Loose, Below's risers, the Keel's recall.
     *
     * <p>Answers null when the wielder already has {@link #MAX_IN_FLIGHT} out, so the caller can
     * leave that station's Edge where it is rather than paying for a blade that was refused.
     */
    public static SwordBladeEntity loose(ServerLevel level, ServerPlayer wielder, ResourceLocation skillId,
            Vec3 at, Vec3 heading, int slot, int edge, double damage, double knockback,
            double speed, int flightTicks) {
        if (inFlight(level, wielder) >= MAX_IN_FLIGHT) {
            return null;
        }
        SwordBladeEntity blade = create(level, wielder, skillId, at, slot, edge, damage, knockback, speed);
        blade.entityData.set(STATE, STATE_FLYING);
        blade.serverData().putInt(TAG_FLIGHT, Math.max(1, flightTicks));
        blade.aim(heading, speed);
        level.addFreshEntity(blade);
        return blade;
    }

    /** A blade that came down and stayed there: Below's misses, and anything set into the ground. */
    public static SwordBladeEntity plant(ServerLevel level, ServerPlayer wielder, ResourceLocation skillId,
            Vec3 at, int slot, int edge) {
        makeRoom(level, wielder, MAX_LYING - 1);
        SwordBladeEntity blade = create(level, wielder, skillId, at, slot, edge, 0.0D, 0.0D, 0.0D);
        blade.entityData.set(STATE, STATE_PLANTED);
        level.addFreshEntity(blade);
        return blade;
    }

    /**
     * A shed: the blade the settle took off an over-stretched Array, cutting its line home.
     *
     * <p>It carries no damage of its own - the service has already cut the line with
     * {@code SwordMath.shedDamage}, because the harm is a property of how long the line was and
     * not of how much metal travelled it - so this is the picture and the Edge, and nothing else.
     */
    public static SwordBladeEntity shedHome(ServerLevel level, ServerPlayer wielder, Vec3 from, Vec3 to,
            int slot, int edge, double speed) {
        SwordBladeEntity blade = create(level, wielder, MagicContent.CALL_THE_BLADE.id(),
                from, slot, edge, 0.0D, 0.0D, speed);
        blade.entityData.set(STATE, STATE_RETURNING);
        blade.aim(to.subtract(from), speed);
        level.addFreshEntity(blade);
        return blade;
    }

    /**
     * The common half, built through a hand-written tag.
     *
     * <p>{@code SpellEffectEntity.create} answers a {@code SpellEffectEntity} and its save hooks
     * are package-protected to {@code entity.fx}, so the copy-through-NBT trick
     * {@code EldritchConstructEntity} uses is not open to a class outside that package. The tag
     * carries what the superclass would have filled in. {@code Life} is <b>zero</b> deliberately:
     * a life above zero makes {@code SpellEffectEntity.tick} call {@code finish()} the moment
     * {@code tickCount} reaches it, and a blade's clock belongs to its state and changes with it.
     */
    private static SwordBladeEntity create(ServerLevel level, ServerPlayer wielder, ResourceLocation skillId,
            Vec3 at, int slot, int edge, double damage, double knockback, double speed) {
        SwordBladeEntity blade = new SwordBladeEntity(MagicalEntities.SWORD_BLADE.get(), level);
        CompoundTag tag = new CompoundTag();
        tag.putString("SkillId", skillId.toString());
        tag.putUUID("Owner", wielder.getUUID());
        tag.putInt("Life", 0);
        tag.putByte("Mode", MODE_SILENT);
        tag.putFloat("Radius", 0.0F);
        tag.putFloat("Damage", (float) damage);
        tag.putFloat("Knockback", (float) knockback);
        tag.putFloat("Speed", (float) speed);
        tag.putInt("Seed", level.random.nextInt(64));
        blade.readAdditionalSaveData(tag);
        blade.serverData().putInt(TAG_SLOT, slot);
        blade.serverData().putInt(TAG_VICTIM, -1);
        blade.setExtra(Math.max(0, edge));
        blade.setPos(at.x, at.y, at.z);
        return blade;
    }

    private void aim(Vec3 heading, double speed) {
        Vec3 unit = heading.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : heading.normalize();
        velocity = unit.scale(Math.max(0.05D, speed));
        setDirection(unit);
    }

    /**
     * Room for one more standing blade, made by letting the oldest ones go.
     *
     * <p>Newest first, which is <em>ascending</em> {@code tickCount}: an entity that has been
     * alive longer has counted more ticks, so the oldest blade is the one with the highest count
     * and it is at the end of this list.
     */
    private static void makeRoom(ServerLevel level, ServerPlayer wielder, int keep) {
        List<SwordBladeEntity> lying = ownedBy(level, wielder, sweep(wielder)).stream()
                .filter(SwordBladeEntity::lying)
                .sorted((a, b) -> Integer.compare(a.tickCount, b.tickCount))
                .toList();
        for (int i = Math.max(0, keep); i < lying.size(); i++) {
            // Its Edge is already spent - a standing blade is metal in the world - so there is
            // nothing to credit here, only the entity to let go of.
            lying.get(i).discard();
        }
    }

    public static List<SwordBladeEntity> ownedBy(ServerLevel level, Entity wielder, AABB box) {
        return level.getEntitiesOfClass(SwordBladeEntity.class, box,
                blade -> !blade.isRemoved() && wielder.getUUID().equals(blade.ownerUuid()));
    }

    public static int inFlight(ServerLevel level, ServerPlayer wielder) {
        int count = 0;
        for (SwordBladeEntity blade : ownedBy(level, wielder, sweep(wielder))) {
            if (blade.state() == STATE_FLYING) {
                count++;
            }
        }
        return count;
    }

    private static AABB sweep(Entity wielder) {
        return wielder.getBoundingBox().inflate(OWNER_SWEEP);
    }

    // ---- data -----------------------------------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(STATE, STATE_FLYING);
    }

    public byte state() {
        return entityData.get(STATE);
    }

    /**
     * What this blade is worth, and it is always one sword.
     *
     * <p>It used to be a divisible quantity of Edge, which is why the field is still here and
     * still on {@code EXTRA}: the renderer reads it as the blade's weight and every spawner
     * passes 1. The name is kept because the accessor is the renderer's, and renaming it would
     * churn a file whose subject is a sword model rather than a resource.
     */
    public int edge() {
        return extra();
    }

    /** Which bearing it came off. The internal cooldown is keyed by it, and so is the recall. */
    public int slot() {
        return serverData().getInt(TAG_SLOT);
    }

    /** Standing somewhere: in a body or in the ground. Both are Edge the wielder can walk to. */
    public boolean lying() {
        byte state = state();
        return state == STATE_SPENT || state == STATE_PLANTED;
    }

    /** Called home: by the Keel's leash, by a recall, or because there is nothing left to hold it. */
    public void recall() {
        if (state() == STATE_RETURNING) {
            return;
        }
        enter(STATE_RETURNING);
    }

    private void enter(byte state) {
        entityData.set(STATE, state);
        stateTicks = 0;
        setValue(0.0F);
    }

    // ---- tick -------------------------------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        stateTicks++;
        switch (state()) {
            case STATE_FLYING -> tickFlying(level);
            case STATE_SPENT -> tickSpent(level);
            case STATE_PLANTED -> tickPlanted();
            default -> tickReturning(level);
        }
    }

    /**
     * One step of flight: clip the wall, take the first body before it, and only then move.
     *
     * <p>A blade that runs out of budget or meets stone has not landed, so it turns for home; a
     * blade that meets a body has, and stays in it.
     */
    private void tickFlying(ServerLevel level) {
        Vec3 from = position();
        Vec3 to = from.add(velocity);
        BlockHitResult wall = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        Vec3 reach = wall.getType() == HitResult.Type.MISS ? to : wall.getLocation();
        LivingEntity body = firstBody(from, reach);
        if (body != null) {
            land(level, body);
            return;
        }
        if (wall.getType() != HitResult.Type.MISS) {
            recall();
            return;
        }
        setPos(to.x, to.y, to.z);
        setDirection(velocity);
        int budget = Math.max(1, serverData().getInt(TAG_FLIGHT));
        setValue(Math.min(1.0F, stateTicks / (float) budget));
        if (stateTicks >= budget) {
            recall();
        }
    }

    /** The nearest living thing whose box the segment passes through, the owner excepted. */
    private LivingEntity firstBody(Vec3 from, Vec3 to) {
        Entity owner = owner();
        AABB path = getBoundingBox().expandTowards(to.subtract(from)).inflate(SWEEP_SLACK);
        LivingEntity closest = null;
        double nearest = Double.MAX_VALUE;
        for (Entity candidate : level().getEntities(this, path, target -> target instanceof LivingEntity living
                && living.isAlive() && target != owner)) {
            LivingEntity living = (LivingEntity) candidate;
            if (waitingOn(living)) {
                continue;
            }
            Optional<Vec3> at = living.getBoundingBox().inflate(SWEEP_SLACK).clip(from, to);
            if (at.isEmpty()) {
                continue;
            }
            double distance = from.distanceToSqr(at.get());
            if (distance < nearest) {
                nearest = distance;
                closest = living;
            }
        }
        return closest;
    }

    /** The blade is in, its Edge stays spent, and it rides the body it landed in for 600 ticks. */
    private void land(ServerLevel level, LivingEntity body) {
        Entity owner = owner();
        wound(body, owner == null ? this : owner, damage());
        if (knockback() > 0.0F) {
            SkillTargets.shove(body, position(), knockback(), 0.05D);
        }
        serverData().putInt(TAG_VICTIM, body.getId());
        enter(STATE_SPENT);
        Vec3 centre = body.getBoundingBox().getCenter();
        setPos(centre.x, centre.y, centre.z);
        // A blade that has landed is a standing blade and counts against the same cap a planted
        // one does; this one is already in the sweep, so the room to keep is the whole cap.
        if (owner instanceof ServerPlayer wielder) {
            makeRoom(level, wielder, MAX_LYING);
        }
    }

    /**
     * A body's hurt cooldown cleared, and this blade's own clock against this victim started.
     *
     * <p>Both halves, and neither is enforced by anything in the build. See the class note.
     */
    private void wound(LivingEntity target, Entity source, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        target.invulnerableTime = 0;
        serverData().putInt(icdKey(target), tickCount + VICTIM_COOLDOWN_TICKS);
        MagicDamageService.hurt(target, damageSources().indirectMagic(this, source), (float) amount, skillId());
    }

    private boolean waitingOn(LivingEntity target) {
        return serverData().getInt(icdKey(target)) > tickCount;
    }

    private String icdKey(LivingEntity target) {
        return ICD_PREFIX + slot() + "_" + target.getId();
    }

    /** Standing in a body: it goes where the body goes, and falls out when the body is gone. */
    private void tickSpent(ServerLevel level) {
        Entity victim = level.getEntity(serverData().getInt(TAG_VICTIM));
        if (victim == null || !victim.isAlive()) {
            enter(STATE_PLANTED);
            return;
        }
        Vec3 centre = victim.getBoundingBox().getCenter();
        setPos(centre.x, centre.y, centre.z);
        age();
    }

    private void tickPlanted() {
        age();
    }

    /** Both standing states dissolve at the same age, and the Edge they carried stays spent. */
    private void age() {
        setValue(Math.min(1.0F, stateTicks / (float) LYING_TICKS));
        if (stateTicks >= LYING_TICKS) {
            discard();
        }
    }

    /**
     * The line home, and the one place a sword may put itself back all at once.
     *
     * <p>Only with Returning: without it the sword stays away and walks back on the service's own
     * clock, which is what makes every volley a real spend until the rung that grants the passive.
     */
    private void tickReturning(ServerLevel level) {
        if (!(owner() instanceof ServerPlayer wielder) || !wielder.isAlive() || stateTicks > RETURN_CAP_TICKS) {
            discard();
            return;
        }
        Vec3 home = wielder.getBoundingBox().getCenter();
        Vec3 offset = home.subtract(position());
        double distance = offset.length();
        if (distance <= ARRIVE_RANGE) {
            PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
            if (SwordService.returning(state)) {
                SwordService.returnSwords(wielder, state, 1);
                state.sync(wielder);
            }
            discard();
            return;
        }
        Vec3 step = offset.normalize().scale(Math.min(distance, Math.max(0.05D, RETURN_SPEED)));
        Vec3 to = position().add(step);
        setPos(to.x, to.y, to.z);
        setDirection(step);
        setValue(0.0F);
    }

    // ---- entity contract ---------------------------------------------------------------------------

    /**
     * No {@link SpellBehavior}, ever.
     *
     * <p>A blade carries a skill id so its damage is attributed to the skill that threw it, and
     * {@code SpellEffectEntity.tick} dispatches to whatever behaviour is registered under that id.
     * The day somebody registers one for Call the Blade - a cast circle, a flash - every blade in
     * the air would silently start running it as well. The tick here is written out in full and
     * wants no help.
     */
    @Override
    public SpellBehavior behavior() {
        return NOTHING;
    }

    /**
     * Never written to disk. An override rather than the builder's {@code noSave()} flag, because
     * copying a neighbouring registration in {@code MagicalEntities} gets you saving by default,
     * and a blade that survived a restart would be a sword the service has no record of sending.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
