package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.SpaceWaypoint;
import com.efkrdnz.magical.magic.menu.SpaceWalkerMenu;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.network.SpaceWalkerActionPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Inventory;

public final class SpaceWalkerScreen extends AbstractContainerScreen<SpaceWalkerMenu> {
    private static final int BG = 0xF00A1320;
    private static final int PANEL = 0xFF12233A;
    private static final int PANEL_ALT = 0xFF0D1B2D;
    private static final int CYAN = 0xFF88DFFF;
    private static final int VISIBLE_ROWS = 7;

    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private EditBox nameBox;
    private int scroll;
    private int selectedDimension;

    public SpaceWalkerScreen(SpaceWalkerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 500;
        imageHeight = 264;
        inventoryLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        int left = leftPos;
        int top = topPos;
        int currentX = minecraft != null && minecraft.player != null ? minecraft.player.getBlockX() : 0;
        int currentY = minecraft != null && minecraft.player != null ? minecraft.player.getBlockY() : 64;
        int currentZ = minecraft != null && minecraft.player != null ? minecraft.player.getBlockZ() : 0;
        selectedDimension = currentDimensionIndex();
        xBox = coordinateBox(left + 25, top + 86, Integer.toString(currentX), Component.translatable("screen.magical.space_walker_x"));
        yBox = coordinateBox(left + 94, top + 86, Integer.toString(currentY), Component.translatable("screen.magical.space_walker_y"));
        zBox = coordinateBox(left + 163, top + 86, Integer.toString(currentZ), Component.translatable("screen.magical.space_walker_z"));
        nameBox = new EditBox(font, left + 25, top + 165, 200, 18, Component.translatable("screen.magical.space_walker_name"));
        nameBox.setMaxLength(SpaceWaypoint.MAX_NAME_LENGTH);
        nameBox.setValue("Waypoint");
        addRenderableWidget(xBox);
        addRenderableWidget(yBox);
        addRenderableWidget(zBox);
        addRenderableWidget(nameBox);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        guiGraphics.fill(left, top, left + imageWidth, top + imageHeight, BG);
        guiGraphics.fill(left + 12, top + 30, left + 238, top + 244, PANEL);
        guiGraphics.fill(left + 250, top + 30, left + 488, top + 244, PANEL_ALT);
        guiGraphics.drawString(font, title, left + 14, top + 10, CYAN, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_direct"), left + 25, top + 38, 0xD8F6FF, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_dimension"), left + 25, top + 55, 0x8CA9B8, false);
        button(guiGraphics, left + 25, top + 66, 200, 16, 0xFF173B58, Component.literal(displayDimension(selectedDimensionId())));
        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_x"), left + 25, top + 75, 0x8CA9B8, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_y"), left + 94, top + 75, 0x8CA9B8, false);
        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_z"), left + 163, top + 75, 0x8CA9B8, false);
        button(guiGraphics, left + 25, top + 114, 96, 20, 0xFF1C5D73, Component.translatable("screen.magical.space_walker_teleport_short"));
        button(guiGraphics, left + 129, top + 114, 96, 20, 0xFF31527C, Component.translatable("screen.magical.space_walker_create_portal"));
        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_save_title"), left + 25, top + 152, 0xD8F6FF, false);
        button(guiGraphics, left + 25, top + 192, 200, 20, 0xFF1B4F66, Component.translatable("screen.magical.space_walker_save_current"));
        guiGraphics.drawWordWrap(font, Component.translatable("screen.magical.space_walker_hint"), left + 25, top + 218, 196, 0x96B9CA);

        guiGraphics.drawString(font, Component.translatable("screen.magical.space_walker_waypoints"), left + 262, top + 40, CYAN, false);
        List<SpaceWaypoint> waypoints = ClientMagicState.get().spaceWaypoints();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, waypoints.size() - VISIBLE_ROWS)));
        if (waypoints.isEmpty()) {
            guiGraphics.drawWordWrap(font, Component.translatable("screen.magical.space_walker_no_waypoints"), left + 262, top + 62, 190, 0x8FA6B8);
            return;
        }
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= waypoints.size()) {
                break;
            }
            SpaceWaypoint waypoint = waypoints.get(index);
            int y = top + 60 + row * 22;
            guiGraphics.fill(left + 262, y, left + 476, y + 19, 0xFF182D46);
            guiGraphics.drawString(font, font.plainSubstrByWidth(waypoint.name(), 70), left + 266, y + 3, 0xEAF9FF, false);
            guiGraphics.drawString(font, font.plainSubstrByWidth(displayDimension(waypoint.dimension()) + " " + waypoint.x() + "," + waypoint.y() + "," + waypoint.z(), 116), left + 266, y + 12, 0x8CA9B8, false);
            button(guiGraphics, left + 386, y + 2, 26, 15, 0xFF1C5D73, Component.literal("Go"));
            button(guiGraphics, left + 416, y + 2, 26, 15, 0xFF31527C, Component.literal("P"));
            button(guiGraphics, left + 446, y + 2, 24, 15, 0xFF5A2430, Component.literal("X"));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = leftPos;
        int top = topPos;
        if (inside(mouseX, mouseY, left + 25, top + 66, 200, 16)) {
            cycleDimension(button == 1 ? -1 : 1);
            return true;
        }
        if (inside(mouseX, mouseY, left + 25, top + 114, 96, 20)) {
            sendCoordinateTeleport();
            return true;
        }
        if (inside(mouseX, mouseY, left + 129, top + 114, 96, 20)) {
            sendCoordinatePortal();
            return true;
        }
        if (inside(mouseX, mouseY, left + 25, top + 192, 200, 20)) {
            sendSaveWaypoint();
            return true;
        }
        List<SpaceWaypoint> waypoints = ClientMagicState.get().spaceWaypoints();
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= waypoints.size()) {
                break;
            }
            int y = top + 60 + row * 22;
            if (inside(mouseX, mouseY, left + 386, y + 2, 26, 15)) {
                MagicalNetwork.sendSpaceWalkerAction(SpaceWalkerActionPayload.TELEPORT_WAYPOINT, index, "", 0, 0, 0);
                return true;
            }
            if (inside(mouseX, mouseY, left + 416, y + 2, 26, 15)) {
                MagicalNetwork.sendSpaceWalkerAction(SpaceWalkerActionPayload.CREATE_PORTAL_WAYPOINT, index, "", 0, 0, 0);
                return true;
            }
            if (inside(mouseX, mouseY, left + 446, y + 2, 24, 15)) {
                MagicalNetwork.sendSpaceWalkerAction(SpaceWalkerActionPayload.DELETE_WAYPOINT, index, "", 0, 0, 0);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, leftPos + 250, topPos + 30, 238, 214)) {
            int max = Math.max(0, ClientMagicState.get().spaceWaypoints().size() - VISIBLE_ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private EditBox coordinateBox(int x, int y, String value, Component label) {
        EditBox box = new EditBox(font, x, y, 58, 18, label);
        box.setMaxLength(8);
        box.setValue(value);
        return box;
    }

    private void sendCoordinateTeleport() {
        Integer x = parseCoordinate(xBox.getValue());
        Integer y = parseCoordinate(yBox.getValue());
        Integer z = parseCoordinate(zBox.getValue());
        if (x == null || y == null || z == null) {
            return;
        }
        MagicalNetwork.sendSpaceWalkerAction(SpaceWalkerActionPayload.TELEPORT_COORDINATES, -1, "", selectedDimensionId(), x, y, z);
    }

    private void sendCoordinatePortal() {
        Integer x = parseCoordinate(xBox.getValue());
        Integer y = parseCoordinate(yBox.getValue());
        Integer z = parseCoordinate(zBox.getValue());
        if (x == null || y == null || z == null) {
            return;
        }
        MagicalNetwork.sendSpaceWalkerAction(SpaceWalkerActionPayload.CREATE_PORTAL_COORDINATES, -1, "", selectedDimensionId(), x, y, z);
    }

    private void sendSaveWaypoint() {
        Integer x = parseCoordinate(xBox.getValue());
        Integer y = parseCoordinate(yBox.getValue());
        Integer z = parseCoordinate(zBox.getValue());
        if (x == null || y == null || z == null) {
            return;
        }
        MagicalNetwork.sendSpaceWalkerAction(SpaceWalkerActionPayload.SAVE_CURRENT, -1, nameBox.getValue(), selectedDimensionId(), x, y, z);
    }

    private Integer parseCoordinate(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void button(GuiGraphics guiGraphics, int x, int y, int width, int height, int color, Component label) {
        guiGraphics.fill(x, y, x + width, y + height, color);
        guiGraphics.drawCenteredString(font, font.plainSubstrByWidth(label.getString(), width - 4), x + width / 2, y + (height - 8) / 2, 0xF4FBFF);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void cycleDimension(int direction) {
        List<String> dimensions = dimensions();
        if (dimensions.isEmpty()) {
            selectedDimension = 0;
            return;
        }
        selectedDimension = Math.floorMod(selectedDimension + direction, dimensions.size());
    }

    private int currentDimensionIndex() {
        String current = minecraft != null && minecraft.level != null ? minecraft.level.dimension().location().toString() : "minecraft:overworld";
        List<String> dimensions = dimensions();
        int index = dimensions.indexOf(current);
        return index < 0 ? Math.max(0, dimensions.indexOf("minecraft:overworld")) : index;
    }

    private String selectedDimensionId() {
        List<String> dimensions = dimensions();
        if (dimensions.isEmpty()) {
            return "minecraft:overworld";
        }
        selectedDimension = Math.max(0, Math.min(selectedDimension, dimensions.size() - 1));
        return dimensions.get(selectedDimension);
    }

    private List<String> dimensions() {
        if (minecraft == null || minecraft.getConnection() == null) {
            return List.of("minecraft:overworld");
        }
        List<String> ids = new ArrayList<>();
        for (ResourceKey<Level> key : minecraft.getConnection().levels()) {
            ids.add(key.location().toString());
        }
        ids.sort(Comparator.comparingInt((String id) -> id.equals("minecraft:overworld") ? 0 : 1).thenComparing(String::toString));
        return ids.isEmpty() ? List.of("minecraft:overworld") : ids;
    }

    private String displayDimension(String dimension) {
        if (dimension == null || dimension.isBlank()) {
            return "overworld";
        }
        int separator = dimension.indexOf(':');
        return separator >= 0 ? dimension.substring(separator + 1) : dimension;
    }
}
