package com.warlonmhite.hempdustry.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.component.StashContents;
import net.minecraft.advancements.criterion.CollectionContentsPredicate;
import net.minecraft.advancements.criterion.CollectionPredicate;
import net.minecraft.advancements.criterion.ItemPredicate;
import net.minecraft.advancements.criterion.SingleComponentItemPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An item predicate on what a Stash Jar holds ({@code "hempdustry:stash_contents"} in an item predicate's
 * {@code predicates}), in the shape of vanilla's {@code minecraft:bundle_contents}: {@code items} is the
 * same collection predicate ({@code contains}, {@code count}, {@code size}) over the jar's entries.
 *
 * <p>Each entry is tested as one stack of its item holding the entry's whole count, so {@code count} in an
 * item predicate reads how many buds of that kind the jar holds, whatever tiers and bands they blend. An
 * entry carries its chemotype and anything else its stack kept, so a component predicate on an entry reads
 * those too. An empty jar carries no {@code stash_contents} at all, so it never matches, as an item with no
 * bundle contents never matches vanilla's.
 */
public record StashContentsPredicate(Optional<CollectionPredicate<ItemStack, ItemPredicate>> items)
        implements SingleComponentItemPredicate<StashContents> {

    public static final Codec<StashContentsPredicate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CollectionPredicate.<ItemStack, ItemPredicate>codec(ItemPredicate.CODEC).optionalFieldOf("items")
                    .forGetter(StashContentsPredicate::items)
    ).apply(instance, StashContentsPredicate::new));

    /** A jar holding at least one item matching each of {@code items}. */
    public static StashContentsPredicate holding(ItemPredicate... items) {
        return new StashContentsPredicate(Optional.of(new CollectionPredicate<>(
                Optional.of(CollectionContentsPredicate.of(items)), Optional.empty(), Optional.empty())));
    }

    @Override
    public DataComponentType<StashContents> componentType() {
        return ModComponents.STASH_CONTENTS;
    }

    @Override
    public boolean matches(StashContents contents) {
        if (items.isEmpty()) {
            return true;
        }
        List<ItemStack> held = new ArrayList<>(contents.size());
        for (StashContents.Entry entry : contents.entries()) {
            ItemStack stack = entry.item().copy();
            stack.setCount(entry.count());
            held.add(stack);
        }
        return items.get().test(held);
    }
}
