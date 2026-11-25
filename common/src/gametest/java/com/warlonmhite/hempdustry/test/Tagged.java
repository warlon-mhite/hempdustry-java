package com.warlonmhite.hempdustry.test;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * Runs a test body with a mob's or a block's tags changed for the length of the call, and puts them back afterwards.
 * A tag is datapack data, bound once when the data loads; a test that needs a mob or a block out of a tag the mod
 * ships with it, or in one it does not, binds that one holder's tags itself. The body runs inside one call, so no
 * other test ever sees the change.
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

    /** {@code body} with {@code block} in {@code tag}. */
    static void within(Block block, TagKey<Block> tag, Runnable body) {
        with(block.builtInRegistryHolder(), tags -> tags.add(tag), body);
    }

    private static void with(EntityType<?> type, java.util.function.Consumer<Set<TagKey<EntityType<?>>>> change,
                             Runnable body) {
        with(type.builtInRegistryHolder(), change, body);
    }

    private static <T> void with(Holder.Reference<T> holder, java.util.function.Consumer<Set<TagKey<T>>> change,
                                 Runnable body) {
        List<TagKey<T>> before = holder.tags().toList();
        Set<TagKey<T>> after = new HashSet<>(before);
        change.accept(after);
        bind(holder, after);
        try {
            body.run();
        } finally {
            bind(holder, before);
        }
    }

    private static <T> void bind(Holder.Reference<T> holder, Collection<TagKey<T>> tags) {
        try {
            Method bind = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
            bind.setAccessible(true);
            bind.invoke(holder, tags);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not bind a test holder's tags", e);
        }
    }
}
