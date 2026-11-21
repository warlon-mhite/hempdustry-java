package com.warlonmhite.hempdustry.block;

import net.minecraft.world.level.gameevent.GameEvent;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.GrowPotBlock;
import com.warlonmhite.hempdustry.block.custom.HydroTrayBlock;
import com.warlonmhite.hempdustry.block.entity.custom.InfuserBlockEntity;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.util.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * A dispenser pours milk into an Infuser in front of it, and keeps the empty bucket — and, below, fills
 * a Hydro Tray and waters a Grow Pot the same way.
 *
 * <p>This is the Infuser's milk automation. Milk used to go in through a slot a hopper could feed;
 * it is poured by hand now, and the vanilla way to automate "use this item on that block" is a
 * dispenser — it charges a respawn anchor with glowstone, fills bottles at a beehive, and scoops
 * fluid into buckets it then keeps. A comparator behind the tub reads 0 once a batch is collected,
 * so a dispenser can refill it on its own.
 *
 * <p><b>Refusing keeps the bucket.</b> A full tub, or milk with no recipe to go into, fails with
 * the dispenser's failure click and the milk stays put — spitting it out in front of a busy tub
 * would be the worse outcome.
 *
 * <p><b>Registered per item, so only vanilla's milk and hemp milk get it.</b> The tub accepts all of
 * {@code #hempdustry:milk_buckets} by hand, but dispenser behaviours are keyed by item and a tag's
 * contents are not known at startup. Anything not facing an Infuser falls through to whatever the
 * item did before, so another mod's milk behaviour is wrapped rather than replaced.
 */
public final class ModDispenserBehaviors {
    private static final OptionalDispenseItemBehavior POUR_INTO_INFUSER = new OptionalDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource pointer, ItemStack stack) {
            InfuserBlockEntity infuser = infuserInFront(pointer);
            ItemStack empty = InfuserBlockEntity.emptiedContainer(stack);
            this.setSuccess(infuser != null && infuser.fill(stack, null));
            return this.isSuccess() ? this.consumeWithRemainder(pointer, stack, empty) : stack;
        }
    };

    /**
     * A dispenser fills a Hydro Tray in front of it and keeps the empty bucket — the tray's own
     * refill, automated the vanilla way, exactly as the Infuser's milk is above.
     *
     * <p>It wraps vanilla's water-bucket behaviour rather than replacing it: anything that is not a
     * fillable tray still places water as it always did.
     */
    private static final OptionalDispenseItemBehavior FILL_HYDRO_TRAY = new OptionalDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource pointer, ItemStack stack) {
            BlockPos front = pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING));
            Level world = pointer.level();
            this.setSuccess(HydroTrayBlock.fill(world, front, world.getBlockState(front)));
            return this.isSuccess()
                    ? this.consumeWithRemainder(pointer, stack, new ItemStack(Items.BUCKET))
                    : stack;
        }
    };

    /**
     * A dispenser pours a water bottle into a Grow Pot in front of it and keeps the glass bottle — the
     * pot's drip line. It waters a wet pot too, exactly as a hand does, so a line that fires on a timer
     * overwaters. Vanilla's own bottle behaviour (mud) is wrapped, so anything else in front still gets it.
     */
    private static final OptionalDispenseItemBehavior WATER_GROW_POT = new OptionalDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource pointer, ItemStack stack) {
            BlockPos front = pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING));
            Level world = pointer.level();
            GrowPotBlock.water(world, front, world.getBlockState(front), world.getRandom());
            this.setSuccess(true);
            return this.consumeWithRemainder(pointer, stack, new ItemStack(Items.GLASS_BOTTLE));
        }
    };

    private ModDispenserBehaviors() {
    }

    public static void registerDispenserBehaviors() {
        pourIntoInfusers(Items.MILK_BUCKET);
        pourIntoInfusers(ModItems.HEMP_MILK_BUCKET);
        fillHydroTrays();
        waterGrowPots();
        trimHemp();
    }

    private static void trimHemp() {
        trimWith(Items.SHEARS);
        // A hand trims with anything in #c:tools/shear, so another mod's shears work too. A dispenser's behaviours
        // are keyed by item, and the tag is known only once a world's data is loaded, after every mod has
        // registered its own: so each pair in the tag that a dispenser already uses is wrapped then. A pair a
        // dispenser only throws out shears no sheep in vanilla either, and is left as it is.
        Services.EVENTS.onServerStarted(server -> BuiltInRegistries.ITEM.getTagOrEmpty(ModTags.Conventional.SHEAR_TOOLS)
                .forEach(shears -> {
                    if (DispenserBlock.DISPENSER_REGISTRY.containsKey(shears.value())) {
                        trimWith(shears.value());
                    }
                }));
    }

    /**
     * Wraps the dispenser behaviour of {@code shears} (vanilla's shears sheep, harvests beehives and cuts leashes),
     * once, so only a hemp plant in a trim window it has not used is ours, and everything else is sheared as before.
     */
    private static void trimWith(Item shears) {
        if (!TRIMMING.add(shears)) {
            return;
        }
        DispenseItemBehavior previous = DispenserBlock.DISPENSER_REGISTRY.getOrDefault(shears, new DefaultDispenseItemBehavior());
        DispenserBlock.registerBehavior(shears, (pointer, stack) -> stack.is(ModTags.Conventional.SHEAR_TOOLS)
                && Defoliation.dispenserCanTrim(pointer.level(), pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING)))
                ? TRIM_HEMP.dispense(pointer, stack)
                : previous.dispense(pointer, stack));
    }

    /** Whether a dispenser holding {@code shears} trims hemp: a pair whose behaviour is wrapped. */
    public static boolean trimsWith(Item shears) {
        return TRIMMING.contains(shears);
    }

    private static final Set<Item> TRIMMING = Collections.newSetFromMap(new IdentityHashMap<>());

    /** A trim, as a player's shears give it: the dispenser's click, and a shearing for the sculk to hear. */
    private static final OptionalDispenseItemBehavior TRIM_HEMP = new OptionalDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource pointer, ItemStack stack) {
            BlockPos front = pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING));
            boolean trimmed = Defoliation.trimByDispenser(pointer.level(), front, stack);
            this.setSuccess(trimmed);
            if (trimmed) {
                pointer.level().gameEvent(null, GameEvent.SHEAR, front);
            }
            return stack;
        }
    };

    private static void pourIntoInfusers(Item milk) {
        // Nobody registers anything for milk in vanilla, so this is normally the plain "drop it"
        // behaviour. Spelled out rather than trusting the map's own default: 1.21.1's map has one,
        // 1.21.11's does not and answers null.
        DispenseItemBehavior previous = DispenserBlock.DISPENSER_REGISTRY.getOrDefault(milk, new DefaultDispenseItemBehavior());
        DispenserBlock.registerBehavior(milk, (pointer, stack) -> infuserInFront(pointer) != null
                ? POUR_INTO_INFUSER.dispense(pointer, stack)
                : previous.dispense(pointer, stack));
    }

    private static void fillHydroTrays() {
        // Vanilla does register a behaviour for a water bucket (it places the fluid), so this wraps
        // that one: only a tray with room takes the pour, and everything else behaves as before.
        DispenseItemBehavior previous =
                DispenserBlock.DISPENSER_REGISTRY.getOrDefault(Items.WATER_BUCKET, new DefaultDispenseItemBehavior());
        DispenserBlock.registerBehavior(Items.WATER_BUCKET, (pointer, stack) -> fillableTrayInFront(pointer)
                ? FILL_HYDRO_TRAY.dispense(pointer, stack)
                : previous.dispense(pointer, stack));
    }

    private static void waterGrowPots() {
        // Vanilla registers the potion's behaviour (a water bottle turns dirt to mud); wrap it.
        DispenseItemBehavior previous = DispenserBlock.DISPENSER_REGISTRY.getOrDefault(Items.POTION, new DefaultDispenseItemBehavior());
        DispenserBlock.registerBehavior(Items.POTION, (pointer, stack) -> GrowPotBlock.isWaterBottle(stack)
                && GrowPotBlock.isPot(pointer.level().getBlockState(
                        pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING))))
                ? WATER_GROW_POT.dispense(pointer, stack)
                : previous.dispense(pointer, stack));
    }

    private static boolean fillableTrayInFront(BlockSource pointer) {
        BlockPos front = pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING));
        BlockState state = pointer.level().getBlockState(front);
        return state.getBlock() instanceof HydroTrayBlock
                && state.getValue(HydroTrayBlock.LEVEL) < HydroTrayBlock.MAX_LEVEL;
    }

    @Nullable
    private static InfuserBlockEntity infuserInFront(BlockSource pointer) {
        BlockPos front = pointer.pos().relative(pointer.state().getValue(DispenserBlock.FACING));
        return pointer.level().getBlockEntity(front) instanceof InfuserBlockEntity infuser ? infuser : null;
    }
}
