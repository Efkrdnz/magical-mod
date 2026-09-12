package com.efkrdnz.magical.magic.menu;

import com.efkrdnz.magical.entity.TrainingDummyEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.registry.MagicalMenus;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * The training dummy's settings, bound to one dummy rather than to the player.
 *
 * <p>Slotless, like {@link ClassTreeMenu}, so {@code clickMenuButton} is the whole client-to-server
 * channel and no payload of its own is needed. Which dummy is being configured travels once, as the
 * entity id in the menu-open buffer; everything the screen draws afterwards comes off that entity's
 * synched configuration, so two players with the screen open see each other's edits.
 */
public final class TrainingDummyMenu extends AbstractContainerMenu {

    /** Toggle one skill in the rotation: base + index into {@link #skillOptions()}. */
    public static final int BUTTON_SKILL_BASE = 0;
    /** Toggle one passive: base + index into {@link #passiveOptions()}. */
    public static final int BUTTON_PASSIVE_BASE = 2000;
    /**
     * Set the delay: base + ticks.
     *
     * <p>{@code clickMenuButton} carries one int and nothing else, so a value control has to encode
     * its value in the id. The dummy clamps to {@code [MIN_DELAY, MAX_DELAY]} anyway, which keeps
     * this range inside its block no matter what a client sends.
     */
    public static final int BUTTON_DELAY_BASE = 4000;
    public static final int BUTTON_CLEAR_SKILLS = 6000;
    public static final int BUTTON_RESET_METER = 6001;
    public static final int BUTTON_TOGGLE_PARRY = 6002;
    public static final int BUTTON_TOGGLE_QTE = 6003;
    public static final int BUTTON_REMOVE = 6004;

    private final Player player;
    private final int dummyId;

    public TrainingDummyMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readVarInt());
    }

    public TrainingDummyMenu(int containerId, Inventory inventory, int dummyId) {
        super(MagicalMenus.TRAINING_DUMMY.get(), containerId);
        this.player = inventory.player;
        this.dummyId = dummyId;
    }

    public static void open(ServerPlayer player, TrainingDummyEntity dummy) {
        player.openMenu(new SimpleMenuProvider(
                        (containerId, inventory, menuPlayer) -> new TrainingDummyMenu(containerId, inventory, dummy.getId()),
                        Component.translatable("gui.magical.training_dummy")),
                buf -> buf.writeVarInt(dummy.getId()));
    }

    /**
     * Every skill in the game, sub-skills included.
     *
     * <p>Deliberately not {@code MagicContent.allSkills()}, which filters sub-skills out for the
     * random-unlock and codex paths. A practice dummy that could not throw Gabriel's individual
     * wings or an Aegis sub-skill at you would be missing exactly the attacks worth practising.
     */
    public static List<ResourceLocation> skillOptions() {
        return MagicContent.orderedSkillIds();
    }

    /**
     * The passives a dummy can carry.
     *
     * <p>Curses are left out: they are penalties with a dispel condition attached, applied to a
     * player who has earned them, and nothing about them changes what a dummy does to you.
     */
    public static List<MagicPassiveDefinition> passiveOptions() {
        return MagicPassiveContent.all().stream().filter(passive -> !passive.curse()).toList();
    }

    public int dummyId() {
        return dummyId;
    }

    /** The dummy on whichever side is asking, or null if it has gone. */
    public TrainingDummyEntity dummy() {
        return player.level().getEntity(dummyId) instanceof TrainingDummyEntity dummy ? dummy : null;
    }

    @Override
    public boolean stillValid(Player player) {
        TrainingDummyEntity dummy = dummy();
        return dummy != null && dummy.isAlive() && dummy.distanceToSqr(player) < 16 * 16;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer)) {
            return true;
        }
        TrainingDummyEntity dummy = dummy();
        if (dummy == null) {
            return true;
        }
        if (id >= BUTTON_CLEAR_SKILLS) {
            switch (id) {
                case BUTTON_CLEAR_SKILLS -> dummy.clearSkills();
                case BUTTON_RESET_METER -> dummy.reset();
                case BUTTON_TOGGLE_PARRY -> dummy.setParryIncoming(!dummy.parryIncoming());
                case BUTTON_TOGGLE_QTE -> dummy.setAlwaysQte(!dummy.alwaysQte());
                case BUTTON_REMOVE -> dummy.discard();
                default -> { }
            }
            return true;
        }
        if (id >= BUTTON_DELAY_BASE) {
            dummy.setDelayTicks(id - BUTTON_DELAY_BASE);
            return true;
        }
        if (id >= BUTTON_PASSIVE_BASE) {
            List<MagicPassiveDefinition> passives = passiveOptions();
            int index = id - BUTTON_PASSIVE_BASE;
            if (index < passives.size()) {
                dummy.togglePassive(passives.get(index).id());
            }
            return true;
        }
        List<ResourceLocation> skills = skillOptions();
        int index = id - BUTTON_SKILL_BASE;
        if (index >= 0 && index < skills.size()) {
            dummy.toggleSkill(skills.get(index));
        }
        return true;
    }
}
