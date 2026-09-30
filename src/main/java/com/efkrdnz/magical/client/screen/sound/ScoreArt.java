package com.efkrdnz.magical.client.screen.sound;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.sound.Family;
import com.efkrdnz.magical.magic.sound.Track;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The Score's sprites: one 128x128 greyscale atlas drawn by {@code scripts/score-art.py}, tinted per
 * draw so a bead, a clef or a button takes its track's or its family's colour. The coordinates here
 * mirror that script's {@code LAYOUT} table; {@code ScoreArtTest} holds the two together.
 */
public final class ScoreArt {

    public static final ResourceLocation ATLAS = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "textures/gui/score/atlas.png");
    public static final int ATLAS_SIZE = 128;

    /** A rectangle of the atlas. */
    public record Sprite(String name, int u, int v, int w, int h) {}

    public static final Sprite BEAD_CIRCLE_12 = new Sprite("bead_circle_12", 0, 0, 12, 12);
    public static final Sprite BEAD_DIAMOND_12 = new Sprite("bead_diamond_12", 12, 0, 12, 12);
    public static final Sprite BEAD_STAR_12 = new Sprite("bead_star_12", 24, 0, 12, 12);
    public static final Sprite SOCKET_12 = new Sprite("socket_12", 36, 0, 12, 12);
    public static final Sprite SOCKET_BEAT_12 = new Sprite("socket_beat_12", 48, 0, 12, 12);
    public static final Sprite BEAD_CIRCLE_8 = new Sprite("bead_circle_8", 0, 12, 8, 8);
    public static final Sprite BEAD_DIAMOND_8 = new Sprite("bead_diamond_8", 8, 12, 8, 8);
    public static final Sprite BEAD_STAR_8 = new Sprite("bead_star_8", 16, 12, 8, 8);
    public static final Sprite SOCKET_8 = new Sprite("socket_8", 24, 12, 8, 8);
    public static final Sprite SOCKET_BEAT_8 = new Sprite("socket_beat_8", 32, 12, 8, 8);

    public static final Sprite DRUM = new Sprite("drum", 0, 24, 16, 16);
    public static final Sprite BASS_CLEF = new Sprite("bass_clef", 16, 24, 16, 16);
    public static final Sprite TREBLE_CLEF = new Sprite("treble_clef", 32, 24, 16, 16);
    public static final Sprite WAVES = new Sprite("waves", 48, 24, 16, 16);
    public static final Sprite KEYS = new Sprite("keys", 64, 24, 16, 16);
    public static final Sprite BELL = new Sprite("bell", 80, 24, 16, 16);
    public static final Sprite LYRE = new Sprite("lyre", 96, 24, 16, 16);
    public static final Sprite QUARTER = new Sprite("quarter", 112, 24, 16, 16);
    public static final Sprite BOLT_NOTE = new Sprite("bolt_note", 0, 40, 16, 16);

    public static final Sprite PLAY = new Sprite("play", 16, 40, 12, 12);
    public static final Sprite STOP = new Sprite("stop", 28, 40, 12, 12);
    public static final Sprite SEAL = new Sprite("seal", 40, 40, 12, 12);
    public static final Sprite METRONOME = new Sprite("metronome", 52, 40, 12, 12);
    public static final Sprite SHARP = new Sprite("sharp", 64, 40, 12, 12);
    public static final Sprite PREV = new Sprite("prev", 76, 40, 12, 12);
    public static final Sprite NEXT = new Sprite("next", 88, 40, 12, 12);
    public static final Sprite REST = new Sprite("rest", 100, 40, 12, 12);

    public static final Sprite FLOURISH = new Sprite("flourish", 0, 56, 128, 9);

    public static final Sprite IMPORT = new Sprite("import", 0, 68, 12, 12);
    public static final Sprite EXPORT = new Sprite("export", 12, 68, 12, 12);
    public static final Sprite SHEET = new Sprite("sheet", 24, 68, 12, 12);

    public static final Sprite[] ALL = {
        BEAD_CIRCLE_12, BEAD_DIAMOND_12, BEAD_STAR_12, SOCKET_12, SOCKET_BEAT_12,
        BEAD_CIRCLE_8, BEAD_DIAMOND_8, BEAD_STAR_8, SOCKET_8, SOCKET_BEAT_8,
        DRUM, BASS_CLEF, TREBLE_CLEF, WAVES, KEYS, BELL, LYRE, QUARTER, BOLT_NOTE,
        PLAY, STOP, SEAL, METRONOME, SHARP, PREV, NEXT, REST, FLOURISH, IMPORT, EXPORT, SHEET
    };

    private ScoreArt() {}

    /** A written note: the kit a drum head, the bass a diamond, the melody a star. */
    public static Sprite bead(Track track, int cell) {
        boolean large = cell >= ScoreLayout.LARGE_CELL;
        return switch (track) {
            case PERCUSSION -> large ? BEAD_CIRCLE_12 : BEAD_CIRCLE_8;
            case BASS -> large ? BEAD_DIAMOND_12 : BEAD_DIAMOND_8;
            case MELODY -> large ? BEAD_STAR_12 : BEAD_STAR_8;
        };
    }

    /** An empty cell; the first sixteenth of every beat is cut a little deeper. */
    public static Sprite socket(boolean beat, int cell) {
        if (cell >= ScoreLayout.LARGE_CELL) {
            return beat ? SOCKET_BEAT_12 : SOCKET_12;
        }
        return beat ? SOCKET_BEAT_8 : SOCKET_8;
    }

    public static Sprite glyph(Track track) {
        return switch (track) {
            case PERCUSSION -> DRUM;
            case BASS -> BASS_CLEF;
            case MELODY -> TREBLE_CLEF;
        };
    }

    public static Sprite glyph(Family family) {
        return switch (family) {
            case DRUMS -> DRUM;
            case LOW -> WAVES;
            case KEYS -> KEYS;
            case BELLS -> BELL;
            case STRINGS -> LYRE;
        };
    }

    /** Draws a sprite at its own size, tinted by an ARGB colour. */
    public static void draw(GuiGraphics graphics, Sprite sprite, int x, int y, int argb) {
        graphics.blit(RenderType::guiTextured, ATLAS, x, y, sprite.u(), sprite.v(), sprite.w(), sprite.h(),
                sprite.w(), sprite.h(), ATLAS_SIZE, ATLAS_SIZE, argb);
    }

    /** Draws a sprite centred on a point. */
    public static void drawCentred(GuiGraphics graphics, Sprite sprite, int cx, int cy, int argb) {
        draw(graphics, sprite, cx - sprite.w() / 2, cy - sprite.h() / 2, argb);
    }
}
