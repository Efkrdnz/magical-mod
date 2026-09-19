package com.efkrdnz.magical.client.renderer.verse;

import com.efkrdnz.magical.client.renderer.fx.FxBudget;
import com.efkrdnz.magical.client.renderer.fx.FxContext;
import com.efkrdnz.magical.client.renderer.fx.paint.FilamentPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.MarkPainter;
import com.efkrdnz.magical.client.renderer.fx.paint.OrbPainter;
import com.efkrdnz.magical.entity.verse.VerseBehaviours;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.incantation.Behaviour;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.incantation.Wake;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a verse body: the {@link VerseLooks} row for its prototype in its school's colour, a small
 * glyph orbiting it per behaviour it carries, a filament trailing behind it per wake, and a wider
 * bloom when it is a Lantern. A static fades in and out over its life; a flying body is full
 * until it ends. A bolt also wears a head: its tube is two ribbons along the flight line, and
 * the caster it left sees that line end-on, where a ribbon is a point, so the billboarded head
 * is what a first-person frame shows. Everything is drawn through the existing painters, so the
 * budget and the LOD are theirs.
 */
public final class VerseBodyRenderer extends EntityRenderer<VerseBodyEntity, VerseBodyRenderer.State> {

    private static final float GLYPH_SIZE = 0.12F;
    private static final float GLYPH_ORBIT = 0.2F;
    private static final float GLYPH_TURN_PER_TICK = 0.2F;
    private static final float GLYPH_OPACITY = 0.9F;
    private static final float WAKE_LENGTH = 1.6F;
    private static final float WAKE_WIDTH = 0.6F;
    private static final float WAKE_OPACITY = 0.7F;
    private static final float LANTERN_SCALE = 3.0F;
    private static final float LANTERN_OPACITY = 0.35F;
    /**
     * The beam shader's reveal window: 0.5 shows the whole length, 1.0 has receded to nothing (the
     * end of a hitscan flash). A body is a whole bolt for as long as it flies.
     */
    private static final float BEAM_WHOLE = 0.5F;
    /** The head a bolt wears, on its drawn size; the caster sees the tube end-on and this is what shows. */
    private static final float BOLT_HEAD_SCALE = 1.5F;
    private static final float BOLT_HEAD_OPACITY = 0.9F;

    public VerseBodyRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static final class State extends EntityRenderState {
        public float partialTick;
        public VersePrototype.Look look = VersePrototype.Look.ORB;
        public boolean isStatic;
        public MagicSchool school = MagicSchool.ARCANE;
        public float radius;
        public float age;
        public int life;
        public int behaviours;
        public int wakes;
        public Vec3 direction = new Vec3(0.0D, 0.0D, 1.0D);
        public int seed;
        public double distanceSqr;
        public Vec3 cameraOffset = Vec3.ZERO;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VerseBodyEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        VersePrototype prototype = entity.prototype();
        state.partialTick = partialTick;
        state.look = prototype.look();
        state.isStatic = prototype.isStatic();
        state.school = entity.school();
        state.radius = entity.radius();
        state.age = entity.tickCount + partialTick;
        state.life = entity.life();
        state.behaviours = entity.behaviourMask();
        state.wakes = entity.wakeMask();
        state.direction = entity.direction();
        state.seed = entity.seed();
        Vec3 pos = entity.getPosition(partialTick);
        Vec3 camera = entityRenderDispatcher.camera.getPosition();
        state.distanceSqr = camera.distanceToSqr(pos);
        state.cameraOffset = camera.subtract(pos);
    }

