package com.efkrdnz.magical.magic.primordial;

import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.CounterableSkillThreat;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.service.InterceptableSpell;
import com.efkrdnz.magical.magic.visual.Accent;
import com.efkrdnz.magical.magic.SpacePocketService;
import com.efkrdnz.magical.magic.mind.DreamService;
import com.efkrdnz.magical.magic.service.Bodies;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Primordial school against a real level: where the ground is, what may be carved or raised,
 * how hard the land answers (sampled for {@link Wellspring}), and what a spell caught in a storm
 * is. Every block a catastrophe moves goes through {@code ConjuredTerrainService} and comes back.
 */
public final class PrimordialService {
    public static final int SKY_RADIUS = 4;
    public static final int HEAT_RADIUS = 6;
    public static final int WATER_RADIUS = 8;
    /** Rain on the caster is a sea overhead. */
    public static final int RAIN_WATER = Wellspring.WATER_FULL / 4;
    /** A spell this close to its owner is riding them, not thrown. */
    public static final double RIDING = 2.0D;
    /** Moved at least this far (squared) since last tick: in flight. */
    public static final double IN_FLIGHT_SQR = 0.0025D;
    private static final int UNBURY_STEPS = 12;

    private PrimordialService() {}

    /**
     * The top of the ground at a point: the block at the point itself, climbed while buried (at most
     * {@code up}) or followed down to the first solid block (at most {@code down}). Walked from the
     * point rather than scanned from above it, so a roof, a canopy or an overhang over where the
     * caster aimed is never taken for the ground they aimed at. Null where there is none.
     */
    public static BlockPos surface(Level level, Vec3 at, int up, int down) {
        return top(level, at, up, down, false);
    }

    /** Like {@link #surface} but a fluid is ground too: a storm or a wave rides the sea, not the seabed. */
    public static BlockPos floor(Level level, Vec3 at, int up, int down) {
        return top(level, at, up, down, true);
    }

    private static BlockPos top(Level level, Vec3 at, int up, int down, boolean fluids) {
        BlockPos p = BlockPos.containing(at);
        if (!level.isLoaded(p)) {
            return null;
        }
        if (ground(level, p, fluids)) {
            for (int i = 0; i < up; i++) {
                BlockPos above = p.above();
                if (!level.isLoaded(above) || !ground(level, above, fluids)) {
                    return p;
                }
                p = above;
            }
            // still buried after the whole climb: a tunnel, a cliff face - no ground to wake here
            return null;
        }
        for (int i = 0; i < down; i++) {
            p = p.below();
            if (!level.isLoaded(p)) {
                return null;
            }
            if (ground(level, p, fluids)) {
                return p;
            }
        }
        return null;
    }

    private static boolean ground(Level level, BlockPos pos, boolean fluids) {
        return solid(level, pos) || fluids && !level.getBlockState(pos).getFluidState().isEmpty();
    }

