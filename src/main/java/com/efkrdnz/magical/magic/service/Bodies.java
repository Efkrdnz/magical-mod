package com.efkrdnz.magical.magic.service;

import com.efkrdnz.magical.entity.mind.FigmentEntity;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;

/**
 * Every sweep of the world for bodies, outside the Mind's own code, asks here. A figment is a real,
 * tracked mob - it has to be, or a husk that believes it could not hunt it - so a plain
 * {@code getEntities} hands it back like any other, and a spell would aim at it, count it, shove it,
 * set it alight or carry it through a portal: all of it drawn for everyone and none of it there. This
 * is the one place that says no, so a new skill cannot forget to. {@code BodiesSweepTest} reads the
 * source and fails on any untyped sweep that goes round it.
 */
public final class Bodies {
    private Bodies() {}

    /** Whether an entity is a body to anything outside a mind: everything but a figment. */
    public static boolean isBody(Entity entity) {
        return !FigmentEntity.isFigment(entity);
    }

    /** {@link EntityGetter#getEntities(Entity, AABB, Predicate)}, minus figments. */
    public static List<Entity> around(EntityGetter level, @Nullable Entity except, AABB box,
            Predicate<? super Entity> filter) {
        return level.getEntities(except, box, entity -> isBody(entity) && filter.test(entity));
    }

    /** {@link EntityGetter#getEntities(Entity, AABB)}, minus figments. */
    public static List<Entity> around(EntityGetter level, @Nullable Entity except, AABB box) {
        return level.getEntities(except, box, Bodies::isBody);
    }

    /** {@link EntityGetter#getEntitiesOfClass(Class, AABB, Predicate)}, minus figments. */
    public static <T extends Entity> List<T> of(EntityGetter level, Class<T> type, AABB box,
            Predicate<? super T> filter) {
        return level.getEntitiesOfClass(type, box, entity -> isBody(entity) && filter.test(entity));
    }

    /** {@link EntityGetter#getEntitiesOfClass(Class, AABB)}, minus figments. */
    public static <T extends Entity> List<T> of(EntityGetter level, Class<T> type, AABB box) {
        return level.getEntitiesOfClass(type, box, Bodies::isBody);
    }
}