    @Override
    public boolean shouldRender(VerseBodyEntity entity, Frustum frustum, double cameraX, double cameraY, double cameraZ) {
        AABB box = entity.getBoundingBox().inflate(Math.max(1.0D, entity.radius() + 1.0D));
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(cameraX, cameraY, cameraZ)) && frustum.isVisible(box);
    }

    @Override
    public void render(State state, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        VerseLooks.Row row = VerseLooks.of(state.look);
        SchoolMaterial material = SchoolMaterial.of(state.school);
        int rgb = material.variantColor(VerseLooks.PALETTE_VARIANT);
        int bright = material.variantColor(VerseLooks.GLYPH_VARIANT);
        FxContext ctx = new FxContext(poseStack, buffers, state.partialTick, entityRenderDispatcher.cameraOrientation(), state.cameraOffset)
                .timing(state.age, state.isStatic ? state.life : 0.0F, state.seed);
        ctx.detail = FxBudget.detailForDistance(3, state.distanceSqr);
        ctx.lod = FxBudget.lodForDistance(state.distanceSqr);
        float fade = state.isStatic ? ctx.fade() : 1.0F;
        float size = VerseLooks.drawnSize(row, state.radius);

        poseStack.pushPose();
        switch (row.shape()) {
            case BOLT -> {
                poseStack.pushPose();
                FilamentPainter.orientAlong(poseStack, state.direction);
                poseStack.translate(0.0D, 0.0D, -row.length() * 0.5D);
                FilamentPainter.beam(ctx, row.filament(), size, row.length(), rgb, fade * row.opacity(), BEAM_WHOLE, row.count(), row.paramB());
                poseStack.popPose();
                OrbPainter.billboard(ctx, FxKinds.Orb.PLASMA, size * BOLT_HEAD_SCALE, rgb, fade * row.opacity() * BOLT_HEAD_OPACITY, ctx.phase, 3, 8);
            }
            case ORB -> OrbPainter.billboard(ctx, row.orb(), size, rgb, fade * row.opacity(), ctx.phase, row.count(), row.paramB());
            case MARK -> {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
                poseStack.translate(0.0D, 0.0D, -0.04D);
                MarkPainter.mark(ctx, row.mark(), size, rgb, fade * row.opacity(), ctx.phase, row.count(), row.paramB());
                poseStack.popPose();
            }
        }
        if (VerseBehaviours.has(state.behaviours, Behaviour.LANTERN)) {
            OrbPainter.billboard(ctx, FxKinds.Orb.BLOOM_FLASH, size * LANTERN_SCALE, bright, fade * LANTERN_OPACITY, ctx.phase, 1, 4);
        }
        drawGlyphs(ctx, poseStack, state, size, bright, fade);
        drawWakes(ctx, poseStack, state, size, fade);
        poseStack.popPose();
    }

    /** One small orb per behaviour the body carries, spaced round it and turning with its age. */
    private static void drawGlyphs(FxContext ctx, PoseStack poseStack, State state, float size, int bright, float fade) {
        Behaviour[] all = Behaviour.values();
        int carried = Integer.bitCount(state.behaviours);
        if (carried == 0) {
            return;
        }
        float orbit = size + GLYPH_ORBIT;
        int index = 0;
        for (Behaviour behaviour : all) {
            if (!VerseBehaviours.has(state.behaviours, behaviour)) {
                continue;
            }
            double angle = index * (Math.PI * 2.0D / carried) + state.age * GLYPH_TURN_PER_TICK;
            poseStack.pushPose();
            poseStack.translate(Math.cos(angle) * orbit, Math.sin(angle) * orbit * 0.5D, Math.sin(angle) * orbit);
            OrbPainter.billboard(ctx, VerseLooks.glyph(behaviour), GLYPH_SIZE, bright, fade * GLYPH_OPACITY, ctx.phase, 1, 8);
            poseStack.popPose();
            index++;
        }
    }

    /** One filament per wake, trailing behind the body along the line it came down. */
    private static void drawWakes(FxContext ctx, PoseStack poseStack, State state, float size, float fade) {
        if (state.wakes == 0 || state.isStatic) {
            return;
        }
        Vec3 back = state.direction.scale(-1.0D);
        for (Wake wake : Wake.values()) {
            if ((state.wakes & (1 << wake.ordinal())) == 0) {
                continue;
            }
            poseStack.pushPose();
            FilamentPainter.orientAlong(poseStack, back);
            FilamentPainter.beam(ctx, VerseLooks.wake(wake), size * WAKE_WIDTH, WAKE_LENGTH, VerseLooks.wakeColor(wake), fade * WAKE_OPACITY, BEAM_WHOLE, 4, 6);
            poseStack.popPose();
        }
    }
}
