package com.efkrdnz.magical.magic.mind;

import java.util.Set;

public record ImaginedBlock(Offset at, String blockId, Set<Sense> senses) {
    public ImaginedBlock {
        senses = Set.copyOf(senses);
    }

    public ImaginedBlock withSenses(Set<Sense> next) {
        return new ImaginedBlock(at, blockId, next);
    }
}
