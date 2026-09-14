package com.efkrdnz.magical.client.model.eldritch;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.model.geo.GeoFormatException;
import com.efkrdnz.magical.client.model.geo.GeoModel;
import com.efkrdnz.magical.client.model.geo.GeoModelBaker;
import com.efkrdnz.magical.client.model.geo.GeoModelParser;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The creatures, by name, rebuilt on every resource reload so an export dropped into the folder
 * shows up at the next F3+T.
 *
 * <p>{@code assets/magical/models/entity/eldritch/<name>.geo.json} is the model, {@code
 * textures/entity/eldritch/<name>.png} its texture, {@code <name>_glow.png} the optional layer
 * drawn full bright. A file the parser refuses is logged with the reason and left out; a
 * construct whose creature is missing draws its FX and nothing else, and says so once.
 */
public final class EldritchModels extends SimplePreparableReloadListener<Map<String, GeoModel>> {
    public static final ResourceLocation KEY = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "eldritch_models");
    private static final String FOLDER = "models/entity/eldritch";
    private static final String SUFFIX = ".geo.json";

    public record Entry(GeoModelBaker.Baked baked, ResourceLocation texture, ResourceLocation glow) {
    }

    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static final Set<String> MISSING_LOGGED = new HashSet<>();

    @Override
    protected Map<String, GeoModel> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, GeoModel> models = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> found : resourceManager.listResources(FOLDER,
                location -> MagicalMod.MODID.equals(location.getNamespace()) && location.getPath().endsWith(SUFFIX)).entrySet()) {
            String path = found.getKey().getPath();
            String name = path.substring(FOLDER.length() + 1, path.length() - SUFFIX.length());
            try (Reader reader = found.getValue().openAsReader()) {
                models.put(name, GeoModelParser.parse(readAll(reader)));
            } catch (IOException | RuntimeException e) {
                MagicalMod.LOGGER.warn("Eldritch model {} skipped: {}", found.getKey(), e.getMessage());
            }
        }
        return models;
    }

    @Override
    protected void apply(Map<String, GeoModel> models, ResourceManager resourceManager, ProfilerFiller profiler) {
        ENTRIES.clear();
        MISSING_LOGGED.clear();
        for (Map.Entry<String, GeoModel> model : models.entrySet()) {
            String name = model.getKey();
            ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/entity/eldritch/" + name + ".png");
            ResourceLocation glow = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/entity/eldritch/" + name + "_glow.png");
            try {
                ENTRIES.put(name, new Entry(GeoModelBaker.bake(model.getValue()), texture,
                        resourceManager.getResource(glow).isPresent() ? glow : null));
            } catch (GeoFormatException e) {
                MagicalMod.LOGGER.warn("Eldritch model {} skipped: {}", name, e.getMessage());
            }
        }
        MagicalMod.LOGGER.info("Eldritch models: {} loaded ({})", ENTRIES.size(), ENTRIES.keySet());
    }

    /** The creature by name, or null once the absence has been logged. */
    public static Entry get(String name) {
        Entry entry = ENTRIES.get(name);
        if (entry == null && MISSING_LOGGED.add(name)) {
            MagicalMod.LOGGER.warn("No eldritch model named {}: expected assets/magical/{}/{}{}", name, FOLDER, name, SUFFIX);
        }
        return entry;
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
