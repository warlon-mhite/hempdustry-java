package com.warlonmhite.hempdustry.test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Runs a test body with a mob's tags changed for the length of the call, and puts them back afterwards. A tag is
 * datapack data, bound once when the data loads; a test that needs a mob out of a tag the mod ships with it, or in
 * one it does not, binds that one holder's tags itself. The body runs inside one call, so no other test ever sees
 * the change.
 */
final class Tagged {
    private Tagged() {
    }

    /** {@code body} with {@code type} out of {@code tag}. */
    static void without(EntityType<?> type, TagKey<EntityType<?>> tag, Runnable body) {
        with(type, tags -> tags.remove(tag), body);
    }

    /** {@code body} with {@code type} in {@code tag}. */
    static void within(EntityType<?> type, TagKey<EntityType<?>> tag, Runnable body) {
        with(type, tags -> tags.add(tag), body);
    }

    private static void with(EntityType<?> type, java.util.function.Consumer<Set<TagKey<EntityType<?>>>> change,
                             Runnable body) {
        Holder.Reference<EntityType<?>> holder = type.builtInRegistryHolder();
        List<TagKey<EntityType<?>>> before = holder.tags().toList();
        Set<TagKey<EntityType<?>>> after = new HashSet<>(before);
        change.accept(after);
        bind(holder, after);
        try {
            body.run();
        } finally {
            bind(holder, before);
        }
    }

    private static void bind(Holder.Reference<EntityType<?>> holder, Collection<TagKey<EntityType<?>>> tags) {
        try {
            Method bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
            bind.setAccessible(true);
            bind.invoke(holder, tags);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not bind a test mob's tags", e);
        }
    }
}
