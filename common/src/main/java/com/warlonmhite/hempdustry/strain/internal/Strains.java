package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

/** How the mod finds the strain behind a block, and the strain's looks that fall back on something else. */
public final class Strains {
    private Strains() {
    }

    /**
     * The strain {@code block} is a plant of: the one whose seeds plant it, or whose wild flower it is.
     * Empty for a plant no strain names (Industrial Hemp is fibre, not a smoking strain).
     *
     * <p>When a datapack strain names the same seeds or flower as one of the mod's, the plant stays the
     * mod's own, then the lowest id, as {@link Strain#fromBuds} rules: a datapack's {@code aaa:kush} reusing
     * Purple Kush's seeds must not hand every Purple Kush plant its odds, while the plant's loot still
     * stamps by its own table.
     *
     * <p>A block answers for the named strains only: a bred plant (the breeding update) keeps its strain on its
     * record, not in its block.
     */
    public static Optional<Holder.Reference<Strain>> ofPlant(Block block, HolderLookup.Provider registries) {
        return Strain.registry(registries).listElements().filter(strain -> {
            Strain value = strain.value();
            return value.seedItem().filter(seed -> seed instanceof BlockItem blockItem
                    && blockItem.getBlock() == block).isPresent()
                    || value.wildFlower().filter(flower -> flower == block).isPresent();
        }).min(Comparator.comparing((Holder.Reference<Strain> entry) -> !entry.key().identifier().getNamespace().equals(Hempdustry.MOD_ID))
                .thenComparing(entry -> entry.key().identifier()));
    }

    /** The colour of {@code strain}'s buds' pistils in a Stash Jar, RGB: its {@code jar_color}, else its own colour. */
    public static int jarColour(Strain strain) {
        return strain.traits().jarColour().orElse(strain.color());
    }
}
