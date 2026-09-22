package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The half of the Sword Summoner that is never saved: the frame, the metal lying in the world, the
 * blades in the air, the recovery clocks and the enforcement pass.
 *
 * <p>{@link SwordArray} is what the wielder owns - the bearings they authored and the Edge on each
 * - and it rides on {@code PlayerMagicState} with everything else they own. Everything here is
 * dropped on logout and rebuilt from nothing on a change of dimension, which is the Pile's
 * decision taken for the Pile's reasons: the blades are matter out in the world and the enemy can
 * break them, a wielder must not be able to leave a forest of planted swords on a shared server,
 * the save format never has to learn what a blade is, and nothing can grow without bound across
 * sessions. What the wielder keeps is the shape.
 *
 * <p><b>Keyed by UUID and dimension, and the dimension is re-checked on every read.</b>
 * {@code ClassPassiveHandler.forget} is wired to logout only, despite its interface javadoc, so a
 * wielder who steps through a portal would otherwise be holding a frame anchored to a point in a
 * level they have left and a bind on an entity id that now means something else entirely.
 *
 * <p>The other thing this file exists for is {@link #payFor}. Read its note before adding a skill.
 */
public final class SwordService {

    /** The offset from a player's feet to the frame origin: their body centre, not their eyes. */
    public static final double BODY_CENTRE = 0.9D;

    /** One point of spent Edge walks back to loose this often. The class is unplayable without it. */
    public static final int RECOVERY_TICKS = 40;

    /** ...and this often with Returning, which is the rung at which a deliberate shed is repeatable. */
    public static final int RECOVERY_TICKS_RETURNING = 20;

    /** Walk this close to your own sword and you take the whole of it back in a stride. */
    public static final double PICKUP_RANGE = 2.0D;

    /** Past this the bound body has got away and the leash lets go. */
    public static final double BIND_BREAK = 40.0D;

    /** Past this a frame you set down snaps back to your body and every blade flies home. */
    public static final double KEEL_LEASH = 24.0D;

    /** Sword God bleeds this often, {@link #STRAIN_BLEED_AMOUNT} for every full block of strain. */
    public static final int STRAIN_BLEED_INTERVAL = 40;

    public static final int STRAIN_BLEED_PER = 20;

    public static final float STRAIN_BLEED_AMOUNT = 1.0F;

    /** A strained Array hums, and vanilla's range for a volume above one is {@code volume * 16}. */
    public static final int HUM_INTERVAL = 40;

    public static final float HUM_VOLUME = 2.0F;

    /** How far off the line home a body may stand and still be cut by a shedding blade. */
    public static final double SHED_SLACK = 0.45D;

    /** How far down a blade looks for a floor. Below's blades stand where the ground is. */
    public static final int SURFACE_SEARCH = 8;

    /** How fast a shed blade cuts its line home, in blocks a tick. */
    public static final double SHED_SPEED = 1.2D;

    /**
     * The live half of one wielder, in one dimension.
     *
     * <p>A mutable class rather than a record because every field here moves and none of it is
     * ever compared, serialised or handed out. The only thing that leaves this file is answers.
     */
    private static final class Held {
        private final ResourceKey<Level> dimension;
        private final LevelSwordWorld world;

        /** Where the origin is attached. HELD is the resting state and the only one that is free. */
        private Bind bind = Bind.HELD;

        /**
         * The frozen half of the frame: origin and facing for SET and SUNK, facing alone for
         * BOUND, and nothing at all for HELD and RIDDEN, which read the wielder every time.
         */
        private Frame anchor = new Frame(0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 1.0F);

        private int boundId = -1;
        private int riddenSlot = -1;

        /** Metal lying in the world: in the air, standing in a body, standing in the ground. */
        private int spent;

        /** What the last enforcement pass could not settle. Non-zero only at Sword God. */
        private int strain;

        private float scale = 1.0F;

        /** The one entity the whole formation costs, remembered so the slow tick never sweeps. */
        private int arrayEntityId = -1;

        private long nextRecovery;
        private long nextBleed;
        private long nextHum;

        private Held(ResourceKey<Level> dimension, LevelSwordWorld world) {
            this.dimension = dimension;
            this.world = world;
        }
    }

    private static final Map<UUID, Held> WIELDERS = new HashMap<>();

    private SwordService() {
    }

    // ---- the live half ------------------------------------------------------------------------

    private static Held held(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        Held existing = WIELDERS.get(player.getUUID());
        if (existing != null && existing.dimension.equals(dimension)) {
            return existing;
        }
        Held fresh = new Held(dimension, new LevelSwordWorld(player.serverLevel()));
        WIELDERS.put(player.getUUID(), fresh);
        return fresh;
    }

    /** Nothing of a Sword Summoner outlives them here. Wired beside {@code PileService.forget}. */
    public static void forget(UUID wielder) {
        WIELDERS.remove(wielder);
    }

    public static void clear() {
        WIELDERS.clear();
    }

    public static LevelSwordWorld world(ServerPlayer player) {
        return held(player).world;
    }

    // ---- the rung -----------------------------------------------------------------------------

    /** True while the wielder is on the chain at all. Everything else here is free for everyone. */
    public static boolean holds(PlayerMagicState state) {
        return state.hasClass(MagicalClasses.SWORD_SUMMONER);
    }

    /** Which rung, off class progress. 0 for a wielder who is not on the chain, which is a floor. */
    public static int rung(PlayerMagicState state) {
        if (state.hasClass(MagicalClasses.SWORD_GOD)) {
            return 3;
        }
        if (state.hasClass(MagicalClasses.SWORD_SAINT)) {
            return 2;
        }
        if (state.hasClass(MagicalClasses.SWORD_RIDER)) {
            return 1;
        }
        return 0;
    }

    public static SwordRules rulesFor(PlayerMagicState state) {
        return SwordRules.forRung(rung(state));
    }

    /**
     * Puts the rung the wielder has climbed onto the Array they loaded.
     *
     * <p><b>The saved Array does not carry its rules.</b> {@code SwordArray.save} writes the
     * version and the packed stations and nothing else, deliberately - no enum ordinal is written
     * anywhere in this kit - so a fresh {@code SwordArray} stands up on {@link SwordRules#SUMMONER}
     * whoever it belongs to. Without this call a Sword God logs back in holding four stations, 24
     * of draw and a whole of 8, and the first thing {@code setRules} would do on the next raise is
     * re-filter their twelve-station shape down to fit the base rung. So it is called on login and
     * on every class evolve, and the slow tick asks again because it costs a comparison.
     *
     * <p>{@code setRules} re-runs the plant rules, which is what makes a rung <em>drop</em> safe
     * too: a shape the new caps cannot hold falls off rather than sitting there illegal.
     */
    public static void refreshRung(ServerPlayer player, PlayerMagicState state) {
        SwordRules wanted = rulesFor(state);
        SwordArray array = state.swordArray();
        if (wanted.equals(array.rules())) {
            return;
        }
        array.setRules(wanted);
        state.sync(player);
    }

    // ---- the frame ----------------------------------------------------------------------------

    public static Bind bind(ServerPlayer player) {
        return held(player).bind;
    }

    public static int boundId(ServerPlayer player) {
        return held(player).boundId;
    }

    /** Which station's blade is under the wielder's feet, or -1. Only meaningful while RIDDEN. */
    public static int riddenSlot(ServerPlayer player) {
        return held(player).riddenSlot;
    }

    public static float scale(ServerPlayer player) {
        return held(player).scale;
    }

    /**
     * Where the Array is hanging, right now.
     *
     * <p>Built rather than stored, because two of the five binds are functions of a body that
     * moves every tick and storing them would be a copy that is wrong by one tick forever. The
     * scale is the one part that is stored, because it is the slow tick's answer and a bill that
     * moved inside a tick would let a settle and the strain it settled disagree.
     */
    public static Frame frame(ServerPlayer player) {
        Held wielder = held(player);
        return switch (wielder.bind) {
            // The wielder is pinned to a ridden frame, so it is their own body either way.
            case HELD, RIDDEN -> new Frame(player.getX(), player.getY() + BODY_CENTRE, player.getZ(),
                    player.getYRot(), player.getXRot(), wielder.scale);
            case SET, SUNK -> wielder.anchor.withScale(wielder.scale);
            case BOUND -> boundFrame(player, wielder);
        };
    }

    /**
     * A bound frame keeps the facing it was bound with and takes its origin off the body.
     *
     * <p>The facing is frozen because Loose fires the forward projection at the instant of the
     * press: a facing that followed the wielder's head afterwards would quietly re-aim a shape
     * that is supposed to have been committed, and the blades already in the air would swing.
     */
    private static Frame boundFrame(ServerPlayer player, Held wielder) {
        LivingEntity body = boundBody(player, wielder);
        if (body == null) {
            return wielder.anchor.withScale(wielder.scale);
        }
        Vec3 centre = body.getBoundingBox().getCenter();
        return wielder.anchor.withOrigin(centre.x, centre.y, centre.z).withScale(wielder.scale);
    }

    private static LivingEntity boundBody(ServerPlayer player, Held wielder) {
        if (wielder.boundId < 0) {
            return null;
        }
        return player.serverLevel().getEntity(wielder.boundId) instanceof LivingEntity living && living.isAlive()
                ? living : null;
    }

    /** Back to the resting state: the origin is your body centre and the facing is your look. */
    public static void hold(ServerPlayer player) {
        Held wielder = held(player);
        wielder.bind = Bind.HELD;
        wielder.boundId = -1;
        wielder.riddenSlot = -1;
        wielder.scale = 1.0F;
    }

    /** The Keel, pressed: origin and facing freeze where they were and you walk out of them. */
    public static void setDown(ServerPlayer player) {
        Held wielder = held(player);
        wielder.anchor = new Frame(player.getX(), player.getY() + BODY_CENTRE, player.getZ(),
                player.getYRot(), player.getXRot(), 1.0F);
        wielder.bind = Bind.SET;
        wielder.boundId = -1;
        wielder.riddenSlot = -1;
        wielder.scale = 1.0F;
    }

    /** The Keel, sneak-pressed: one station's blade goes under your feet and carries you. */
    public static void ride(ServerPlayer player, int slot) {
        Held wielder = held(player);
        wielder.bind = Bind.RIDDEN;
        wielder.riddenSlot = slot;
        wielder.boundId = -1;
        wielder.scale = 1.0F;
    }

    /** Loose: the origin goes onto somebody else, and from Sword Saint their footwork spends you. */
    public static void bindTo(ServerPlayer player, LivingEntity body) {
        Held wielder = held(player);
        wielder.anchor = new Frame(body.getX(), body.getY(), body.getZ(),
                player.getYRot(), player.getXRot(), 1.0F);
        wielder.bind = Bind.BOUND;
        wielder.boundId = body.getId();
        wielder.riddenSlot = -1;
    }

    /**
     * Below: the origin is a point recorded once and never re-acquired.
     *
     * <p>The pitch reflection the bind's name describes is not in the {@link Frame} and must not
     * be: a frame is an origin, a facing and a scale, and the reflection is a property of the
     * stations Below reads. {@link Projection#below} takes no frame for exactly that reason.
     */
    public static void sink(ServerPlayer player, Vec3 point) {
        Held wielder = held(player);
        wielder.anchor = new Frame(point.x, point.y, point.z, player.getYRot(), player.getXRot(), 1.0F);
        wielder.bind = Bind.SUNK;
        wielder.boundId = -1;
        wielder.riddenSlot = -1;
        wielder.scale = 1.0F;
    }

    // ---- the Edge -----------------------------------------------------------------------------

    /** Metal lying in the world: in the air, standing in a body, standing in the ground. */
    public static int spent(ServerPlayer player) {
        return held(player).spent;
    }

    /** {@code whole - bound - spent}: what is left in the wielder to call another blade with. */
    public static int loose(ServerPlayer player, PlayerMagicState state) {
        return state.swordArray().loose(held(player).spent);
    }

    /**
     * Takes metal off a station and puts it in the world, answering how much actually came off.
     *
     * <p>The one door out of a station, and the reason conservation holds without anybody
     * counting: a blade in the air, a blade standing in a body and a blade standing in the ground
     * are all <em>spent</em>, so a detach is a single move between two of the three places and
     * every skill that throws steel goes through here. The bearing is untouched - the shape
     * survives, the metal does not.
     */
    public static int detach(ServerPlayer player, PlayerMagicState state, int slot, int amount) {
        int taken = state.swordArray().spend(slot, amount);
        if (taken > 0) {
            held(player).spent += taken;
        }
        return taken;
    }

    /** Metal that appeared in the world without leaving a station - the settle has already spent it. */
    public static void addSpent(ServerPlayer player, int edge) {
        if (edge > 0) {
            held(player).spent += edge;
        }
    }

    /**
     * Metal coming home: out of the world and back into the wielder.
     *
     * <p>Never below zero, and then a conservation repair. A rung dropped or an Array cleared
     * under a blade that was still in the air can leave {@code bound + spent} above the whole,
     * which reads as a <em>negative</em> loose - and metal in the world is the half that gives
     * way, because the stations are the half the wielder authored. Conservation is the one rule
     * in this structure that nothing may bend, so it is repaired here rather than reported.
     */
    public static void recover(ServerPlayer player, PlayerMagicState state, int edge) {
        if (edge <= 0) {
            return;
        }
        Held wielder = held(player);
        wielder.spent = Math.max(0, wielder.spent - edge);
        int over = state.swordArray().bound() + wielder.spent - state.swordArray().whole();
        if (over > 0) {
            wielder.spent = Math.max(0, wielder.spent - over);
        }
    }

    /** What the last enforcement pass could not settle. Non-zero only under Sword God's rule. */
    public static int strain(ServerPlayer player) {
        return held(player).strain;
    }

    /** For {@code /magical array strain}: the gauge, forced, with no bind walked out to earn it. */
    public static void forceStrain(ServerPlayer player, int strain) {
        held(player).strain = Math.max(0, strain);
    }

    // ---- the one billing call -------------------------------------------------------------------

    /**
     * The cooldown and the mana for one press of this kit, and whether the press may go ahead.
     *
     * <p>Three of the six are registered {@code selfManaged} or {@code holdGated}, and both of
     * those return out of {@code MagicCastingService.castViaRegistry} <em>before</em> it resolves a
     * stat, spends a point of mana, casts the aim ray or starts a clock. That is the whole point of
     * such a handler - it is how a hold bills on release rather than on press - but it means every
     * number on those definitions is decoration until somebody writes this call. The entire
     * Authority of Causality shipped costing nothing because nobody had, and nothing in the build
     * said so: a cost that is never taken looks exactly like a cost that is never needed.
     *
     * <p>Call it <b>after</b> the skill has established it has work to do and <b>before</b> it does
     * it, so a press that refuses itself is never charged. {@code manaOverride} is for the two
     * prices that are not a constant - Call the Blade's {@code 4 + 2n} pour and One Blade's
     * {@code 6} a station - and anything negative means "the definition's own".
     *
     * <p>And note: {@code ctx.aim()} is <b>null</b> in a self-managed or hold-gated handler. A line
     * copied from a plain-press skill that dereferences {@code ctx.aim().point()} throws,
     * {@code castViaRegistry} turns that into {@code CastResult.FAILED}, and the wielder is handed
     * a refund of mana that was never spent.
     */
    public static boolean payFor(ServerPlayer player, PlayerMagicState state,
            MagicSkillDefinition skill, int manaOverride) {
        if (state.isSkillOnCooldown(skill.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return false;
        }
        MagicSkillResolvedStats stats = skill.resolve(state.tuningFor(skill.id()));
        int mana = manaOverride >= 0 ? manaOverride : stats.manaCost();
        if (!MagicSinService.spendManaForSkill(player, state, mana)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        state.setSkillCooldown(skill.id(), stats.cooldownTicks());
        return true;
    }

    // ---- the enforcement pass ---------------------------------------------------------------

    /**
     * One Blade's gather: the bill collapsed rather than the budget raised.
     *
     * <p>{@code frame.scale -> 0} drives the bill to zero, and the only route to a zero bill in
     * this structure is every station letting go - so the fusion is {@link SwordArray#fuse}, which
     * is {@code settle()} at the far end of its own range, and not a second unmanning with its own
     * arithmetic to get wrong. Answers the Edge that came off, which is what the greatsword is
     * made of and what every one of its numbers is a function of.
     */
    public static int collapse(ServerPlayer player, PlayerMagicState state) {
        SwordArray array = state.swordArray();
        int[] before = edges(array);
        Settlement settlement = array.fuse();
        int gathered = 0;
        for (int slot : settlement.shedSlots()) {
            gathered += slot >= 0 && slot < before.length ? before[slot] : 0;
        }
        addSpent(player, gathered);
        held(player).strain = settlement.strainLeft();
        return gathered;
    }

    /**
     * The whole of the live half in one pass, every {@code SLOW_TICK_INTERVAL} ticks.
     *
     * <p>It runs here rather than on the player tick for the reason cooldowns have their own
     * payload: {@code sync()} serialises the entire state on every call and the {@code
     * lastSyncedTag} equality check suppresses the <em>packet</em>, not the work. So everything
     * that moves is gathered up and {@code state.sync} is called once at the end behind a boolean,
     * which is the {@code VersePassives.turn} shape.
     */
    public static void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (!holds(state)) {
            return;
        }
        refreshRung(player, state);
        Held wielder = held(player);
        boolean dirty = releaseBrokenBind(player, wielder);
        dirty |= settle(player, state, wielder);
        bleed(player, wielder);
        dirty |= recoverOne(player, state, wielder);
        dirty |= walkOver(player, state, wielder);
        tendArrayEntity(player, state);
        if (dirty) {
            state.sync(player);
        }
    }

    /**
     * A bind is a leash and a leash has an end: the body dies, leaves the dimension, or gets
     * 40 blocks away. A frame set down has its own, and past it the whole Array comes home.
     */
    private static boolean releaseBrokenBind(ServerPlayer player, Held wielder) {
        if (wielder.bind == Bind.BOUND) {
            double distance = wielder.world.distanceToBound(wielder.boundId, at(player));
            if (distance < 0.0D || distance > BIND_BREAK) {
                hold(player);
                return true;
            }
            return false;
        }
        if (wielder.bind == Bind.SET) {
            Vec3 origin = new Vec3(wielder.anchor.x(), wielder.anchor.y(), wielder.anchor.z());
            if (origin.distanceTo(player.getBoundingBox().getCenter()) > KEEL_LEASH) {
                recallEverything(player);
                hold(player);
                return true;
            }
        }
        return false;
    }

    /**
     * The scale, the strain, and what an over-stretched Array does about it.
     *
     * <p>The scale only moves at Sword Saint and above: {@code freeScale} is what makes a bound
     * opponent's footwork spend the wielder's budget, and before that rung a bind is a re-anchored
     * origin and nothing more. Everything after that is {@link SwordArray#settle}, which is
     * arithmetic and is pinned on exact values with no world under it.
     */
    private static boolean settle(ServerPlayer player, PlayerMagicState state, Held wielder) {
        SwordArray array = state.swordArray();
        float scale = 1.0F;
        if (wielder.bind == Bind.BOUND && array.rules().freeScale()) {
            double distance = wielder.world.distanceToBound(wielder.boundId, at(player));
            if (distance >= 0.0D) {
                scale = ArrayPose.boundScale(distance);
            }
        }
        wielder.scale = scale;

        int[] before = edges(array);
        Settlement settlement = array.settle(array.strainAt(scale), array.rules().overdraw());
        wielder.strain = settlement.strainLeft();
        if (!settlement.shedAnything()) {
            return false;
        }
        Frame frame = frame(player);
        for (int slot : settlement.shedSlots()) {
            shed(player, state, frame, slot, slot >= 0 && slot < before.length ? before[slot] : 0);
        }
        return true;
    }

    /**
     * One blade cutting the line home, and the picture the whole class is remembered for.
     *
     * <p>Its Edge is spent the instant it leaves the station - a blade in the air is metal in the
     * world - and whether it comes back as loose or as spent is Returning's business, decided when
     * it arrives rather than here. The wielder is not cut by their own line home; everything else
     * standing in it is, by how long the line was.
     */
    private static void shed(ServerPlayer player, PlayerMagicState state, Frame frame, int slot, int edge) {
        if (edge <= 0) {
            return;
        }
        addSpent(player, edge);
        Station station = state.swordArray().station(slot);
        if (station == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double[] offset = ArrayPose.worldOffset(station, frame);
        Vec3 from = new Vec3(frame.x() + offset[0], frame.y() + offset[1], frame.z() + offset[2]);
        Vec3 to = player.getBoundingBox().getCenter();
        float damage = (float) SwordMath.shedDamage(from.distanceTo(to));
        for (LivingEntity victim : held(player).world.bodiesOnLine(from, to, SHED_SLACK,
                body -> body.isAlive() && body != player)) {
            SkillTargets.hurt(level, player, victim, damage, MagicContent.CALL_THE_BLADE.id());
            SkillTargets.shove(victim, from, 0.2D, 0.05D);
        }
        SwordBladeEntity.shedHome(level, player, from, to, slot, edge, SHED_SPEED);
    }

    /** Sword God's bill, and the only damage in the mod no armour and no barrier may take a slice of. */
    private static void bleed(ServerPlayer player, Held wielder) {
        if (wielder.strain <= 0) {
            return;
        }
        long now = wielder.world.now();
        if (now >= wielder.nextHum) {
            wielder.nextHum = now + HUM_INTERVAL;
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, HUM_VOLUME, 0.55F);
        }
        if (now < wielder.nextBleed) {
            return;
        }
        wielder.nextBleed = now + STRAIN_BLEED_INTERVAL;
        float amount = STRAIN_BLEED_AMOUNT * (wielder.strain / STRAIN_BLEED_PER);
        if (amount > 0.0F) {
            player.hurt(SwordDamageTypes.strain(player), amount);
        }
    }

    /** The slow route back: one point of spent Edge a clock tick, twice as fast with Returning. */
    private static boolean recoverOne(ServerPlayer player, PlayerMagicState state, Held wielder) {
        if (wielder.spent <= 0) {
            return false;
        }
        long now = wielder.world.now();
        if (now < wielder.nextRecovery) {
            return false;
        }
        wielder.nextRecovery = now + (returning(state) ? RECOVERY_TICKS_RETURNING : RECOVERY_TICKS);
        wielder.spent--;
        return true;
    }

    /**
     * The fast route back, and it is a place rather than a clock: walk to where your swords died.
     *
     * <p>Which is by construction the ground you were just losing, so the recovery the class is
     * built around is a reason to go forward. Unconditional - Returning changes the clock and where
     * a shed blade credits, not this.
     */
    private static boolean walkOver(ServerPlayer player, PlayerMagicState state, Held wielder) {
        AABB box = player.getBoundingBox().inflate(PICKUP_RANGE);
        List<SwordBladeEntity> lying = SwordBladeEntity.ownedBy(player.serverLevel(), player, box);
        boolean took = false;
        for (SwordBladeEntity blade : lying) {
            if (!blade.lying()) {
                continue;
            }
            recover(player, state, blade.edge());
            blade.discard();
            took = true;
        }
        if (took) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        return took;
    }

    /** True when the wielder owns Returning and has not switched it off in the codex. */
    public static boolean returning(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.RETURNING.id());
    }

    // ---- the entity ---------------------------------------------------------------------------

    /**
     * The one entity twelve swords cost, spawned on demand and discarded with the last bearing.
     *
     * <p>Twelve blade positions are worth <b>zero bytes</b> on the wire: {@link ArrayPose} is pure
     * and both sides run it, so the client is told the frame and the shape - both of which change
     * rarely - and works out the rest. Twelve entities at the standard tracking range would be
     * twelve movement packets a tick to every observer for a picture that is one rigid formation.
     */
    public static SwordArrayEntity arrayEntity(ServerPlayer player) {
        Held wielder = held(player);
        if (wielder.arrayEntityId >= 0
                && player.serverLevel().getEntity(wielder.arrayEntityId) instanceof SwordArrayEntity entity
                && !entity.isRemoved()) {
            return entity;
        }
        wielder.arrayEntityId = -1;
        return null;
    }

    /**
     * Raises the formation when there is a shape to draw and drops it when there is not.
     *
     * <p>Public because the slow tick is ten ticks wide and a wielder who has just called their
     * first blade should not spend half a second looking at nothing: Call the Blade calls this
     * the moment a plant is accepted.
     */
    public static void tendArrayEntity(ServerPlayer player, PlayerMagicState state) {
        SwordArrayEntity entity = arrayEntity(player);
        if (state.swordArray().isEmpty()) {
            if (entity != null) {
                entity.discard();
            }
            held(player).arrayEntityId = -1;
            return;
        }
        if (entity == null) {
            held(player).arrayEntityId = SwordArrayEntity.spawn(player.serverLevel(), player).getId();
        }
    }

    /** Every blade this wielder has out, called home. The leash and a rung drop both end here. */
    public static void recallEverything(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(KEEL_LEASH * 2.0D);
        for (SwordBladeEntity blade : SwordBladeEntity.ownedBy(player.serverLevel(), player, box)) {
            blade.recall();
        }
    }

    // ---- small shared arithmetic ----------------------------------------------------------------

    /** The wielder's body centre as the three doubles {@link SwordWorld} asks for. */
    public static double[] at(ServerPlayer player) {
        return new double[] {player.getX(), player.getY() + BODY_CENTRE, player.getZ()};
    }

    /** Every station's Edge before a settle, because a {@link Settlement} carries slots and not metal. */
    public static int[] edges(SwordArray array) {
        int[] out = new int[array.size()];
        for (int i = 0; i < out.length; i++) {
            Station station = array.station(i);
            out[i] = station == null ? 0 : station.edge();
        }
        return out;
    }
}
