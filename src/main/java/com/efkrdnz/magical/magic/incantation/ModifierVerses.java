package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Every modifier writes the state and draws one: {@code draw_actions(1, true)} is the whole of its grammar. */
public final class ModifierVerses {

    public static final ResourceLocation WEIGHT = VerseIds.of("weight");
    public static final ResourceLocation HASTE = VerseIds.of("haste");
    public static final ResourceLocation UNDYING = VerseIds.of("undying");
    public static final ResourceLocation SECOND_WIND = VerseIds.of("second_wind");

    private ModifierVerses() {
    }

    static Verse modifier(String path, int mana, int uses, int beat, int rest, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.MODIFIER, mana, uses, null, 1, Declared.of(1, beat, rest), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            effect.accept(s);
            r.drawActions(1);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(modifier("weight", 3, Verse.UNLIMITED, 2, 0, s -> {
            s.addDamage(2.5D);
            s.addRecoil(10.0D);
        }));
        c.register(modifier("haste", 2, Verse.UNLIMITED, 0, 0, s -> s.multiplySpeed(2.5D)));
        c.register(modifier("undying", 20, 3, 4, 0, s -> s.behaviour(Behaviour.UNDYING)));
        c.register(modifier("second_wind", 5, Verse.UNLIMITED, -3, -7, s -> { }));
        // RANDOM_MODIFIER: a known modifier verse, which does the drawing.
        c.register(Verse.of("wild_mark", VerseType.MODIFIER, 6, Verse.UNLIMITED, null, 1, Declared.of(1, 0, 0), (r, rec, it) -> {
            ControlVerses.wild(r, rec, VerseType.MODIFIER);
            return VerseAction.NONE;
        }).asRecursive());
    }
}
