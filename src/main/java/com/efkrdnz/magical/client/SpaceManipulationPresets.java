package com.efkrdnz.magical.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.Minecraft;

public final class SpaceManipulationPresets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SLOT_COUNT = 9;
    private static final int EMPTY = -1;
    private static final int[] PRESETS = new int[SLOT_COUNT];
    private static boolean loaded;

    static {
        Arrays.fill(PRESETS, EMPTY);
    }

    private SpaceManipulationPresets() {
    }

    public static int slotCount() {
        return SLOT_COUNT;
    }

    public static boolean has(int slot) {
        ensureLoaded();
        return validSlot(slot) && PRESETS[slot] != EMPTY;
    }

    public static int get(int slot) {
        ensureLoaded();
        return validSlot(slot) ? PRESETS[slot] : EMPTY;
    }

    public static void set(int slot, int encodedRule) {
        ensureLoaded();
        if (!validSlot(slot)) {
            return;
        }
        PRESETS[slot] = encodedRule;
        save();
    }

    private static boolean validSlot(int slot) {
        return slot >= 0 && slot < SLOT_COUNT;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file();
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonArray presets = root.getAsJsonArray("presets");
            if (presets == null) {
                return;
            }
            for (int i = 0; i < Math.min(SLOT_COUNT, presets.size()); i++) {
                JsonElement element = presets.get(i);
                PRESETS[i] = element == null || element.isJsonNull() ? EMPTY : element.getAsInt();
            }
        } catch (RuntimeException | IOException ignored) {
            Arrays.fill(PRESETS, EMPTY);
        }
    }

    private static void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonArray presets = new JsonArray();
            for (int preset : PRESETS) {
                if (preset == EMPTY) {
                    presets.add((JsonElement) null);
                } else {
                    presets.add(preset);
                }
            }
            root.add("presets", presets);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("magical_space_presets.json");
    }
}
