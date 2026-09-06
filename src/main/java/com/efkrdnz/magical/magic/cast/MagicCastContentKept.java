package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.magic.GreedVaultService;
import com.efkrdnz.magical.magic.MagicCastingService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.SpaceAuthorityService;
import com.efkrdnz.magical.magic.SpacePocketService;
import com.efkrdnz.magical.magic.SpaceWalkerService;
import net.minecraft.network.chat.Component;

/** Handlers for the KEPT skills: thin wrappers over their existing services. */
public final class MagicCastContentKept {
    private MagicCastContentKept() {}

    public static void register() {
        SkillCastRegistry.register(MagicContent.VAULT_OF_AVARICE, SkillCastRegistry.selfManaged(ctx -> {
            if (!ctx.state().isPassiveEnabled(MagicPassiveContent.SIN_GREED.id())) {
                ctx.player().displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
                return;
            }
            if (ctx.sneak() || ctx.player().isShiftKeyDown()) {
                GreedVaultService.depositAll(ctx.player());
            } else {
                GreedVaultService.open(ctx.player());
            }
        }));
        SkillCastRegistry.register(MagicContent.CREATE_SUBSPACE, SkillCastRegistry.selfManaged(ctx ->
                SpaceAuthorityService.createOrDismissSubspace(ctx.player(), ctx.state(), 0, ctx.player().isShiftKeyDown())));
        SkillCastRegistry.register(MagicContent.MANIPULATE_SPACE, SkillCastRegistry.holdHint("message.magical.space_hold_to_manipulate"));
        SkillCastRegistry.register(MagicContent.POCKET_DIMENSION, SkillCastRegistry.selfManaged(ctx -> SpacePocketService.cast(ctx.player(), ctx.state())));
        SkillCastRegistry.register(MagicContent.SPACE_WALKER, SkillCastRegistry.selfManaged(ctx -> {
            if (ctx.player().isShiftKeyDown()) {
                SpaceWalkerService.open(ctx.player());
            } else {
                SpaceWalkerService.blink(ctx.player());
            }
        }));
        SkillCastRegistry.register(MagicContent.GABRIEL, SkillCastRegistry.holdHint("message.magical.sovereign_aegis_hold"));
        SkillCastRegistry.register(MagicContent.BLACK_FLAMES, SkillCastRegistry.holdHint("message.magical.black_flames_hold"));
        SkillCastRegistry.register(MagicContent.SPATIAL_ARSENAL, SkillCastRegistry.holdHint("message.magical.spatial_arsenal_hold"));
        SkillCastRegistry.register(MagicContent.SOUL_VOW, SkillCastRegistry.holdHint("message.magical.soul_vow_hold"));
        // Circle Arsenal is hold/release like Gabriel and Black Flames: the press only hints.
        SkillCastRegistry.register(MagicContent.CIRCLE_ARSENAL, new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ctx.player().displayClientMessage(Component.translatable("message.magical.circle_arsenal_hold"), true);
                return CastResult.HANDLED;
            }

            @Override
            public boolean holdGated() {
                return true;
            }

            @Override
            public String holdHintKey() {
                return "message.magical.circle_arsenal_hold";
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.volley_force",
                        "screen.magical.tuning.firing_cadence", "screen.magical.tuning.domain_radius", null);
            }
        });
        SkillCastRegistry.register(MagicContent.DIVINE_DIVIDER, new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                return MagicCastingService.legacyDivineDivider(ctx.player(), ctx.stats()) ? CastResult.SUCCESS : CastResult.FAILED;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(0.0F, 5.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.DEFAULT.labels("screen.magical.tuning.cut_force",
                        "screen.magical.tuning.wind_drive", "screen.magical.tuning.blade_width", null);
            }
        });
        SkillCastRegistry.register(MagicContent.ABYSSAL_DISCHARGE, new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                return MagicCastingService.legacyAbyssalDischarge(ctx.player(), ctx.stats()) ? CastResult.SUCCESS : CastResult.FAILED;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(0.0F, 6.0F);
            }
        });
    }
}
