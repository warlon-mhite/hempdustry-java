package com.warlonmhite.hempdustry.config;

import com.warlonmhite.hempdustry.network.ServerSettings;
import org.jetbrains.annotations.Nullable;

/**
 * The config values a client shows a player, as the server it plays on runs them: the machine timings on the recipe
 * viewers' pages and the Hemp Press's platen. The server sends them ({@link ServerSettings}); they are held here, apart
 * from the game's own files: its {@link HempdustryConfig}, and its preferences in {@code hempdustry-client.json}
 * ({@link ClientConfig}), which go on deciding everything that is the game's own and which no server ever writes.
 * Until a server has sent anything, and on a server without the mod, the game's own {@link HempdustryConfig}
 * answers. Forgotten when the player leaves the world, and whenever a connection starts
 * being configured or drops while it is, so a join that failed after the values arrived leaves nothing behind for
 * the next server.
 *
 * <p>In singleplayer the integrated server sends its values like any other, and they are the same file's.
 *
 * <p>Only what a client shows reads this. Every machine on the server reads {@link HempdustryConfig} itself.
 */
public final class ShownConfig {
    /** What the server sent, read and clamped once as it arrived; {@code null} until then. */
    @Nullable
    private static volatile Shown received;

    /** The server's values as a client shows them. */
    private record Shown(double machineSpeedMultiplier, HempdustryConfig.Infuser infuser) {
    }

    private ShownConfig() {
    }

    /** What the server sent, kept until the player leaves the world or the server sends again. */
    public static void receive(ServerSettings settings) {
        // Clamped here, once, as the file's values are as it loads: a value past its range is the server's to
        // fix, and saying so on every frame the press draws would bury the log. A key the server did not send
        // takes the client's own value as it stands now.
        HempdustryConfig own = HempdustryConfig.get();
        double speed = new HempdustryConfig.World(own.world().cropGrowthMultiplier(),
                settings.value(ServerSettings.MACHINE_SPEED, own.world().machineSpeedMultiplier()),
                own.world().creepersSeekHemp(), own.world().mildew(), own.world().plantStress())
                .clamped().machineSpeedMultiplier();
        HempdustryConfig.Infuser infuser = new HempdustryConfig.Infuser(
                (int) settings.value(ServerSettings.INFUSER_MIN, own.infuser().minTimeTicks()),
                (int) settings.value(ServerSettings.INFUSER_FULL, own.infuser().fullTimeTicks())).clamped();
        received = new Shown(speed, infuser);
    }

    /** The player left the world, or a connection is being configured anew: what a server sent no longer holds. */
    public static void forget() {
        received = null;
    }

    /** {@code world.machineSpeedMultiplier} as the server runs it. */
    public static double machineSpeedMultiplier() {
        Shown from = received;
        return from == null ? HempdustryConfig.get().world().machineSpeedMultiplier() : from.machineSpeedMultiplier();
    }

    /** The {@code infuser} section as the server runs it. */
    public static HempdustryConfig.Infuser infuser() {
        Shown from = received;
        return from == null ? HempdustryConfig.get().infuser() : from.infuser();
    }
}
