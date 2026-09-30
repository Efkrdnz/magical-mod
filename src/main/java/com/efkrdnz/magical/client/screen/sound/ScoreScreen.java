package com.efkrdnz.magical.client.screen.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudQuiet;
import com.efkrdnz.magical.client.renderer.sound.SoundNoteRenderer;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.sound.ClientSongs;
import com.efkrdnz.magical.magic.sound.Family;
import com.efkrdnz.magical.magic.sound.Instrument;
import com.efkrdnz.magical.magic.sound.Riff;
import com.efkrdnz.magical.magic.sound.RiffNote;
import com.efkrdnz.magical.magic.sound.Score;
import com.efkrdnz.magical.magic.sound.ScoreText;
import com.efkrdnz.magical.magic.sound.SongPresets;
import com.efkrdnz.magical.magic.sound.SoundState;
import com.efkrdnz.magical.magic.sound.Tempo;
import com.efkrdnz.magical.magic.sound.Track;
import com.efkrdnz.magical.network.MagicalNetwork;
import com.efkrdnz.magical.registry.MagicalSounds;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.glfw.GLFW;

/**
 * The Score: where the Song and the Riff are written. Frameless, like every screen of the
 * Authorities: one scrim over the world, and every string and cell drawn straight onto it.
 *
 * <p>Everything drawn comes from one tinted atlas ({@link ScoreArt}) and the Riff note sprites: a
 * written note is a bead in its track colour set in a socket, each track carries its clef, a button
 * is an icon and a word, and nothing sits on a panel.
 *
 * <p>A song is shared the way a mandachord loop is: Export (or Ctrl+C) copies the tab shown to the
 * clipboard as {@link ScoreText} JSON, Import (or Ctrl+V) reads whatever the clipboard holds - a song or
 * a riff, and the screen turns to its tab - and the preset button walks the three {@link SongPresets}.
 * Each of those replaces what was written, so each can be taken back once with Ctrl+Z.
 *
 * <p>The Song tab is the mandachord - click a cell to write a note, click it again or right-click to
 * erase, drag to paint. A bar that already holds its track's cap refuses the note and says so. The
 * Riff tab is eight strips: click a pitch to write it, right-click a strip for a rest, scroll over a
 * strip to change its instrument. Play previews either, here, on this client; Save sends both, and
 * closing the screen saves what was changed.
 */
public final class ScoreScreen extends Screen implements HudDebug.Captured, HudQuiet {

    private static final int SCRIM = 0xA6060B14;
    private static final int VIGNETTE = 0x70000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int HOT = 0xFFD8E0EA;
    private static final int MUTED = 0xFFA9B4C2;
    private static final int FAINT = 0xFF6F7A88;
    private static final int ACCENT_RGB = 0x6FE0C0;
    private static final int ACCENT = 0xFF000000 | ACCENT_RGB;
    private static final int GOLD = 0xFFFFD23F;
    private static final int WARN = 0xFFFF8A7A;
    private static final int PLAYHEAD = 0x48FFFFFF;
    private static final int BAR_LINE = 0x22FFFFFF;
    private static final int METER_HIGH = 0xFFA6F5DE;
    private static final int METER_LOW = 0xFF3C9C82;
    private static final int MESSAGE_TICKS = 70;
    private static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final ResourceLocation[] NOTES = new ResourceLocation[6];

    static {
        for (int i = 0; i < NOTES.length; i++) {
            NOTES[i] = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/particle/note_" + i + ".png");
        }
    }

    private ScoreLayout layout;
    private ScoreLayout.Tab tab = ScoreLayout.Tab.SONG;
    private Score song;
    private Riff riff;
    private boolean dirty;
    private int page;
    private int hoverSlot = -1;
    private Instrument lastInstrument = Instrument.HARP;
    private Boolean paint;
    private ScoreLayout.Cell lastPainted;
    private Component message;
    private int messageTicks;
    private boolean warning;
    private long previewStart = -1L;
    private long previewLast;
    /** What the last preset, import or undo replaced, so one Ctrl+Z can put it back. */
    private Score undoSong;
    private Riff undoRiff;

    private ScoreScreen() {
        super(Component.translatable("screen.magical.score"));
        SoundState sound = ClientMagicState.get().sound();
        this.song = sound.song();
        this.riff = sound.riff();
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(new ScoreScreen());
    }

