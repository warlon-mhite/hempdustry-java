package com.warlonmhite.hempdustry.entity.ai;

import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.item.custom.StashJarItem;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.Level;
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
 * <h2>Loose buds: what counts</h2>
 *
 * Buds and schwag, the things a Stash Jar takes ({@link StashContents#storable}), anywhere in a player's
 * inventory: the hotbar, the offhand, the armour slots, the stack on the cursor, and inside anything carried that
 * holds items and is no box: a bundle, which is cloth, or another mod's backpack, read one level deep through the
 * loader's item API. A box holds the smell in ({@code #hempdustry:holds_the_smell}): a chest, a barrel or a shulker
 * box carried as an item, and a Stash Jar's glass, whose contents count only while it is open, for
 * {@link #OPEN_TICKS} after a hand last took buds out of it from the player's own inventory or cursor, and then
 * with the loose buds. Putting buds in opens nothing. Packed smokeables, hash, kief and edibles are none of these
 * and never count.
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
 * <p>Nothing is saved: a carried jar's two minutes are kept on the player and a placed jar's on its block
 * entity, in memory; a rejoin or a chunk reloaded shuts the jar again.
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
    /** How far an opened jar is noticed with nothing in it, and at most, in blocks. */
    public static final int NEAREST_REACH = 8;
    public static final int FURTHEST_REACH = 24;
    /** The blocks of reach each full slot of 64 adds. */
    public static final int REACH_PER_SLOT = 4;
    /** Buds in an opened jar for a creeper's check to go for it every time. */
    public static final int SURE_DRAW = 256;

    /**
     * The placed jars each level holds that a hand opened in the last two minutes, so a creeper looks at those
     * and never scans the blocks around it. Weakly keyed: a level gone with its server takes its list along.
     * Each position is asked again at every look, and dropped once its jar is shut, gone or unloaded.
     */
    private static final Map<Level, Set<BlockPos>> OPENED = new WeakHashMap<>();

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
        if (stack.is(ModTags.Items.HOLDS_THE_SMELL)) {
            return 0;
        }
        int buds = 0;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStackTemplate inside : bundle.items()) {
                ItemStack copy = inside.create();
                if (StashContents.storable(copy)) {
                    buds += copy.getCount();
                }
            }
            return buds;
        }
        // Any other carrier of items, as its loader's item API shows it, one level deep: a jar inside it keeps
        // its own smell, the buds loose in it do not.
        for (ItemStack inside : Services.TRANSFER.contents(stack)) {
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
        OPENED.computeIfAbsent(level, key -> new HashSet<>()).add(pos.immutable());
    }

    /** Whether the placed jar at {@code pos} is open and smells. Never loads a chunk to answer. */
    public static boolean smellsOpen(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof StashJarBlockEntity jar
                && jar.smells(level.getGameTime()) && jar.contents().count() > 0;
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
     * One check of {@code mob}'s: an opened jar in reach it goes for, or null. Every jar in reach rolls, at any
     * height, and of those that draw it one is taken at random, so no jar shadows another by the order they
     * were opened in; the walk there finds its own way, or as far as there is one.
     */
    public static @Nullable BlockPos noticeOpenedJar(PathfinderMob mob) {
        Set<BlockPos> opened = OPENED.get(mob.level());
        if (opened == null || opened.isEmpty()) {
            return null;
        }
        BlockPos noticed = null;
        int drawing = 0;
        for (Iterator<BlockPos> it = opened.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (!smellsOpen(mob.level(), pos)) {
                it.remove();
                continue;
            }
            int buds = ((StashJarBlockEntity) mob.level().getBlockEntity(pos)).contents().count();
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
