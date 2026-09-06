package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.magic.menu.MagicPyramidMenu;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public final class MagicCodexService {
    /** Sub-views the codex can be opened directly into. */
    public enum View {
        ROOT, BLACKSMITH_FORGE, SPELL_CREATOR
    }

    /**
     * A menu provider cannot carry open data, so the requested sub-view is parked here and claimed
     * by the screen on its first render. Cleared as soon as it is read.
     */
    private static final Map<UUID, View> PENDING = new ConcurrentHashMap<>();

    private MagicCodexService() {}

    public static void open(ServerPlayer player) {
        openAt(player, View.ROOT);
    }

    public static void openAt(ServerPlayer player, View view) {
        if (view == View.ROOT) {
            PENDING.remove(player.getUUID());
        } else {
            PENDING.put(player.getUUID(), view);
        }
        player.openMenu(new SimpleMenuProvider(
                (containerId, inventory, menuPlayer) -> new MagicPyramidMenu(containerId, inventory),
                Component.translatable("screen.magical.magic_codex")));
    }

    /** Read and clear the pending sub-view for a player; returns ROOT when nothing was requested. */
    public static View claimPendingView(UUID playerId) {
        View view = PENDING.remove(playerId);
        return view == null ? View.ROOT : view;
    }
}