    @Override
    protected void init() {
        layout = new ScoreLayout(width, height);
        page = Math.min(page, layout.pages() - 1);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, SCRIM);
        graphics.fillGradient(0, 0, width, height / 3, VIGNETTE, 0x00000000);
        graphics.fillGradient(0, height - height / 3, width, height, 0x00000000, VIGNETTE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        tabs(graphics, mouseX, mouseY);
        if (tab == ScoreLayout.Tab.SONG) {
            renderSong(graphics, mouseX, mouseY);
        } else {
            renderRiff(graphics, mouseX, mouseY);
        }
    }

    private void tabs(GuiGraphics graphics, int mouseX, int mouseY) {
        for (ScoreLayout.Tab each : ScoreLayout.Tab.values()) {
            Rect rect = layout.tab(each);
            boolean shown = each == tab;
            boolean hot = !shown && inside(rect, mouseX, mouseY);
            ScoreArt.Sprite icon = each == ScoreLayout.Tab.SONG ? ScoreArt.QUARTER : ScoreArt.BOLT_NOTE;
            String label = Component.translatable("score.magical.tab." + each.name().toLowerCase(Locale.ROOT)).getString();
            int wide = icon.w() + 2 + font.width(label);
            int x = rect.x() + (rect.w() - wide) / 2;
            ScoreArt.draw(graphics, icon, x, rect.y(), shown ? ACCENT : hot ? HOT : FAINT);
            graphics.drawString(font, label, x + icon.w() + 2, rect.y() + 5, shown ? WHITE : hot ? HOT : MUTED, true);
        }
        Rect flourish = layout.flourish();
        ScoreArt.draw(graphics, ScoreArt.FLOURISH, flourish.x(), flourish.y(), 0xD0000000 | ACCENT_RGB);
    }

    // ---------------------------------------------------------------- the Song

    private int stepOf(int column) {
        return page * layout.visibleSteps() + column;
    }

    private void renderSong(GuiGraphics graphics, int mouseX, int mouseY) {
        int cell = layout.cell();
        int visible = layout.visibleSteps();
        ScoreLayout.Cell hover = layout.cellAt(mouseX, mouseY);
        int playing = previewStep();
        int playingColumn = playing >= 0 && playing / visible == page ? playing % visible : -1;
        int top = ScoreLayout.GRID_TOP;
        int bottom = layout.gridBottom();
        if (playingColumn >= 0) {
            int x = layout.gridLeft() + playingColumn * cell;
            int middle = (top + bottom) / 2;
            graphics.fillGradient(x, top - 2, x + cell, middle, 0x00FFFFFF, PLAYHEAD);
            graphics.fillGradient(x, middle, x + cell, bottom + 2, PLAYHEAD, 0x00FFFFFF);
        }
        for (int column = 0; column < visible; column += Score.BAR) {
            int bar = stepOf(column) / Score.BAR;
            Rect number = layout.barNumber(column);
            boolean now = playing >= 0 && playing / Score.BAR == bar;
            graphics.drawString(font, String.valueOf(bar + 1), number.x() + 1, number.y(), now ? WHITE : FAINT, true);
            if (column > 0) {
                graphics.fill(number.x(), top, number.x() + 1, bottom, BAR_LINE);
            }
        }
        for (Track track : ScoreLayout.DISPLAY) {
            Rect glyph = layout.trackGlyph(track);
            ScoreArt.draw(graphics, ScoreArt.glyph(track), glyph.x(), glyph.y(), 0xFF000000 | track.rgb());
            for (int row = 0; row < track.rows(); row++) {
                Rect label = layout.rowLabel(track, row);
                String name = rowName(track, row);
                boolean hotRow = hover != null && hover.track() == track && hover.row() == row;
                graphics.drawString(font, name, label.right() - font.width(name), label.y() + (label.h() - 7) / 2,
                        hotRow ? WHITE : MUTED, true);
                for (int column = 0; column < visible; column++) {
                    int step = stepOf(column);
                    Rect at = layout.cell(track, row, column);
                    if (song.has(track, row, step)) {
                        int tint = column == playingColumn ? lighten(track.rgb(), 0.6F) : 0xFF000000 | track.rgb();
                        ScoreArt.draw(graphics, ScoreArt.bead(track, cell), at.x(), at.y(), tint);
                    } else if (hotRow && hover.column() == column) {
                        ScoreArt.draw(graphics, ScoreArt.bead(track, cell), at.x(), at.y(), 0x78000000 | track.rgb());
                    } else {
                        ScoreArt.draw(graphics, ScoreArt.socket(step % 4 == 0, cell), at.x(), at.y(), 0xFF000000 | track.rgb());
                    }
                }
            }
        }
        if (layout.pages() > 1) {
            arrow(graphics, layout.pagePrev(), ScoreArt.PREV, mouseX, mouseY);
            int first = page * visible / Score.BAR + 1;
            int last = (page + 1) * visible / Score.BAR;
            Component bars = first == last ? Component.translatable("score.magical.bar", first)
                    : Component.translatable("score.magical.bars", first, last);
            centered(graphics, layout.pageLabel(), bars, MUTED);
            arrow(graphics, layout.pageNext(), ScoreArt.NEXT, mouseX, mouseY);
        }
        button(graphics, layout.tempo(), ScoreArt.METRONOME, Component.translatable("score.magical.tempo", song.tempo().bpm()), ACCENT, mouseX, mouseY);
        button(graphics, layout.scale(), ScoreArt.SHARP,
                Component.translatable("score.magical.scale." + song.scale().name().toLowerCase(Locale.ROOT)), ACCENT, mouseX, mouseY);
        button(graphics, layout.preset(), ScoreArt.SHEET, presetLabel(), ACCENT, mouseX, mouseY);
        playAndSave(graphics, layout.play(), layout.save(), mouseX, mouseY);
        share(graphics, ScoreLayout.Tab.SONG, mouseX, mouseY);
        Component readout = Component.translatable("score.magical.song_readout",
                count(Track.PERCUSSION), count(Track.BASS), count(Track.MELODY));
        centered(graphics, layout.readout(ScoreLayout.Tab.SONG), readout, MUTED);
        footer(graphics, ScoreLayout.Tab.SONG, "score.magical.hint.song");
    }

