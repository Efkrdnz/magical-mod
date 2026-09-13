package com.efkrdnz.magical.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The sigil shader and the Java that drives it are held together by numbers in two files and a
 * vertex format neither can see. This reads the actual files, the way {@code MagicalTooltipAssetsTest}
 * does for the tooltip panel.
 */
class HudShaderAssetsTest {

    private static final String SHADER = "/assets/magical/shaders/core/rendertype_hud_sigil";
    private static final String INCLUDES = "/assets/magical/shaders/include/";
    private static final String SOURCE_PACKAGE = "src/main/java/com/efkrdnz/magical";
    private static final int MAX_DEPTH_TO_PROJECT_ROOT = 4;

    @Test
    void theShaderHasItsThreeFilesAndTheyPointAtEachOther() throws IOException {
        JsonObject definition = JsonParser.parseString(read(SHADER + ".json")).getAsJsonObject();
        String expected = "magical:core/rendertype_hud_sigil";
        assertEquals(expected, definition.get("vertex").getAsString());
        assertEquals(expected, definition.get("fragment").getAsString());

        // The render type is POSITION_COLOR_TEX_LIGHTMAP with UV2 carrying the packed data.
        String vertex = read(SHADER + ".vsh");
        for (String attribute : new String[] {"in vec3 Position;", "in vec4 Color;", "in vec2 UV0;", "in ivec2 UV2;"}) {
            assertTrue(vertex.contains(attribute), "the vertex shader never declares " + attribute);
        }
        assertTrue(vertex.contains("decodeMagicVertex(UV2, magicA, magicB);"), "the vertex shader does not unpack MagicVertex");

        String fragment = read(SHADER + ".fsh");
        assertTrue(fragment.contains("uniform float GameTime;"));
        assertTrue(fragment.contains("uniform sampler2D Sampler0;"), "no noise sampler");
        assertTrue(fragment.contains("uniform sampler2D Sampler1;"), "no emblem atlas sampler");
        String samplers = definition.getAsJsonArray("samplers").toString();
        assertTrue(samplers.contains("Sampler0") && samplers.contains("Sampler1"), "the definition binds " + samplers);
        assertTrue(definition.getAsJsonArray("uniforms").toString().contains("GameTime"),
                "the shader reads GameTime but its definition never asks for it");
    }

    @Test
    void everyIncludeTheShaderImportsExistsAndTheSharedOnesUseNoDerivatives() throws IOException {
        Pattern imports = Pattern.compile("#moj_import <magical:([^>]+)>");
        Pattern derivative = Pattern.compile("\\b(fwidth|dFdx|dFdy)\\s*\\(");
        for (String stage : new String[] {".vsh", ".fsh"}) {
            Matcher m = imports.matcher(read(SHADER + stage));
            while (m.find()) {
                String include = read(INCLUDES + m.group(1));
                assertFalse(include.isBlank(), "empty include " + m.group(1));
                if (!m.group(1).equals("magic_frag.glsl")) {
                    assertFalse(derivative.matcher(include).find(),
                            m.group(1) + " uses a derivative; that breaks every vertex shader on Intel and AMD");
                }
            }
        }
        // The atlas helpers were lifted out of glyph_ink; both shaders must use the shared copy.
        String glyphInk = read("/assets/magical/shaders/core/rendertype_glyph_ink.fsh");
        assertTrue(glyphInk.contains("#moj_import <magical:magic_atlas.glsl>"), "glyph_ink no longer shares the atlas helpers");
        assertFalse(glyphInk.contains("float atlasInk(int"), "glyph_ink kept a private atlasInk");
    }

    @Test
    void theKindIdsInJavaAndGlslAgree() throws IOException {
        String fragment = read(SHADER + ".fsh");
        Matcher m = Pattern.compile("const int ([A-Z_]+) = (\\d+);").matcher(fragment);
        Map<String, Integer> glsl = new HashMap<>();
        while (m.find()) {
            glsl.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        for (HudKind kind : HudKind.values()) {
            Integer id = glsl.get(kind.name());
            assertNotNull(id, "the shader has no const for " + kind);
            assertEquals(kind.id(), id.intValue(), kind + " has a different id in the shader");
        }
        assertEquals(HudKind.values().length, glsl.size(), "the shader declares kinds Java does not know: " + glsl.keySet());

        Matcher outer = Pattern.compile("const float RING_OUTER = ([0-9.]+);").matcher(fragment);
        assertTrue(outer.find(), "RING_OUTER is not declared in the shader");
        assertEquals(HudKind.RING_OUTER, Float.parseFloat(outer.group(1)), 0.0001F);

        Matcher widths = Pattern.compile("float widthClass\\(int c\\) \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(fragment);
        assertTrue(widths.find(), "widthClass is not declared in the shader");
        Matcher values = Pattern.compile("return ([0-9.]+);").matcher(widths.group(1));
        List<Float> classes = new ArrayList<>();
        while (values.find()) {
            classes.add(Float.parseFloat(values.group(1)));
        }
        assertEquals(HudKind.WIDTH_CLASSES.length, classes.size());
        for (int i = 0; i < classes.size(); i++) {
            assertEquals(HudKind.WIDTH_CLASSES[i], classes.get(i), 0.0001F, "width class " + i);
        }
    }

    /**
     * A {@code GuiGraphics.fill} in HUD code goes to the shared buffer, which is flushed before the
     * sigil's fixed buffer inside {@code drawSpecial} and after it otherwise - either way it lands
     * on the wrong side of the quads. Everything is a PLATE quad instead.
     */
    @Test
    void nothingUnderClientHudCallsGuiGraphicsFill() throws IOException {
        // Arrays.fill is not a draw call; everything else named fill on a GuiGraphics is.
        Pattern fill = Pattern.compile("(?<!Arrays)\\.(fill|fillGradient|blit|renderOutline)\\(");
        List<String> offenders = new ArrayList<>();
        for (Path source : hudSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            Matcher m = fill.matcher(text);
            if (m.find()) {
                offenders.add(source.getFileName() + " -> ." + m.group(1) + "(");
            }
        }
        assertTrue(offenders.isEmpty(), "HUD code drawing outside the batch: " + offenders);
    }

    @Test
    void everyLiteralKeyTheHudUsesIsTranslated() throws IOException {
        String lang = read("/assets/magical/lang/en_us.json");
        Pattern literal = Pattern.compile("Component\\.translatable\\(\\s*\"([^\"]+)\"\\s*[,)]");
        List<String> missing = new ArrayList<>();
        for (Path source : hudSources()) {
            Matcher keys = literal.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (keys.find()) {
                if (!lang.contains('"' + keys.group(1) + '"')) {
                    missing.add(source.getFileName() + " -> " + keys.group(1));
                }
            }
        }
        assertTrue(missing.isEmpty(), "language file is missing: " + missing);
    }

    private static List<Path> hudSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> hud = Files.list(sourceRoot().resolve("client/hud"))) {
            hud.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        }
        return sources;
    }

    private static Path sourceRoot() {
        Path at = Path.of("").toAbsolutePath();
        for (int up = 0; at != null && up < MAX_DEPTH_TO_PROJECT_ROOT; up++, at = at.getParent()) {
            Path source = at.resolve(SOURCE_PACKAGE);
            if (Files.isDirectory(source)) {
                return source;
            }
        }
        throw new AssertionError("could not find " + SOURCE_PACKAGE + " above " + Path.of("").toAbsolutePath());
    }

    private static String read(String path) throws IOException {
        try (InputStream in = HudShaderAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
