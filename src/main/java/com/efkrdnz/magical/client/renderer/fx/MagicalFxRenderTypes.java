package com.efkrdnz.magical.client.renderer.fx;

import com.efkrdnz.magical.MagicalMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * The ten library shaders and their sixteen render types. All use the MagicVertex format
 * (POSITION_COLOR_TEX_LIGHTMAP with NO_LIGHTMAP so UV2 is a free integer channel). Every type is
 * registered as a fixed render buffer so one batch per type per frame is real and the flush order
 * (solids, darkness, glow) is deterministic.
 */
public final class MagicalFxRenderTypes {
    private static final VertexFormat FORMAT = DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP;

    private static final ShaderProgram GLYPH_INK = program("rendertype_glyph_ink");
    private static final ShaderProgram PLASMA_ORB = program("rendertype_plasma_orb");
    private static final ShaderProgram FILAMENT_BEAM = program("rendertype_filament_beam");
    private static final ShaderProgram SURFACE_FIELD = program("rendertype_surface_field");
    private static final ShaderProgram GROUND_MARK = program("rendertype_ground_mark");
    private static final ShaderProgram RIFT_CUT = program("rendertype_rift_cut");
    private static final ShaderProgram LENS_WARP = program("rendertype_lens_warp");
    private static final ShaderProgram SHARD_BODY = program("rendertype_shard_body");
    private static final ShaderProgram SMOKE_VEIL = program("rendertype_smoke_veil");
    private static final ShaderProgram FP_OVERLAY = program("rendertype_fp_overlay");

    private static RenderType shardBody;
    private static RenderType surfaceField;
    private static RenderType groundMark;
    private static RenderType riftCut;
    private static RenderType lensWarp;
    private static RenderType glyphVoid;
    private static RenderType plasmaOrbDark;
    private static RenderType filamentDark;
    private static RenderType smokeVeil;
    private static RenderType glyphInk;
    private static RenderType glyphInkThrough;
    private static RenderType groundMarkAdd;
    private static RenderType plasmaOrb;
    private static RenderType filamentBeam;
    private static RenderType smokeAdd;
    private static RenderType fpOverlay;

    private MagicalFxRenderTypes() {}

