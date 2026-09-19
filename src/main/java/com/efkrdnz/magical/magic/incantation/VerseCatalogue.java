package com.efkrdnz.magical.magic.incantation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** An instance registry of verses, in registration order. {@code VerseContent.CATALOGUE} is the real one. */
public final class VerseCatalogue {

    private final Map<ResourceLocation, Verse> verses = new LinkedHashMap<>();

    public Verse register(Verse verse) {
        if (verses.putIfAbsent(verse.id(), verse) != null) {
            throw new IllegalStateException("two verses share " + verse.id());
        }
        return verse;
    }

    /** Null when unknown; the validator turns that into a finding. */
    public Verse get(ResourceLocation id) {
        return verses.get(id);
    }

    public boolean contains(ResourceLocation id) {
        return verses.containsKey(id);
    }

    public Collection<Verse> all() {
        return Collections.unmodifiableCollection(verses.values());
    }

    public List<Verse> ofType(VerseType type) {
        return verses.values().stream().filter(verse -> verse.type() == type).toList();
    }

    public int size() {
        return verses.size();
    }
}
