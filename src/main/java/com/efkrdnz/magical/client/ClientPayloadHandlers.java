package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.network.ArcanePlayerDataPayload;
import com.efkrdnz.magical.network.ChronosEnvironmentPayload;
import com.efkrdnz.magical.network.CounterClearPayload;
import com.efkrdnz.magical.network.CounterPromptPayload;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.efkrdnz.magical.network.ForgeComboSyncPayload;
import com.efkrdnz.magical.network.ForgeResultPayload;
import com.efkrdnz.magical.network.PlayerMagicStatePayload;

public final class ClientPayloadHandlers {
    public static void handle(com.efkrdnz.magical.network.UnwakingSnapshotPayload payload) {
        ClientUnwakingEncounter.handle(payload);
    }
    private ClientPayloadHandlers() {}

    public static void handle(ArcanePlayerDataPayload payload) {
        ClientArcaneState.set(payload.data());
    }

    public static void handle(PlayerMagicStatePayload payload) {
        ClientMagicState.set(payload.data() == null
                ? new PlayerMagicState()
                : PlayerMagicState.load(payload.data()));
    }

    public static void handle(FirstPersonEffectPayload payload) {
        FirstPersonEffects.apply(payload);
    }

    public static void handle(CounterPromptPayload payload) {
        ClientCounterPrompt.receive(payload);
    }

    public static void handle(CounterClearPayload payload) {
        ClientCounterPrompt.clear(payload);
    }

    public static void handle(ChronosEnvironmentPayload payload) {
        ChronosClientEnvironment.handle(payload);
    }

    public static void handle(com.efkrdnz.magical.network.VisualCuePayload payload) {
        com.efkrdnz.magical.client.fx.TransientVisuals.handle(payload);
    }

    public static void handle(com.efkrdnz.magical.network.StatusSyncPayload payload) {
        ClientStatusState.handle(payload);
    }

    public static void handle(ForgeResultPayload payload) {
        ClientForgeResults.accept(payload);
    }

    public static void handle(ForgeComboSyncPayload payload) {
        ClientForgeCombo.accept(payload);
    }
}
