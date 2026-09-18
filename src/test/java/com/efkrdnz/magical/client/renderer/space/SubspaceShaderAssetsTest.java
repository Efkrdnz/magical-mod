package com.efkrdnz.magical.client.renderer.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The boundary shader and the Java that feeds it share a set of numbers neither can see the other
 * declare. This reads the actual files.
 */
class SubspaceShaderAssetsTest {

    private static final String SHADER = "/assets/magical/shaders/core/rendertype_subspace";
    private static final String RETIRED = "/assets/magical/shaders/core/rendertype_subspace_shell";
    private static final String SOURCE_PACKAGE = "src/main/java/com/efkrdnz/magical";
    private static final int MAX_DEPTH_TO_PROJECT_ROOT = 4;

    @Test
    @DisplayName("the shader has its three files and they point at each other")
    void theShaderIsWiredUp() throws IOException {
        JsonObject definition = JsonParser.parseString(read(SHADER + ".json")).getAsJsonObject();
        assertEquals("magical:core/rendertype_subspace", definition.get("vertex").getAsString());
        assertEquals("magical:core/rendertype_subspace", definition.get("fragment").getAsString());
        assertTrue(definition.getAsJsonArray("samplers").toString().contains("Sampler0"),
                "no noise sampler, so nothing dithers the wall");

        String vertex = read(SHADER + ".vsh");
        for (String attribute : new String[] {"in vec3 Position;", "in vec4 Color;", "in vec2 UV0;", "in ivec2 UV2;"}) {
            assertTrue(vertex.contains(attribute), "the vertex shader never declares " + attribute);
        }
        assertTrue(vertex.contains("decodeMagicVertex(UV2, magicA, magicB);"), "the vertex shader does not unpack MagicVertex");
    }

    /**
     * The one thing the whole vertex format cannot carry is a normal, so the shader rebuilds it out
     * of UV0 - and the mesh has to have written UV0 to match, which {@code SubspaceGeometryTest}
     * holds from the other end.
     */
    @Test
    @DisplayName("the vertex stage rebuilds the shell normal and the view direction")
    void theVertexStageRebuildsTheGeometry() throws IOException {
        String vertex = read(SHADER + ".vsh");
        assertTrue(vertex.contains("out vec3 shellNormal;"), "nothing carries the normal to the fragment stage");
        assertTrue(vertex.contains("out vec3 viewDir;"), "nothing carries the view direction");
        assertTrue(vertex.contains("UV0.x * MAGIC_TWO_PI"), "the bearing is not read off UV0");
        assertTrue(vertex.contains("UV0.y * 2.0 - 1.0"), "the height is not read off UV0");
        // Position arrives camera-relative, so the eye is at the origin and no camera uniform is
        // needed - there is none available to a core shader anyway.
        assertTrue(vertex.contains("viewDir = -Position;"), "the view direction is not the camera-relative position");
    }

    /**
     * A subspace at rest is still. The old dome moved constantly - the shell pulsed, the bands
     * counted up and down, the rings spun, the glints wandered - and constant motion with no
     * meaning behind it is exactly what makes a thing look generated rather than made. Every
     * animation the boundary has left is a one-shot the CPU drives through {@code phase01}, so the
     * shader has no clock at all and cannot grow one without this failing.
     */
    @Test
    @DisplayName("the shader has no clock, because a domain at rest does not move")
    void nothingAnimatesItself() throws IOException {
        assertFalse(read(SHADER + ".fsh").contains("GameTime"), "the fragment shader found a clock");
        assertFalse(read(SHADER + ".vsh").contains("GameTime"), "the vertex shader found a clock");
        assertFalse(read(SHADER + ".json").contains("GameTime"), "the definition hands over a clock");
    }