    private Component count(Track track) {
        int bar = hoverBar();
        return Component.literal(song.notesInBar(track, bar) + "/" + track.barCap()).withColor(track.rgb());
    }

    private int hoverBar() {
        return Math.min(Score.BARS - 1, page * layout.visibleSteps() / Score.BAR);
    }

    private String rowName(Track track, int row) {
        if (track == Track.PERCUSSION) {
            return Component.translatable("score.magical.kit." + row).getString();
        }
        return noteName(Riff.CENTRE + song.scale().semitone(row));
    }

    /** The name of a pitch counted in semitones above F sharp. */
    static String noteName(int pitch) {
        return NOTE_NAMES[Math.floorMod(6 + pitch - Riff.CENTRE, 12)];
    }

    // ---------------------------------------------------------------- the Riff

    private void renderRiff(GuiGraphics graphics, int mouseX, int mouseY) {
        hoverSlot = layout.slotAt(mouseX, mouseY);
        int playing = previewStart >= 0 ? riff.slotAt(Math.max(0L, (now() - previewStart) / Riff.STEP_TICKS)) : -1;
        for (int slot = 0; slot < Riff.MAX_NOTES; slot++) {
            string(graphics, slot, slot == playing, mouseY);
        }
        arrow(graphics, layout.lengthPrev(), ScoreArt.PREV, mouseX, mouseY);
        centered(graphics, layout.lengthLabel(), Component.translatable("score.magical.notes", riff.length()), MUTED);
        arrow(graphics, layout.lengthNext(), ScoreArt.NEXT, mouseX, mouseY);
        Rect amplitudeLabel = layout.amplitudeLabel();
        String amplitude = Component.translatable("score.magical.amplitude").getString();
        graphics.drawString(font, amplitude, amplitudeLabel.right() - font.width(amplitude) - 2, amplitudeLabel.y() + 2, MUTED, true);
        for (int level = Riff.MIN_AMPLITUDE; level <= Riff.MAX_AMPLITUDE; level++) {
            Rect pip = layout.pip(level);
            int rise = 4 + 2 * (level - 1);
            int barTop = pip.bottom() - rise;
            if (level <= riff.amplitude()) {
                graphics.fillGradient(pip.x(), barTop, pip.right(), pip.bottom(), METER_HIGH, METER_LOW);
            } else {
                graphics.fill(pip.x(), barTop, pip.right(), pip.bottom(), inside(pip, mouseX, mouseY) ? 0x80FFFFFF : 0x2EFFFFFF);
            }
        }
        playAndSave(graphics, layout.riffPlay(), layout.riffSave(), mouseX, mouseY);
        share(graphics, ScoreLayout.Tab.RIFF, mouseX, mouseY);
        centered(graphics, layout.readout(ScoreLayout.Tab.RIFF), riffReadout(), MUTED);
        footer(graphics, ScoreLayout.Tab.RIFF, "score.magical.hint.riff");
    }

