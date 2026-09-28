package com.efkrdnz.magical.magic.mind;

import java.util.Set;

public record Figment(Offset at, String creatureId, Script script, Set<Sense> senses) {
    public Figment {
        senses = Set.copyOf(senses);
    }

    public Figment withScript(Script next) {
        return new Figment(at, creatureId, next, senses);
    }

    public Figment withSenses(Set<Sense> next) {
        return new Figment(at, creatureId, script, next);
    }
}
