package com.warlonmhite.hempdustry.neoforge;

import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.component.StashContents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

import java.util.List;

/**
 * A placed Stash Jar to NeoForge's transfer API, and so to a hopper (NeoForge moves a hopper's items
 * through the capability when the block is no container) and to every pipe. In: only what
 * {@code #hempdustry:stash_jar_storable} takes, as many as the jar has room for, blended into its
 * strain. Out: newest strain first and its best buds first, the order an empty hand takes them.
 *
 * <p>The handler's indices are the stacks each strain would give back, newest strain first, plus one
 * empty index past them holding the room left: each stack's index holds what it has, and its kind's room
 * left in the slots it fills (on the kind's first stack). The empty
 * index's room is a new kind's, its free slots of 64, or, asked about one resource, what the jar would
 * take of it (a strain's own slot first): a jar takes a new kind only into a free slot, so one with every
 * slot taken has no room for a third strain however few items it holds. The capacities add up to no
 * more than the jar's. An insert lands wherever the jar puts it, whatever
 * index it names: the jar keeps its own order, and the API allows a handler to accept into another
 * index than the one asked. The snapshot is
 * the contents, one immutable value; the world hears of a change once the root transaction commits.
 */
final class NeoForgeStashJarHandler extends SnapshotJournal<StashContents> implements ResourceHandler<ItemResource> {
    private final StashJarBlockEntity jar;
    // The parts of the contents value last asked about. A pipe or a storage bus asks index by index,
    // and the value never changes, only gets replaced, so one layout serves the whole scan.
    private StashContents laidOut;
    private List<ItemStack> parts = List.of();
    private List<Integer> rooms = List.of();

    NeoForgeStashJarHandler(StashJarBlockEntity jar) {
        this.jar = jar;
    }

    private List<ItemStack> parts() {
        StashContents contents = jar.contents();
        if (contents != laidOut) {
            parts = contents.parts();
            rooms = contents.partRooms();
            laidOut = contents;
        }
        return parts;
    }

    @Override
    public int size() {
        return parts().size() + 1;
    }

    @Override
    public ItemResource getResource(int index) {
        List<ItemStack> parts = parts();
        return index >= 0 && index < parts.size() ? ItemResource.of(parts.get(index)) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        List<ItemStack> parts = parts();
        return index >= 0 && index < parts.size() ? parts.get(index).getCount() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        // A stack's index holds what it has and the room its kind has left in its own slots; the last, empty one
        // the room a new kind has, in slots.
        List<ItemStack> parts = parts();
        if (index >= 0 && index < parts.size()) {
            return getAmountAsLong(index) + rooms.get(index);
        }
        return resource.isEmpty() ? (long) jar.contents().freeSlots(jar.capacity()) * StashContents.SLOT
                : jar.contents().mutable().room(resource.toStack(), jar.capacity());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return resource.isEmpty() || StashContents.storable(resource.toStack());
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return insert(resource, amount, transaction);
    }

    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack stack = resource.toStack(amount);
        if (!StashContents.storable(stack)) {
            return 0;
        }
        return jar.insertInTransaction(stack, () -> updateSnapshots(transaction));
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return resource.equals(getResource(index)) ? extract(resource, amount, transaction) : 0;
    }

    @Override
    public int extract(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return jar.extractInTransaction(resource::matches, amount, () -> updateSnapshots(transaction));
    }

    @Override
    protected StashContents createSnapshot() {
        return jar.contents();
    }

    @Override
    protected void revertToSnapshot(StashContents snapshot) {
        jar.setContentsInTransaction(snapshot);
    }

    @Override
    protected void onRootCommit(StashContents original) {
        jar.contentsChanged();
    }
}
