package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.hud.HudState;
import com.efkrdnz.magical.client.hud.RuleFlash;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import com.efkrdnz.magical.magic.SpaceTargetGroup;
import com.efkrdnz.magical.network.ArcanePlayerDataPayload;
import com.efkrdnz.magical.network.ChronosEnvironmentPayload;
import com.efkrdnz.magical.client.hud.ClientCooldowns;
import com.efkrdnz.magical.network.CooldownSyncPayload;
import com.efkrdnz.magical.network.CounterClearPayload;
import com.efkrdnz.magical.network.CounterPromptPayload;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.efkrdnz.magical.network.ForgeComboSyncPayload;
import com.efkrdnz.magical.network.ForgeResultPayload;
import com.efkrdnz.magical.network.PlayerMagicStatePayload;
import com.efkrdnz.magical.network.SpaceRuleAppliedPayload;
import net.minecraft.client.Minecraft;

public final class ClientPayloadHandlers {
    public static void handle(com.efkrdnz.magical.network.UnwakingSnapshotPayload payload) {
        ClientUnwakingEncounter.handle(payload);
    }
    private ClientPayloadHandlers() {}

    public static void handle(ArcanePlayerDataPayload payload) {
        ClientArcaneState.set(payload.data());
    }

    public static void handle(PlayerMagicStatePayload payload) {
        PlayerMagicState previous = ClientMagicState.get();
        ClientMagicState.set(payload.data() == null
                ? new PlayerMagicState()
                : PlayerMagicState.load(payload.data()));
        com.efkrdnz.magical.client.hud.HudAnnouncer.observe(previous, ClientMagicState.get(), ClientMagicState.receivedAtTick());
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

    public static void handle(CooldownSyncPayload payload) {
        ClientCooldowns.accept(payload);
    }

    /** A space rule landed on this player's subspace: the formula flashes. Ordinals off the wire are checked, not trusted. */
    public static void handle(SpaceRuleAppliedPayload payload) {
        SpaceRuleCategory[] categories = SpaceRuleCategory.values();
        SpaceRuleOperation[] operations = SpaceRuleOperation.values();
        SpaceTargetGroup[] targets = SpaceTargetGroup.values();
        if (payload.category() < 0 || payload.category() >= categories.length
                || payload.operation() < 0 || payload.operation() >= operations.length
                || payload.targetGroup() < 0 || payload.targetGroup() >= targets.length) {
            return;
        }
        SpaceRuleCategory category = categories[payload.category()];
        SpaceRuleOperation operation = operations[payload.operation()];
        if (operation.category() != category) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        RuleFlash.begin(category, operation, targets[payload.targetGroup()], HudState.nowTicks(), minecraft.font, HudState.options());
    }
}
