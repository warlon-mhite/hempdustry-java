package com.warlonmhite.hempdustry.entity;

import com.warlonmhite.hempdustry.network.PlayerGesture;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.function.Consumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.cubemob.SulfurCube;
import net.minecraft.world.entity.player.Player;

/**
 * The footbag: a sulfur cube holding a hemp bale, which its archetype ({@code hempdustry:footbag})
 * makes stop dead and pop up when hit. Vanilla keeps what a cube swallowed in its body slot, and
 * picks its archetype by that item's tags, so the same question answers on either side.
 *
 * <p>A player who hits one kicks it: the knee comes up in place of the arm's swing. Mojang never
 * animates legs for an action and a player punches everything else; the footbag is the one thing
 * whose whole point is the foot.
 */
public final class Footbag {
    /**
     * Starts the kick on this client, for the player who made it — set by {@code HempdustryClient},
     * a no-op on a dedicated server. The kicker's own client starts it at the click, as it starts the
     * swing; the server tells everyone else.
     */
    public static Consumer<Player> kickHere = player -> {};

    private Footbag() {
    }

    public static boolean is(Entity entity) {
        return entity instanceof SulfurCube cube && cube.getItemBySlot(EquipmentSlot.BODY).is(ModTags.Items.FOOTBAG);
    }

    /** A player's attack, on whichever side it runs: a kick, if what was hit is a footbag. */
    public static void attacked(Player player, Entity target) {
        if (!is(target)) {
            return;
        }
        if (player.level().isClientSide()) {
            kickHere.accept(player);
        } else {
            PlayerGesture.send(player, PlayerGesture.Kind.KICK, false);
        }
    }
}
