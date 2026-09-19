package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Draw k, then (for a formation) write the pattern: the Lua draws first and sets the pattern after. */
public final class MulticastVerses {

    public static final ResourceLocation COUPLET = VerseIds.of("couplet");
    public static final ResourceLocation EPIC = VerseIds.of("epic");

    private MulticastVerses() {
    }

    static Verse multicast(String path, int mana, int uses, int draw, Consumer<ShotState> after) {
        return Verse.of(path, VerseType.MULTICAST, mana, uses, null, 1, Declared.of(draw, 0, 0), (r, rec, it) -> {
            r.drawActions(draw);
            after.accept(r.state());
            return VerseAction.NONE;
        });
    }

    static void register(VerseCatalogue c) {
        c.register(multicast("couplet", 0, Verse.UNLIMITED, 2, s -> { }));
        c.register(multicast("tercet", 2, Verse.UNLIMITED, 3, s -> { }));
        c.register(multicast("quatrain", 4, Verse.UNLIMITED, 4, s -> { }));
        c.register(multicast("octave", 12, Verse.UNLIMITED, 8, s -> { }));
        c.register(multicast("loose_couplet", 0, Verse.UNLIMITED, 2, s -> s.addSpread(10.0D)));
        c.register(multicast("loose_tercet", 1, Verse.UNLIMITED, 3, s -> s.addSpread(20.0D)));
        c.register(multicast("cleft", 2, Verse.UNLIMITED, 2, s -> { s.setPattern(45.0D); s.addSpread(-8.0D); }));
        c.register(multicast("trident", 3, Verse.UNLIMITED, 3, s -> { s.setPattern(20.0D); s.addSpread(-5.0D); }));
        c.register(multicast("mirror", 0, Verse.UNLIMITED, 2, s -> { s.setPattern(180.0D); s.addSpread(-5.0D); }));
        c.register(multicast("column", 3, Verse.UNLIMITED, 3, s -> { s.setPattern(90.0D); s.addSpread(-8.0D); }));
        c.register(multicast("pentacle", 5, Verse.UNLIMITED, 5, s -> { s.setPattern(180.0D); s.addSpread(-12.0D); }));
        c.register(multicast("hexad", 6, Verse.UNLIMITED, 6, s -> { s.setPattern(180.0D); s.addSpread(-15.0D); }));
        // BURST_X: draw whatever is left in the unread pile. The Lua guards on #deck > 0, so on an
        // empty pile nothing is drawn at all and drawManyCount keeps whatever the last draw set.
        c.register(Verse.of("epic", VerseType.MULTICAST, 20, 10, null, 1, Declared.of(Declared.ALL, 0, 0), (r, rec, it) -> {
            if (!r.deck().isEmpty()) {
                r.drawActions(r.deck().size());
            }
            return VerseAction.NONE;
        }));
    }
}
