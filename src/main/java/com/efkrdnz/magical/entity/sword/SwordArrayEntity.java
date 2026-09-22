package com.efkrdnz.magical.entity.sword;

import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.incantation.VersePassives;
import com.efkrdnz.magical.magic.sword.ArrayPose;
import com.efkrdnz.magical.magic.sword.Bind;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.Projection;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One entity for twelve swords: the Array at rest, and the wielder's guard.
 *
 * <p><b>The resting case is one entity, and that is the whole reason this type exists.</b>
 * {@link ArrayPose#worldOffset} is pure and both the server behaviour and the client painter run
 * it with identical arguments, so a blade's position costs <em>nothing</em> on the wire: the
 * client is told the frame and the shape - both of which change rarely - and works the twelve
 * positions out itself. The naive alternative is twelve entities at the standard tracking range,
 * which is twelve movement packets a tick to every observer within 256 blocks for a picture that
 * is one rigid formation, and four players with Arrays would be forty-eight of them.
 *
 * <p><b>Zero new {@code EntityDataAccessor}s.</b> It rides the fourteen slots
 * {@link SpellEffectEntity} already has: {@code EXTRA} is the 24-bit manned mask (twelve real,
 * twelve mirrored), {@code VALUE} the frame scale, {@code DIR} the frame facing, {@code RADIUS}
 * held at zero - the renderer must zero it again, because the radius a sword entity carries is the
 * frame's gameplay reach and not the size of its drawing - {@code MODE} carries bit 1 so
 * {@code offerCounters} returns immediately, and {@code DATA} carries the shape, the bind, the
 * bound body and the strain as one compound that is <b>replaced and never mutated</b>: mutating
 * the tag {@code syncedData()} hands back does not dirty the accessor, so it desyncs in silence
 * and appears to work whenever some other field happens to change in the same tick.
 *
 * <p>The wielder rides the {@code TARGET} slot, which the Array has no other use for, because
 * {@code OWNER_ID} is written only by {@code SpellEffectEntity.create} and there is no public way
 * to set it on a subclass built outside that package. The server still has the owner UUID and
 * {@code owner()} answers there; the client reads {@code livingTarget()}.
 *
 * <p>It also owns <b>the projectile half of Ward of the Array</b>. That lives here rather than in
 * the passive handler because an arrow has to be turned before it lands rather than discounted
 * after it has, and because the only 20 Hz box query in the whole kit is then gated on
 * {@code EXTRA != 0} - a player with no Array and a player who does not own the class both pay
 * nothing for it.
 */
public class SwordArrayEntity extends SpellEffectEntity {

    /** How far out a closing projectile is answered. */
    public static final double WARD_SCAN = 8.0D;

    /**
     * How many stations may turn something in one tick.
     *
     * <p>Without it an arrow storm empties a twelve-station Array inside a single frame, which is
     * the shape of every guard that looks generous and is actually a delete button.
     */
    public static final int WARD_PER_TICK = 2;

    /** What one interception costs the station that made it. The bearing survives; the metal does not. */
    public static final int WARD_EDGE_COST = 1;

    /** Mode bit 1: a child of a controller, never offered as a counter. */
    public static final byte MODE_SILENT = 2;

    public static final String TAG_SHAPE = "Shape";
    public static final String TAG_BIND = "Bind";
    public static final String TAG_BOUND = "Bound";
    public static final String TAG_STRAIN = "Strain";

    /** Where the mirrored half of the manned mask starts. Twelve real bits, then twelve twins. */
    public static final int MIRROR_SHIFT = 12;

    private static final int FLARE_LIFE = 6;

    /** See {@link #behavior()}: the formation's tick is written out in full and dispatches to nothing. */
    private static final SpellBehavior NOTHING = entity -> { };

    /** The same flare the Halo verse puts on a turned arrow, at the same size. */
    private static final float FLARE_SCALE = 0.22F;

    public SwordArrayEntity(EntityType<? extends SwordArrayEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Raises the formation for one wielder.
     *
     * <p>Built through a hand-written tag rather than {@code SpellEffectEntity.create}, because
     * {@code create} answers a {@code SpellEffectEntity} and its save hooks are package-protected
     * to {@code entity.fx} - the trick {@code EldritchConstructEntity} uses is only open to a
     * class living in that package. The tag carries the four fields the superclass would have
     * filled in, and {@code Life} is <b>zero</b>: a life above zero makes {@code tick} call
     * {@code finish()} the moment {@code tickCount} reaches it, and this entity outlives every
     * clock in the kit.
     */
    public static SwordArrayEntity spawn(ServerLevel level, ServerPlayer wielder) {
        SwordArrayEntity entity = new SwordArrayEntity(MagicalEntities.SWORD_ARRAY.get(), level);
        CompoundTag tag = new CompoundTag();
        tag.putString("SkillId", MagicContent.CALL_THE_BLADE.id().toString());
        tag.putUUID("Owner", wielder.getUUID());
        tag.putInt("Life", 0);
        tag.putByte("Mode", MODE_SILENT);
        tag.putFloat("Radius", 0.0F);
        entity.readAdditionalSaveData(tag);
        entity.setTarget(wielder);
        Frame frame = SwordService.frame(wielder);
        entity.setPos(frame.x(), frame.y(), frame.z());
        level.addFreshEntity(entity);
        return entity;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level) || isRemoved()) {
            return;
        }
        if (!(owner() instanceof ServerPlayer wielder) || !wielder.isAlive()) {
            discard();
            return;
        }
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        SwordArray array = state.swordArray();
        if (array.isEmpty()) {
            discard();
            return;
        }
        Frame frame = SwordService.frame(wielder);
        follow(frame);
        writePicture(array, SwordService.bind(wielder), SwordService.boundId(wielder),
                SwordService.strain(wielder), mirrored(state));
        ward(level, wielder, state, array, frame);
    }

    /** The frame, every tick, and nothing else: the twelve positions are arithmetic on both sides. */
    private void follow(Frame frame) {
        setPos(frame.x(), frame.y(), frame.z());
        setValue(frame.scale());
        setDirection(Vec3.directionFromRotation(frame.pitch(), frame.yaw()));
        // The gameplay reach is not the size of the drawing, and ProfileRendererShell inflates a
        // silhouette to whatever radius it is handed.
        setRadius(0.0F);
    }

    /**
     * The shape, the bind, the bound body and the strain, written only when one of them moves.
     *
     * <p>A compound compared before it is set, because this is the one slot on the entity that is
     * not a primitive and re-sending twelve packed ints twenty times a second to everybody in
     * range would undo the whole reason there is one entity here rather than twelve.
     */
    public void writePicture(SwordArray array, Bind bind, int boundId, int strain, boolean mirrored) {
        setExtra(mask(array, mirrored));
        CompoundTag data = new CompoundTag();
        int stations = Math.min(array.size(), SwordArray.MAX_STATIONS);
        int[] shape = new int[stations];
        for (int i = 0; i < stations; i++) {
            Station station = array.station(i);
            shape[i] = station == null ? 0 : station.packed();
        }
        data.putIntArray(TAG_SHAPE, shape);
        data.putByte(TAG_BIND, (byte) bind.ordinal());
        data.putInt(TAG_BOUND, boundId);
        data.putInt(TAG_STRAIN, strain);
        if (!data.equals(syncedData())) {
            setSyncedData(data);
        }
    }

    // ---- the mask ------------------------------------------------------------------------------

    /**
     * Which bearings have metal on them: bits 0..11 the wielder's own, bits 12..23 their twins.
     *
     * <p>The twin arithmetic is {@link Projection#mirror}'s, restated here because that method
     * answers a list of stations and the mask needs to know <em>which slot</em> each twin belongs
     * to. It is the same construction and the same dedupe - a twin that landed on a bearing the
     * wielder actually wrote is dropped - and {@code ProjectionTest} is what pins the arithmetic
     * both copies run.
     */
    private static int mask(SwordArray array, boolean mirrored) {
        int mask = 0;
        int stations = Math.min(array.size(), SwordArray.MAX_STATIONS);
        for (int i = 0; i < stations; i++) {
            Station station = array.station(i);
            if (station != null && station.manned()) {
                mask |= 1 << i;
            }
        }
        if (!mirrored) {
            return mask;
        }
        List<Station> twins = Projection.mirror(array);
        for (int i = 0; i < stations; i++) {
            Station station = array.station(i);
            if (station != null && station.manned() && twins.contains(twinOf(station))) {
                mask |= 1 << (MIRROR_SHIFT + i);
            }
        }
        return mask;
    }

    /** {@code yaw + 12}, pitch negated, reach unchanged, Edge halved with a floor of one. */
    private static Station twinOf(Station station) {
        return new Station((station.yaw() + Station.YAW_STEPS / 2) % Station.YAW_STEPS,
                -station.pitch(), station.reach(), Math.max(1, station.edge() / 2));
    }

    private static boolean mirrored(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.MIRROR_OF_THE_ARRAY.id());
    }

    // ---- Ward of the Array, the projectile half -------------------------------------------------

    /**
     * Every closing projectile that arrives down a manned bearing, turned by that bearing.
     *
     * <p>No targeting of its own, because <em>the shape already is the choice</em>: a blow is
     * answered if and only if it comes in within {@link Projection#WARD_CONE} degrees of a bearing
     * the wielder chose to man, so the wielder who spent four presses covering their flanks is the
     * one whose flanks are covered. The arrow is theirs afterwards, exactly as the Halo verse
     * leaves it.
     *
     * <p>{@link #WARD_PER_TICK} stations at most, and a station may answer once a tick: an arrow
     * storm cannot empty the Array in a frame, and one very fast volley cannot take four Edge off
     * one bearing between two ticks of anything else.
     */
    private void ward(ServerLevel level, ServerPlayer wielder, PlayerMagicState state,
            SwordArray array, Frame frame) {
        if (extra() == 0 || !state.isPassiveEnabled(MagicPassiveContent.WARD_OF_THE_ARRAY.id())) {
            return;
        }
        Vec3 centre = new Vec3(frame.x(), frame.y(), frame.z());
        AABB box = new AABB(centre, centre).inflate(WARD_SCAN);
        boolean[] used = new boolean[Math.max(1, array.size())];
        int turned = 0;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box,
                shot -> shot.isAlive() && shot.getOwner() != wielder)) {
            if (turned >= WARD_PER_TICK) {
                break;
            }
            Vec3 offset = projectile.position().subtract(centre);
            if (offset.lengthSqr() > WARD_SCAN * WARD_SCAN
                    || !VersePassives.closing(projectile.getDeltaMovement(), offset)) {
                continue;
            }
            int slot = Projection.covers(array, frame, new double[] {offset.x, offset.y, offset.z});
            if (slot < 0 || slot >= used.length || used[slot]) {
                continue;
            }
            Station station = array.station(slot);
            if (station == null || !station.manned()) {
                continue;
            }
            if (!projectile.deflect(ProjectileDeflection.REVERSE, wielder, wielder, true)) {
                continue;
            }
            used[slot] = true;
            turned++;
            SwordService.detach(wielder, state, slot, WARD_EDGE_COST);
            flare(level, projectile.position());
        }
        if (turned > 0) {
            state.sync(wielder);
        }
    }

    private static void flare(ServerLevel level, Vec3 at) {
        int color = MagicContent.CALL_THE_BLADE.color();
        level.addFreshEntity(SkillClashEffectEntity.create(level, at, color, color, FLARE_LIFE, FLARE_SCALE));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 1.5F);
    }

    // ---- entity contract -------------------------------------------------------------------------

    /**
     * No {@link SpellBehavior}, ever.
     *
     * <p>The formation carries Call the Blade's id so it wears that skill's visual profile, and
     * {@code SpellEffectEntity.tick} dispatches to whatever behaviour is registered under the id
     * it is carrying. The day somebody registers one for Call the Blade - a cast circle, a flash -
     * every standing Array would silently start running it as well.
     */
    @Override
    public SpellBehavior behavior() {
        return NOTHING;
    }

    /**
     * Never written to disk. An override rather than the builder's {@code noSave()} flag, because
     * copying a neighbouring registration in {@code MagicalEntities} gets you saving by default -
     * only two entity types in the mod use the flag - and a formation that survived a restart
     * would be a formation whose live half had not.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
