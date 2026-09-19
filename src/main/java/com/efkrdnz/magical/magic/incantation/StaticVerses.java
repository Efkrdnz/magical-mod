package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Bodies that do not move: placed a block ahead of the hand, or at the release point inside a payload. */
public final class StaticVerses {

    public static final ResourceLocation DETONATION = VerseIds.of("detonation");

    private StaticVerses() {
    }

    static Verse stationary(String path, int mana, int uses, VersePrototype prototype, int beat, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.STATIC, mana, uses, prototype, 1, Declared.of(0, beat, 0), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            effect.accept(s);
            r.addProjectile(prototype);
            return VerseAction.NONE;
        });
    }

    static Verse carrier(String path, int mana, int uses, VersePrototype prototype, int beat, PayloadKind kind, int draw) {
        return Verse.of(path, VerseType.STATIC, mana, uses, prototype, 1, Declared.of(draw, beat, 0), (r, rec, it) -> {
            switch (kind) {
                case LATCH -> r.addProjectileLatch(prototype, draw);
                case FUSE -> r.addProjectileFuse(prototype, 0, draw);
                default -> r.addProjectileEpitaph(prototype, draw);
            }
            r.state().addBeat(beat);
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(stationary("detonation", 20, Verse.UNLIMITED, VersePrototypes.BURST, 1, s -> s.addScreenshake(1.0D)));
        c.register(carrier("held_word", 8, Verse.UNLIMITED, VersePrototypes.WORD_HELD, 3, PayloadKind.EPITAPH, 3));
    }
}
