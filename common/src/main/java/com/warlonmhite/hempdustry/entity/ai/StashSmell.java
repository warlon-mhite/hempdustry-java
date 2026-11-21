package com.warlonmhite.hempdustry.entity.ai;

import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.config.HempdustryConfig;
import com.warlonmhite.hempdustry.item.custom.StashJarItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

/**
 * Creepers smell a stash: buds carried loose let a creeper notice their carrier from further away.
 *
 * <h2>What counts</h2>
 *
 * Buds and schwag, the things a Stash Jar takes ({@link StashContents#storable}), anywhere in a player's
 * inventory: the hotbar, the offhand, the armour slots, the stack on the cursor, and inside a bundle,
 * which is cloth and holds no smell in. A Stash Jar's glass does: a carried jar's contents count only while
 * it is open, for {@link #OPEN_TICKS} after a hand last took buds out of it, and then with the loose buds.
 * Putting buds in opens nothing. Packed smokeables, hash, kief and edibles are none of these and never
 * count.
 *
 * <h2>How far</h2>
 *
 * A creeper notices a player out to its follow range (16 blocks) times the player's visibility, which
 * vanilla already scales: 0.8 sneaking, less invisible, 0.5 behind a creeper head. Loose buds multiply it
 * by {@link #SOME_BUDS} (any bud: 20 blocks) or {@link #A_STACK} (64 or more: 24), with every vanilla
 * factor still applying, so a sneaking player with a stack is noticed at 19.2 and with a creeper head on
 * too at 9.6. The creeper still needs to see the player: the smell lengthens its reach and never lets it
 * target through a wall. A creeper keeps a target as far as it noticed it, so the same factor widens the
 * distance at which it gives up the chase; otherwise it would notice a player at 20 blocks and drop them
 * at the next tick, being past its 16.
 *
 * <p>All of it sits under {@code world.creepersSeekHemp}, the switch that already decides whether hemp
 * draws creepers, and is read at each check, so {@code /hempdustry reload} takes effect at once.
 *
 * <p>Nothing is saved: a carried jar's two minutes are kept on the player, in memory, and a rejoin closes
 * the jar again.
 */
public final class StashSmell {

    /** How long a jar stays open after a hand last took buds out of it: two minutes. */
    public static final int OPEN_TICKS = 2 * 60 * 20;
    /** A creeper's reach on a player carrying any loose bud. */
    public static final double SOME_BUDS = 1.25;
    /** A creeper's reach on a player carrying a stack of loose buds or more. */
    public static final double A_STACK = 1.5;
    /** How many loose buds count as a stack. */
    public static final int STACK = 64;

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
     * How much further a creeper notices {@code player}: 1 with no loose buds or with the switch off,
     * {@link #SOME_BUDS} with any, {@link #A_STACK} with a stack or more.
     */
    public static double reachFactor(Player player) {
        if (!HempdustryConfig.get().world().creepersSeekHemp()) {
            return 1.0;
        }
        int buds = looseBuds(player);
        return buds >= STACK ? A_STACK : buds > 0 ? SOME_BUDS : 1.0;
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
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle == null) {
            return 0;
        }
        int buds = 0;
        for (ItemStackTemplate inside : bundle.items()) {
            ItemStack copy = inside.create();
            if (StashContents.storable(copy)) {
                buds += copy.getCount();
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
     * Whether {@code player} is one the server's list holds: a person, not a machine's hand. A deployer's fake
     * player can carry its owner's UUID, so the list is asked for the object itself.
     */
    public static boolean isAPlayer(Player player) {
        MinecraftServer server = player.level().getServer();
        return server != null && server.getPlayerList().getPlayer(player.getUUID()) == player;
    }
}
