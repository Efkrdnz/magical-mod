package com.efkrdnz.magical.client.mind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The creative tabs, filtered to what the wielder has learned. Pure: a tab is an id and the
 * impression keys that live in it, so the Daydream lexicon screen can shelve names it never has
 * to turn into items.
 */
public final class LexiconShelves {
    /** The id of the shelf that holds what no tab does. */
    public static final String OTHER = "other";

    private LexiconShelves() {
    }

    /** A tab's registry id (or {@code "search"} / {@code "other"}) and the keys shelved on it. */
    public record Shelf(String id, List<String> keys) {
    }

    /**
     * Each tab keeps its known keys, once each and in its own order; a tab left empty is dropped;
     * whatever is known and in no tab goes last, sorted, on an "other" shelf.
     */
    public static List<Shelf> shelve(List<Shelf> tabs, Set<String> known) {
        List<Shelf> shelves = new ArrayList<>();
        Set<String> shelved = new HashSet<>();
        for (Shelf tab : tabs) {
            Set<String> kept = new LinkedHashSet<>();
            for (String key : tab.keys()) {
                if (known.contains(key)) {
                    kept.add(key);
                }
            }
            shelved.addAll(kept);
            if (!kept.isEmpty()) {
                shelves.add(new Shelf(tab.id(), List.copyOf(kept)));
            }
        }
        Set<String> rest = new TreeSet<>();
        for (String key : known) {
            if (!shelved.contains(key)) {
                rest.add(key);
            }
        }
        // Keys held by a tab but not known are never in the rest, and a known key in some tab is
        // in shelved, so the rest is exactly what no tab holds.
        if (!rest.isEmpty()) {
            shelves.add(new Shelf(OTHER, List.copyOf(rest)));
        }
        return shelves;
    }

    /** Known keys whose name holds the query, any case; a blank query is all of them. By name, then key. */
    public static List<String> search(Collection<String> known, Function<String, String> nameOf, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String key : known) {
            if (needle.isEmpty() || nameOf.apply(key).toLowerCase(Locale.ROOT).contains(needle)) {
                found.add(key);
            }
        }
        found.sort(Comparator.comparing((String key) -> nameOf.apply(key)).thenComparing(Comparator.naturalOrder()));
        return found;
    }
}
