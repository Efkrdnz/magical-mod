package com.efkrdnz.magical.entity.sword;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.Bind;
import com.efkrdnz.magical.magic.sword.Frame;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.sword.stance.StanceWatchService;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * One entity for twelve swords: the formation at rest, and the wielder's guard.
 *
 * <p><b>The resting case is one entity, and that is the whole reason this type exists.</b>
 * {@code Formation.place} is pure and both the server behaviour and the client painter run it with
 * identical arguments, so a sword's position costs <em>nothing</em> on the wire: the client is
 * told the stance, the present mask and the frame - all of which change rarely - and works the
 * twelve positions out itself. The naive alternative is twelve entities at the standard tracking
 * range, which is twelve movement packets a tick to every observer within 256 blocks for a picture
 * that is one rigid formation, and four players with their steel out would be forty-eight of them.
 *
 * <p><b>Zero new {@code EntityDataAccessor}s.</b> It rides the slots {@link SpellEffectEntity}
 * already has: {@code EXTRA} is the present mask (bit <i>i</i> set means sword <i>i</i> is in
 * formation), {@code VALUE} the frame scale, {@code DIR} the frame facing, {@code RADIUS} held at
 * zero - the renderer must zero it again, because the radius a sword entity carries is the frame's
 * gameplay reach and not the size of its drawing - {@code MODE} carries bit 1 so
 * {@code offerCounters} returns immediately, and {@code DATA} carries the stance and the bind as
 * one compound that is <b>replaced and never mutated</b>: mutating the tag {@code syncedData()}
 * hands back does not dirty the accessor, so it desyncs in silence and appears to work whenever
 * some other field happens to change in the same tick.
 *
 * <p>The wielder rides the {@code TARGET} slot, which the formation has no other use for, because
 * {@code OWNER_ID} is written only by {@code SpellEffectEntity.create} and there is no public way
 * to set it on a subclass built outside that package. The server still has the owner UUID and
 * {@code owner()} answers there; the client reads {@code livingTarget()}.
 *
 * <p>It is also <b>the gate on {@link StanceWatchService}</b>, and that is not incidental. The
 * Watch has to run every tick - an arrow must be turned before it lands, not discounted after -
 * and this entity exists only while the steel is out, so a wielder with no class and a wielder
 * with their swords away both pay nothing for any of it.
 */
public class SwordArrayEntity extends SpellEffectEntity {

    /** Mode bit 1: a child of a controller, never offered as a counter. */
    public static final byte MODE_SILENT = 2;

    public static final String TAG_STANCE = "Stance";
    public static final String TAG_BIND = "Bind";

    /** See {@link #behavior()}: the formation's tick is written out in full and dispatches to nothing. */
    private static final SpellBehavior NOTHING = entity -> { };

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
     * clock in the kit - it ends when the steel is sheathed and not before.
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
        // The whole frame and not merely the origin, because every synched slot follow() fills is
        // read by the client the instant the spawn packet lands and is written again only by the
        // first server tick. VALUE defaults to zero and VALUE is the frame scale, so a freshly
        // raised formation would be drawn collapsed onto its own origin for one tick; DIR defaults
        // to the zero vector on the same schedule, which is a facing nothing can be turned by.
        entity.follow(SwordService.frame(wielder));
        PlayerMagicState state = wielder.getData(MagicalAttachments.MAGIC_STATE);
        entity.writePicture(state.swordArray().stance(), SwordService.bind(wielder),
                SwordService.presentMask(wielder, state));
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
        if (!state.swordArray().drawn()) {
            // The toggle discards this itself, so reaching here means something else took the
            // steel away - a reset, a rung drop, a load. Off means gone, so go.
            discard();
            return;
        }
        follow(SwordService.frame(wielder));
        writePicture(state.swordArray().stance(), SwordService.bind(wielder),
                SwordService.presentMask(wielder, state));
        StanceWatchService.tick(level, wielder, state);
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
     * The stance, the bind and which swords are present, written only when one of them moves.
     *
     * <p>A compound compared before it is set, because this is the one slot on the entity that is
     * not a primitive and re-sending it twenty times a second to everybody in range would undo
     * the whole reason there is one entity here rather than twelve. The mask is on {@code EXTRA},
     * which is a primitive and dirties itself.
     */
    public void writePicture(SwordStance stance, Bind bind, int presentMask) {
        setExtra(presentMask);
        CompoundTag data = new CompoundTag();
        data.putByte(TAG_STANCE, (byte) stance.ordinal());
        data.putByte(TAG_BIND, (byte) bind.ordinal());
        if (!data.equals(syncedData())) {
            setSyncedData(data);
        }
    }

    // ---- what the client reads -------------------------------------------------------------------

    /** Total: an ordinal that arrived out of range answers the default rather than throwing. */
    public SwordStance stance() {
        return SwordStance.byOrdinal(syncedData().getByte(TAG_STANCE));
    }

    /** Bit <i>i</i> set means sword <i>i</i> is in formation. The gaps are swords that are away. */
    public int presentMask() {
        return extra();
    }

    /** How many swords the client should draw. */
    public int presentCount() {
        return Integer.bitCount(presentMask());
    }

    /**
     * How many slots the formation is laid out for, which is <b>not</b> the number drawn.
     *
     * <p>{@code Formation.place} is told the wielder's whole complement so a sword that has gone
     * away leaves a gap where it stood rather than letting its neighbours close ranks. The
     * highest set bit is the floor on that count and the mask cannot say more than that, which is
     * enough: a wielder whose top sword is away for a moment draws their remaining ones a little
     * more evenly spread, and nothing about that reads as wrong.
     */
    public int slotCount() {
        int mask = presentMask();
        return mask == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(mask);
    }

    // ---- entity contract -------------------------------------------------------------------------

    /**
     * No {@link SpellBehavior}, ever.
     *
     * <p>The formation carries Call the Blade's id so it wears that skill's visual profile, and
     * {@code SpellEffectEntity.tick} dispatches to whatever behaviour is registered under the id
     * it is carrying. The day somebody registers one for Call the Blade - a cast circle, a flash -
     * every standing formation would silently start running it as well.
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