    /**
     * Whether nothing stands between a block and the sky: read off the heightmap, which moves the
     * moment a roof is placed, where sky light takes a lighting pass to notice it.
     */
    public static boolean opensToSky(Level level, BlockPos pos) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) <= pos.getY();
    }

    public static boolean solid(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.getCollisionShape(level, pos).isEmpty();
    }

    /** May a catastrophe take this block away (and give it back later)? */
    public static boolean carvable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || sacred(level, pos)) {
            return false;
        }
        BlockState s = level.getBlockState(pos);
        return !s.isAir() && s.getFluidState().isEmpty() && !s.hasBlockEntity()
                && s.getDestroySpeed(level, pos) >= 0.0F && !s.is(BlockTags.FEATURES_CANNOT_REPLACE)
                && !s.is(BlockTags.PORTALS) && !s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF);
    }

    /** May a catastrophe put a block here? */
    public static boolean raisable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos) || sacred(level, pos)) {
            return false;
        }
        BlockState s = level.getBlockState(pos);
        return s.canBeReplaced() && s.getFluidState().isEmpty() && !s.hasBlockEntity();
    }

    /** Ground no catastrophe may touch: the shell of a pocket room, a dreamscape. */
    private static boolean sacred(ServerLevel level, BlockPos pos) {
        return SpacePocketService.isProtectedPocketShell(level, pos) || DreamService.isDream(level, pos);
    }

    /**
     * Take a block out of the world (or turn it into {@code replacement}) through the edit, together
     * with everything that hangs on it - a torch, a rail, a flower, a carpet - so nothing pops off as a
     * drop the give-back could never return. Refused, leaving the world untouched, wherever the cut
     * would do something the ledger cannot undo: a block entity or a portal beside it, a fluid that
     * would pour in, sand or gravel over it, or a neighbour that hangs on it and cannot be recorded.
     * Returns what stood there, or null when refused.
     */
    public static BlockState carve(ServerLevel level, ConjuredTerrainService.Edit edit, BlockPos pos, BlockState replacement) {
        if (edit == null || !carvable(level, pos)) {
            return null;
        }
        List<BlockPos> hanging = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos n = pos.relative(side);
            if (!level.isLoaded(n)) {
                return null;
            }
            BlockState ns = level.getBlockState(n);
            if (ns.isAir()) {
                continue;
            }
            if (!ns.getFluidState().isEmpty() || ns.hasBlockEntity() || ns.is(BlockTags.PORTALS)
                    || side == Direction.UP && ns.getBlock() instanceof FallingBlock) {
                return null;
            }
            if (solid(level, n)) {
                continue;
            }
            // something light that may hang on this block: recorded and lifted away first
            if (ns.getDestroySpeed(level, n) < 0.0F || ns.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || sacred(level, n)) {
                return null;
            }
            hanging.add(n);
        }
        for (BlockPos n : hanging) {
            ConjuredTerrainService.replace(level, edit, n, Blocks.AIR.defaultBlockState());
        }
        BlockState was = level.getBlockState(pos);
        ConjuredTerrainService.replace(level, edit, pos, replacement);
        if (!replacement.isAir()) {
            matter(level).add(pos.immutable());
        }
        return was;
    }

    /** Put a block where there was room for one, through the edit; it is conjured matter until given back. */
    public static boolean place(ServerLevel level, ConjuredTerrainService.Edit edit, BlockPos pos, BlockState state) {
        if (edit == null || !raisable(level, pos)) {
            return false;
        }
        if (ConjuredTerrainService.replace(level, edit, pos, state)) {
            matter(level).add(pos.immutable());
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ conjured matter

    /**
     * Every block a catastrophe has put into the world and not yet given back, per dimension. None of
     * it drops anything when broken ({@code PrimordialScars.onDrops}), or a crater lining or a cone
     * mined out before the ground closed is a free stack of basalt on top of the ground coming back.
     * Never saved: an edit the level loads with is given back at load, so no matter survives a restart.
     */
    private static final Map<ResourceKey<Level>, Set<BlockPos>> MATTER = new HashMap<>();

    private static Set<BlockPos> matter(Level level) {
        return MATTER.computeIfAbsent(level.dimension(), k -> new HashSet<>());
    }

    public static boolean isMatter(Level level, BlockPos pos) {
        Set<BlockPos> held = MATTER.get(level.dimension());
        return held != null && held.contains(pos);
    }

    /**
     * Give an edit back: whatever stands in its cells is lifted out first so nobody is walled into the
     * ground returning, the matter stops being matter, and only what is still the catastrophe's own is
     * restored - a block a player put there since is kept.
     */
    public static void giveBack(ServerLevel level, ConjuredTerrainService.Edit edit, Predicate<BlockState> stillOurs) {
        if (edit == null) {
            return;
        }
        List<BlockPos> cells = List.copyOf(edit.positions());
        List<LivingEntity> inside = new ArrayList<>();
        for (BlockPos cell : cells) {
            if (!level.isLoaded(cell)) {
                continue;
            }
            for (LivingEntity body : Bodies.of(level, LivingEntity.class, new AABB(cell), LivingEntity::isAlive)) {
                if (!inside.contains(body)) {
                    inside.add(body);
                }
            }
        }
        Set<BlockPos> held = MATTER.get(level.dimension());
        if (held != null) {
            cells.forEach(held::remove);
        }
        ConjuredTerrainService.restoreUnlessBuiltOver(level, edit, stillOurs);
        for (LivingEntity body : inside) {
            unbury(level, body, body.getY());
        }
    }

    public static void forgetAllMatter() {
        MATTER.clear();
    }

    /** Stand a body at the first height from {@code y} up where it fits, never inside the rock. */
    public static void unbury(ServerLevel level, LivingEntity body, double y) {
        double at = y;
        for (int i = 0; i < UNBURY_STEPS && !level.noCollision(body, body.getBoundingBox().move(0.0D, at - body.getY(), 0.0D)); i++) {
            at = Math.floor(at) + 1.0D;
        }
        if (Math.abs(at - body.getY()) < 1.0E-4D) {
            return;
        }
        if (body instanceof ServerPlayer player) {
            player.teleportTo(player.getX(), at, player.getZ());
        } else {
            body.setPos(body.getX(), at, body.getZ());
        }
        body.fallDistance = 0.0F;
    }

    public static boolean stoneLike(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER) || s.is(BlockTags.STONE_BRICKS)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE)
                || s.is(Blocks.COBBLED_DEEPSLATE);
    }

    public static boolean hot(BlockState s) {
        return s.getFluidState().is(FluidTags.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(BlockTags.FIRE) || s.is(BlockTags.CAMPFIRES);
    }

    // ------------------------------------------------------------------ the wellsprings

    public static float sky(ServerLevel level, BlockPos at) {
        int open = 0;
        int sampled = 0;
        for (int dx = -SKY_RADIUS; dx <= SKY_RADIUS; dx += 2) {
            for (int dz = -SKY_RADIUS; dz <= SKY_RADIUS; dz += 2) {
                sampled++;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX() + dx, at.getZ() + dz) <= at.getY() + 1) {
                    open++;
                }
            }
        }
        return Wellspring.sky(open, sampled);
    }

    public static float stone(ServerLevel level, List<BlockPos> tops, int depth) {
        int stone = 0;
        int sampled = 0;
        for (BlockPos top : tops) {
            for (int d = 0; d < depth; d++) {
                sampled++;
                if (stoneLike(level.getBlockState(top.below(d)))) {
                    stone++;
                }
            }
        }
        return Wellspring.stone(stone, sampled);
    }

    public static float heat(ServerLevel level, BlockPos at) {
        int hot = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -HEAT_RADIUS; dx <= HEAT_RADIUS; dx++) {
            for (int dy = -HEAT_RADIUS; dy <= HEAT_RADIUS; dy++) {
                for (int dz = -HEAT_RADIUS; dz <= HEAT_RADIUS; dz++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.isLoaded(p) && hot(level.getBlockState(p))) {
                        hot++;
                    }
                }
            }
        }
        return Wellspring.heat(hot, level.dimension() == Level.NETHER, at.getY());
    }

    public static float water(ServerLevel level, BlockPos at) {
        int water = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -WATER_RADIUS; dx <= WATER_RADIUS; dx++) {
            for (int dy = -WATER_RADIUS; dy <= WATER_RADIUS; dy++) {
                for (int dz = -WATER_RADIUS; dz <= WATER_RADIUS; dz++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    if (level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER)) {
                        water++;
                    }
                }
            }
        }
        if (level.isRainingAt(at.above())) {
            water += RAIN_WATER;
        }
        return Wellspring.water(water);
    }

    /** The caster hears how hard the land answered: "The sky answers: strong". */
    public static void say(Entity caster, String wellspring, float reading) {
        if (!(caster instanceof ServerPlayer player)) {
            return;
        }
        Wellspring.Word word = Wellspring.word(reading);
        ChatFormatting colour = switch (word) {
            case FAINT -> ChatFormatting.GRAY;
            case STEADY -> ChatFormatting.WHITE;
            case STRONG -> ChatFormatting.GOLD;
            case OVERWHELMING -> ChatFormatting.RED;
        };
        player.displayClientMessage(Component.translatable("message.magical.primordial.reading",
                Component.translatable("wellspring.magical." + wellspring),
                Component.translatable(word.key()).withStyle(colour)), true);
    }

    public static void refuse(Entity caster, String key) {
        if (caster instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
        }
    }

    // ------------------------------------------------------------------ spells caught in a storm

    /** A spell (or plain projectile) and who threw it; {@code skill} is null for anything that is not a skill. */
    public record Caught(Entity entity, Entity owner, MagicSkillDefinition skill, boolean frost) {
        public boolean magic() {
            return skill != null;
        }

        public boolean primordial() {
            return skill != null && skill.school() == MagicSchool.PRIMORDIAL;
        }
    }

    public static Caught read(Entity e) {
        if (e instanceof SpellEffectEntity fx) {
            MagicSkillDefinition skill = MagicContent.get(fx.skillId());
            boolean frost = skill != null && fx.profile().accent() == Accent.FROST;
            return new Caught(e, fx.owner(), skill, frost);
        }
        if (e instanceof CounterableSkillThreat threat) {
            return new Caught(e, threat.counterOwner(), MagicContent.get(threat.counterSkillId()), false);
        }
        if (e instanceof InterceptableSpell spell) {
            return new Caught(e, spell.spellOwner(), null, false);
        }
        if (e instanceof Projectile projectile) {
            return new Caught(e, projectile.getOwner(), null, false);
        }
        return null;
    }

    public static boolean spellLike(Entity e) {
        return e instanceof SpellEffectEntity || e instanceof CounterableSkillThreat
                || e instanceof InterceptableSpell || e instanceof Projectile;
    }

    /** Thrown, not carried: it moved since last tick and is not riding its owner. */
    public static boolean inFlight(Entity e, Entity owner) {
        if (e.position().distanceToSqr(e.xo, e.yo, e.zo) < IN_FLIGHT_SQR) {
            return false;
        }
        return owner == null || owner.level() != e.level() || owner.distanceToSqr(e) > RIDING * RIDING;
    }

    public static boolean sameOwner(Entity a, Entity b) {
        return a != null && b != null && a.getUUID().equals(b.getUUID());
    }

    /**
     * End a spell the way a counter does: flagged and gone. A Primordial effect is finished instead,
     * so the ground it holds is given back rather than left open until the next restart.
     */
    public static void quell(Entity e) {
        if (e instanceof SpellEffectEntity fx) {
            MagicSkillDefinition skill = MagicContent.get(fx.skillId());
            if (skill != null && skill.school() == MagicSchool.PRIMORDIAL) {
                fx.finish();
                return;
            }
            fx.serverData().putBoolean("Countered", true);
            fx.setPhase(SpellEffectEntity.PHASE_DONE);
        }
        e.discard();
    }

    // ------------------------------------------------------------------ masses

    /** Write a rock's blocks onto an effect's synced data (a fresh tag, so the change is sent). */
    public static void carry(SpellEffectEntity fx, List<BlockState> states, List<int[]> offsets, float scale, float spin, boolean glow) {
        int n = Math.min(MassCodec.MAX_BLOCKS, Math.min(states.size(), offsets.size()));
        int[] ids = new int[n];
        int[] offs = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = Block.getId(states.get(i));
            int[] o = offsets.get(i);
            offs[i] = MassCodec.pack(o[0], o[1], o[2]);
        }
        var tag = fx.syncedData().copy();
        tag.putIntArray(MassCodec.STATES, ids);
        tag.putIntArray(MassCodec.OFFSETS, offs);
        tag.putFloat(MassCodec.SCALE, scale);
        tag.putFloat(MassCodec.SPIN, spin);
        tag.putBoolean(MassCodec.GLOW, glow);
        fx.setSyncedData(tag);
    }

    public static void spin(SpellEffectEntity fx, float spin) {
        var tag = fx.syncedData().copy();
        tag.putFloat(MassCodec.SPIN, spin);
        fx.setSyncedData(tag);
    }

    /** The rock is gone: nothing left to draw. */
    public static void drop(SpellEffectEntity fx) {
        var tag = fx.syncedData().copy();
        tag.remove(MassCodec.STATES);
        tag.remove(MassCodec.OFFSETS);
        fx.setSyncedData(tag);
    }
}
