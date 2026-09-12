package com.efkrdnz.magical.client.tooltip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;

import org.junit.jupiter.api.Test;

/**
 * The magical tooltip is three separate things that have to line up to the pixel: a shader quad
 * this mod places, a background sheet vanilla blits over it, and a frame vanilla blits over that.
 * Nothing in the compiler connects them. They are connected by arithmetic over numbers that live in
 * two PNG files and one Java constant, and the failure when those drift is a panel visibly offset
 * from its own border - obvious in a screenshot, invisible everywhere else.
 *
 * <p>So this reads the actual files rather than restating what they are supposed to contain.
 */
class MagicalTooltipAssetsTest {

    private static final String SPRITES = "/assets/magical/textures/gui/sprites/tooltip/";
    private static final String SHADERS = "/assets/magical/shaders/core/rendertype_magical_tooltip";

    /** The sprite size both {@code .mcmeta} files declare, and the size the PNGs must actually be. */
    private static final int SPRITE_SIZE = 100;

    /**
     * The gap {@code TooltipRenderUtil} leaves between the box it blits and the text box inside it.
     * Its {@code PADDING_LEFT} is public; the nine-pixel margin beside it is not, so it is written
     * out here - and the assertion below is what notices if either one ever moves.
     */
    private static final int TOOLTIP_MARGIN = 9;

    private static final String SOURCE_PACKAGE = "src/main/java/com/efkrdnz/magical";

    /** {@code build/minecraft-junit} is two down from the project root; three is room to spare. */
    private static final int MAX_DEPTH_TO_PROJECT_ROOT = 4;

    @Test
    void theBackgroundSheetIsTheSizeAndShapeVanillaWillBlitIt() throws IOException {
        BufferedImage background = image("magical_background.png");
        assertEquals(SPRITE_SIZE, background.getWidth());
        assertEquals(SPRITE_SIZE, background.getHeight());

        int inset = fillInset(background);
        assertEquals(TOOLTIP_MARGIN + TooltipRenderUtil.PADDING_LEFT - inset, MagicalTooltipPanel.BLEED,
                "the panel no longer reaches exactly as far as the sheet vanilla draws over it");

        assertTrue(alpha(background, SPRITE_SIZE / 2, SPRITE_SIZE / 2) > 0,
                "the sheet is fully transparent, so a tooltip with no working shader would be unreadable");
    }

    @Test
    void theFrameSitsInsideTheSheetAndKeepsItsNotchedCorners() throws IOException {
        BufferedImage frame = image("magical_frame.png");
        assertEquals(SPRITE_SIZE, frame.getWidth());
        assertEquals(SPRITE_SIZE, frame.getHeight());

        int line = fillInset(image("magical_background.png")) + 1;
        assertTrue(alpha(frame, line + 1, line) > 0, "there is no top edge to the frame");
        assertTrue(alpha(frame, line, line + 1) > 0, "there is no left edge to the frame");
        assertEquals(0, alpha(frame, line, line),
                "the corner pixel is filled, which squares off a silhouette players read as a tooltip");
        assertEquals(0, alpha(frame, SPRITE_SIZE / 2, SPRITE_SIZE / 2),
                "the frame is filled in the middle, so it would paint over every line of text");
    }

    /**
     * Both sprites are nine-sliced, and a wrong border is the one mistake that looks fine on a
     * tooltip the size of the sprite and falls apart on every other size.
     */
    @Test
    void bothSpritesDeclareTheNineSliceTheyAreDrawnFor() throws IOException {
        assertNineSlice("magical_background.png.mcmeta", 9, false);
        assertNineSlice("magical_frame.png.mcmeta", 10, true);
    }

    @Test
    void theShaderHasItsThreeFilesAndTheyPointAtEachOther() throws IOException {
        JsonObject definition = JsonParser.parseString(read(SHADERS + ".json")).getAsJsonObject();
        String expected = "magical:core/rendertype_magical_tooltip";
        assertEquals(expected, definition.get("vertex").getAsString());
        assertEquals(expected, definition.get("fragment").getAsString());

        // The render type is built on POSITION_TEX_COLOR, so all three attributes must be declared
        // or the program links against a format it is not being fed.
        String vertex = read(SHADERS + ".vsh");
        assertFalse(vertex.isBlank(), "the vertex shader is empty");
        for (String attribute : new String[] {"in vec3 Position;", "in vec2 UV0;", "in vec4 Color;"}) {
            assertTrue(vertex.contains(attribute), "the vertex shader never declares " + attribute);
        }

        // GameTime is what animates the panel, and a uniform read but never declared is silently zero.
        String fragment = read(SHADERS + ".fsh");
        assertFalse(fragment.isBlank(), "the fragment shader is empty");
        assertTrue(fragment.contains("uniform float GameTime;"));
        assertTrue(definition.getAsJsonArray("uniforms").toString().contains("GameTime"),
                "the shader reads GameTime but its definition never asks for it");
    }

