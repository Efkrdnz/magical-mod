package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * A dream is real while you are in it and worth nothing when you leave: no item and no experience can
 * appear in it (what a dreamer throws comes back to their hand), nothing in it is broken or placed by
 * hand, nothing carried is used, a block answers only an empty hand and only if it is a door, trapdoor,
 * fence gate, button or lever, no creature can be milked,
 * sheared or traded with, and no explosion or grief takes a block. What a dreamer carries in they
 * carry out, and nothing else.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class DreamGuard {
    private DreamGuard() {}

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ItemEntity || entity instanceof ExperienceOrb)
                || !DreamService.isDream(event.getLevel(), entity.getX(), entity.getZ())) {
            return;
        }
        if (entity instanceof ItemEntity item && item.getOwner() instanceof ServerPlayer thrower) {
            // What the hand cannot take back stays in the dream as a real item, never deleted.
            thrower.getInventory().add(item.getItem());
            if (!item.getItem().isEmpty()) {
                return;
            }
        }
        event.setCanceled(true);
    }

    /** No experience of any kind is earned in a dream; what a dreamer brings in they keep. */
    @SubscribeEvent
    public static void onExperience(PlayerXpEvent.XpChange event) {
        if (event.getAmount() > 0 && DreamService.isDream(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLevels(PlayerXpEvent.LevelChange event) {
        if (event.getLevels() > 0 && DreamService.isDream(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** The only blocks a dream answers to an empty hand: the ones that only open, close or switch. */
    private static boolean answersByHand(BlockState state) {
        return state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.BUTTONS) || state.getBlock() instanceof LeverBlock;
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof Level level && DreamService.isDream(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof Level level && DreamService.isDream(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (DreamService.isDream(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!DreamService.isDream(event.getLevel(), event.getPos())) {
            return;
        }
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            DreamService.touched(serverPlayer, event.getPos());
        }
        event.setUseItem(TriState.FALSE);
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()
                || !answersByHand(event.getLevel().getBlockState(event.getPos()))) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && DreamService.isDream(event.getLevel(), event.getPos())) {
            DreamService.touched(player, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (DreamService.isDream(event.getTarget())) {
            if (event.getEntity() instanceof ServerPlayer player) {
                DreamService.touched(player, event.getTarget());
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        if (DreamService.isDream(event.getTarget())) {
            if (event.getEntity() instanceof ServerPlayer player) {
                DreamService.touched(player, event.getTarget());
            }
            event.setCanceled(true);
        }
    }

    /** A blow in a dream is a real blow; one on the Flaw also ends the dream. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && DreamService.isDream(event.getTarget())) {
            DreamService.touched(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> DreamService.isDream(level, pos));
    }

    @SubscribeEvent
    public static void onGrief(EntityMobGriefingEvent event) {
        if (DreamService.isDream(event.getEntity())) {
            event.setCanGrief(false);
        }
    }

    /** A wielder building their own dream is never hunted by what they dreamed. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player && DreamService.dreamingOwn(player.getUUID())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getTags().contains(DreamService.DREAM_TAG) && entity.level() instanceof ServerLevel level
                && DreamService.isDream(entity)) {
            DreamService.figmentGone(level, entity);
        }
    }
}
