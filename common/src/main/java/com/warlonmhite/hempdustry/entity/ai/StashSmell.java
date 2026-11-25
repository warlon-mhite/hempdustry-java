package com.warlonmhite.hempdustry.entity.ai;

import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.item.custom.StashJarItem;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ContainerComponent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Creepers (the mobs of {@code #hempdustry:drawn_to_stash}) smell a stash: a Stash Jar a hand has just opened
 * draws them ({@link SeekStashJarGoal}), and buds carried loose give their carrier away to a creeper that would
 * otherwise see less of them.
 *
 * <h2>An opened jar</h2>
 *
 * A placed jar smells for {@link #OPEN_TICKS} after a hand took buds out of it, the puff it lets off being
 * the cue: each take-out starts the two minutes again, and putting buds in, a hopper or a pipe never open it.
 * While open it is noticed within {@link #reach} of a creeper, 8 blocks plus 4 per full slot of 64 up to 24
 * (a full Stash Jar 16, a full Gilded jar or anything larger 24), and each check goes for it at
 * {@link #draws} buds in 256, a sure thing from four stacks. Schwag counts as buds: it reeks.
 *
 * <h2>Opened storage</h2>
 *
 * Any other placed block a player opens a menu on lets the smell out the same way, at the jar's numbers, read from
 * the buds inside it at each look ({@link #budsAt}): a chest (a double one is one stash), a barrel, a shulker box, a
 * hopper, a furnace, another mod's crate. The trigger is the jar's: a real player's hand taking buds out through the
 * menu, whichever way they leave the block's slots ({@link #afterClick}); opening, looking and putting buds in let
 * nothing out, and nor do hoppers, pipes and machines. Nothing in the world shows it, no puff and no sound: the
 * Stash Jar's information page says so. Out: the ender chest, whose things are its opener's; drawers and other
 * storage without a menu; a chest on a minecart, a boat or a donkey, opened as an entity.
 *
 * <h2>Loose buds: what counts</h2>
 *
 * Buds and schwag, the things a Stash Jar takes ({@link StashContents#storable}), anywhere in a player's
 * inventory: the hotbar, the offhand, the armour slots, the stack on the cursor, a slot another mod adds for a
 * backpack on the back ({@link WornSlots}: Curios, Trinkets Updated, Traveler's Backpack's own), and inside a bag
 * ({@code #hempdustry:leaks_the_smell}): a bundle, which is cloth, or another mod's backpack, read one level deep
 * off the stack or through the loader's item API ({@link #budsIn}). Bags are listed, never guessed: anything else
 * carried that holds items holds the smell in, a chest, a barrel or a shulker box carried as an item, or a crate of
 * a mod no one listed. A Stash Jar's glass holds it in too, but its contents count while it is open, for
 * {@link #OPEN_TICKS} after a hand last took buds out of it from the player's own inventory or cursor, and then
 * with the loose buds. A jar in a bag stays shut: only the buds loose in the bag leak. Putting buds in opens
 * nothing. Packed smokeables, hash, kief and edibles are none of these and never count.
 *
 * <h2>How far</h2>
 *
 * A creeper notices a player out to its follow range times the player's visibility
 * ({@code TargetingConditions#test}); its follow range is 16 blocks ({@code Mob#createMobAttributes}), and
 * vanilla scales the visibility ({@code LivingEntity#getVisibilityPercent}): 0.8 sneaking, less invisible,
 * 0.5 behind a creeper head. Loose buds multiply that visibility by {@link #SOME_BUDS} (any bud) or
 * {@link #A_STACK} (64 or more), held at full visibility ({@link #visibility}): they never take a creeper's
 * reach past its follow range. Vanilla keeps 8 blocks between the 16 at which a creeper notices a player and
 * the 24 within which no monster spawns ({@code NaturalSpawner}, {@code 576.0} = 24²), and the smell keeps
 * them. What it does is give away a player vanilla sees less of: sneaking, a player carrying any bud is
 * noticed at the full 16; behind a creeper head, at 10 with a bud and 12 with a stack. The creeper still
 * needs to see the player: the smell never lets it target through a wall. Since it never notices a player
 * past its follow range, vanilla's chase, which ends there, needs nothing of it.
 *
 * <p>All of it sits under {@code world.creepersSeekHemp}, the switch that already decides whether hemp
 * draws creepers, and is read at each check, so {@code /hempdustry reload} takes effect at once.
 *
 * <p>Nothing is saved: a carried jar's two minutes are kept on the player, a placed jar's on its block entity
 * and other storage's in a list per level, in memory; a rejoin shuts the carried jars, a restart every stash.
 */
public final class StashSmell {

    /** How long a jar stays open after a hand last took buds out of it: two minutes. */
    public static final int OPEN_TICKS = 2 * 60 * 20;
    /** How much more of a player carrying any loose bud a creeper sees, up to all of them. */
    public static final double SOME_BUDS = 1.25;
    /** How much more of a player carrying a stack of loose buds or more a creeper sees, up to all of them. */
    public static final double A_STACK = 1.5;
    /** How many loose buds count as a stack. */
    public static final int STACK = 64;
    /** How far an opened jar is noticed holding less than a full slot of buds, and at most, in blocks. */
    public static final int NEAREST_REACH = 8;
    public static final int FURTHEST_REACH = 24;
    /** The blocks of reach each full slot of 64 adds. */
    public static final int REACH_PER_SLOT = 4;
    /** Buds in an opened jar for a creeper's check to go for it every time. */
    public static final int SURE_DRAW = 256;

    /**
     * The placed stashes each level holds that a hand opened in the last two minutes, jars and storage blocks, and
     * until when each smells, so a creeper looks at those and never scans the blocks around it. Weakly keyed: a
     * level gone with its server takes its list along. Each position is asked again at every look, and dropped once
     * its stash is shut, gone or unloaded; one emptied for a moment is
     * kept, and smells again if filled before its time runs out.
     */
    private static final Map<Level, Map<BlockPos, Long>> OPENED = new WeakHashMap<>();

    private StashSmell() {
    }

    /**
     * What a player keeps of their smell, in memory only: when their carried jars close again, and the last
     * count, so the creepers around asking in one tick count the inventory once.
     */
    public interface Carrier {
        long hempdustry$carriedJarOpenUntil();

        void hempdustry$setCarriedJarOpenUntil(long gameTime);

        long hempdustry$budsCountedAt();

        int hempdustry$budsCounted();

        void hempdustry$setBudsCounted(long gameTime, int count);

        /** The menu the player last opened by using a block, and that block; null before any. */
        @Nullable AbstractContainerMenu hempdustry$blockMenu();

        BlockPos hempdustry$blockMenuAt();

        void hempdustry$setBlockMenu(AbstractContainerMenu menu, BlockPos pos);
    }

    /**
     * {@code player} used the block at {@code pos}: a menu that use opened is that block's, so buds a hand takes out
     * of it later are taken out of that block ({@link #budsInBlockSlots}). A menu opened any other way (an
     * entity's, an item's, a key's) is no block's and lets no smell out.
     */
    public static void usedBlock(ServerPlayer player, BlockPos pos) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != player.inventoryMenu && player instanceof Carrier carrier && carrier.hempdustry$blockMenu() != menu) {
            carrier.hempdustry$setBlockMenu(menu, pos.immutable());
        }
    }

    /**
     * Before a click in {@code menu}: a copy of the buds in each of its slots that are the block's and not the
     * player's, or null when the click can let no smell out (a menu no block opened, a client, a machine's hand).
     */
    public static ItemStack @Nullable [] budsInBlockSlots(AbstractContainerMenu menu, Player player) {
        if (!(player instanceof ServerPlayer) || !(player instanceof Carrier carrier)
                || carrier.hempdustry$blockMenu() != menu || !isAPlayer(player)) {
            return null;
        }
        ItemStack[] buds = new ItemStack[menu.slots.size()];
        for (int index = 0; index < buds.length; index++) {
            Slot slot = menu.slots.get(index);
            ItemStack stack = slot.getItem();
            if (slot.container != player.getInventory() && StashContents.storable(stack)) {
                buds[index] = stack.copy();
            }
        }
        return buds;
    }

    /**
     * After a click in {@code menu}: if any buds {@link #budsInBlockSlots} saw left their slot (fewer of them, or
     * something else in their place), a hand took them out of the block, and its smell is out. Every way a stack
     * leaves a slot for the player ends here: a click, a shift-click, a number key, a double-click gathering, a
     * drop with Q, a bundle filled from the slot. Putting buds in takes nothing out.
     */
    public static void afterClick(AbstractContainerMenu menu, Player player, ItemStack @Nullable [] before) {
        if (before == null || !(player instanceof Carrier carrier)) {
            return;
        }
        for (int index = 0; index < before.length && index < menu.slots.size(); index++) {
            ItemStack was = before[index];
            ItemStack now = menu.slots.get(index).getItem();
            if (was != null && (!ItemStack.isSameItemSameComponents(was, now) || now.getCount() < was.getCount())) {
                openPlacedStorage((ServerLevel) player.level(), carrier.hempdustry$blockMenuAt(), player);
                return;
            }
        }
    }

    /**
     * Whether {@code mob} is one a stash draws: {@code #hempdustry:drawn_to_stash}, vanilla's creeper by default. It
     * decides who comes to an opened jar ({@link SeekStashJarGoal}) and who notices loose buds from further away.
     */
    public static boolean drawnToStash(Entity mob) {
        return mob.is(ModTags.EntityTypes.DRAWN_TO_STASH);
    }

    /**
     * How much more of {@code player} a creeper sees: 1 with no loose buds or with the switch off,
     * {@link #SOME_BUDS} with any, {@link #A_STACK} with a stack or more; {@link #visibility} holds the result.
     */
    public static double reachFactor(Player player) {
        if (!ModGameRules.Switch.MOBS_DRAWN_TO_HEMP.on(player.level())) {
            return 1.0;
        }
        int buds = looseBuds(player);
        return buds >= STACK ? A_STACK : buds > 0 ? SOME_BUDS : 1.0;
    }

    /**
     * The visibility a creeper reads off {@code player}: vanilla's ({@code vanilla}) times the loose buds'
     * {@link #reachFactor}, held at full visibility so the reach never passes the creeper's follow range, and
     * never below vanilla's own (a loader's hook may have raised it).
     */
    public static double visibility(double vanilla, Player player) {
        return Math.max(vanilla, Math.min(1.0, vanilla * reachFactor(player)));
    }

    /** How many buds {@code player} carries where the air reaches them (see the class comment). */
    public static int looseBuds(Player player) {
        long now = player.level().getGameTime();
        if (player instanceof Carrier carrier && carrier.hempdustry$budsCountedAt() == now) {
            return carrier.hempdustry$budsCounted();
        }
        boolean jarsOpen = carriedJarOpen(player);
        Inventory inventory = player.getInventory();
        int buds = count(player.containerMenu == null ? ItemStack.EMPTY : player.containerMenu.getCarried(), jarsOpen);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            buds += count(inventory.getItem(slot), jarsOpen);
        }
        for (ItemStack worn : WornSlots.of(player)) {
            buds += count(worn, jarsOpen);
        }
        if (player instanceof Carrier carrier) {
            carrier.hempdustry$setBudsCounted(now, buds);
        }
        return buds;
    }

    private static int count(ItemStack stack, boolean jarsOpen) {
        if (stack.isEmpty()) {
            return 0;
        }
        if (StashContents.storable(stack)) {
            return stack.getCount();
        }
        if (stack.getItem() instanceof StashJarItem) {
            return jarsOpen ? StashJarItem.contentsOf(stack).count() : 0;
        }
        // Only a bag leaks; anything else that holds items is a box, and boxes hold the smell in.
        if (!stack.is(ModTags.Items.LEAKS_THE_SMELL)) {
            return 0;
        }
        return budsIn(stack);
    }

    /**
     * The buds loose inside {@code bag}, one level deep: a jar inside it keeps its own smell. Read off any component
     * of the stack that holds items, as vanilla's bundle and container ones do ({@link ContainerComponent}, which
     * is also what another mod's backpack keeps its contents in when it keeps them on the stack); a bag with none
     * is asked through its loader's item API, as a backpack keeping its contents elsewhere answers.
     */
    private static int budsIn(ItemStack bag) {
        int buds = 0;
        boolean onTheStack = false;
        for (TypedDataComponent<?> component : bag.getComponents()) {
            if (component.value() instanceof ContainerComponent<?> inside) {
                onTheStack = true;
                buds += inside.itemCopies().filter(StashContents::storable).mapToInt(ItemStack::getCount).sum();
            }
        }
        if (onTheStack) {
            return buds;
        }
        for (ItemStack inside : Services.TRANSFER.contents(bag)) {
            if (StashContents.storable(inside)) {
                buds += inside.getCount();
            }
        }
        return buds;
    }

    /** Whether the jars {@code player} carries are open: a hand took buds out of one in the last two minutes. */
    public static boolean carriedJarOpen(Player player) {
        return player instanceof Carrier carrier
                && player.level().getGameTime() < carrier.hempdustry$carriedJarOpenUntil();
    }

    /**
     * A hand took buds out of a jar {@code player} carries: their jars count with the loose buds for the next
     * two minutes, the count starting again at each take-out. Only a player the server's list holds opens a
     * jar, so a machine's hand never keeps one smelling.
     */
    public static void openCarriedJar(Player player) {
        if (player.level().isClientSide() || !isAPlayer(player) || !(player instanceof Carrier carrier)) {
            return;
        }
        carrier.hempdustry$setCarriedJarOpenUntil(player.level().getGameTime() + OPEN_TICKS);
        // The jar's contents count from now, not from the next tick.
        carrier.hempdustry$setBudsCounted(Long.MIN_VALUE, 0);
    }

    /**
     * A hand took buds out of the placed jar at {@code pos}: it smells for the next two minutes. Only a player
     * the server's list holds opens it; a machine's hand never keeps a jar smelling with nobody there.
     */
    public static void openPlacedJar(ServerLevel level, BlockPos pos, StashJarBlockEntity jar, Player player) {
        if (!isAPlayer(player)) {
            return;
        }
        jar.letSmellOut(level.getGameTime() + OPEN_TICKS);
        opened(level).put(pos.immutable(), level.getGameTime() + OPEN_TICKS);
    }

    /**
     * A hand took buds out of the placed storage block at {@code pos} (a chest, a barrel, a shulker box, a hopper, a
     * furnace, another mod's crate: any block container with a menu): it smells for the next two minutes, as an
     * opened jar does, with no puff and no sound. Only a player the server's list holds lets it out. A double chest
     * is one stash, kept at one of its halves; an ender chest holds its opener's things, not the block's, and never
     * smells.
     */
    public static void openPlacedStorage(ServerLevel level, BlockPos pos, Player player) {
        if (!isAPlayer(player) || !isStorage(level, pos)) {
            return;
        }
        letSmellOut(level, stashAt(level, pos), level.getGameTime() + OPEN_TICKS);
    }

    /**
     * Lets the smell of the storage block at {@code pos} out until {@code gameTime}, which may be now: a test shuts
     * it that way, as the two minutes running out would.
     */
    public static void letSmellOut(ServerLevel level, BlockPos pos, long gameTime) {
        opened(level).put(pos.immutable(), gameTime);
    }

    /** Whether the block at {@code pos} is storage whose smell a hand can let out (see {@link #openPlacedStorage}). */
    public static boolean isStorage(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && !(entity instanceof StashJarBlockEntity) && !(entity instanceof EnderChestBlockEntity);
    }

    /** The position a stash at {@code pos} is kept at: the block itself, or for a double chest the lower of its two. */
    public static BlockPos stashAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.getBlockState(other).getBlock() instanceof ChestBlock && other.asLong() < pos.asLong()) {
                return other;
            }
        }
        return pos;
    }

    /**
     * How many buds and schwag the stash at {@code pos} holds: a jar's contents; a chest's, both halves of a double
     * one; any other container's slots; and a block that is no vanilla container, as its loader's item API shows it
     * (another mod's storage). Nothing for anything else.
     */
    public static int budsAt(Level level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null) {
            return 0;
        }
        if (entity instanceof StashJarBlockEntity jar) {
            return jar.contents().count();
        }
        BlockState state = level.getBlockState(pos);
        Container container = state.getBlock() instanceof ChestBlock chest
                ? ChestBlock.getContainer(chest, state, level, pos, true)
                : entity instanceof Container own ? own : null;
        int buds = 0;
        if (container != null) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (StashContents.storable(stack)) {
                    buds += stack.getCount();
                }
            }
            return buds;
        }
        for (ItemStack inside : Services.TRANSFER.contents(level, pos)) {
            if (StashContents.storable(inside)) {
                buds += inside.getCount();
            }
        }
        return buds;
    }

    /**
     * Whether the stash at {@code pos} (a jar, or storage a hand let the smell out of) is open and smells: in its two
     * minutes, and holding a bud. Never loads a chunk to answer.
     */
    public static boolean smellsOpen(Level level, BlockPos pos) {
        return stillOpen(level, pos) && budsAt(level, pos) > 0;
    }

    /**
     * Whether the stash at {@code pos} is loaded, still there and in its two minutes, buds or not. A stash emptied
     * for a moment stays open: a hopper or a hand filling it again within the two minutes lets the smell out again.
     */
    private static boolean stillOpen(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        long now = level.getGameTime();
        if (level.getBlockEntity(pos) instanceof StashJarBlockEntity jar) {
            return jar.smells(now);
        }
        Map<BlockPos, Long> opened = OPENED.get(level);
        Long until = opened == null ? null : opened.get(pos);
        return until != null && now < until && isStorage(level, pos);
    }

    private static Map<BlockPos, Long> opened(Level level) {
        return OPENED.computeIfAbsent(level, key -> new HashMap<>());
    }

    /** How far an opened jar holding {@code buds} is noticed: 8 blocks, 4 more per full slot of 64, 24 at most. */
    public static int reach(int buds) {
        return Math.min(FURTHEST_REACH, NEAREST_REACH + REACH_PER_SLOT * (buds / StashContents.SLOT));
    }

    /** One creeper's check on an opened jar holding {@code buds}: it goes for it at buds in 256. */
    public static boolean draws(RandomSource random, int buds) {
        return random.nextInt(SURE_DRAW) < buds;
    }

    /**
     * One check of {@code mob}'s: an opened stash in reach it goes for, or null. Every stash in reach rolls, at any
     * height, and of those that draw it one is taken at random, so no stash shadows another by the order they
     * were opened in; the walk there finds its own way, or as far as there is one.
     */
    public static @Nullable BlockPos noticeOpenedStash(PathfinderMob mob) {
        Map<BlockPos, Long> opened = OPENED.get(mob.level());
        if (opened == null || opened.isEmpty()) {
            return null;
        }
        BlockPos noticed = null;
        int drawing = 0;
        for (Iterator<BlockPos> it = opened.keySet().iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!stillOpen(mob.level(), pos)) {
                it.remove();
                continue;
            }
            int buds = budsAt(mob.level(), pos);
            if (buds <= 0) {
                continue;
            }
            int reach = reach(buds);
            if (mob.position().distanceToSqr(Vec3.atCenterOf(pos)) <= (double) reach * reach
                    && draws(mob.getRandom(), buds) && mob.getRandom().nextInt(++drawing) == 0) {
                noticed = pos;
            }
        }
        return noticed;
    }

    /**
     * Whether {@code player} is one the server's list holds: a person, not a machine's hand. A deployer's fake
     * player can carry its owner's UUID, so the list is asked for the object itself.
     */
    public static boolean isAPlayer(Player player) {
        MinecraftServer server = player.level().getServer();
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
}
