package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.incantation.Verse.Declared;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;

/** Draw k, then (for a formation) write the pattern: the Lua draws first and sets the pattern after. */
public final class MulticastVerses {

    public static final ResourceLocation COUPLET = VerseIds.of("couplet");

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
    }
}
