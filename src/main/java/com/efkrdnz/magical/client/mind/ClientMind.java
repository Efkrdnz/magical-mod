package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.mind.Belief;
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

/** Every scene this client has been told about, and the belief rows it has been sent. */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class ClientMind {
    public static final float FIGMENT_VISIBLE = 0.5F;
    public static final float OWNER_ALPHA = 0.45F;

    public record Cell(BlockPos pos, BlockState state, int element) {}

    public record View(int id, boolean mine, List<Cell> cells) {}

    private static final Map<Integer, View> SCENES = new LinkedHashMap<>();
    private static final Map<Integer, List<BeliefSyncPayload.Entry>> ROWS = new HashMap<>();
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
    }

    public static void accept(BeliefSyncPayload payload) {
        ROWS.put(payload.scene(), List.copyOf(payload.entries()));
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

    public static boolean sees(int scene, int element) {
        return visibility(scene, element) >= FIGMENT_VISIBLE;
    }

    public static void clear() {
        SCENES.clear();
        ROWS.clear();
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
