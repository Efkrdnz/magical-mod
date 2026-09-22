package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicDamageService;
import com.efkrdnz.magical.magic.MagicSinService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.HoldService;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.Station;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordMath;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.TierProfile;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * SWORD T-3, Sword Saint and above - the Array collapsed into one edge, and it is not a new
 * object.
 *
 * <p>{@code frame.scale -> 0} drives the bill to zero, and there is exactly one route to a zero
 * bill in this structure: every station letting go. So the fusion is {@code SwordArray.fuse},
 * which is {@code settle()} run at the far end of its own range - the same arithmetic that has
 * been shedding blades out of an over-stretched Array since Sword Saint - and the greatsword's
 * length, arc and damage are all functions of the Edge that collapsed, because that is literally
 * what it is made of. Fused from {@code HELD} it is a sword in your hand; fused from {@code BOUND}
 * it is twelve converging cuts arriving on one body and <em>then</em> a sword, and the renderer
 * needs no special case for the difference because it reads the frame.
 *
 * <p><b>The gather is the one counterable thing in the kit.</b>
 * {@code MagicCounterService.matchesForbiddenDepth} is
 * {@code incomingTier >= 0 || counter.tier() >= -incomingTier}, so a tier -3 skill is answerable
 * by an existing tier-3 counter while nothing at all answers -5 or the Authorities. The gather is
 * therefore an ordinary {@link SpellEffectEntity} with mode bit 1 <b>clear</b> and a twelve-tick
 * window, and killing the wielder during the fuse costs them the entire Array.
 *
 * <p><b>Where the bill is taken, and the one place this departs from the design.</b> The design
 * says "billed on release". This bills at the moment the fuse completes, through
 * {@link SwordService#payFor} with {@code manaOverride = 6 x stationsFused}, because that is the
 * only moment in the sequence that cannot be skipped: a hold that is never released, a client
 * that stops sending, a logout mid-carry and a slash that is never thrown all reach the end of
 * the timeline without a "release" ever arriving, and the failure they would share is the one
 * this whole kit is written against - a press that costs nothing. A gather answered by a counter
 * never reaches the fuse, so it is charged the cooldown and no mana, which is what the design
 * asks for; a wielder who fuses and then carries the blade for two seconds without swinging has
 * paid for the gather rather than for nothing, which is what it changes.
 */
public final class OneBladeSkill implements SkillModule {

    /** Ticks of convergence. Twelve, and the whole of them are a hole in the wielder's guard. */
    public static final int FUSE_TICKS = 12;

    /** Mana a station costs to fuse, charged once when the blade forms. */
    public static final int FUSE_MANA_PER_STATION = 6;

    /** One horizontal cut per this many ticks, and each of them is paid for. */
    public static final int SLASH_INTERVAL = 16;

    public static final int SLASH_MANA = 12;

    /** How tall the cut is, in blocks, and it does not grow with the Edge. */
    public static final double SLASH_HEIGHT = 3.0D;

    /** The blast's wedge, as multiples of the blade's own length. */
    public static final double BLAST_LENGTH_FACTOR = 2.0D;

    public static final double BLAST_WIDTH_FACTOR = 0.5D;

    /** What carrying the thing costs in footwork. */
    public static final double CARRY_SLOW = -0.20D;

    /**
     * How long the greatsword is <em>drawn</em>, in blocks, and why it is not how long it is.
     *
     * <p>The blade's real length is {@code SwordMath.oneBladeReach(totalEdge)} -
     * {@code 2.5 + 0.18 * Edge} - which runs from 2.68 at one point of Edge to 8.98 at a Sword
     * God's whole 36, and the entity is handed exactly that as its synced radius. <b>A BODY
     * silhouette cannot read it.</b> {@code ProfileRendererShell} feeds the synced radius to
     * {@code MARK}, {@code SWARM} and a few {@code FIELD} forms and to {@code FILAMENT} lengths,
     * and to nothing else - so a solid is drawn at whatever its profile says, forever. The
     * profile said {@code 9.0}, which is {@code oneBladeReach} at the maximum Edge in the game:
     * every fusion, down to the two-point one, was drawn as the largest blade the class can
     * make. On {@code Form.PRISM} that number is the <em>height</em> and the form is not a travel
     * form, so what stood in the world was a nine-block square post growing straight up out of
     * the hand, world-aligned, seeded with a random yaw - and the hand is 0.6 blocks in front of
     * the eye and a quarter of a block below it, so in first person the post was a bar forty
     * degrees wide standing in the middle of the frame for the whole carry.
     *
     * <p>Since the drawing cannot follow the Edge, it takes the one length that is true of every
     * fusion: {@code SwordMath.ONE_BLADE_BASE_REACH}, the shortest blade the skill can make. A
     * short drawing of a long blade is a reading the player can correct by looking at the Edge;
     * a long drawing of a short one is a promise of reach that is not there, and that is the
     * failure this kit has now shipped four times. The complete fix is a renderer of its own
     * drawing the Duskfall model, as {@code SwordBladeRenderer} now does for every other blade
     * in the school.
     */
    public static final float DRAWN_LENGTH = (float) SwordMath.ONE_BLADE_BASE_REACH;

    /**
     * The blade's breadth scale. {@code BodyPainter} takes a {@code CROSSED_BLADES} cross-section
     * as {@code 0.22 * sizeA} on both axes, so 1.0 is a blade 0.44 blocks across the flat -
     * about twice a vanilla sword, which is what twelve of them fused into one comes to.
     */
    public static final float DRAWN_BREADTH = 1.0F;

    /**
     * Per-victim internal cooldown on the slash, and it is one half of a rule with two.
     *
     * <p>A slash clears {@code invulnerableTime} before it wounds, because sixteen ticks is
     * inside vanilla's own hurt cooldown and without it every second cut would land as nothing.
     * Clearing i-frames without a cooldown of the kit's own is the opposite failure - it is what
     * turns twelve blades in one tick into an instant delete - so both halves are here and
     * {@code SwordBladeEntity.wound} carries the same pair for the same reason.
     */
    public static final int VICTIM_COOLDOWN_TICKS = 10;

    /** Ticks before a missing hold is believed. The client's first refresh arrives a tick late. */
    private static final int HOLD_GRACE = 5;

    private static final ResourceLocation CARRY_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "one_blade_carry");

    /** Live fusions, keyed by wielder. Never saved: a greatsword is not a thing you log out with. */
    private static final Map<UUID, Fusion> FUSIONS = new HashMap<>();

    /**
     * One wielder's collapsed Array.
     *
     * <p>{@code edges} is the shape as it stood the instant before the collapse, kept so an
     * unused fusion can put every blade back on the bearing it came off rather than dumping the
     * metal into the wielder's loose pool - the bearings are the build and a fusion that changed
     * them would be a second authoring surface.
     */
    private static final class Fusion {
        private final int slot;
        private final int entityId;
        private final int stations;
        private final int totalEdge;
        private final int[] edges;
        private final int carryTicks;
        private final Map<Integer, Integer> struck = new HashMap<>();
        private int ticks;
        private boolean formed;
        private int nextSlash;

        private Fusion(int slot, int entityId, int stations, int totalEdge, int[] edges, int carryTicks) {
            this.slot = slot;
            this.entityId = entityId;
            this.stations = stations;
            this.totalEdge = totalEdge;
            this.edges = edges;
            this.carryTicks = carryTicks;
        }
    }

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ONE_BLADE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerPlayer player = ctx.player();
                if (player == null) {
                    return CastResult.HANDLED;
                }
                gather(ctx, player, ctx.state());
                return CastResult.HANDLED;
            }

            /**
             * Hold-gated, so {@code castViaRegistry} returns before stats, mana, the aim ray and
             * the cooldown - {@code ctx.aim()} is <b>null</b> in here and nothing may dereference
             * it. The press starts the fuse for free; {@link SwordService#payFor} takes the bill
             * when the blade forms.
             */
            @Override
            public boolean holdGated() {
                return true;
            }

            @Override
            public String holdHintKey() {
                return "message.magical.one_blade_hold";
            }

            /** Twelve ticks in which a tier-3 counter can shatter the convergence where it stands. */
            @Override
            public int counterWindowTicks() {
                return FUSE_TICKS;
            }

            /** Never null. A null here is an NPE on the server thread inside entity ticking. */
            @Override
            public MobCastProfile mob() {
                return MobCastProfile.NONE;
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.cut_force", "screen.magical.tuning.arm_speed",
                        "screen.magical.tuning.length", "screen.magical.tuning.hold",
                        "screen.magical.tuning.thrift");
            }
        };
    }

    // ---- the fuse ---------------------------------------------------------------------------------

    /** True while this wielder is carrying the fused blade, which is when no other cast is accepted. */
    public static boolean carrying(ServerPlayer player) {
        Fusion fusion = FUSIONS.get(player.getUUID());
        return fusion != null && fusion.formed;
    }

    /**
     * The press: every manned station lets go and converges on the wielder's hand.
     *
     * <p>The Edge is spent the instant the blades leave their bearings, because a blade in the air
     * is metal in the world whatever it is on its way to - which is also why a counter needs no
     * special accounting here. It has already happened.
     */
    private static void gather(CastContext ctx, ServerPlayer player, PlayerMagicState state) {
        if (!SwordService.holds(state) || !SwordService.rulesFor(state).coincidence()) {
            player.displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
            return;
        }
        if (FUSIONS.containsKey(player.getUUID())) {
            return;
        }
        SwordArray array = state.swordArray();
        if (array.manned() <= 0) {
            player.displayClientMessage(Component.translatable("message.magical.sword_no_edge"), true);
            return;
        }
        if (state.isSkillOnCooldown(MagicContent.ONE_BLADE.id())) {
            player.displayClientMessage(Component.translatable("message.magical.skill_cooling"), true);
            return;
        }

        int stations = array.manned();
        int[] edges = SwordService.edges(array);
        int gathered = SwordService.collapse(player, state);
        if (gathered <= 0) {
            player.displayClientMessage(Component.translatable("message.magical.sword_no_edge"), true);
            return;
        }
        int carry = Math.max(FUSE_TICKS, ctx.duration());
        Vec3 hand = hand(player);
        SpellEffectEntity blade = SpellEffectEntity.spawn(ctx, hand, FUSE_TICKS + carry + 2,
                (float) SwordMath.oneBladeReach(gathered), player.getLookAngle());
        FUSIONS.put(player.getUUID(), new Fusion(ctx.slot(), blade.getId(), stations, gathered, edges, carry));
        SwordService.tendArrayEntity(player, state);
        ctx.level().playSound(null, hand.x, hand.y, hand.z,
                SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.8F, 0.7F);
        state.sync(player);
    }

    /** One tick of one fusion: converge, form, carry, and four ways out. */
    private static boolean tickFusion(ServerPlayer player, PlayerMagicState state, Fusion fusion) {
        SpellEffectEntity blade = player.serverLevel().getEntity(fusion.entityId) instanceof SpellEffectEntity found
                && !found.isRemoved() ? found : null;
        if (blade == null) {
            // The convergence is gone and this file did not remove it: a counter, or the entity
            // dying with the chunk. Either way the Edge stays spent - it is out in the world -
            // and an unformed gather still pays the clock, which is the whole of the counterplay.
            if (!fusion.formed) {
                shatter(player, state);
            } else {
                collapseUnused(player, state, fusion);
            }
            return true;
        }

        fusion.ticks++;
        Vec3 hand = hand(player);
        blade.setPos(hand.x, hand.y, hand.z);
        // Drawing only, and it has to be here rather than at the spawn. The silhouette is a
        // travel form, so the renderer points it along this vector; set once at the gather it
        // would be a greatsword frozen on the bearing the wielder happened to be facing twelve
        // ticks ago, while the cut that follows reads player.getLookAngle() fresh every time.
        // Nothing in this skill's behaviour ever reads effectDirection, so this moves no volume.
        blade.setDirection(player.getLookAngle());

        if (!fusion.formed) {
            blade.setValue(1.0F - (float) fusion.ticks / FUSE_TICKS);
            if (fusion.ticks < FUSE_TICKS) {
                // Letting the key go mid-convergence makes nothing, so nothing is charged for it.
                return released(player, fusion) && abort(player, state, fusion, blade);
            }
            if (!form(player, state, fusion, blade)) {
                return true;
            }
        }
        blade.setValue(0.0F);
        return finished(player, state, fusion, blade);
    }

    /** The fuse completed: the blade exists, and this is where the whole thing is paid for. */
    private static boolean form(ServerPlayer player, PlayerMagicState state, Fusion fusion, SpellEffectEntity blade) {
        if (!SwordService.payFor(player, state, MagicContent.ONE_BLADE, FUSE_MANA_PER_STATION * fusion.stations)) {
            abort(player, state, fusion, blade);
            return false;
        }
        fusion.formed = true;
        blade.setPhase(SpellEffectEntity.PHASE_ACTIVE);
        blade.setRadius((float) SwordMath.oneBladeReach(fusion.totalEdge));
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CARRY_MODIFIER);
            speed.addTransientModifier(new AttributeModifier(CARRY_MODIFIER, CARRY_SLOW,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.5F);
        state.sync(player);
        return true;
    }

    /** True once the carry is over: the key let go, or the hundred and twenty ticks ran out. */
    private static boolean finished(ServerPlayer player, PlayerMagicState state, Fusion fusion, SpellEffectEntity blade) {
        if (fusion.ticks < FUSE_TICKS + fusion.carryTicks && !released(player, fusion)) {
            return false;
        }
        blade.discard();
        collapseUnused(player, state, fusion);
        return true;
    }

    /**
     * Whether the wielder has let the key go.
     *
     * <p>A slot below zero is a debug cast ({@code magical-debug skill magical:one_blade}) with no
     * key behind it at all, so it is never treated as released and runs its full timeline -
     * otherwise the one command that can photograph this skill would end it on the first tick.
     */
    private static boolean released(ServerPlayer player, Fusion fusion) {
        return fusion.slot >= 0 && fusion.ticks > HOLD_GRACE && !HoldService.isHeld(player, fusion.slot);
    }

    /** The fuse was let go or could not be paid for: nothing was made, so nothing is charged. */
    private static boolean abort(ServerPlayer player, PlayerMagicState state, Fusion fusion, SpellEffectEntity blade) {
        blade.discard();
        returnBlades(player, state, fusion);
        state.sync(player);
        return true;
    }

    /** A counter, mid-convergence. The metal is out there; the clock runs anyway. */
    private static void shatter(ServerPlayer player, PlayerMagicState state) {
        MagicSkillResolvedStats stats = MagicContent.ONE_BLADE.resolve(state.tuningFor(MagicContent.ONE_BLADE.id()));
        state.setSkillCooldown(MagicContent.ONE_BLADE.id(), stats.cooldownTicks());
        SwordService.tendArrayEntity(player, state);
        state.sync(player);
    }

    /** The blade was carried and never swung: every bearing takes its own metal back. */
    private static void collapseUnused(ServerPlayer player, PlayerMagicState state, Fusion fusion) {
        returnBlades(player, state, fusion);
        state.sync(player);
    }

    /**
     * Puts the shape back exactly as it was.
     *
     * <p>Recover first and plant after: {@code SwordArray.plant} clamps what it gives a station to
     * the wielder's <em>loose</em> Edge, and until the collapse's metal has come home out of
     * {@code spent} there is none. A station that has been re-manned in the meantime is left
     * alone rather than topped up past what it held.
     */
    private static void returnBlades(ServerPlayer player, PlayerMagicState state, Fusion fusion) {
        SwordService.recover(player, state, fusion.totalEdge);
        clearCarry(player);
        SwordArray array = state.swordArray();
        for (int slot = 0; slot < fusion.edges.length && slot < array.size(); slot++) {
            Station station = array.station(slot);
            if (fusion.edges[slot] <= 0 || station == null || station.manned()) {
                continue;
            }
            array.plant(station.withEdge(fusion.edges[slot]), SwordService.spent(player));
        }
        SwordService.tendArrayEntity(player, state);
    }

    private static void clearCarry(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(CARRY_MODIFIER);
        }
    }

    /** The point the blades converge on and the greatsword is then held at. */
    private static Vec3 hand(ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x).normalize();
        return player.getEyePosition().add(look.scale(0.6D)).add(side.scale(0.35D)).add(0.0D, -0.25D, 0.0D);
    }

    // ---- the two things it does ---------------------------------------------------------------------

    /**
     * The slash: a horizontal arc of {@code min(160, 60 + 3 x edge)} degrees, centred on the look.
     *
     * <p>Answers false when the press did nothing, so the client half can say so rather than
     * eating the input. The mana is the slash's own; the fusion was paid for when it formed.
     */
    public static boolean slash(ServerPlayer player, PlayerMagicState state) {
        Fusion fusion = FUSIONS.get(player.getUUID());
        if (fusion == null || !fusion.formed || fusion.ticks < fusion.nextSlash) {
            return false;
        }
        if (!MagicSinService.spendManaForSkill(player, state, SLASH_MANA)) {
            player.displayClientMessage(Component.translatable("message.magical.not_enough_mana"), true);
            return false;
        }
        fusion.nextSlash = fusion.ticks + SLASH_INTERVAL;
        double length = SwordMath.oneBladeReach(fusion.totalEdge);
        double arc = SwordMath.oneBladeArc(fusion.totalEdge);
        float amount = (float) SwordMath.oneBladeSlash(fusion.totalEdge, SwordService.strain(player));
        cut(player, fusion, length, arc, length, amount, 1.2D);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
        state.sync(player);
        return true;
    }

    /**
     * The blast: the blade drives point-first and spends everything at once, and it <b>ends</b>.
     *
     * <p>Free of its own price and the only exit that does not put the shape back: the Edge
     * scatters to loose with every station unmanned, which is the difference between a wielder
     * who cashed their Array in and one who merely picked it up again.
     */
    public static boolean blast(ServerPlayer player, PlayerMagicState state) {
        Fusion fusion = FUSIONS.get(player.getUUID());
        if (fusion == null || !fusion.formed) {
            return false;
        }
        double length = SwordMath.oneBladeReach(fusion.totalEdge);
        float amount = (float) SwordMath.oneBladeBlast(fusion.totalEdge, SwordService.strain(player));
        cut(player, fusion, length * BLAST_LENGTH_FACTOR, 0.0D, length * BLAST_WIDTH_FACTOR * 0.5D, amount, 1.2D);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.1F);

        if (player.serverLevel().getEntity(fusion.entityId) instanceof SpellEffectEntity blade) {
            blade.discard();
        }
        FUSIONS.remove(player.getUUID());
        clearCarry(player);
        SwordService.recover(player, state, fusion.totalEdge);
        SwordService.tendArrayEntity(player, state);
        state.sync(player);
        return true;
    }

    /**
     * One cut, as either an arc or a wedge.
     *
     * <p>{@code arcDegrees} above zero is the slash - everything inside half that angle of the
     * look, out to {@code reach}. At zero it is the blast, and {@code halfWidth} is the lateral
     * limit instead: a wedge rather than a fan, because the blast is the blade driven forward and
     * not swung.
     */
    private static void cut(ServerPlayer player, Fusion fusion, double reach, double arcDegrees,
            double halfWidth, float amount, double knockback) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        double cos = arcDegrees > 0.0D ? Math.cos(Math.toRadians(arcDegrees) / 2.0D) : 0.0D;
        AABB box = new AABB(eye, eye).inflate(reach, SLASH_HEIGHT / 2.0D + 0.5D, reach);

        for (LivingEntity victim : SkillTargets.hostilesIn(level, player, box)) {
            Vec3 centre = victim.getBoundingBox().getCenter();
            Vec3 to = centre.subtract(eye);
            if (Math.abs(to.y) > SLASH_HEIGHT / 2.0D) {
                continue;
            }
            Vec3 toFlat = new Vec3(to.x, 0.0D, to.z);
            double distance = toFlat.length();
            if (distance > reach || distance < 1.0E-4D) {
                continue;
            }
            Vec3 unit = toFlat.scale(1.0D / distance);
            double forward = unit.dot(flat);
            if (arcDegrees > 0.0D) {
                if (forward < cos) {
                    continue;
                }
            } else if (forward <= 0.0D || Math.abs(toFlat.dot(side)) > halfWidth) {
                continue;
            }
            if (waitingOn(fusion, victim)) {
                continue;
            }
            wound(player, fusion, victim, amount);
            SkillTargets.shove(victim, eye, knockback, 0.15D);
        }
    }

    /** Both halves of the i-frame rule, in the one place this skill wounds anything. */
    private static void wound(ServerPlayer player, Fusion fusion, LivingEntity victim, float amount) {
        victim.invulnerableTime = 0;
        fusion.struck.put(victim.getId(), fusion.ticks + VICTIM_COOLDOWN_TICKS);
        MagicDamageService.hurt(victim, player.serverLevel().damageSources().indirectMagic(player, player),
                amount, MagicContent.ONE_BLADE.id());
    }

    private static boolean waitingOn(Fusion fusion, LivingEntity victim) {
        return fusion.struck.getOrDefault(victim.getId(), Integer.MIN_VALUE) > fusion.ticks;
    }

    // ---- what the game calls ------------------------------------------------------------------------

    /** Wired to logout, to a change of dimension and to the server stopping. Idempotent. */
    public static void forget(UUID wielder) {
        FUSIONS.remove(wielder);
    }

    public static void clear() {
        FUSIONS.clear();
    }

    /**
     * One Blade's subscribers, on the {@code SwordRiteEvents} pattern: the arms in one place, the
     * logic out of them, and an empty-map return before anything is read.
     *
     * <p>The state machine is here rather than in {@link #behavior()} for one reason - a counter
     * discards the gather entity through {@code onCountered}, which does <em>not</em> call
     * {@code onExpire}, so a behaviour cannot see the one outcome the design names by hand. The
     * entity's disappearance is observed from outside instead, and there is exactly one path.
     */
    @EventBusSubscriber(modid = MagicalMod.MODID)
    public static final class Events {

        private Events() {}

        @SubscribeEvent
        public static void onServerTick(ServerTickEvent.Post event) {
            if (FUSIONS.isEmpty()) {
                return;
            }
            MinecraftServer server = event.getServer();
            FUSIONS.entrySet().removeIf(entry -> {
                ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
                if (player == null || !player.isAlive()) {
                    return true;
                }
                return tickFusion(player, player.getData(MagicalAttachments.MAGIC_STATE), entry.getValue());
            });
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                clearCarry(player);
            }
            forget(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                clearCarry(player);
            }
            forget(event.getEntity().getUUID());
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            clear();
        }
    }

    /**
     * The gather entity does nothing but be there and be counterable.
     *
     * <p>Every decision is taken by {@link Events}, so this is deliberately a stub: two tick paths
     * for one object is how a state machine ends up disagreeing with itself, and the only thing a
     * behaviour can see that the arm cannot is nothing at all.
     */
    @Override
    public SpellBehavior behavior() {
        return effect -> {
            if (effect.livingOwner() == null) {
                effect.finish();
            }
        };
    }

    // ---- what it looks like --------------------------------------------------------------------------

    /**
     * {@code EmblemId.GREATSWORD} is one heavy double-edged blade with a wide guard: the Array
     * collapsed, which is literally what this skill leaves in the hand. An emblem may not be
     * shared - {@code VisualProfiles} treats a repeated one as a <b>hard</b> collision and throws
     * at common setup - so it is its own constant with its own stroke arm.
     *
     * <p>{@code holdable(true)} on the <b>builder</b> and not only {@code holdGated()} on the
     * handler: {@code GenericHoldInput} polls {@code VisualProfiles.of(skill).holdable()} and the
     * handler's own flag is dead code to it, so a skill with one and not the other compiles,
     * registers, casts on press and never receives a hold. No {@code stamps(...)} layer - the
     * school's stamp is {@code StampId.EDGE} at atlas cell 32 and {@code STAMP_BAND}'s
     * {@code paramB} is five bits wide.
     */
    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SWORD)
                .circle(CircleScript.of(SchoolMaterial.SWORD).emblem(EmblemId.GREATSWORD).frame(8)
                        .band(GlyphKind.TICK_BAND, 36, ColorRole.BRIGHT)
                        .band(GlyphKind.BRAID_BAND, 8, ColorRole.DIM)
                        .star(8, 3)
                        .core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                // The Bearing's arithmetic, for the same anchor: an EYE_FORWARD circle is hung
                // 0.9 blocks from the eye and turned to face the camera, so tier 4's radius of
                // 3.0 is 73 degrees of a 70-degree frame. Hold-gated, so it does not draw today.
                .tier(TierProfile.forTier(definition().tier()).withRadius(TheBearingSkill.HAND_RING_RADIUS))
                // CROSSED_BLADES rather than PRISM, and the form is the whole of it: it is one of
                // the shell's travel forms, so the pose is turned until local +Z runs along the
                // entity's synced direction, and BodyPainter lays the mesh from z = 0 to z =
                // length. The blade therefore lies along the aim, out of the hand, instead of
                // standing straight up through the camera. Still BODY - shard_body, depth-writing,
                // opaque - because a greatsword is an object and an additive family would make it
                // light.
                .silhouette(Silhouette.body(Silhouette.Form.CROSSED_BLADES, FxKinds.Body.METAL_BANDS,
                        4, DRAWN_BREADTH, DRAWN_LENGTH))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.CRACKED_GLASS)
                .holdable(true)
                .budget(3)
                .bounds(9.0F, 3.0F, 2.0F);
    }
}
