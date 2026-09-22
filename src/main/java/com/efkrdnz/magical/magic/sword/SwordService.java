package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.entity.sword.SwordBladeEntity;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.efkrdnz.magical.magic.sword.stance.Slot;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The half of the Sword Summoner that is never saved: the frame, which swords are away, the return
 * clocks and the one entity the whole formation costs.
 *
 * <p>{@link SwordArray} is what the wielder owns - a stance and whether the steel is out - and it
 * rides on {@code PlayerMagicState} with everything else they own. Everything here is dropped on
 * logout and rebuilt from nothing on a change of dimension, which is the Pile's decision taken for
 * the Pile's reasons: the swords are matter out in the world and the enemy can break them, a
 * wielder must not be able to leave a forest of planted steel on a shared server, the save format
 * never has to learn what a sword is, and nothing can grow without bound across sessions.
 *
 * <p><b>The resource is one small integer and it is visible.</b> A wielder has
 * {@code rules.swords()} of them; some are <em>present</em>, in formation round their body, and
 * some are <em>away</em> - flying, standing in something, or cutting their line home. An away
 * sword comes back on a clock. There is no Edge, no bill, no draw and no strain, because the thing
 * those three measured was a shape the wielder could not see; you can count these.
 *
 * <p><b>Keyed by UUID and dimension, and the dimension is re-checked on every read.</b>
 * {@code ClassPassiveHandler.forget} is wired to logout only, despite its interface javadoc, so a
 * wielder who steps through a portal would otherwise be holding a frame anchored to a point in a
 * level they have left.
 *
 * <p>The other thing this file exists for is {@link #payFor}. Read its note before adding a skill.
 */
public final class SwordService {

    /** The offset from a player's feet to the frame origin: their body centre, not their eyes. */
    public static final double BODY_CENTRE = 0.9D;

    /** One away sword walks home this often. The class is unplayable without it. */
    public static final int RETURN_TICKS = 60;

    /** ...and this often with Returning, which is the rung at which a volley is repeatable. */
    public static final int RETURN_TICKS_RETURNING = 30;

    /** Walk this close to one of your own fallen swords and you take it back in a stride. */
    public static final double PICKUP_RANGE = 2.0D;

    /** Past this a frame you set down snaps back to your body and every sword flies home. */
    public static final double KEEL_LEASH = 24.0D;

    /** How far off the line home a body may stand and still be cut by a returning sword. */
    public static final double SHED_SLACK = 0.45D;

    /** How far down a sword looks for a floor. Below's swords stand where the ground is. */
    public static final int SURFACE_SEARCH = 8;

    /** How fast a recalled sword cuts its line home, in blocks a tick. */
    public static final double SHED_SPEED = 1.2D;

    /**
     * How long a body the wielder wounded stays the answer to "what should Rain fall on".
     *
     * <p>Five seconds, which is long enough that the follow-up reads as a consequence of the hit
     * and short enough that it cannot be set up in one fight and spent in the next. Rain is the
     * one Watch that is a reaction rather than a sweep, and this is the whole of the reaction.
     */
    public static final int VICTIM_MEMORY_TICKS = 100;

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

        /** The frozen half of the frame: origin and facing for SET and SUNK, nothing otherwise. */
        private Frame anchor = new Frame(0.0D, 0.0D, 0.0D, 0.0F, 0.0F, 1.0F);

        /**
         * Bit <i>i</i> set means sword <i>i</i> is not with the wielder.
         *
         * <p>A mask rather than a count, and that is a picture decision: with a count the present
         * swords would have to be the first <i>n</i> slots, so spending one would re-pack the
         * whole formation and every remaining sword would slide sideways. With a mask a sword
         * leaves a <em>gap</em> where it stood, which is what a missing sword looks like.
         */
        private int awayMask;

        private float scale = 1.0F;

        /** The one entity the whole formation costs, remembered so the slow tick never sweeps. */
        private int arrayEntityId = -1;

        private long nextReturn;
        private long nextWatch;

        /** Mirror of the Array's own clock, so the two Watches cannot starve one another. */
        private long nextMirror;

        /** The stance seen on the previous tick, and the one held before it. See {@code mirror}. */
        private SwordStance lastStance;
        private SwordStance mirror;

        /** What the wielder last wounded, for {@code Watch.DROP}. */
        private int victimId = -1;
        private long victimAt = Long.MIN_VALUE;

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
     * <p><b>The saved Array does not carry its rules.</b> {@code SwordArray.save} writes a version,
     * a stance ordinal and a flag, so a fresh one stands up on {@link SwordRules#SUMMONER} whoever
     * it belongs to. Without this call a Sword God logs back in with four swords and two stances,
     * and the Rain they were standing in is clamped to Guard on the first read - permanently, on
     * disk, because the clamped value is what gets written back.
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

    // ---- the steel ----------------------------------------------------------------------------

    /** How many swords this wielder has in all, present and away together. */
    public static int swords(PlayerMagicState state) {
        return state.swordArray().swords();
    }

    /** Every sword that exists for this wielder, as a mask. Nothing above it is ever read. */
    private static int liveMask(PlayerMagicState state) {
        int count = swords(state);
        return count >= 32 ? -1 : (1 << count) - 1;
    }

    /** Bit <i>i</i> set means sword <i>i</i> is in formation: what the entity syncs and the client draws. */
    public static int presentMask(ServerPlayer player, PlayerMagicState state) {
        if (!state.swordArray().drawn()) {
            return 0;
        }
        return ~held(player).awayMask & liveMask(state);
    }

    /** How many swords are with the wielder. The cap on every volley, and it is on screen. */
    public static int present(ServerPlayer player, PlayerMagicState state) {
        return Integer.bitCount(presentMask(player, state));
    }

    /** How many are flying, standing in something, or cutting their line home. */
    public static int away(ServerPlayer player, PlayerMagicState state) {
        if (!state.swordArray().drawn()) {
            return 0;
        }
        return Integer.bitCount(held(player).awayMask & liveMask(state));
    }

    /**
     * Sends up to {@code want} swords away and answers how many actually went.
     *
     * <p>The lowest present slot first, so the order is deterministic and two clients watching the
     * same volley see the same gaps appear. <b>Answers fewer than asked rather than refusing</b>,
     * which is the whole of the resource model: a wielder down to two swords Looses with two, and
     * nothing anywhere has to carry a separate cap.
     */
    public static int spendSwords(ServerPlayer player, PlayerMagicState state, int want) {
        if (want <= 0 || !state.swordArray().drawn()) {
            return 0;
        }
        Held wielder = held(player);
        int live = liveMask(state);
        int taken = 0;
        while (taken < want) {
            int free = ~wielder.awayMask & live;
            if (free == 0) {
                break;
            }
            wielder.awayMask |= Integer.lowestOneBit(free);
            taken++;
        }
        if (taken > 0) {
            wielder.nextReturn = Math.max(wielder.nextReturn, wielder.world.now() + returnTicks(state));
        }
        return taken;
    }

    /** One sword away, or false when there were none to send. */
    public static boolean spendSword(ServerPlayer player, PlayerMagicState state) {
        return spendSwords(player, state, 1) == 1;
    }

    /** {@code n} swords home at once. A walk-over and a recall both end here. */
    public static void returnSwords(ServerPlayer player, PlayerMagicState state, int n) {
        Held wielder = held(player);
        int live = liveMask(state);
        for (int i = 0; i < n; i++) {
            int gone = wielder.awayMask & live;
            if (gone == 0) {
                return;
            }
            wielder.awayMask &= ~Integer.lowestOneBit(gone);
        }
    }

    /** 60 ticks, 30 with Returning, and halved again by Sword God's relentless rule. */
    public static int returnTicks(PlayerMagicState state) {
        int base = returning(state) ? RETURN_TICKS_RETURNING : RETURN_TICKS;
        return rulesFor(state).relentless() ? Math.max(1, base / 2) : base;
    }

    /** True when the wielder owns Returning and has not switched it off in the codex. */
    public static boolean returning(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.RETURNING.id());
    }

    // ---- the toggle ---------------------------------------------------------------------------

    /**
     * Call the Blade: every sword appears in the current stance, in one motion.
     *
     * @return false when the steel was already out, so the caller can toggle the other way
     */
    public static boolean draw(ServerPlayer player, PlayerMagicState state) {
        if (!state.swordArray().setDrawn(true)) {
            return false;
        }
        Held wielder = held(player);
        wielder.awayMask = 0;
        hold(player);
        tendArrayEntity(player, state);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.45F, 1.5F);
        state.sync(player);
        return true;
    }

    /**
     * The other press: the swords are <b>gone</b>.
     *
     * <p>Off means gone and not dormant - no entity, no interception, no upkeep, nothing drawn -
     * which is the arrangement the wielder asked for and the one an opponent can read at a glance.
     * The away swords dissolve where they stand rather than flying home, because a dismissal is a
     * dismissal; they are back in the wielder the instant the steel is called again.
     *
     * @return false when the steel was already away
     */
    public static boolean sheathe(ServerPlayer player, PlayerMagicState state) {
        if (!state.swordArray().setDrawn(false)) {
            return false;
        }
        Held wielder = held(player);
        wielder.awayMask = 0;
        dissolveEverything(player);
        SwordArrayEntity entity = arrayEntity(player);
        if (entity != null) {
            entity.discard();
        }
        wielder.arrayEntityId = -1;
        hold(player);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 0.7F);
        state.sync(player);
        return true;
    }

    // ---- the frame ----------------------------------------------------------------------------

    public static Bind bind(ServerPlayer player) {
        return held(player).bind;
    }

    public static float scale(ServerPlayer player) {
        return held(player).scale;
    }

    /**
     * Where the formation is hanging, right now.
     *
     * <p>Built rather than stored, because the resting bind is a function of a body that moves
     * every tick and storing it would be a copy that is wrong by one tick forever.
     *
     * <p><b>Which part of the wielder the origin is on depends on the stance's anchor</b>, and
     * that is the single most load-bearing line in the file. A {@code LOOK} stance is pinned to
     * the <em>eye</em> and carries the wielder's pitch, so Vanguard's two rings are centred on the
     * aim line by construction and stay centred however the head moves. A {@code BODY} stance is
     * pinned to the body centre at <b>pitch zero</b>, so a Crown stays level and does not swing
     * under the wielder's feet when they glance down. Put a LOOK ring on the chest-pinned origin
     * instead and it is centred at pitch zero and slides off the crosshair at every other pitch -
     * correct in every screenshot taken standing still, wrong the moment anybody plays.
     */
    public static Frame frame(ServerPlayer player) {
        Held wielder = held(player);
        return switch (wielder.bind) {
            // The wielder is pinned to a ridden frame, so it is their own body either way.
            case HELD, RIDDEN -> bodyFrame(player, wielder);
            case SET, SUNK -> wielder.anchor.withScale(wielder.scale);
        };
    }

    private static Frame bodyFrame(ServerPlayer player, Held wielder) {
        if (stanceOf(player).anchor() == SwordStance.Anchor.LOOK) {
            Vec3 eye = player.getEyePosition();
            return new Frame(eye.x, eye.y, eye.z, player.getYRot(), player.getXRot(), wielder.scale);
        }
        return new Frame(player.getX(), player.getY() + BODY_CENTRE, player.getZ(),
                player.getYRot(), 0.0F, wielder.scale);
    }

    /** Back to the resting state: the origin is on the wielder and the scale is one. */
    public static void hold(ServerPlayer player) {
        Held wielder = held(player);
        wielder.bind = Bind.HELD;
        wielder.scale = 1.0F;
    }

    /** The Keel, pressed: origin and facing freeze where they were and you walk out of them. */
    public static void setDown(ServerPlayer player) {
        Held wielder = held(player);
        Frame now = frame(player);
        wielder.anchor = new Frame(now.x(), now.y(), now.z(), now.yaw(), now.pitch(), 1.0F);
        wielder.bind = Bind.SET;
        wielder.scale = 1.0F;
    }

    /** The Keel, sneak-pressed: one sword goes under your feet and carries you. */
    public static void ride(ServerPlayer player) {
        Held wielder = held(player);
        wielder.bind = Bind.RIDDEN;
        wielder.scale = 1.0F;
    }

    /** Below: the origin is a point recorded once and never re-acquired. */
    public static void sink(ServerPlayer player, Vec3 point) {
        Held wielder = held(player);
        wielder.anchor = new Frame(point.x, point.y, point.z, player.getYRot(), 0.0F, 1.0F);
        wielder.bind = Bind.SUNK;
        wielder.scale = 1.0F;
    }

    /** One Blade drives this to zero, which walks every sword onto the origin and is the fusion. */
    public static void setScale(ServerPlayer player, float scale) {
        held(player).scale = Math.max(0.0F, scale);
    }

    // ---- where a sword actually is ---------------------------------------------------------------

    public static SwordStance stanceOf(ServerPlayer player) {
        return player.getData(MagicalAttachments.MAGIC_STATE).swordArray().stance();
    }

    /**
     * The argument {@code Formation.place} takes for its wave terms, and <b>both sides must agree
     * on it</b>.
     *
     * <p>It is the level's game time, because that is the one clock a client and a server both
     * have and both agree about. An entity age would not do: the client's copy starts when the
     * spawn packet lands, which is a different tick for every observer, so two players watching
     * one Crown would see it turned to two different angles.
     */
    public static double phase(ServerPlayer player) {
        return player.serverLevel().getGameTime();
    }

    /**
     * How far above the horizon the wielder is looking, positive up, in the stance's own frame.
     *
     * <p>Zero for a {@code LOOK} stance, whose frame already carries the pitch, and the wielder's
     * own elevation for a {@code BODY} one. Minecraft's pitch is positive <em>down</em>, so the
     * negation here is the conversion, and this method plus {@code ArrayPose.pitchOf} are the only
     * two places in the kit the two conventions are allowed to meet.
     */
    public static double lookElevation(ServerPlayer player, SwordStance stance) {
        return stance.anchor() == SwordStance.Anchor.LOOK ? 0.0D : -player.getXRot();
    }

    /** Sword {@code index}'s place in the world, or null when that sword is not present. */
    public static Vec3 swordPosition(ServerPlayer player, PlayerMagicState state, int index) {
        if ((presentMask(player, state) & (1 << index)) == 0) {
            return null;
        }
        Frame frame = frame(player);
        double[] offset = ArrayPose.worldOffset(slotOf(player, state, index), frame);
        return new Vec3(frame.x() + offset[0], frame.y() + offset[1], frame.z() + offset[2]);
    }

    /** Which way sword {@code index} points, as {yaw, pitch} in Minecraft degrees. */
    public static float[] swordFacing(ServerPlayer player, PlayerMagicState state, int index) {
        Frame frame = frame(player);
        double[] direction = ArrayPose.worldDirection(slotOf(player, state, index), frame);
        return new float[] {ArrayPose.yawOf(direction), ArrayPose.pitchOf(direction)};
    }

    private static Slot slotOf(ServerPlayer player, PlayerMagicState state, int index) {
        SwordStance stance = state.swordArray().stance();
        return Formation.place(stance, index, swords(state), phase(player),
                lookElevation(player, stance));
    }

    /** Every present sword's place, in slot order, skipping the gaps. Never null, may be empty. */
    public static List<Vec3> presentPositions(ServerPlayer player, PlayerMagicState state) {
        List<Vec3> out = new ArrayList<>();
        int mask = presentMask(player, state);
        for (int i = 0; i < swords(state); i++) {
            if ((mask & (1 << i)) != 0) {
                Vec3 at = swordPosition(player, state, i);
                if (at != null) {
                    out.add(at);
                }
            }
        }
        return out;
    }

    // ---- what the wielder last wounded ------------------------------------------------------------

    /**
     * Remembered for {@code Watch.DROP}, which is a follow-up rather than a turret.
     *
     * <p>Called from the damage event for <b>every</b> player in the game, so the cheap refusal is
     * here rather than at the call site: without it, the first punch anybody throws allocates them
     * a {@code Held} they will never use, and the map grows a row per player on the server rather
     * than a row per Sword Summoner.
     */
    public static void noteVictim(ServerPlayer player, LivingEntity victim) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.swordArray().drawn()) {
            return;
        }
        Held wielder = held(player);
        wielder.victimId = victim.getId();
        wielder.victimAt = wielder.world.now();
    }

    /** The body Rain should fall on, or null once the memory has gone cold or the body has. */
    public static LivingEntity recentVictim(ServerPlayer player) {
        Held wielder = held(player);
        if (wielder.victimId < 0 || wielder.world.now() - wielder.victimAt > VICTIM_MEMORY_TICKS) {
            return null;
        }
        return player.serverLevel().getEntity(wielder.victimId) instanceof LivingEntity living
                && living.isAlive() ? living : null;
    }

    /**
     * The Watch's own clock, kept here because {@code Held} is the only per-wielder state there is.
     *
     * <p>Answers true and re-arms, or answers false. An interval of zero means the behaviour is
     * not on a clock at all - {@code Watch.GLIDE} is a posture - and such a caller gets true every
     * tick with the clock pushed one tick ahead, which costs nothing and keeps the branch here
     * rather than in six places.
     */
    public static boolean watchDue(ServerPlayer player, int interval) {
        Held wielder = held(player);
        long now = wielder.world.now();
        if (interval > 0 && now < wielder.nextWatch) {
            return false;
        }
        wielder.nextWatch = now + Math.max(1, interval);
        return true;
    }

    /**
     * Notices a change of stance, and remembers the one just left.
     *
     * <p>Called from the Watch tick rather than from whatever wrote the stance, because the stance
     * can be written by the picker, by a command, by a rung clamp or by a load, and a reflection
     * that only some of those armed would be a passive that works when you change stance one way
     * and not the other.
     */
    public static void noteStance(ServerPlayer player, SwordStance current) {
        Held wielder = held(player);
        if (wielder.lastStance == current) {
            return;
        }
        if (wielder.lastStance != null) {
            wielder.mirror = wielder.lastStance;
        }
        wielder.lastStance = current;
    }

    /**
     * The stance held before the current one, which is what Mirror of the Array reflects.
     *
     * <p>Null until the wielder has changed stance at least once this session, and never saved:
     * the apex passive arms itself the first time its owner moves, which is a good deal more
     * legible than a reflection that is already running the moment you log in.
     */
    public static SwordStance mirrorStance(ServerPlayer player) {
        return held(player).mirror;
    }

    /** {@link #watchDue} for the reflection, on a clock of its own. */
    public static boolean mirrorWatchDue(ServerPlayer player, int interval) {
        Held wielder = held(player);
        long now = wielder.world.now();
        if (interval > 0 && now < wielder.nextMirror) {
            return false;
        }
        wielder.nextMirror = now + Math.max(1, interval);
        return true;
    }

    // ---- the one billing call -------------------------------------------------------------------

    /**
     * The cooldown and the mana for one press of this kit, and whether the press may go ahead.
     *
     * <p>Most of the kit is registered {@code selfManaged} or {@code holdGated}, and both of those
     * return out of {@code MagicCastingService.castViaRegistry} <em>before</em> it resolves a stat,
     * spends a point of mana, casts the aim ray or starts a clock. That is the whole point of such
     * a handler - it is how a hold bills on release rather than on press - but it means every
     * number on those definitions is decoration until somebody writes this call. The entire
     * Authority of Causality shipped costing nothing because nobody had, and nothing in the build
     * said so: a cost that is never taken looks exactly like a cost that is never needed.
     *
     * <p>Call it <b>after</b> the skill has established it has work to do and <b>before</b> it
     * does it, so a press that refuses itself is never charged. Anything negative for
     * {@code manaOverride} means "the definition's own".
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

    // ---- the slow tick --------------------------------------------------------------------------

    /**
     * The whole of the live half in one pass, every {@code SLOW_TICK_INTERVAL} ticks.
     *
     * <p>It runs here rather than on the player tick for the reason cooldowns have their own
     * payload: {@code sync()} serialises the entire state on every call and the
     * {@code lastSyncedTag} equality check suppresses the <em>packet</em>, not the work. So
     * everything that moves is gathered up and {@code state.sync} is called once at the end behind
     * a boolean, which is the {@code VersePassives.turn} shape.
     *
     * <p>The Watch is <b>not</b> here. It runs on {@code SwordArrayEntity.tick}, every tick, which
     * is where it has to be - an arrow has to be turned before it lands rather than discounted
     * afterwards - and that entity exists only while the steel is out, so the gate costs nothing.
     */
    public static void slowTick(ServerPlayer player, PlayerMagicState state) {
        if (!holds(state)) {
            return;
        }
        refreshRung(player, state);
        Held wielder = held(player);
        boolean dirty = releaseBrokenBind(player, wielder);
        dirty |= returnOne(player, state, wielder);
        dirty |= walkOver(player, state);
        tendArrayEntity(player, state);
        if (dirty) {
            state.sync(player);
        }
    }

    /** A frame set down has a leash, and past it the whole formation comes home. */
    private static boolean releaseBrokenBind(ServerPlayer player, Held wielder) {
        if (wielder.bind != Bind.SET) {
            return false;
        }
        Vec3 origin = new Vec3(wielder.anchor.x(), wielder.anchor.y(), wielder.anchor.z());
        if (origin.distanceTo(player.getBoundingBox().getCenter()) <= KEEL_LEASH) {
            return false;
        }
        recallEverything(player);
        hold(player);
        return true;
    }

    /** The slow route back: one away sword a clock tick. */
    private static boolean returnOne(ServerPlayer player, PlayerMagicState state, Held wielder) {
        if (away(player, state) <= 0) {
            return false;
        }
        long now = wielder.world.now();
        if (now < wielder.nextReturn) {
            return false;
        }
        wielder.nextReturn = now + returnTicks(state);
        returnSwords(player, state, 1);
        return true;
    }

    /**
     * The fast route back, and it is a place rather than a clock: walk to where your swords fell.
     *
     * <p>Which is by construction the ground you were just losing, so the recovery the class is
     * built around is a reason to go forward.
     */
    private static boolean walkOver(ServerPlayer player, PlayerMagicState state) {
        AABB box = player.getBoundingBox().inflate(PICKUP_RANGE);
        List<SwordBladeEntity> lying = SwordBladeEntity.ownedBy(player.serverLevel(), player, box);
        int took = 0;
        for (SwordBladeEntity blade : lying) {
            if (!blade.lying()) {
                continue;
            }
            blade.discard();
            took++;
        }
        if (took > 0) {
            returnSwords(player, state, took);
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        return took > 0;
    }

    // ---- the entity ---------------------------------------------------------------------------

    /**
     * The one entity twelve swords cost, spawned on demand and discarded when the steel goes away.
     *
     * <p>Twelve sword positions are worth <b>zero bytes</b> on the wire: {@code Formation} is pure
     * and both sides run it, so the client is told the stance, the mask and the frame - all of
     * which change rarely - and works out the rest. Twelve entities at the standard tracking range
     * would be twelve movement packets a tick to every observer for one rigid formation.
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
     * Raises the formation while the steel is out and drops it when it is not.
     *
     * <p>Public because the slow tick is ten ticks wide and a wielder who has just called their
     * steel should not spend half a second looking at nothing: the toggle calls this itself.
     */
    public static void tendArrayEntity(ServerPlayer player, PlayerMagicState state) {
        SwordArrayEntity entity = arrayEntity(player);
        if (!state.swordArray().drawn()) {
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

    /** Every sword this wielder has out, called home. The leash and a rung drop both end here. */
    public static void recallEverything(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(KEEL_LEASH * 2.0D);
        for (SwordBladeEntity blade : SwordBladeEntity.ownedBy(player.serverLevel(), player, box)) {
            blade.recall();
        }
    }

    /**
     * Every sword this wielder has out, gone where it stands.
     *
     * <p>The difference from {@link #recallEverything} is the whole meaning of the toggle: a
     * recall is the steel coming back, a dissolve is the steel ceasing to exist. Sheathing does
     * the second, because the wielder chose "off means the swords vanish entirely" and a flock of
     * blades flying home after a dismissal is neither off nor entirely.
     */
    private static void dissolveEverything(ServerPlayer player) {
        AABB box = player.getBoundingBox().inflate(KEEL_LEASH * 2.0D);
        for (SwordBladeEntity blade : SwordBladeEntity.ownedBy(player.serverLevel(), player, box)) {
            blade.discard();
        }
    }

    // ---- small shared arithmetic ----------------------------------------------------------------

    /** The wielder's body centre as the three doubles {@link SwordWorld} asks for. */
    public static double[] at(ServerPlayer player) {
        return new double[] {player.getX(), player.getY() + BODY_CENTRE, player.getZ()};
    }
}
