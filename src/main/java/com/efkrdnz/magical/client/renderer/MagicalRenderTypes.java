package com.efkrdnz.magical.client.renderer;

import com.efkrdnz.magical.MagicalMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class MagicalRenderTypes {
    private static final ShaderProgram SINGULARITY_LENS_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_singularity_lens"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram EVENT_HORIZON_FIELD_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_event_horizon_field"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram STELLAR_GLOW_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_stellar_glow"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram WHITE_HOLE_WAVE_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_white_hole_wave"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram SPATIAL_RIFT_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_spatial_rift"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram ASTRAL_STEP_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_astral_step"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram ASTRAL_GATE_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_astral_gate"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram SACRIFICIAL_CORE_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_sacrificial_core"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram TOWER_AURA_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_tower_aura"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram CHRONO_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_chrono"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram CHRONO_VORTEX_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_chrono_vortex"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram CHRONO_RIFT_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_chrono_rift"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram CHRONO_CLOCK_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_chrono_clock"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram FUSION_ORB_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_fusion_orb"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram FUSION_BEAM_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_fusion_beam"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram FORGE_EDGE_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_forge_edge"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static final ShaderProgram FORGE_IMPACT_PROGRAM = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "core/rendertype_forge_impact"),
            DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);
    private static RenderType singularityLens;
    private static RenderType eventHorizonField;
    private static RenderType stellarGlow;
    private static RenderType whiteHoleWave;
    private static RenderType spatialRift;
    private static RenderType magicCircle;
    private static RenderType astralStep;
    private static RenderType astralGate;
    private static RenderType sacrificialCore;
    private static RenderType towerAura;
    private static RenderType chrono;
    private static RenderType chronoVortex;
    private static RenderType chronoRift;
    private static RenderType chronoClock;
    private static RenderType fusionOrb;
    private static RenderType fusionBeam;
    private static RenderType forgeEdge;
    private static RenderType forgeImpact;

    private MagicalRenderTypes() {}

    public static void registerShaders(RegisterShadersEvent event) {
        event.registerShader(SINGULARITY_LENS_PROGRAM);
        event.registerShader(SPATIAL_RIFT_PROGRAM);
        event.registerShader(EVENT_HORIZON_FIELD_PROGRAM);
        event.registerShader(STELLAR_GLOW_PROGRAM);
        event.registerShader(WHITE_HOLE_WAVE_PROGRAM);
        event.registerShader(ASTRAL_STEP_PROGRAM);
        event.registerShader(ASTRAL_GATE_PROGRAM);
        event.registerShader(SACRIFICIAL_CORE_PROGRAM);
        event.registerShader(TOWER_AURA_PROGRAM);
        event.registerShader(CHRONO_PROGRAM);
        event.registerShader(CHRONO_VORTEX_PROGRAM);
        event.registerShader(CHRONO_RIFT_PROGRAM);
        event.registerShader(CHRONO_CLOCK_PROGRAM);
        event.registerShader(FUSION_ORB_PROGRAM);
        event.registerShader(FUSION_BEAM_PROGRAM);
        event.registerShader(FORGE_EDGE_PROGRAM);
        event.registerShader(FORGE_IMPACT_PROGRAM);
    }

    public static RenderType singularityLens() {
        if (singularityLens == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(SINGULARITY_LENS_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            singularityLens = RenderType.create(
                    "magical_singularity_lens",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    state);
        }
        return singularityLens;
    }

    public static RenderType spatialRift() {
        if (spatialRift == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(SPATIAL_RIFT_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            spatialRift = RenderType.create(
                    "magical_spatial_rift",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    state);
        }
        return spatialRift;
    }

    public static RenderType magicCircle() {
        if (magicCircle == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            magicCircle = RenderType.create(
                    "magical_magic_circle",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    3072,
                    false,
                    true,
                    state);
        }
        return magicCircle;
    }

    public static RenderType astralStep() {
        if (astralStep == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ASTRAL_STEP_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            astralStep = RenderType.create(
                    "magical_astral_step",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    4096,
                    false,
                    true,
                    state);
        }
        return astralStep;
    }

    public static RenderType astralGate() {
        if (astralGate == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(ASTRAL_GATE_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            astralGate = RenderType.create(
                    "magical_astral_gate",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    2048,
                    false,
                    true,
                    state);
        }
        return astralGate;
    }

    public static RenderType sacrificialCore() {
        if (sacrificialCore == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(SACRIFICIAL_CORE_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            sacrificialCore = RenderType.create(
                    "magical_sacrificial_core",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    2048,
                    false,
                    true,
                    state);
        }
        return sacrificialCore;
    }

    public static RenderType towerAura() {
        if (towerAura == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(TOWER_AURA_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            towerAura = RenderType.create(
                    "magical_tower_aura",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    8192,
                    false,
                    true,
                    state);
        }
        return towerAura;
    }

    public static RenderType chrono() {
        if (chrono == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CHRONO_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            chrono = RenderType.create(
                    "magical_chrono",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    true,
                    state);
        }
        return chrono;
    }

    public static RenderType chronoVortex() {
        if (chronoVortex == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CHRONO_VORTEX_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            chronoVortex = RenderType.create(
                    "magical_chrono_vortex",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    true,
                    state);
        }
        return chronoVortex;
    }

    public static RenderType chronoRift() {
        if (chronoRift == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CHRONO_RIFT_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            chronoRift = RenderType.create(
                    "magical_chrono_rift",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    true,
                    state);
        }
        return chronoRift;
    }

    /** Additive radial energy glow (orbs, cores, shards, sparks). */
    public static RenderType fusionOrb() {
        if (fusionOrb == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(FUSION_ORB_PROGRAM))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            fusionOrb = RenderType.create(
                    "magical_fusion_orb",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    8192,
                    false,
                    true,
                    state);
        }
        return fusionOrb;
    }

    /** Additive longitudinal energy tube (beams, trails, arcs, rings, pillars). */
    public static RenderType fusionBeam() {
        if (fusionBeam == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(FUSION_BEAM_PROGRAM))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            fusionBeam = RenderType.create(
                    "magical_fusion_beam",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    true,
                    state);
        }
        return fusionBeam;
    }

    /** Additive blade ribbon: the cutting edge of every forged strike. */
    public static RenderType forgeEdge() {
        if (forgeEdge == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(FORGE_EDGE_PROGRAM))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            forgeEdge = RenderType.create(
                    "magical_forge_edge",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    true,
                    state);
        }
        return forgeEdge;
    }

    /** Additive impact disc: cracked rim, hot core and turning spokes on a unit-disc quad. */
    public static RenderType forgeImpact() {
        if (forgeImpact == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(FORGE_IMPACT_PROGRAM))
                    .setTransparencyState(RenderStateShard.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            forgeImpact = RenderType.create(
                    "magical_forge_impact",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    true,
                    state);
        }
        return forgeImpact;
    }

    public static RenderType chronoClock() {
        if (chronoClock == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(CHRONO_CLOCK_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            chronoClock = RenderType.create(
                    "magical_chrono_clock",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    256,
                    false,
                    true,
                    state);
        }
        return chronoClock;
    }

    public static RenderType eventHorizonField() {
        if (eventHorizonField == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(EVENT_HORIZON_FIELD_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            eventHorizonField = RenderType.create(
                    "magical_event_horizon_field",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    state);
        }
        return eventHorizonField;
    }

    public static RenderType stellarGlow() {
        if (stellarGlow == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(STELLAR_GLOW_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            stellarGlow = RenderType.create(
                    "magical_stellar_glow",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    state);
        }
        return stellarGlow;
    }

    public static RenderType whiteHoleWave() {
        if (whiteHoleWave == null) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(WHITE_HOLE_WAVE_PROGRAM))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                    .setOverlayState(RenderStateShard.NO_OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false);
            whiteHoleWave = RenderType.create(
                    "magical_white_hole_wave",
                    DefaultVertexFormat.POSITION_TEX_COLOR,
                    VertexFormat.Mode.QUADS,
                    1536,
                    false,
                    true,
                    state);
        }
        return whiteHoleWave;
    }
}
