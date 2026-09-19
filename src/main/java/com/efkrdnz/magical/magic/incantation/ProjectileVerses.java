package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Leaves: a verse that adds a body and draws nothing, unless it carries a payload. */
public final class ProjectileVerses {

    public static final ResourceLocation NEEDLE = VerseIds.of("needle");
    public static final ResourceLocation EMBER = VerseIds.of("ember");

    private ProjectileVerses() {
    }

    /** Deltas onto the state, then the body, exactly as Spark Bolt is written. */
    static Verse projectile(String path, int mana, int uses, VersePrototype prototype, int beat, int rest,
                            Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.PROJECTILE, mana, uses, prototype, 1, Declared.of(0, beat, rest), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            r.addRest(rest);
            effect.accept(s);
            r.addProjectile(prototype);
            return VerseAction.NONE;
        });
    }

    /** A body that carries a payload of {@code draw} verses, released by {@code kind}. */
    static Verse carrier(String path, int mana, int uses, VersePrototype prototype, int beat, PayloadKind kind,
                         int fuseTicks, int draw, Consumer<ShotState> effect) {
        return Verse.of(path, VerseType.PROJECTILE, mana, uses, prototype, 1, Declared.of(draw, beat, 0), (r, rec, it) -> {
            ShotState s = r.state();
            s.addBeat(beat);
            effect.accept(s);
            switch (kind) {
                case LATCH -> r.addProjectileLatch(prototype, draw);
                case FUSE -> r.addProjectileFuse(prototype, fuseTicks, draw);
                case EPITAPH -> r.addProjectileEpitaph(prototype, draw);
                default -> r.addProjectile(prototype);
            }
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(projectile("needle", 4, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, 0, s -> {
            s.addSpread(-1.0D);
            s.addCrit(5.0D);
        }));
        c.register(projectile("ember", 14, 15, VersePrototypes.EMBER, 12, 0, s -> {
            s.addSpread(4.0D);
            s.addRecoil(20.0D);
            s.hitEffect(HitEffect.BURN);
        }));
        c.register(carrier("needle_latch", 6, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.LATCH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("needle_fuse", 6, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.FUSE, 4, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("needle_twin_latch", 8, Verse.UNLIMITED, VersePrototypes.NEEDLE, 1, PayloadKind.LATCH, 0, 2, s -> s.addCrit(5.0D)));
        c.register(projectile("orb", 7, Verse.UNLIMITED, VersePrototypes.ORB, 2, 0, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_latch", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.LATCH, 0, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_fuse", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.FUSE, 8, 1, s -> s.addCrit(5.0D)));
        c.register(carrier("orb_epitaph", 9, Verse.UNLIMITED, VersePrototypes.ORB, 2, PayloadKind.EPITAPH, 0, 1, s -> s.addCrit(5.0D)));
    }
}
