package com.efkrdnz.magical.client.model.geo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the Bedrock entity geometry format ({@code format_version} 1.12 and later as Blockbench
 * writes it): {@code minecraft:geometry[0].description} for the identifier and texture size,
 * {@code bones[]} with {@code name, parent, pivot, rotation, mirror, cubes[]}, each cube with
 * {@code origin, size, uv, inflate, mirror, rotation, pivot}. Box UV only: a per-face {@code uv}
 * object is refused with the bone named, because the fix is in Blockbench, not here.
 */
public final class GeoModelParser {
    private static final int DEFAULT_TEXTURE_SIZE = 64;

    private GeoModelParser() {
    }

    public static GeoModel parse(String json) {
        try {
            return parse(JsonParser.parseString(json).getAsJsonObject());
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new GeoFormatException("not a geometry file: " + e.getMessage());
        }
    }

    public static GeoModel parse(JsonObject root) {
        JsonArray geometries = root.getAsJsonArray("minecraft:geometry");
        if (geometries == null || geometries.isEmpty()) {
            throw new GeoFormatException("no minecraft:geometry entry - export a Bedrock entity geometry");
        }
        JsonObject geometry = geometries.get(0).getAsJsonObject();
        JsonObject description = geometry.has("description") ? geometry.getAsJsonObject("description") : new JsonObject();
        String identifier = description.has("identifier") ? description.get("identifier").getAsString() : "";
        int textureWidth = intOr(description, "texture_width", DEFAULT_TEXTURE_SIZE);
        int textureHeight = intOr(description, "texture_height", DEFAULT_TEXTURE_SIZE);
        List<GeoModel.Bone> bones = new ArrayList<>();
        if (geometry.has("bones")) {
            for (JsonElement element : geometry.getAsJsonArray("bones")) {
                bones.add(bone(element.getAsJsonObject()));
            }
        }
        return new GeoModel(identifier, textureWidth, textureHeight, List.copyOf(bones));
    }

    private static GeoModel.Bone bone(JsonObject json) {
        if (!json.has("name")) {
            throw new GeoFormatException("a bone without a name");
        }
        String name = json.get("name").getAsString();
        String parent = json.has("parent") ? json.get("parent").getAsString() : null;
        boolean mirror = json.has("mirror") && json.get("mirror").getAsBoolean();
        List<GeoModel.Cube> cubes = new ArrayList<>();
        if (json.has("cubes")) {
            for (JsonElement element : json.getAsJsonArray("cubes")) {
                cubes.add(cube(name, element.getAsJsonObject(), mirror));
            }
        }
        return new GeoModel.Bone(name, parent, floats(json, "pivot", 3, new float[] {0, 0, 0}),
                floats(json, "rotation", 3, new float[] {0, 0, 0}), mirror, List.copyOf(cubes));
    }

    private static GeoModel.Cube cube(String bone, JsonObject json, boolean boneMirror) {
        if (json.has("uv") && !json.get("uv").isJsonArray()) {
            throw new GeoFormatException("bone " + bone + " has a cube with per-face UV; export with Box UV");
        }
        float[] origin = floats(json, "origin", 3, null);
        float[] size = floats(json, "size", 3, null);
        if (origin == null || size == null) {
            throw new GeoFormatException("bone " + bone + " has a cube without an origin or a size");
        }
        float[] rotation = floats(json, "rotation", 3, null);
        float[] pivot = floats(json, "pivot", 3, null);
        if (rotation != null && pivot == null) {
            // Bedrock rotates a pivot-less cube about its centre.
            pivot = new float[] {origin[0] + size[0] / 2.0F, origin[1] + size[1] / 2.0F, origin[2] + size[2] / 2.0F};
        }
        boolean mirror = json.has("mirror") ? json.get("mirror").getAsBoolean() : boneMirror;
        float inflate = json.has("inflate") ? json.get("inflate").getAsFloat() : 0.0F;
        return new GeoModel.Cube(origin, size, floats(json, "uv", 2, new float[] {0, 0}), inflate, mirror, rotation, pivot);
    }

    private static float[] floats(JsonObject json, String key, int count, float[] fallback) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return fallback;
        }
        JsonArray array = json.getAsJsonArray(key);
        if (array.size() != count) {
            throw new GeoFormatException(key + " needs " + count + " numbers, has " + array.size());
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = array.get(i).getAsFloat();
        }
        return out;
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
}
