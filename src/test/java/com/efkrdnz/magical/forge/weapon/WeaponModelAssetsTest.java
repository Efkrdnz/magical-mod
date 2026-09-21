package com.efkrdnz.magical.forge.weapon;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * What a weapon model has to satisfy to survive contact with vanilla's model loader.
 *
 * <p>{@code MagicalWeaponsTest.everyWeaponHasItsThreeAssetFiles} already asks whether the three
 * files exist. That was enough while every weapon was one flat quad wearing a 16x16 icon. It stops
 * being enough the moment a real Blockbench export lands in here, because every way such an export
 * goes wrong is silent: the game logs a line nobody reads and draws a black-and-magenta sword, or
 * draws the right shape wearing two pixels of the wrong texture.
 *
 * <p>Three of those ways are checked here.
 *
 * <p><b>The namespace.</b> A model exported from a standalone resource pack carries that pack's
 * namespace on every texture reference. Copy the file into this mod and the reference still points
 * at a namespace this mod does not ship, which resolves to the missing texture and nothing fails.
 *
 * <p><b>The UV space.</b> {@code texture_size} is a Blockbench field. Vanilla does not read it: UVs
 * are always in 0..16 regardless of how many pixels the texture has. An export that leaves its UVs
 * in texture pixels therefore samples a tiny corner of the sheet across the whole model, and the
 * parser accepts it happily because any float is a valid UV.
 *
 * <p><b>The geometry bounds.</b> Vanilla rejects an element outside -16..32, and a rotation that is
 * not 0, +/-22.5 or +/-45 on a single axis. That rejection is a load-time error rather than a test
 * failure, so the weapon simply has no model in the finished game.
 */
class WeaponModelAssetsTest {

    /** What vanilla allows an element corner to be, in model units. */
    private static final float MIN_COORD = -16.0F;
    private static final float MAX_COORD = 32.0F;

    /** Vanilla reads UVs in this space and ignores {@code texture_size} entirely. */
    private static final float MAX_UV = 16.0F;

    /** The only element rotations vanilla accepts. */
    private static final Set<Float> LEGAL_ANGLES = Set.of(-45.0F, -22.5F, 0.0F, 22.5F, 45.0F);

    private static final Set<String> LEGAL_AXES = Set.of("x", "y", "z");

    @Test
    void everyTextureAWeaponModelNamesIsOneThisModShips() {
        List<String> broken = new ArrayList<>();
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            JsonObject model = read(weapon.path());
            JsonObject textures = model.getAsJsonObject("textures");
            if (textures == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : textures.entrySet()) {
                String reference = entry.getValue().getAsString();
                if (reference.startsWith("#")) {
                    continue;
                }
                if (!reference.startsWith("magical:")) {
                    broken.add(weapon.path() + " -> " + entry.getKey() + " = " + reference
                            + " (a namespace this mod does not ship, so it draws the missing texture)");
                    continue;
                }
                String path = "/assets/magical/textures/" + reference.substring("magical:".length()) + ".png";
                if (WeaponModelAssetsTest.class.getResource(path) == null) {
                    broken.add(weapon.path() + " -> " + entry.getKey() + " = " + reference
                            + " (no file at " + path + ")");
                }
            }
        }
        assertTrue(broken.isEmpty(), "weapon models name textures that will not resolve: " + broken);
    }

    @Test
    void everyUvIsInTheSpaceVanillaReads() {
        List<String> broken = new ArrayList<>();
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            JsonArray elements = read(weapon.path()).getAsJsonArray("elements");
            if (elements == null) {
                continue;
            }
            for (int i = 0; i < elements.size(); i++) {
                JsonObject faces = elements.get(i).getAsJsonObject().getAsJsonObject("faces");
                if (faces == null) {
                    continue;
                }
                for (Map.Entry<String, JsonElement> face : faces.entrySet()) {
                    JsonArray uv = face.getValue().getAsJsonObject().getAsJsonArray("uv");
                    if (uv == null) {
                        continue;
                    }
                    for (int c = 0; c < uv.size(); c++) {
                        float value = uv.get(c).getAsFloat();
                        if (value < 0.0F || value > MAX_UV) {
                            broken.add(weapon.path() + " element " + i + " " + face.getKey()
                                    + " uv " + value + " is outside 0.." + MAX_UV
                                    + "; vanilla ignores texture_size, so UVs must already be normalised");
                        }
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), "weapon models carry UVs vanilla will misread: " + broken);
    }

    @Test
    void everyElementIsInsideTheBoxVanillaAllows() {
        List<String> broken = new ArrayList<>();
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            JsonArray elements = read(weapon.path()).getAsJsonArray("elements");
            if (elements == null) {
                continue;
            }
            for (int i = 0; i < elements.size(); i++) {
                JsonObject element = elements.get(i).getAsJsonObject();
                for (String corner : List.of("from", "to")) {
                    JsonArray point = element.getAsJsonArray(corner);
                    for (int c = 0; c < point.size(); c++) {
                        float value = point.get(c).getAsFloat();
                        if (value < MIN_COORD || value > MAX_COORD) {
                            broken.add(weapon.path() + " element " + i + " " + corner + " " + value
                                    + " is outside " + MIN_COORD + ".." + MAX_COORD);
                        }
                    }
                }
            }
        }
        assertTrue(broken.isEmpty(), "weapon models reach outside the box vanilla loads: " + broken);
    }

    @Test
    void everyRotationIsOneVanillaAccepts() {
        List<String> broken = new ArrayList<>();
        for (WeaponDefinition weapon : MagicalWeapons.all()) {
            JsonArray elements = read(weapon.path()).getAsJsonArray("elements");
            if (elements == null) {
                continue;
            }
            for (int i = 0; i < elements.size(); i++) {
                JsonObject rotation = elements.get(i).getAsJsonObject().getAsJsonObject("rotation");
                if (rotation == null) {
                    continue;
                }
                float angle = rotation.get("angle").getAsFloat();
                String axis = rotation.get("axis").getAsString();
                if (!LEGAL_ANGLES.contains(angle)) {
                    broken.add(weapon.path() + " element " + i + " turns " + angle
                            + " degrees; vanilla allows only 0, +/-22.5 and +/-45");
                }
                if (!LEGAL_AXES.contains(axis)) {
                    broken.add(weapon.path() + " element " + i + " turns about " + axis);
                }
            }
        }
        assertTrue(broken.isEmpty(), "weapon models rotate in ways vanilla refuses: " + broken);
    }

    private static JsonObject read(String path) {
        String resource = "/assets/magical/models/item/" + path + ".json";
        try (InputStream in = WeaponModelAssetsTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " is not shipped");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + resource, e);
        }
    }
}
