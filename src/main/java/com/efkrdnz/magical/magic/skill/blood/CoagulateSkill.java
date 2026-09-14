package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.BloodHarvestEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodFieldData;
import com.efkrdnz.magical.magic.blood.shape.BloodShapeGeometry;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.passive.ClassPassiveEffects;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.registry.MagicalEntities;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * BLOOD T-1 - wear it.
 *
 * <p>Every pool of yours within Draw flies into you, and up to the price from the Vessel with it;
 * the blood sets into a ring round your body and stands as barrier at the Shell rate. It is a
 * layer above the barrier, not a refill of it: while it stands it raises the cap by what is left
 * of it, so a full barrier can still wear a shell. Blows wear the shell first - every drop in the
 * barrier comes off the shell, and the synced integrity follows - and when it goes, after Set
 * ticks or at nothing, half of what was left drains back into the Vessel. With no blood anywhere
 * it is refused: a shell needs something to set.
 *
 * <p>The barrier it grants is the same barrier every other school uses, so it is spent the same
 * way and shows in the same meter. The shell keeps its own share in the state's counter for this
 * skill, which is what {@code BloodPassives} adds to the cap.
 */
public final class CoagulateSkill implements SkillModule {

    /** How far pools are drawn from at one point of Draw, in blocks. */
    public static final double BASE_DRAW = 8.0D;

    /** Barrier per hundred blood at one point of Shell. */
    public static final int SHELL_PER_HUNDRED = 60;

    /** The share of what is left that drains back into the Vessel when the shell goes. */
    public static final float REFUND_SHARE = 0.5F;

    /** Cubes the shell may have. VoxelStyle.SHELL's cap, which the server cannot read. */
    public static final int CUBES = 600;

    private static final float RING_RADIUS = 0.9F;
    private static final float THICKNESS = 0.05F;
    private static final float HEIGHT = 0.15F;
    /** The shell stops at the shoulders: the head stays out of it, and so does the view. */
    private static final float WALL_SHARE = 0.7F;
    private static final int FORM_TICKS = 16;
    private static final double BASE_PITCH = 0.0625D;
    private static final int MIN_SET = 40;
    private static final String GRANTED_KEY = "granted";
    private static final String SHELL_KEY = "shell";
    private static final String RATE_KEY = "rate";
    private static final String LAST_KEY = "last";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.COAGULATE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                PlayerMagicState state = ctx.state();
                double draw = BASE_DRAW * ctx.stats().speed();
                List<BloodHarvestEntity> pools = BloodHarvestEntity.poolsWithin(player, draw).stream()
                        .filter(pool -> pool.position().distanceTo(player.position()) <= draw)
                        .toList();
                int fromPools = pools.stream().mapToInt(BloodHarvestEntity::worth).sum();
                int fromVessel = Math.min(state.bloodVessel(), BloodService.cost(ctx.stats()));
                int total = fromPools + fromVessel;
                if (total <= 0) {
                    player.displayClientMessage(Component.translatable("message.magical.no_blood_to_set"), true);
                    return CastResult.FAILED;
                }
                // The worth is taken now; the empty blood still flies in, so the draw is seen.
                pools.forEach(pool -> pool.drink(player));
                state.addBloodVessel(-fromVessel);

                float rate = SHELL_PER_HUNDRED * ctx.size();
                int granted = Math.max(1, Math.round(total * rate / 100.0F));
                // The cap rises by the shell before the shell goes on, or a full barrier would
                // clamp it away and the blood would have bought nothing.
                state.setPassiveCounter(definition().id(), granted);
                ClassPassiveEffects.refreshPoolBonuses(player, state);
                int before = state.barrier();
                state.addBarrier(granted);
                int shell = state.barrier() - before;