    @Test
    @DisplayName("every element the renderer can draw has an id the shader agrees with")
    void theKindIdsAgree() throws IOException {
        Map<String, Integer> glsl = new HashMap<>();
        Matcher m = Pattern.compile("const int KIND_([A-Z_]+) = (\\d+);").matcher(read(SHADER + ".fsh"));
        while (m.find()) {
            glsl.put(m.group(1), Integer.parseInt(m.group(2)));
        }
        Map<String, Integer> java = new HashMap<>();
        for (Field field : SubspaceKind.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == int.class && !field.getName().equals("COUNT")) {
                java.put(field.getName(), constant(field));
            }
        }
        assertEquals(java, glsl, "the shader and SubspaceKind disagree about what can be drawn");
        assertEquals(SubspaceKind.COUNT, java.size(), "SubspaceKind.COUNT does not count its own kinds");
    }

    /**
     * The optics are computed twice - once in doubles where a test can reach them, once in floats
     * on the card where nothing can. Both copies have to be the same curve or the numbers
     * {@code SubspaceOpticsTest} pins are numbers about nothing.
     */
    @Test
    @DisplayName("every optical constant is the same on both sides")
    void theOpticsAgree() throws IOException {
        Map<String, Double> glsl = new HashMap<>();
        Matcher m = Pattern.compile("const float ([A-Z_]+) = ([0-9.eE+-]+);").matcher(read(SHADER + ".fsh"));
        while (m.find()) {
            glsl.put(m.group(1), Double.parseDouble(m.group(2)));
        }
        List<String> missing = new ArrayList<>();
        for (Field field : SubspaceOptics.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != double.class) {
                continue;
            }
            Double there = glsl.get(field.getName());
            if (there == null) {
                missing.add(field.getName());
                continue;
            }
            assertEquals(constantDouble(field), there, Math.abs(constantDouble(field)) * 1.0E-6D + 1.0E-9D,
                    field.getName() + " differs between Java and the shader");
        }
        assertTrue(missing.isEmpty(), "the shader never declares: " + missing);
    }

    @Test
    @DisplayName("the two colours the shader owns are the two colours Java named")
    void thePaletteAgrees() throws IOException {
        String fragment = read(SHADER + ".fsh");
        assertVec3(fragment, "INK", SubspaceOptics.INK);
        assertVec3(fragment, "BURNISH", SubspaceOptics.BURNISH);
    }

    /**
     * {@code additiveOut} returns {@code vec4(rgb * glow * opacity, glow * opacity)}, which is
     * written for a ONE/ONE blend where the alpha channel is thrown away. On the premultiplied
     * blend this render type uses, that alpha is the amount of the background it removes, so a
     * bright fragment would cut a hole in the world instead of lighting it.
     */
    @Test
    @DisplayName("the output is premultiplied by hand, not through the additive helper")
    void theOutputIsPremultiplied() throws IOException {
        String fragment = read(SHADER + ".fsh");
        assertFalse(fragment.contains("additiveOut("), "additiveOut would punch a hole under a premultiplied blend");
        assertFalse(fragment.contains("straightOut("), "straightOut is not premultiplied");
        assertTrue(fragment.contains("* ColorModulator"), "the shader ignores the render type's modulator");
        Matcher writes = Pattern.compile("fragColor = vec4\\(([^;]*), alpha\\)").matcher(fragment);
        assertTrue(writes.find(), "the shader never writes a premultiplied colour beside its own alpha");
        assertTrue(writes.group(1).contains("* alpha"), "the colour was not multiplied by its own alpha: " + writes.group(1));
    }

    @Test
    @DisplayName("the billboard the dome used to be is gone from the resource pack")
    void theOldShellIsDeleted() {
        for (String stage : new String[] {".json", ".vsh", ".fsh"}) {
            assertNull(RETIRED + stage);
        }
    }

    /**
     * The old renderer drew six unrelated things on one vanilla render type and called it a dome:
     * two shell bands, a hundred and twenty sphere rings, orbit arcs, glyph ticks and wandering
     * glints, all additive, all at a fixed tessellation, all distance-sorted every frame. None of
     * that survives, and naming it here is how it stays gone.
     */
    @Test
    @DisplayName("nothing of the old dome is left in the renderer")
    void theOldDomeIsGone() throws IOException {
        String renderer = Files.readString(sourceRoot().resolve("client/renderer/SpaceSubspaceRenderer.java"), StandardCharsets.UTF_8);
        // Named as code rather than as words: the class comment says what used to be here, and a
        // test that trips over its own explanation teaches the next reader to delete the comment.
        for (String ghost : new String[] {
                "drawShellBands", "drawSphereRings", "drawOrbitArcs", "drawGlyphTicks", "drawGlints",
                "import net.minecraft.client.renderer.RenderType;", "getBuffer(RenderType."}) {
            assertFalse(renderer.contains(ghost), "the renderer still has " + ghost);
        }
    }

    private static void assertVec3(String fragment, String name, int rgb) {
        Matcher m = Pattern.compile("const vec3 " + name + " = vec3\\(([0-9.]+), ([0-9.]+), ([0-9.]+)\\) / 255.0;").matcher(fragment);
        assertTrue(m.find(), "the shader never declares " + name);
        for (int channel = 0; channel < 3; channel++) {
            assertEquals(SubspaceOptics.component(rgb, channel) * 255.0D, Double.parseDouble(m.group(channel + 1)), 1.0E-6D,
                    name + " channel " + channel);
        }
    }

    private static int constant(Field field) {
        try {
            field.setAccessible(true);
            return field.getInt(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(field.getName(), e);
        }
    }

    private static double constantDouble(Field field) {
        try {
            field.setAccessible(true);
            return field.getDouble(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(field.getName(), e);
        }
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

    private static void assertNull(String path) {
        try (InputStream in = SubspaceShaderAssetsTest.class.getResourceAsStream(path)) {
            org.junit.jupiter.api.Assertions.assertNull(in, "the retired shader is still shipped: " + path);
        } catch (IOException e) {
            throw new AssertionError(path, e);
        }
    }

    private static String read(String path) throws IOException {
        try (InputStream in = SubspaceShaderAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