    private static ShaderProgram program(String name) {
        return new ShaderProgram(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/" + name), FORMAT, ShaderDefines.EMPTY);
    }

    public static void registerShaders(RegisterShadersEvent event) {
        event.registerShader(GLYPH_INK);
        event.registerShader(PLASMA_ORB);
        event.registerShader(FILAMENT_BEAM);
        event.registerShader(SURFACE_FIELD);
        event.registerShader(GROUND_MARK);
        event.registerShader(RIFT_CUT);
        event.registerShader(LENS_WARP);
        event.registerShader(SHARD_BODY);
        event.registerShader(SMOKE_VEIL);
        event.registerShader(FP_OVERLAY);
    }

    /** Flush order: solids -> darkness -> glow. */
    public static List<RenderType> flushOrder() {
        List<RenderType> order = new ArrayList<>();
        order.add(shardBody());
        order.add(surfaceField());
        order.add(groundMark());
        order.add(riftCut());
        order.add(lensWarp());
        order.add(glyphVoid());
        order.add(plasmaOrbDark());
        order.add(filamentDark());
        order.add(smokeVeil());
        order.add(glyphInk());
        order.add(groundMarkAdd());
        order.add(plasmaOrb());
        order.add(filamentBeam());
        order.add(smokeAdd());
        return order;
    }

    public static void registerRenderBuffers(RegisterRenderBuffersEvent event) {
        for (RenderType type : flushOrder()) {
            event.registerRenderBuffer(type);
        }
    }

    private static RenderType.CompositeState.CompositeStateBuilder base(ShaderProgram program, boolean additive, boolean noiseOnly) {
        RenderStateShard.EmptyTextureStateShard textures = noiseOnly
                ? new RenderStateShard.TextureStateShard(FxTextures.NOISE, TriState.TRUE, false)
                : RenderStateShard.MultiTextureStateShard.builder()
                        .add(FxTextures.NOISE, true, false)
                        .add(FxTextures.EMBLEMS, true, false)
                        .build();
        return RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(program))
                .setTextureState(textures)
                .setTransparencyState(additive ? RenderStateShard.ADDITIVE_TRANSPARENCY : RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .setOverlayState(RenderStateShard.NO_OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE);
    }

    private static RenderType create(String name, RenderType.CompositeState.CompositeStateBuilder builder, int bufferSize, boolean sort) {
        return RenderType.create("magical_" + name, FORMAT, VertexFormat.Mode.QUADS, bufferSize, false, sort, builder.createCompositeState(false));
    }

    public static RenderType glyphInk() {
        if (glyphInk == null) {
            glyphInk = create("glyph_ink", base(GLYPH_INK, true, false), 16384, false);
        }
        return glyphInk;
    }

    public static RenderType glyphVoid() {
        if (glyphVoid == null) {
            glyphVoid = create("glyph_void", base(GLYPH_INK, false, false), 8192, true);
        }
        return glyphVoid;
    }

    /** Additive glyphs through terrain; use only inside the AFTER_CUTOUT_BLOCKS level-stage pass. */
    public static RenderType glyphInkThrough() {
        if (glyphInkThrough == null) {
            glyphInkThrough = create("glyph_ink_through", base(GLYPH_INK, true, false).setDepthTestState(RenderStateShard.NO_DEPTH_TEST), 8192, false);
        }
        return glyphInkThrough;
    }

    public static RenderType plasmaOrb() {
        if (plasmaOrb == null) {
            plasmaOrb = create("plasma_orb", base(PLASMA_ORB, true, true), 8192, false);
        }
        return plasmaOrb;
    }

    public static RenderType plasmaOrbDark() {
        if (plasmaOrbDark == null) {
            plasmaOrbDark = create("plasma_orb_dark", base(PLASMA_ORB, false, true), 4096, true);
        }
        return plasmaOrbDark;
    }

    public static RenderType filamentBeam() {
        if (filamentBeam == null) {
            filamentBeam = create("filament_beam", base(FILAMENT_BEAM, true, true), 16384, false);
        }
        return filamentBeam;
    }

    public static RenderType filamentDark() {
        if (filamentDark == null) {
            filamentDark = create("filament_dark", base(FILAMENT_BEAM, false, true), 8192, true);
        }
        return filamentDark;
    }

    public static RenderType surfaceField() {
        if (surfaceField == null) {
            surfaceField = create("surface_field", base(SURFACE_FIELD, false, true), 16384, true);
        }
        return surfaceField;
    }

    public static RenderType groundMark() {
        if (groundMark == null) {
            groundMark = create("ground_mark", base(GROUND_MARK, false, true), 4096, true);
        }
        return groundMark;
    }

    public static RenderType groundMarkAdd() {
        if (groundMarkAdd == null) {
            groundMarkAdd = create("ground_mark_add", base(GROUND_MARK, true, true), 4096, false);
        }
        return groundMarkAdd;
    }

    public static RenderType riftCut() {
        if (riftCut == null) {
            riftCut = create("rift_cut", base(RIFT_CUT, false, true), 4096, true);
        }
        return riftCut;
    }

    public static RenderType lensWarp() {
        if (lensWarp == null) {
            lensWarp = create("lens_warp", base(LENS_WARP, false, true), 2048, true);
        }
        return lensWarp;
    }

    /** Solid bodies write depth so glow behind them is occluded. */
    public static RenderType shardBody() {
        if (shardBody == null) {
            // The size is in BYTES, not vertices: this format is 28 bytes a vertex, so the old
            // 16384 was 585 vertices - smaller than a single prism. A blood voxel field is about
            // 437 KB, and undersizing only costs a run of reallocations on its first frame.
            shardBody = create("shard_body", base(SHARD_BODY, false, true).setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE), 786432, true);
        }
        return shardBody;
    }

    public static RenderType smokeVeil() {
        if (smokeVeil == null) {
            smokeVeil = create("smoke_veil", base(SMOKE_VEIL, false, false), 16384, true);
        }
        return smokeVeil;
    }

    public static RenderType smokeAdd() {
        if (smokeAdd == null) {
            smokeAdd = create("smoke_add", base(SMOKE_VEIL, true, false), 16384, false);
        }
        return smokeAdd;
    }

    /** Screen-space overlay quad (GUI pass). */
    public static RenderType fpOverlay() {
        if (fpOverlay == null) {
            fpOverlay = create("fp_overlay", base(FP_OVERLAY, false, true).setDepthTestState(RenderStateShard.NO_DEPTH_TEST), 1024, false);
        }
        return fpOverlay;
    }

    public static ShaderProgram smokeVeilProgram() {
        return SMOKE_VEIL;
    }
}