                float wall = player.getBbHeight() * WALL_SHARE;
                double pitch = pitchFor(wall);
                BloodFieldData data = BloodFieldData.of(List.of(ring(pitch)), HEIGHT, wall, THICKNESS,
                        (float) pitch, 0.0F, 0.0F, FORM_TICKS, 0, player.getId());
                SpellEffectEntity ring = SpellEffectEntity.spawn(ctx, player.position(),
                        Math.max(MIN_SET, ctx.duration()), RING_RADIUS + 0.5F, ctx.look());
                ring.setSyncedData(data.encode());
                ring.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                CompoundTag scratch = ring.serverData();
                scratch.putInt(GRANTED_KEY, Math.max(1, shell));
                scratch.putInt(SHELL_KEY, shell);
                scratch.putFloat(RATE_KEY, rate);
                scratch.putInt(LAST_KEY, state.barrier());
                state.sync(player);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.HONEY_BLOCK_PLACE,
                        SoundSource.PLAYERS, 0.8F, 0.6F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return new TuningView(false, true, true, true, true, null,
                        "screen.magical.tuning.draw", "screen.magical.tuning.shell",
                        "screen.magical.tuning.set", "screen.magical.tuning.thrift");
            }

            @Override
            public MobCastProfile mob() {
                return null;
            }
        };
    }

    /** A closed ring of canvas points round the caster, one per pitch. */
    static double[] ring(double pitch) {
        int points = Math.max(8, (int) Math.round(Math.PI * 2.0D * RING_RADIUS / pitch));
        double[] line = new double[(points + 1) * 2];
        for (int i = 0; i <= points; i++) {
            double angle = Math.PI * 2.0D * i / points;
            line[i * 2] = Math.cos(angle) * RING_RADIUS;
            line[i * 2 + 1] = Math.sin(angle) * RING_RADIUS;
        }
        return line;
    }

    /** The finest pitch that keeps the whole ring inside the style's cap: a coarser shell over a truncated one. */
    static double pitchFor(float wall) {
        double pitch = BASE_PITCH;
        for (int step = 0; step < 24; step++) {
            int points = Math.max(8, (int) Math.round(Math.PI * 2.0D * RING_RADIUS / pitch)) + 1;
            if (BloodShapeGeometry.voxelDemand(points, wall, THICKNESS, pitch) <= CUBES) {
                return pitch;
            }
            pitch *= 1.15D;
        }
        return pitch;
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity ring) {
                if (!(ring.owner() instanceof ServerPlayer player) || !player.isAlive()) {
                    ring.finish();
                    return;
                }
                ring.setPos(player.getX(), player.getY(), player.getZ());
                CompoundTag scratch = ring.serverData();
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                // Every drop in the barrier since last tick came off the shell first. A rise is
                // not the shell's: barrier that comes back from elsewhere is not this blood.
                int barrier = state.barrier();
                int shell = Math.min(scratch.getInt(SHELL_KEY), barrier);
                int drop = scratch.getInt(LAST_KEY) - barrier;
                if (drop > 0) {
                    shell = Math.max(0, shell - drop);
                }
                scratch.putInt(LAST_KEY, barrier);
                if (shell != scratch.getInt(SHELL_KEY)) {
                    scratch.putInt(SHELL_KEY, shell);
                    hold(player, state, shell);
                    BloodFieldData data = BloodFieldData.decode(ring.syncedData());
                    if (data != null) {
                        ring.setSyncedData(data.withIntegrity(shell / (float) Math.max(1, scratch.getInt(GRANTED_KEY))).encode());
                    }
                }
                if (shell <= 0) {
                    ring.finish();
                }
            }

            @Override
            public void onExpire(SpellEffectEntity ring) {
                if (!(ring.owner() instanceof ServerPlayer player)) {
                    return;
                }
                CompoundTag scratch = ring.serverData();
                PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
                int shell = Math.min(scratch.getInt(SHELL_KEY), state.barrier());
                float rate = Math.max(1.0F, scratch.getFloat(RATE_KEY));
                if (shell > 0) {
                    // The shell was the barrier: what is left of it goes, and half of the blood it
                    // was drains back.
                    state.setBarrier(state.barrier() - shell);
                    state.addBloodVessel(Mth.floor(shell / rate * 100.0F * REFUND_SHARE));
                }
                hold(player, state, 0);
                state.sync(player);
            }
        };
    }

    /** What the shell still holds up: the counter the cap is raised by, refreshed at once, the barrier re-clamped. */
    private static void hold(ServerPlayer player, PlayerMagicState state, int shell) {
        state.setPassiveCounter(MagicContent.COAGULATE.id(), Math.max(0, shell));
        ClassPassiveEffects.refreshPoolBonuses(player, state);
        state.setBarrier(state.barrier());
        state.sync(player);
    }

    /** Whether a shell of this player's is standing: the guard against a counter its ring left behind. */
    public static boolean shellStanding(ServerPlayer player) {
        return !player.serverLevel().getEntities(MagicalEntities.SPELL_EFFECT.get(), player.getBoundingBox().inflate(8.0D),
                effect -> effect.owner() == player && !effect.isRemoved()
                        && MagicContent.COAGULATE.id().equals(effect.definition().id())).isEmpty();
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.INFINITY).frame(5)
                        .band(GlyphKind.CHAIN_BAND, 8, ColorRole.DIM)
                        .band(GlyphKind.SOLID_RING, 1, ColorRole.BRIGHT)
                        .stamps(StampId.RING, 5).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("coagulate", 2.5F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.DROPLET, FxKinds.Overlay.IRIS_CLOSE)
                // The whole cap: the budget class scales it, and a shell with a gap is no shell.
                .budget(3)
                .bounds(2.5F, 2.5F, 1.5F);
    }
}
