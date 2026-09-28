package com.efkrdnz.magical.magic.mind;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public final class ReverieNbt {
    private ReverieNbt() {}

    public static CompoundTag save(Reverie reverie) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", reverie.name());
        tag.putInt("facing", reverie.facing());
        ListTag blocks = new ListTag();
        for (ImaginedBlock block : reverie.blocks()) {
            CompoundTag entry = at(block.at());
            entry.putString("id", block.blockId());
            entry.putInt("senses", Sense.mask(block.senses()));
            blocks.add(entry);
        }
        tag.put("blocks", blocks);
        ListTag figments = new ListTag();
        for (Figment figment : reverie.figments()) {
            CompoundTag entry = at(figment.at());
            entry.putString("id", figment.creatureId());
            entry.putString("stance", figment.script().stance().name());
            entry.putString("reaction", figment.script().reaction().name());
            entry.putInt("senses", Sense.mask(figment.senses()));
            figments.add(entry);
        }
        tag.put("figments", figments);
        return tag;
    }

    public static Reverie load(CompoundTag tag) {
        Reverie reverie = new Reverie();
        reverie.setName(tag.getString("name"));
        reverie.setFacing(tag.getInt("facing"));
        for (Tag raw : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            if (reverie.size() >= Lexicon.MAX_BUDGET) {
                break;
            }
            CompoundTag entry = (CompoundTag) raw;
            reverie.put(new ImaginedBlock(offset(entry), entry.getString("id"),
                    Sense.fromMask(entry.getInt("senses"))));
        }
        for (Tag raw : tag.getList("figments", Tag.TAG_COMPOUND)) {
            if (reverie.size() >= Lexicon.MAX_BUDGET) {
                break;
            }
            CompoundTag entry = (CompoundTag) raw;
            Script script = new Script(named(Stance.class, entry.getString("stance"), Stance.IDLE),
                    named(Reaction.class, entry.getString("reaction"), Reaction.IGNORE));
            reverie.put(new Figment(offset(entry), entry.getString("id"), script,
                    Sense.fromMask(entry.getInt("senses"))));
        }
        reverie.normaliseSenses();
        return reverie;
    }

    private static CompoundTag at(Offset offset) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("x", offset.dx());
        entry.putInt("y", offset.dy());
        entry.putInt("z", offset.dz());
        return entry;
    }

    private static Offset offset(CompoundTag entry) {
        return new Offset(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
    }

    private static <E extends Enum<E>> E named(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException missing) {
            return fallback;
        }
    }
}
