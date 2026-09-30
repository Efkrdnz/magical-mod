package com.efkrdnz.magical.magic.skill.primordial;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicCounterService;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.primordial.CycloneFeed;
import com.efkrdnz.magical.magic.primordial.Funnel;
import com.efkrdnz.magical.magic.primordial.PrimordialService;
import com.efkrdnz.magical.magic.primordial.StormElement;
import com.efkrdnz.magical.magic.service.Bodies;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * PRIMORDIAL T-4 - a storm that eats spells.
 *
 * <p>The funnel touches down where the caster aims and drifts toward whatever ground they look at.
 * Bodies near it are drawn in, spun, lifted and thrown out of the crown; a pulse of damage lands on
 * everything inside every half second. A spell in flight that enters it is swallowed: its owner's
 * spell feeds the storm its element ({@link CycloneFeed}), anybody else's is dispelled with a
 * clash, and so is any arrow the owner did not loose. The owner's own catastrophes are met rather
 * than eaten: a wave or a falling star that crosses the storm colours it and keeps going. The
 * land colours a calm storm - lava makes it Ember, water Tide. Wellspring: the open sky.
 */
public final class CycloneSkill implements SkillModule {
    public static final double AIM_RANGE = 24.0D;
    public static final double BASE_RADIUS = 2.6D;
    public static final double HEIGHT_PER_RADIUS = 3.0D;
    /** Never a degenerate funnel: a zero radius or height would put NaN into every pull. */
    private static final double MIN_RADIUS = 0.5D;
    private static final double DRIFT = 0.12D;
    private static final double STEER_RANGE = 48.0D;
    private static final double STEER_LEASH = 64.0D;
    private static final int STEER_EVERY = 5;
    public static final int PULSE = 10;
    private static final int LAND_EVERY = 10;
    private static final int WIND_EVERY = 30;
    private static final double BLEND = 0.45D;
    private static final float FLING_DAMAGE = 0.6F;
    private static final float CRIMSON_HEAL = 0.2F;
    private static final double GATE_MARGIN = 0.5D;
    private static final double GATE_FLOOR = 0.7D;
    private static final int MET_MEMORY = 16;
    public static final String KEY_ELEMENT = "El";
    public static final String KEY_STACKS = "St";
    public static final String KEY_FED = "Fed";
    private static final String KEY_WELL = "Well";
    private static final String KEY_TX = "TX";
    private static final String KEY_TZ = "TZ";
    private static final String KEY_MET = "Met";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CYCLONE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 at = ctx.aim() != null ? ctx.aim().point() : ctx.feet().add(ctx.look().scale(6.0D));
                BlockPos floor = PrimordialService.floor(level, at, 3, 12);
                Vec3 eye = floor != null ? Vec3.atBottomCenterOf(floor.above()) : at;
                float sky = PrimordialService.sky(level, BlockPos.containing(eye));
                PrimordialService.say(ctx.caster(), "sky", sky);
                float radius = (float) (BASE_RADIUS * ctx.size() * sky);
                SpellEffectEntity storm = SpellEffectEntity.spawn(ctx, eye, ctx.duration(), radius, ctx.look());
                storm.serverData().putFloat(KEY_WELL, sky);
                storm.serverData().putDouble(KEY_TX, eye.x);
                storm.serverData().putDouble(KEY_TZ, eye.z);
                write(storm, CycloneFeed.State.CALM, -100);
                storm.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.6F, 0.45F);
                level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8F, 0.4F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return AIM_RANGE;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }
        };
    }

    /** The funnel an effect of this radius, element and depth has; the client draws the same one. */
    public static Funnel funnel(float radius, StormElement element, int stacks) {
        double widen = CycloneFeed.widen(stacks);
        double r = Math.max(MIN_RADIUS, radius * element.reach() * widen);
        return new Funnel(r, Math.max(MIN_RADIUS * HEIGHT_PER_RADIUS, radius * HEIGHT_PER_RADIUS * widen));
    }

    public static CycloneFeed.State read(CompoundTag synced) {
        int ordinal = synced.getInt(KEY_ELEMENT);
        StormElement[] all = StormElement.values();
        StormElement element = ordinal >= 0 && ordinal < all.length ? all[ordinal] : StormElement.DUST;
        return element == StormElement.DUST ? CycloneFeed.State.CALM : new CycloneFeed.State(element, synced.getInt(KEY_STACKS));
    }

    private static void write(SpellEffectEntity storm, CycloneFeed.State state, int fedTick) {
        CompoundTag tag = storm.syncedData().copy();
        tag.putInt(KEY_ELEMENT, state.element().ordinal());
        tag.putInt(KEY_STACKS, state.stacks());
        tag.putInt(KEY_FED, fedTick);
        storm.setSyncedData(tag);
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity storm) {
                ServerLevel level = storm.serverLevel();
                Entity owner = storm.owner();
                CycloneFeed.State state = read(storm.syncedData());
                if (storm.tickCount % STEER_EVERY == 0) {
                    steer(level, storm, owner);
                }
                drift(level, storm);
                if (storm.tickCount % LAND_EVERY == 0) {
                    CycloneFeed.State landed = CycloneFeed.land(state, ground(level, storm.blockPosition()));
                    if (landed != state) {
                        state = landed;
                        write(storm, state, storm.tickCount);
                        fedCue(level, storm, state, storm.position().add(0.0D, 1.0D, 0.0D));
                    }
                }
                float well = storm.serverData().contains(KEY_WELL) ? storm.serverData().getFloat(KEY_WELL) : 1.0F;
                Funnel funnel = funnel(storm.radius(), state.element(), state.stacks());
                state = gate(level, storm, owner, funnel, state);
                funnel = funnel(storm.radius(), state.element(), state.stacks());
                draw(level, storm, owner, funnel, state, well);
                if (storm.tickCount % PULSE == 0) {
                    pulse(level, storm, owner, funnel, state, well);
                }
                if (storm.tickCount % WIND_EVERY == 1) {
                    level.playSound(null, storm.getX(), storm.getY() + 2.0D, storm.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 1.3F, 0.4F + 0.05F * state.stacks());
                }
            }

            @Override
            public void onExpire(SpellEffectEntity storm) {
                ServerLevel level = storm.serverLevel();
                level.sendParticles(ParticleTypes.CLOUD, storm.getX(), storm.getY() + 2.0D, storm.getZ(), 30, storm.radius(), 1.5D, storm.radius(), 0.08D);
                level.playSound(null, storm.getX(), storm.getY(), storm.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.8F, 1.4F);
            }
        };
    }

    /** Where the owner looks is where the storm goes. */
    private static void steer(ServerLevel level, SpellEffectEntity storm, Entity owner) {
        if (!(owner instanceof LivingEntity living) || !living.isAlive() || living.level() != level
                || living.distanceToSqr(storm) > STEER_LEASH * STEER_LEASH) {
            return;
        }
        Vec3 eye = living.getEyePosition();
        Vec3 end = eye.add(living.getLookAngle().scale(STEER_RANGE));
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, living));
        Vec3 target = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
        storm.serverData().putDouble(KEY_TX, target.x);
        storm.serverData().putDouble(KEY_TZ, target.z);
    }

    private static void drift(ServerLevel level, SpellEffectEntity storm) {
        CompoundTag data = storm.serverData();
        double tx = data.contains(KEY_TX) ? data.getDouble(KEY_TX) : storm.getX();
        double tz = data.contains(KEY_TZ) ? data.getDouble(KEY_TZ) : storm.getZ();
        double dx = tx - storm.getX();
        double dz = tz - storm.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double step = Math.min(dist, DRIFT * Math.max(0.35F, storm.speed()));
        double nx = storm.getX();
        double nz = storm.getZ();
        if (dist > 1.0E-3D) {
            nx += dx / dist * step;
            nz += dz / dist * step;
        }
        // a storm never stands still: a slow wander round wherever it is going
        double wander = storm.tickCount * 0.11D + storm.seed();
        nx += Math.cos(wander) * 0.035D;
        nz += Math.sin(wander * 1.3D) * 0.035D;
        BlockPos floor = PrimordialService.floor(level, new Vec3(nx, storm.getY(), nz), 3, 8);
        double ny = floor != null ? floor.getY() + 1.0D : storm.getY() - 0.3D;
        storm.setPos(nx, ny, nz);
    }

    private static StormElement ground(ServerLevel level, BlockPos at) {
        BlockState here = level.getBlockState(at);
        BlockState under = level.getBlockState(at.below());
        boolean lava = here.getFluidState().is(FluidTags.LAVA) || under.getFluidState().is(FluidTags.LAVA);
        boolean water = here.getFluidState().is(FluidTags.WATER) || under.getFluidState().is(FluidTags.WATER);
        return StormElement.ground(lava, water);
    }

    /** Swallow, meet or dispel whatever magic is thrown into the funnel. */
    private static CycloneFeed.State gate(ServerLevel level, SpellEffectEntity storm, Entity owner, Funnel funnel, CycloneFeed.State state) {
        double r = funnel.radius() + GATE_MARGIN;
        AABB box = new AABB(storm.getX() - r, storm.getY() - 1.0D, storm.getZ() - r, storm.getX() + r, storm.getY() + funnel.height(), storm.getZ() + r);
        for (Entity e : Bodies.around(level, storm, box, e -> !(e instanceof LivingEntity) && e.isAlive() && PrimordialService.spellLike(e))) {
            if (e.isRemoved() || (e instanceof SpellEffectEntity fx && fx.skillIndex() == storm.skillIndex())) {
                continue;
            }
            double dx = e.getX() - storm.getX();
            double dy = e.getY() - storm.getY();
            double dz = e.getZ() - storm.getZ();
            double wall = gateAt(funnel, dy);
            if (dy < -1.0D || dy > funnel.height() || dx * dx + dz * dz > wall * wall) {
                continue;
            }
            PrimordialService.Caught caught = PrimordialService.read(e);
            if (caught == null || !PrimordialService.inFlight(e, caught.owner())) {
                continue;
            }
            if (PrimordialService.sameOwner(caught.owner(), owner)) {
                if (!caught.magic()) {
                    continue; // the owner's own arrows fly through
                }
                StormElement fed = StormElement.fed(caught.skill().school(), caught.skill().id().getPath(), caught.frost());
                if (caught.primordial()) {
                    // the owner's catastrophes are met, not eaten, and colour it once each
                    if (!remember(storm, e.getId())) {
                        continue;
                    }
                } else {
                    PrimordialService.quell(e);
                }
                state = CycloneFeed.swallow(state, fed);
                write(storm, state, storm.tickCount);
                fedCue(level, storm, state, e.position());
                tell(owner, state);
            } else if (!caught.magic() && e instanceof Projectile projectile) {
                // a real arrow or trident is somebody's item: the storm throws it back out, once,
                // and it is the storm owner's after, so it flies through on the way out
                if (remember(storm, e.getId())) {
                    MagicCounterService.spawnClash(level, e.position(), 0xFFFFFF, state.element().color());
                    projectile.deflect(ProjectileDeflection.REVERSE, storm, owner instanceof LivingEntity ? owner : null, false);
                }
            } else {
                int incoming = caught.skill() != null ? caught.skill().color() : 0xFFFFFF;
                MagicCounterService.spawnClash(level, e.position(), incoming, state.element().color());
                PrimordialService.quell(e);
            }
        }
        return state;
    }

    /**
     * How far from the axis the eye takes a spell at a height. The funnel is narrowest at its foot,
     * which is exactly where a rolling or low spell travels, and a body being dragged in stands in
     * front of it there: so the gate never closes below {@link #GATE_FLOOR} of the crown.
     */
    static double gateAt(Funnel funnel, double dy) {
        return Math.max(funnel.radiusAt(Math.max(0.0D, dy)), funnel.radius() * GATE_FLOOR) + GATE_MARGIN;
    }

    /** True the first time this storm meets that entity. */
    private static boolean remember(SpellEffectEntity storm, int id) {
        CompoundTag data = storm.serverData();
        int[] met = data.getIntArray(KEY_MET);
        for (int m : met) {
            if (m == id) {
                return false;
            }
        }
        int keep = Math.min(met.length, MET_MEMORY - 1);
        int[] next = new int[keep + 1];
        System.arraycopy(met, met.length - keep, next, 0, keep);
        next[keep] = id;
        data.putIntArray(KEY_MET, next);
        return true;
    }

    private static void tell(Entity owner, CycloneFeed.State state) {
        if (!(owner instanceof ServerPlayer player)) {
            return;
        }
        Component text = state.calm()
                ? Component.translatable("message.magical.cyclone.cleansed")
                : Component.translatable("message.magical.cyclone.fed",
                        Component.translatable(state.element().key()).withColor(state.element().color()),
                        "I".repeat(Math.max(1, state.stacks())));
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.GRAY), true);
    }

    private static void fedCue(ServerLevel level, SpellEffectEntity storm, CycloneFeed.State state, Vec3 at) {
        level.sendParticles(matter(state.element()), at.x, at.y, at.z, 24, 0.6D, 0.6D, 0.6D, 0.12D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.2F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
    }

    /** The element's own matter, thrown on the server for the moments everybody should see. */
    public static ParticleOptions matter(StormElement element) {
        return switch (element) {
            case DUST -> ParticleTypes.DUST_PLUME;
            case EMBER -> ParticleTypes.FLAME;
            case TIDE -> ParticleTypes.SPLASH;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case RADIANT -> ParticleTypes.END_ROD;
            case MAELSTROM -> ParticleTypes.REVERSE_PORTAL;
            case BLIGHT -> ParticleTypes.SQUID_INK;
            case CRIMSON -> new net.minecraft.core.particles.DustParticleOptions(StormElement.CRIMSON.color(), 1.2F);
            case ARCANE -> ParticleTypes.ENCHANT;
            case STEEL -> ParticleTypes.CRIT;
            case STONE -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
            case DEEP -> ParticleTypes.SCULK_SOUL;
        };
    }

    private static boolean holdable(LivingEntity body) {
        return !body.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** In, round, up; out of the crown. */
    private static void draw(ServerLevel level, SpellEffectEntity storm, Entity owner, Funnel funnel, CycloneFeed.State state, float well) {
        double r = funnel.reach();
        AABB box = new AABB(storm.getX() - r, storm.getY() - 1.0D, storm.getZ() - r, storm.getX() + r, storm.getY() + funnel.height(), storm.getZ() + r);
        double strength = state.element().pull() * well;
        for (LivingEntity body : Bodies.of(level, LivingEntity.class, box, b -> SkillTargets.isHostile(owner, b))) {
            if (!holdable(body)) {
                continue;
            }
            double dx = body.getX() - storm.getX();
            double dy = body.getY() - storm.getY();
            double dz = body.getZ() - storm.getZ();
            if (!funnel.inReach(dx, dy, dz)) {
                continue;
            }
            boolean inside = funnel.inside(dx, dy, dz);
            if (inside && funnel.atCrown(dy)) {
                double[] f = funnel.fling(dx, dz, strength * (state.element() == StormElement.STONE ? 1.3D : 1.0D));
                body.setDeltaMovement(f[0], f[1], f[2]);
                body.hurtMarked = true;
                SkillTargets.hurt(level, owner, body, storm.damage() * FLING_DAMAGE * well, storm.skillId());
                continue;
            }
            double[] want = funnel.pull(dx, dy, dz, strength);
            Vec3 v = body.getDeltaMovement();
            double vy = inside ? Math.max(v.y, want[1]) : v.y;
            body.setDeltaMovement(v.x + (want[0] - v.x) * BLEND, vy, v.z + (want[2] - v.z) * BLEND);
            if (inside) {
                body.fallDistance = 0.0F;
            }
            body.hurtMarked = true;
        }
    }

    /** The hit every half second, and what the element does with it. */
    private static void pulse(ServerLevel level, SpellEffectEntity storm, Entity owner, Funnel funnel, CycloneFeed.State state, float well) {
        StormElement element = state.element();
        float potency = CycloneFeed.potency(state.stacks());
        float base = storm.damage() * well * element.damage() * potency;
        double r = funnel.radius() + GATE_MARGIN;
        AABB box = new AABB(storm.getX() - r, storm.getY() - 1.0D, storm.getZ() - r, storm.getX() + r, storm.getY() + funnel.height(), storm.getZ() + r);
        List<LivingEntity> inside = new ArrayList<>();
        for (LivingEntity body : Bodies.of(level, LivingEntity.class, box, b -> SkillTargets.isHostile(owner, b))) {
            double dx = body.getX() - storm.getX();
            double dy = body.getY() - storm.getY();
            double dz = body.getZ() - storm.getZ();
            double wall = funnel.radiusAt(dy) + GATE_MARGIN;
            if (dy >= -1.0D && dy <= funnel.height() && dx * dx + dz * dz <= wall * wall) {
                inside.add(body);
            }
        }
        boolean rooted = false;
        for (LivingEntity body : inside) {
            float dealt = base;
            if (element == StormElement.RADIANT && body.getType().is(EntityTypeTags.UNDEAD)) {
                dealt *= 2.0F;
            }
            SkillTargets.hurt(level, owner, body, dealt, storm.skillId());
            int ticks = Math.round(60 * potency);
            switch (element) {
                case EMBER -> body.igniteForSeconds(3.0F * potency);
                case TIDE -> {
                    body.clearFire();
                    body.setAirSupply(Math.max(-19, body.getAirSupply() - Math.round(60 * potency)));
                    body.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 0));
                }
                case FROST -> {
                    body.setTicksFrozen(Math.min(body.getTicksRequiredToFreeze() + 80, body.getTicksFrozen() + Math.round(50 * potency)));
                    body.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 1));
                }
                case RADIANT -> body.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
                case BLIGHT -> body.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks, Math.max(0, state.stacks() - 1)));
                case CRIMSON -> {
                    if (owner instanceof LivingEntity living && living.isAlive()) {
                        living.heal(dealt * CRIMSON_HEAL);
                    }
                }
                case ARCANE -> {
                    for (MobEffectInstance effect : List.copyOf(body.getActiveEffects())) {
                        if (effect.getEffect().value().isBeneficial()) {
                            body.removeEffect(effect.getEffect());
                        }
                    }
                }
                case DEEP -> {
                    if (!rooted) {
                        rooted = true;
                        MagicStatusService.apply(body, MagicStatus.ROOTED, Math.round(20 * potency), storm.skillId(), owner);
                    }
                }
                default -> {
                }
            }
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.PRIMORDIAL)
                .circle(CircleScript.of(SchoolMaterial.PRIMORDIAL).emblem(EmblemId.VORTEX).frame(5)
                        .band(GlyphKind.WAVE_BAND, 12, ColorRole.BRIGHT)
                        .band(GlyphKind.DASHED_RING, 1, ColorRole.INK)
                        .stamps(StampId.SPIRAL, 6).core(CoreKind.RIPPLE, ColorRole.HOT).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.mark(FxKinds.Mark.VORTEX_SPIRAL, 1.6F).withRole(ColorRole.DIM))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.DUST, FxKinds.Overlay.VIGNETTE)
                .budget(3)
                .bounds(4.0F, 9.0F, 1.0F);
    }
}