    /**
     * Every translation key the tooltip and the armoury tab write is in the language file.
     *
     * <p>Scanned out of the source rather than listed here, because a list would only ever say what
     * someone remembered to add to it. A missing key is not an error anywhere in the stack: the game
     * draws the key itself, so the tooltip reads {@code tooltip.magical.smith.bound} and the tab is
     * called {@code itemGroup.magical.armoury}, and it looks exactly like a bug in someone else's
     * mod. Keys built by concatenation are not literals and are skipped; those are pinned by the
     * tests that own the content they name.
     */
    @Test
    void everyLiteralKeyTheseLinesUseIsTranslated() throws IOException {
        String lang = read("/assets/magical/lang/en_us.json");
        // The trailing comma-or-paren is what keeps a concatenated key out: the prefix of
        // "forge.magical.glyph." + id is a string literal too, and it is not a key.
        Pattern literal = Pattern.compile("Component\\.translatable\\(\\s*\"([^\"]+)\"\\s*[,)]");
        List<String> missing = new ArrayList<>();
        for (Path source : sources()) {
            Matcher keys = literal.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (keys.find()) {
                if (!lang.contains('"' + keys.group(1) + '"')) {
                    missing.add(source.getFileName() + " -> " + keys.group(1));
                }
            }
        }
        assertTrue(missing.isEmpty(), "language file is missing: " + missing);
    }

    /** The tooltip package, plus the two files elsewhere that write lines into the same box. */
    private static List<Path> sources() throws IOException {
        Path root = sourceRoot();
        List<Path> sources = new ArrayList<>(List.of(
                root.resolve("item/MagicalWeaponItem.java"),
                root.resolve("registry/MagicalCreativeTabs.java")));
        try (Stream<Path> tooltip = Files.list(root.resolve("client/tooltip"))) {
            tooltip.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
        }
        return sources;
    }

    /**
     * The mod's source package, found by walking up from wherever the runner started us.
     *
     * <p>It is not the project directory: the NeoForge test plugin runs JUnit from
     * {@code build/minecraft-junit}, so that it has a game directory to write into. Walking up is
     * what makes this work from there, from the project root, and from an IDE.
     */
    private static Path sourceRoot() {
        Path at = Path.of("").toAbsolutePath();
        for (int up = 0; at != null && up < MAX_DEPTH_TO_PROJECT_ROOT; up++, at = at.getParent()) {
            Path source = at.resolve(SOURCE_PACKAGE);
            if (Files.isDirectory(source)) {
                return source;
            }
        }
        throw new AssertionError("could not find " + SOURCE_PACKAGE + " above "
                + Path.of("").toAbsolutePath());
    }

    private static void assertNineSlice(String name, int border, boolean stretchInner) throws IOException {
        JsonObject scaling = JsonParser.parseString(read(SPRITES + name)).getAsJsonObject()
                .getAsJsonObject("gui").getAsJsonObject("scaling");
        assertEquals("nine_slice", scaling.get("type").getAsString(), name);
        assertEquals(SPRITE_SIZE, scaling.get("width").getAsInt(), name);
        assertEquals(SPRITE_SIZE, scaling.get("height").getAsInt(), name);
        assertEquals(border, scaling.get("border").getAsInt(), name);
        assertEquals(stretchInner,
                scaling.has("stretch_inner") && scaling.get("stretch_inner").getAsBoolean(), name);
    }

    /** How far in from the corner the sprite's first opaque pixel is, along the diagonal. */
    private static int fillInset(BufferedImage image) {
        for (int at = 0; at < image.getWidth(); at++) {
            if (alpha(image, at, at) > 0) {
                return at;
            }
        }
        throw new AssertionError("the sprite has no opaque pixel anywhere on its diagonal");
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static BufferedImage image(String name) throws IOException {
        try (InputStream stream = MagicalTooltipAssetsTest.class.getResourceAsStream(SPRITES + name)) {
            assertNotNull(stream, "could not find " + SPRITES + name + " on the test classpath");
            return ImageIO.read(stream);
        }
    }

    private static String read(String path) throws IOException {
        try (InputStream stream = MagicalTooltipAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "could not find " + path + " on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