    /** One slot of the riff: a fretted string with its note sitting on it, the family over it, the names under it. */
    private void string(GuiGraphics graphics, int slot, boolean playing, int mouseY) {
        Rect strip = layout.strip(slot);
        boolean used = slot < riff.length();
        Instrument instrument = used ? riff.instrument(slot) : null;
        int rgb = instrument == null ? 0xB8C2CE : instrument.family().rgb();
        int x = layout.stringX(slot);
        if (playing) {
            graphics.fillGradient(strip.x(), strip.y(), strip.right(), strip.bottom(), rgb & 0xFFFFFF, 0x48000000 | rgb);
        }
        for (int pitch = 0; pitch < Riff.PITCHES; pitch++) {
            int y = layout.pitchCentre(pitch);
            String name = noteName(pitch);
            if (name.equals("C")) {
                graphics.fill(strip.x(), y, strip.right(), y + 1, used ? 0x40FFFFFF : 0x1AFFFFFF);
            } else {
                int reach = name.length() > 1 ? 1 : 3;
                int tick = used ? 0x2CFFFFFF : 0x12FFFFFF;
                graphics.fill(x - 2 - reach, y, x - 2, y + 1, tick);
                graphics.fill(x + 3, y, x + 3 + reach, y + 1, tick);
            }
        }
        if (used) {
            graphics.fill(x, strip.y(), x + 1, strip.bottom(), instrument == null ? 0x50FFFFFF : 0xC0000000 | rgb);
        } else {
            for (int y = strip.y(); y < strip.bottom(); y += 3) {
                graphics.fill(x, y, x + 1, y + 1, 0x30FFFFFF);
            }
        }
        if (used && slot == hoverSlot) {
            int pitch = layout.pitchAt(slot, mouseY);
            Instrument ghost = instrument != null ? instrument : lastInstrument;
            if (pitch >= 0 && (instrument == null || pitch != riff.pitch(slot))) {
                note(graphics, ghost.family(), x, layout.pitchCentre(pitch), 0x70000000 | ghost.family().rgb());
            }
        }
        Rect icon = layout.familyIcon(slot);
        if (instrument != null) {
            note(graphics, instrument.family(), x, layout.pitchCentre(riff.pitch(slot)), playing ? lighten(rgb, 0.6F) : 0xFF000000 | rgb);
            ScoreArt.draw(graphics, ScoreArt.glyph(instrument.family()), icon.x(), icon.y(),
                    slot == hoverSlot || playing ? lighten(rgb, 0.3F) : 0xFF000000 | rgb);
        } else if (used) {
            ScoreArt.drawCentred(graphics, ScoreArt.REST, x, strip.y() + strip.h() / 2, MUTED);
            ScoreArt.drawCentred(graphics, ScoreArt.REST, x, icon.y() + icon.h() / 2, FAINT);
        }
        String name = !used ? "" : instrument == null ? Component.translatable("score.magical.rest").getString()
                : Component.translatable("instrument.magical." + instrument.id()).getString();
        Rect label = layout.slotLabel(slot, 0);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(name, label.w()), label.x() + label.w() / 2, label.y(),
                instrument == null ? FAINT : 0xFF000000 | rgb);
        if (instrument != null) {
            Rect second = layout.slotLabel(slot, 1);
            graphics.drawCenteredString(font, noteName(riff.pitch(slot)), second.x() + second.w() / 2, second.y(), MUTED);
        }
    }

    /**
     * A note sprite with its head on a point: the head, not the middle of the picture, is what sits on
     * the pitch, so the stem rises above it the way it does on a stave.
     */
    private static void note(GuiGraphics graphics, Family family, int x, int y, int argb) {
        int glyph = SoundNoteRenderer.glyph(family);
        int headX = glyph == 2 ? 8 : glyph == 4 ? 5 : 6;
        int headY = glyph == 2 ? 11 : 12;
        graphics.blit(RenderType::guiTextured, NOTES[glyph], x - headX, y - headY, 0, 0, 16, 16, 16, 16, 16, 16, argb);
    }

    private Component riffReadout() {
        int slot = hoverSlot >= 0 && hoverSlot < riff.length() ? hoverSlot : -1;
        String perSecond = String.format(Locale.ROOT, "%.1f", riff.manaPerSecond());
        if (slot < 0 || riff.instrument(slot) == null) {
            return Component.translatable("score.magical.per_second", perSecond);
        }
        RiffNote note = RiffNote.resolve(riff.instrument(slot), riff.pitch(slot), riff.amplitude());
        Family family = note.family();
        return Component.translatable("score.magical.per_note",
                Component.translatable("family.magical." + family.name().toLowerCase(Locale.ROOT)).withColor(family.rgb()),
                String.format(Locale.ROOT, "%.1f", note.damage()), String.format(Locale.ROOT, "%.1f", note.mana()), perSecond);
    }

    // ---------------------------------------------------------------- shared drawing

    private void playAndSave(GuiGraphics graphics, Rect play, Rect save, int mouseX, int mouseY) {
        boolean running = previewStart >= 0;
        button(graphics, play, running ? ScoreArt.STOP : ScoreArt.PLAY,
                Component.translatable(running ? "score.magical.stop" : "score.magical.play"), ACCENT, mouseX, mouseY);
        button(graphics, save, ScoreArt.SEAL, Component.translatable(dirty ? "score.magical.save" : "score.magical.saved"),
                dirty ? GOLD : FAINT, mouseX, mouseY);
    }

    private void share(GuiGraphics graphics, ScoreLayout.Tab shown, int mouseX, int mouseY) {
        button(graphics, layout.importButton(shown), ScoreArt.IMPORT, Component.translatable("score.magical.import"), ACCENT, mouseX, mouseY);
        button(graphics, layout.exportButton(shown), ScoreArt.EXPORT, Component.translatable("score.magical.export"), ACCENT, mouseX, mouseY);
    }

    /** The preset the song is, by name, or the word for the button when it is none of them. */
    private Component presetLabel() {
        int index = presetIndex();
        return index < 0 ? Component.translatable("score.magical.presets") : Component.literal(SongPresets.ALL.get(index).name());
    }

    private int presetIndex() {
        for (int i = 0; i < SongPresets.ALL.size(); i++) {
            if (SongPresets.ALL.get(i).score().equals(song)) {
                return i;
            }
        }
        return -1;
    }

    private void footer(GuiGraphics graphics, ScoreLayout.Tab shown, String hintKey) {
        Rect hint = layout.hint(shown);
        Component line = messageTicks > 0 && message != null ? message : Component.translatable(hintKey);
        int colour = messageTicks > 0 && message != null ? (warning ? WARN : ACCENT) : FAINT;
        String fitted = font.plainSubstrByWidth(line.getString(), hint.w());
        graphics.drawCenteredString(font, fitted, hint.x() + hint.w() / 2, hint.y() + 2, colour);
    }

    /** An icon and its word, centred in the rect; the whole of it lights under the pointer. */
    private void button(GuiGraphics graphics, Rect rect, ScoreArt.Sprite icon, Component text, int iconTint, int mouseX, int mouseY) {
        boolean hot = inside(rect, mouseX, mouseY);
        String word = font.plainSubstrByWidth(text.getString(), rect.w() - icon.w() - 3);
        int wide = icon.w() + 3 + font.width(word);
        int x = rect.x() + (rect.w() - wide) / 2;
        ScoreArt.draw(graphics, icon, x, rect.y(), hot ? WHITE : iconTint);
        graphics.drawString(font, word, x + icon.w() + 3, rect.y() + 2, hot ? WHITE : MUTED, true);
    }

    private void arrow(GuiGraphics graphics, Rect rect, ScoreArt.Sprite icon, int mouseX, int mouseY) {
        ScoreArt.drawCentred(graphics, icon, rect.x() + rect.w() / 2, rect.y() + rect.h() / 2,
                inside(rect, mouseX, mouseY) ? WHITE : ACCENT);
    }

    private void centered(GuiGraphics graphics, Rect rect, Component text, int colour) {
        if (font.width(text) <= rect.w() + 12) {
            graphics.drawCenteredString(font, text, rect.x() + rect.w() / 2, rect.y() + 2, colour);
            return;
        }
        String fitted = font.plainSubstrByWidth(text.getString(), rect.w() + 12);
        graphics.drawCenteredString(font, fitted, rect.x() + rect.w() / 2, rect.y() + 2, colour);
    }

    /** An opaque colour a fraction of the way from {@code rgb} to white. */
    private static int lighten(int rgb, float toWhite) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        r += (int) ((255 - r) * toWhite);
        g += (int) ((255 - g) * toWhite);
        b += (int) ((255 - b) * toWhite);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static boolean inside(Rect rect, double x, double y) {
        return x >= rect.x() && x < rect.right() && y >= rect.y() && y < rect.bottom();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        for (ScoreLayout.Tab each : ScoreLayout.Tab.values()) {
            if (inside(layout.tab(each), x, y)) {
                tab = each;
                stopPreview();
                return true;
            }
        }
        if (inside(layout.importButton(tab), x, y)) {
            importClipboard();
            return true;
        }
        if (inside(layout.exportButton(tab), x, y)) {
            exportClipboard();
            return true;
        }
        boolean handled = tab == ScoreLayout.Tab.SONG ? clickSong(x, y, button) : clickRiff(x, y, button);
        return handled || super.mouseClicked(x, y, button);
    }

    private boolean clickSong(double x, double y, int button) {
        ScoreLayout.Cell cell = layout.cellAt(x, y);
        if (cell != null) {
            int step = stepOf(cell.column());
            paint = button == 0 && !song.has(cell.track(), cell.row(), step);
            lastPainted = cell;
            write(cell, paint);
            return true;
        }
        if (layout.pages() > 1 && inside(layout.pagePrev(), x, y)) {
            page = Math.floorMod(page - 1, layout.pages());
            return true;
        }
        if (layout.pages() > 1 && inside(layout.pageNext(), x, y)) {
            page = (page + 1) % layout.pages();
            return true;
        }
        if (inside(layout.tempo(), x, y)) {
            Tempo next = button == 1 ? song.tempo().next().next() : song.tempo().next();
            song = song.withTempo(next);
            dirty = true;
            restartPreview();
            return true;
        }
        if (inside(layout.scale(), x, y)) {
            int shift = button == 1 ? song.scale().ordinal() - 1 : song.scale().ordinal() + 1;
            song = song.withScale(com.efkrdnz.magical.magic.sound.Scale.values()[Math.floorMod(shift, com.efkrdnz.magical.magic.sound.Scale.values().length)]);
            dirty = true;
            return true;
        }
        if (inside(layout.preset(), x, y)) {
            int current = presetIndex();
            int next = current < 0 ? (button == 1 ? SongPresets.ALL.size() - 1 : 0) : SongPresets.next(current, button == 1 ? -1 : 1);
            SongPresets.Preset preset = SongPresets.ALL.get(next);
            replaceSong(preset.score(), Component.translatable("score.magical.loaded", preset.name()));
            return true;
        }
        if (inside(layout.play(), x, y)) {
            togglePreview();
            return true;
        }
        if (inside(layout.save(), x, y)) {
            save();
            return true;
        }
        return false;
    }

    private void write(ScoreLayout.Cell cell, boolean on) {
        Score.Edit edit = song.set(cell.track(), cell.row(), stepOf(cell.column()), on);
        if (edit.refusal() == Score.Refusal.BAR_FULL) {
            say(Component.translatable("score.magical.bar_full",
                    Component.translatable("score.magical.track." + cell.track().name().toLowerCase(Locale.ROOT)), cell.track().barCap()), true);
            return;
        }
        if (!edit.score().equals(song)) {
            song = edit.score();
            dirty = true;
            if (on && minecraft != null && minecraft.level != null && minecraft.player != null) {
                ClientSongs.playStep(minecraft.level, minecraft.player.position(), onlyCell(cell), stepOf(cell.column()), 0.6F, false);
            }
        }
    }

    /** A score holding only one cell, so writing a note sounds that note and nothing else on its step. */
    private Score onlyCell(ScoreLayout.Cell cell) {
        long[] rows = new long[Track.TOTAL_ROWS];
        rows[cell.track().firstRow() + cell.row()] = 1L << stepOf(cell.column());
        return Score.of(song.tempo(), song.scale(), rows);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dragX, double dragY) {
        if (tab == ScoreLayout.Tab.SONG && paint != null) {
            ScoreLayout.Cell cell = layout.cellAt(x, y);
            if (cell != null && !cell.equals(lastPainted)) {
                lastPainted = cell;
                write(cell, paint);
            }
            return true;
        }
        if (tab == ScoreLayout.Tab.RIFF && button == 0) {
            int slot = layout.slotAt(x, y);
            int pitch = slot >= 0 ? layout.pitchAt(slot, y) : -1;
            if (slot >= 0 && slot < riff.length() && pitch >= 0 && riff.instrument(slot) != null && riff.pitch(slot) != pitch) {
                riff = riff.withNote(slot, riff.instrument(slot), pitch);
                dirty = true;
            }
            return true;
        }
        return super.mouseDragged(x, y, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        paint = null;
        lastPainted = null;
        return super.mouseReleased(x, y, button);
    }

    private boolean clickRiff(double x, double y, int button) {
        int slot = layout.slotAt(x, y);
        if (slot >= 0 && slot < riff.length()) {
            if (button == 1) {
                riff = riff.withRest(slot);
                dirty = true;
                return true;
            }
            int pitch = layout.pitchAt(slot, y);
            Instrument instrument = riff.instrument(slot) != null ? riff.instrument(slot) : lastInstrument;
            riff = riff.withNote(slot, instrument, pitch >= 0 ? pitch : riff.pitch(slot));
            dirty = true;
            sound(instrument, riff.pitch(slot));
            return true;
        }
        if (inside(layout.lengthPrev(), x, y)) {
            riff = riff.withLength(riff.length() - 1);
            dirty = true;
            return true;
        }
        if (inside(layout.lengthNext(), x, y)) {
            riff = riff.withLength(riff.length() + 1);
            dirty = true;
            return true;
        }
        for (int amplitude = Riff.MIN_AMPLITUDE; amplitude <= Riff.MAX_AMPLITUDE; amplitude++) {
            if (inside(layout.pip(amplitude), x, y)) {
                riff = riff.withAmplitude(amplitude);
                dirty = true;
                return true;
            }
        }
        if (inside(layout.riffPlay(), x, y)) {
            togglePreview();
            return true;
        }
        if (inside(layout.riffSave(), x, y)) {
            save();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (tab == ScoreLayout.Tab.RIFF) {
            int slot = layout.slotAt(x, y);
            if (slot >= 0 && slot < riff.length() && scrollY != 0) {
                Instrument current = riff.instrument(slot);
                Instrument next = current == null ? lastInstrument : current.next(scrollY > 0 ? -1 : 1);
                lastInstrument = next;
                riff = riff.withNote(slot, next, riff.pitch(slot));
                dirty = true;
                sound(next, riff.pitch(slot));
                return true;
            }
        } else if (scrollY != 0 && layout.pages() > 1) {
            page = Math.floorMod(page + (scrollY > 0 ? -1 : 1), layout.pages());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ---------------------------------------------------------------- sharing and undo

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && !hasShiftDown() && !hasAltDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_Z -> {
                    undo();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    exportClipboard();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    importClipboard();
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void exportClipboard() {
        if (minecraft == null) {
            return;
        }
        boolean songShown = tab == ScoreLayout.Tab.SONG;
        int preset = presetIndex();
        String text = songShown ? ScoreText.writeSong(song, preset < 0 ? "" : SongPresets.ALL.get(preset).name()) : ScoreText.writeRiff(riff);
        minecraft.keyboardHandler.setClipboard(text);
        say(Component.translatable(songShown ? "score.magical.exported.song" : "score.magical.exported.riff"), false);
    }

    private void importClipboard() {
        if (minecraft == null) {
            return;
        }
        ScoreText.Read read = ScoreText.read(minecraft.keyboardHandler.getClipboard());
        if (!read.ok()) {
            say(Component.translatable("score.magical.import." + read.problem().name().toLowerCase(Locale.ROOT), read.detail()), true);
            return;
        }
        Component name = read.name().isEmpty()
                ? Component.translatable(read.isSong() ? "score.magical.a_song" : "score.magical.a_riff")
                : Component.literal(read.name());
        Component said = read.dropped() > 0
                ? Component.translatable("score.magical.imported_trimmed", name, read.dropped())
                : Component.translatable("score.magical.imported", name);
        if (read.isSong()) {
            tab = ScoreLayout.Tab.SONG;
            replaceSong(read.song(), said);
        } else {
            tab = ScoreLayout.Tab.RIFF;
            replaceRiff(read.riff(), said);
        }
    }

    private void replaceSong(Score next, Component said) {
        undoSong = song;
        undoRiff = null;
        song = next;
        page = 0;
        dirty = true;
        restartPreview();
        say(said, false);
    }

    private void replaceRiff(Riff next, Component said) {
        undoRiff = riff;
        undoSong = null;
        stopPreview();
        riff = next;
        dirty = true;
        say(said, false);
    }

    /** Puts back what the last preset, import or undo replaced; an undo is itself undone the same way. */
    private void undo() {
        if (undoSong != null) {
            tab = ScoreLayout.Tab.SONG;
            replaceSong(undoSong, Component.translatable("score.magical.undone"));
        } else if (undoRiff != null) {
            tab = ScoreLayout.Tab.RIFF;
            replaceRiff(undoRiff, Component.translatable("score.magical.undone"));
        } else {
            say(Component.translatable("score.magical.nothing_to_undo"), true);
        }
    }

    // ---------------------------------------------------------------- preview and saving

    private long now() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    private int previewStep() {
        if (previewStart < 0 || tab != ScoreLayout.Tab.SONG) {
            return -1;
        }
        long index = (now() - previewStart) / song.tempo().ticksPerStep();
        return (int) Math.floorMod(index, (long) Score.STEPS);
    }

    private void togglePreview() {
        if (previewStart >= 0) {
            stopPreview();
        } else {
            previewStart = now();
            previewLast = -1L;
        }
    }

    private void restartPreview() {
        if (previewStart >= 0) {
            previewStart = now();
            previewLast = -1L;
        }
    }

    private void stopPreview() {
        previewStart = -1L;
    }

    @Override
    public void tick() {
        super.tick();
        if (messageTicks > 0) {
            messageTicks--;
        }
        if (previewStart < 0 || minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        long elapsed = now() - previewStart;
        if (tab == ScoreLayout.Tab.SONG) {
            long index = elapsed / song.tempo().ticksPerStep();
            for (long step = previewLast + 1; step <= index; step++) {
                int wrapped = (int) Math.floorMod(step, (long) Score.STEPS);
                ClientSongs.playStep(minecraft.level, minecraft.player.position(), song, wrapped, 0.7F, false);
                int shownPage = wrapped / layout.visibleSteps();
                if (layout.pages() > 1 && shownPage != page && wrapped % layout.visibleSteps() == 0) {
                    page = shownPage;
                }
            }
            previewLast = index;
        } else {
            long index = elapsed / Riff.STEP_TICKS;
            for (long note = previewLast + 1; note <= index; note++) {
                int slot = riff.slotAt(note);
                if (riff.instrument(slot) != null) {
                    sound(riff.instrument(slot), riff.pitch(slot));
                }
            }
            previewLast = index;
        }
    }

    private void sound(Instrument instrument, int pitch) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        minecraft.level.playLocalSound(minecraft.player.getX(), minecraft.player.getEyeY(), minecraft.player.getZ(),
                MagicalSounds.note(instrument).get(), SoundSource.PLAYERS, 0.5F, Riff.pitchRate(pitch), false);
    }

    private void save() {
        CompoundTag data = new CompoundTag();
        data.put("song", SoundState.saveSong(song));
        data.put("riff", SoundState.saveRiff(riff));
        MagicalNetwork.sendSound(data);
        dirty = false;
        say(Component.translatable("score.magical.saved_message"), false);
    }

    private void say(Component text, boolean warn) {
        message = text;
        warning = warn;
        messageTicks = MESSAGE_TICKS;
    }

    @Override
    public void removed() {
        if (dirty) {
            save();
        }
        super.removed();
    }
}
