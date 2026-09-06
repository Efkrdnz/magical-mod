package com.efkrdnz.magical.arcane;

import java.util.Set;

public record Race(String id, Set<Affinity> affinities) {
    public static final Race HUMAN = new Race("human", Set.of(Affinity.ARCANE));
    public static final Race ELF = new Race("elf", Set.of(Affinity.ARCANE, Affinity.FIRE, Affinity.WATER));
    public static final Race DWARF = new Race("dwarf", Set.of(Affinity.EARTH, Affinity.METAL));
    public static final Race BEASTKIN = new Race("beastkin", Set.of(Affinity.NATURE));
    public static final Race DEMON = new Race("demon", Set.of(Affinity.SHADOW, Affinity.CHAOS));
    public static final Race CELESTIAL = new Race("celestial", Set.of(Affinity.DIVINE, Affinity.LIGHT));
}
