package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.mind.Consensus;
import com.efkrdnz.magical.network.BeliefSyncPayload;
import com.efkrdnz.magical.network.IllusionEndPayload;
import com.efkrdnz.magical.network.IllusionScenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Every scene this client has been told about, and the belief rows it has been sent. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientMind {
    public static final float FIGMENT_VISIBLE = 0.5F;
    public static final float OWNER_ALPHA = 0.45F;

    public record Cell(BlockPos pos, BlockState state, int element) {}

    public record View(int id, boolean mine, List<Cell> cells) {}

    /** How one imagined cell is drawn: its block at {@code alpha} (0 draws none), the owner's lilac edge, and whether the hardening rim may show. */
    public record CellLook(float alpha, boolean edge, boolean rim) {}

    private static final CellLook REAL = new CellLook(0.0F, false, true);
    private static final CellLook OWNED = new CellLook(OWNER_ALPHA, true, false);

    private static final Map<Integer, View> SCENES = new LinkedHashMap<>();
    private static final Map<Integer, List<BeliefSyncPayload.Entry>> ROWS = new HashMap<>();
    private static final Map<Integer, Set<Integer>> MANIFESTED = new HashMap<>();
    /** When each (scene, element) became real, in client game time; drives the hardening rim. */
    private static final Map<Long, Long> HARDENED = new HashMap<>();
    /** A sync taken with no level to read the time from. */
    static final long NO_CLOCK = Long.MIN_VALUE;
    private static ClientLevel lastLevel;

    private ClientMind() {}

    public static void accept(IllusionScenePayload payload) {
        List<Cell> cells = new ArrayList<>();
        for (IllusionScenePayload.Cell cell : payload.cells()) {
            cells.add(new Cell(cell.pos(), Block.stateById(cell.state()), cell.element()));
        }
        SCENES.put(payload.scene(), new View(payload.scene(), payload.mine(), List.copyOf(cells)));
    }

    public static void accept(IllusionEndPayload payload) {
        SCENES.remove(payload.scene());
        ROWS.remove(payload.scene());
        MANIFESTED.remove(payload.scene());
        HARDENED.keySet().removeIf(k -> (int) (k >> 32) == payload.scene());
    }

    public static void accept(BeliefSyncPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        record(payload, level == null ? NO_CLOCK : level.getGameTime());
    }

    /**
     * Takes a sync at client game time {@code now}. An element real now and not at the last sync has just
     * hardened. The scene's first sync stamps nothing: what is already real then hardened before this client
     * was watching, and a lie that set long ago must not replay its rim for a newcomer. With no clock
     * ({@link #NO_CLOCK}) nothing is stamped either.
     */
    static void record(BeliefSyncPayload payload, long now) {
        ROWS.put(payload.scene(), List.copyOf(payload.entries()));
        Set<Integer> was = MANIFESTED.get(payload.scene());
        Set<Integer> real = Set.copyOf(payload.manifested());
        if (was != null && now != NO_CLOCK) {
            for (int element : real) {
                if (!was.contains(element)) {
                    HARDENED.put(key(payload.scene(), element), now);
                }
            }
        }
        MANIFESTED.put(payload.scene(), real);
    }

    static long key(int scene, int element) {
        return ((long) scene << 32) | (element & 0xFFFFFFFFL);
    }

    public static boolean manifested(int scene, int element) {
        return MANIFESTED.getOrDefault(scene, Set.of()).contains(element);
    }

    /** How much of the lilac rim is left on an element that has just become real, 1 to 0. */
    public static float hardening(int scene, int element, float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0.0F : hardeningAt(scene, element, partial, level.getGameTime());
    }

    static float hardeningAt(int scene, int element, float partial, long now) {
        Long at = HARDENED.get(key(scene, element));
        if (at == null || !manifested(scene, element)) {
            return 0.0F;
        }
        float age = (now - at) + partial;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - age / Consensus.HARDEN_TICKS));
    }

    public static Collection<View> scenes() {
        return SCENES.values();
    }

    public static boolean mine(int scene) {
        View view = SCENES.get(scene);
        return view != null && view.mine();
    }

    public static List<BeliefSyncPayload.Entry> rows(int scene) {
        return ROWS.getOrDefault(scene, List.of());
    }

    /** A viewer's belief in an element as this client was told it; -1 when they have seen through it. */
    public static float belief(int scene, int viewer, int element) {
        for (BeliefSyncPayload.Entry entry : rows(scene)) {
            if (entry.viewer() == viewer && entry.element() == element) {
                return entry.shattered() ? -1.0F : entry.value();
            }
        }
        return 0.0F;
    }

    /** How solid this element looks to this client, 0 (not there) to 1. */
    public static float visibility(int scene, int element) {
        View view = SCENES.get(scene);
        Minecraft minecraft = Minecraft.getInstance();
        if (view == null || minecraft.player == null) {
            return 0.0F;
        }
        if (view.mine()) {
            return 1.0F;
        }
        float belief = belief(scene, minecraft.player.getId(), element);
        return belief <= 0.0F ? 0.0F : Math.min(1.0F, belief / Belief.CONVINCED);
    }

    /**
     * How a cell is drawn. It is real here only when its element is real <em>and</em> the world holds its
     * block at the cell: then the world draws it and all that is left of the lie is the hardening rim. A
     * cell of a real element that could not be placed is still only an illusion - at the owner's 45% with
     * the lilac edge, or at this viewer's own belief ({@code believed}, see {@link #visibility}) - so no
     * doubter ever sees a full-strength ghost.
     */
    public static CellLook look(boolean realElement, boolean worldHolds, boolean mine, float believed) {
        if (realElement && worldHolds) {
            return REAL;
        }
        return mine ? OWNED : new CellLook(believed, false, false);
    }

    public static boolean sees(int scene, int element) {
        return visibility(scene, element) >= FIGMENT_VISIBLE;
    }

    public static void clear() {
        SCENES.clear();
        ROWS.clear();
        MANIFESTED.clear();
        HARDENED.clear();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != lastLevel) {
            clear();
            FigmentDummies.clear();
            lastLevel = level;
        }
    }
}
