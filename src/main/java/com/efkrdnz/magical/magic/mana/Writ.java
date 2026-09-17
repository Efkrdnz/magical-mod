package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * One law, standing in one Ledger.
 *
 * <p>A writ binds either a single spell or a whole school - exactly one of {@code skill} and
 * {@code school} is set and the other is null. That is the escalation the book is worth keeping
 * for: witness one fire spell and you may tax that spell, or you may tax fire.
 *
 * <p>It has no position and no radius. A writ is in force wherever the spell it names is cast, in
 * every dimension, until the hand that wrote it strikes it out.
 */
public record Writ(ResourceLocation skill, MagicSchool school, WritAspect aspect, WritOperation operation,
                   WritSubject subject) {

    /** True when this writ has anything to say about the spell being cast. */
    public boolean covers(MagicSkillDefinition definition) {
        if (definition == null) {
            return false;
        }
        return school != null ? definition.school() == school : definition.id().equals(skill);
    }

    /**
     * True when this writ binds the caster, given who wrote it.
     *
     * <p>ALL includes the author. A wielder who legislates everyone has legislated themselves, and
     * the Ledger does not carve them an exception they never asked for.
     */
    public boolean binds(UUID author, UUID caster) {
        boolean self = author != null && author.equals(caster);
        return switch (subject) {
            case ALL -> true;
            case MINE -> self;
            case THEIRS -> !self;
        };
    }

    /** Two writs share a line of the book when they bind the same aspect of the same target. */
    public boolean sameLineAs(Writ other) {
        return other != null && aspect == other.aspect
                && school == other.school
                && Objects.equals(skill, other.skill);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (skill != null) {
            tag.putString("Skill", skill.toString());
        }
        if (school != null) {
            tag.putString("School", school.name());
        }
        tag.putString("Aspect", aspect.name());
        tag.putString("Operation", operation.name());
        tag.putString("Subject", subject.name());
        return tag;
    }

    /** Null when the tag names an aspect, operation, subject or school this build no longer has. */
    public static Writ load(CompoundTag tag) {
        WritAspect aspect = byName(WritAspect.values(), tag.getString("Aspect"));
        WritOperation operation = byName(WritOperation.values(), tag.getString("Operation"));
        WritSubject subject = byName(WritSubject.values(), tag.getString("Subject"));
        if (aspect == null || operation == null || subject == null) {
            return null;
        }
        ResourceLocation skill = tag.contains("Skill") ? ResourceLocation.tryParse(tag.getString("Skill")) : null;
        MagicSchool school = tag.contains("School") ? byName(MagicSchool.values(), tag.getString("School")) : null;
        if (skill == null && school == null) {
            return null;
        }
        return new Writ(skill, school, aspect, operation, subject);
    }

    private static <T extends Enum<T>> T byName(T[] values, String name) {
        for (T value : values) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        return null;
    }
}
