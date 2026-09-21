package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/**
 * Bodies that put a block in the world and take it back: sprays along a line, seas over the floor,
 * touches into what is already there, a clod heaped where it lands. The matter and its shape are
 * the prototype's; the verse writes its deltas and adds the body, exactly as a static does, so the
 * evaluator never learns a new rule. A material marks the press as having produced a body, which
 * is what spends the charges of the hand ({@link VerseType#spawnsBodies}).
 */
public final class MaterialVerses {

    public static final ResourceLocation SPRAY_WATER = VerseIds.of("spray_water");
    public static final ResourceLocation SPRAY_FLAME = VerseIds.of("spray_flame");
    public static final ResourceLocation SEA_WATER = VerseIds.of("sea_water");
    public static final ResourceLocation SEA_LAVA = VerseIds.of("sea_lava");
    public static final ResourceLocation TOUCH_STONE = VerseIds.of("touch_stone");
    public static final ResourceLocation CLOD = VerseIds.of("clod");

    private MaterialVerses() {
    }

    /** Deltas onto the state, then the body, as every leaf does. */
    static Verse material(String path, int mana, int uses, VersePrototype prototype, int beat, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.MATERIAL, mana, uses, prototype, 1, Declared.of(0, beat, 0), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            effect.accept(s);
            r.addProjectile(prototype);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        // A spray arcs like a hose: its gravity is its own, written before the body as a Sink writes it.
        c.register(material("spray_water", 6, Verse.UNLIMITED, VersePrototypes.SPRAY_WATER, 2, s -> {
            s.addSpread(3.0D);
            s.addGravity(0.02D);
        }));
        // The burn is the prototype's, as an Ember's is, so it never reaches the verses behind it.
        c.register(material("spray_flame", 9, 20, VersePrototypes.SPRAY_FLAME, 2, s -> {
            s.addSpread(3.0D);
            s.addGravity(0.02D);
            s.addRecoil(10.0D);
        }));
        c.register(material("sea_water", 12, Verse.UNLIMITED, VersePrototypes.SEA_WATER, 5, s -> { }));
        c.register(material("sea_flame", 16, 15, VersePrototypes.SEA_FLAME, 5, s -> { }));
        // The accident: a Void Pit's beat, three uses, and it does not care whose feet it is at.
        c.register(material("sea_lava", 45, 3, VersePrototypes.SEA_LAVA, 27, s -> s.addScreenshake(1.0D)));
        c.register(material("touch_stone", 14, Verse.UNLIMITED, VersePrototypes.TOUCH_STONE, 7, s -> { }));
        c.register(material("touch_glass", 8, Verse.UNLIMITED, VersePrototypes.TOUCH_GLASS, 5, s -> { }));
        c.register(material("touch_water", 14, 10, VersePrototypes.TOUCH_WATER, 7, s -> { }));
        c.register(material("touch_ice", 12, Verse.UNLIMITED, VersePrototypes.TOUCH_ICE, 7, s -> { }));
        c.register(material("clod", 5, Verse.UNLIMITED, VersePrototypes.CLOD, 3, s -> s.addGravity(0.03D)));
    }
}
