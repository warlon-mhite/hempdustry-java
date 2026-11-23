package com.warlonmhite.hempdustry.config;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.platform.Services;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A game's own preferences — {@code config/hempdustry-client.json}: whether it checks for updates, the crops' biome
 * tint, the ripe wisps, the player animations, the fan's and the jar's puffs.
 *
 * <h2>Why a file of their own</h2>
 *
 * A modpack ships {@code config/hempdustry.json}, and a pack update writes it over. While these preferences lived
 * in its {@code client} section, every update put each player's choices back to the pack's. Nothing ships this file,
 * so nothing writes over it.
 *
 * <h2>Where it starts from</h2>
 *
 * The first time a game has no such file, it is started from the {@code client} section of {@code hempdustry.json}
 * — the choices made there before 2.2, or the ones a pack wants its players to start with — and written out. From
 * then on this file alone is read. The old section stays readable and written in {@code hempdustry.json} for ever
 * (a key of a file a release wrote is never taken away); it only seeds a new file. If {@code hempdustry.json} cannot
 * be read at that moment, nothing is written, so the next start tries again rather than freezing the defaults in.
 *
 * <h2>Read like the main file</h2>
 *
 * Merged over the defaults, so a file missing a key still loads; numbers clamped and logged; every key named in its
 * {@code _comment} with its default and range; a value of the wrong type keeps what was running and the rest is
 * applied; a file it cannot read is applied not at all; either way the file is <b>left exactly as it is</b>
 * ({@link HempdustryConfig#load()}).
 *
 * <p>Only a client loads it, when the client starts ({@code HempdustryClient#init}), and {@code /hempdustry reload}
 * re-reads it in a game that did. A dedicated server never reads or writes it: its players' preferences are in
 * their own games.
 */
public final class ClientConfig {
    /** The file's name in the config folder. */
    public static final String FILE = Hempdustry.MOD_ID + "-client.json";

    /** The file: one {@code client} section, shaped as {@code hempdustry.json}'s, so a key has one name in both. */
    public static final Codec<HempdustryConfig.Client> CODEC = HempdustryConfig.Client.CODEC.fieldOf("client").codec();

    /** The {@code _comment} of this file's section: every key, its default and range, and what it does. */
    static final String COMMENT = "This game's own preferences, read by it alone and sent to nobody. A modpack update "
            + "that replaces hempdustry.json leaves this file alone, so a pack should not ship it: the client section "
            + "of hempdustry.json starts this file the first time a game has none. "
            + "updateCheck (default true): false stops this game checking for a newer release on start-up. A modpack "
            + "turns it off in that client section, since the update it would announce cannot be taken anyway. "
            + "biomeTintStrength (default 0.35, 0-1): how far hemp growing in the world is pulled towards the "
            + "local grass colour, so a plant does not clash with the biome around it. "
            + "0 paints the crops exactly as drawn, 1 is the full tint vanilla gives grass. "
            + "ripeAroma (default true): false stops the wisps that drift off ripe plants; vanilla's Particles "
            + "setting thins or hides them along with every other particle. "
            + "animations (default auto; auto, vanilla or off): how this game animates a player smoking, "
            + "coughing, sniffing a jar or kicking a footbag. auto follows whatever animates the rest of the "
            + "body (Not Enough Animations, Fresh Animations' player pack, or vanilla), vanilla is always "
            + "vanilla's rigid style, off shows none of it. "
            + "fanPuffs (default true): false stops the puffs of air a running Grow Fan lets off down the "
            + "lane it blows on. terpenePuffs (default true): false stops the puff a Stash Jar lets off when "
            + "a hand opens it. Both are this game's alone: the fan still blows, the jar's smell still "
            + "gets out, and other players still see the puffs if their own files let them. "
            + "/hempdustry reload re-reads this file in a game that hosts its own world.";

    /** The {@code _comment} of the {@code client} section {@code hempdustry.json} keeps. */
    static final String LEGACY_COMMENT = "Moved to hempdustry-client.json in this folder, each game's own file, which a "
            + "modpack update does not replace. A game with no hempdustry-client.json yet starts one from the values "
            + "here, once; after that they are read for nothing, and a dedicated server never reads them. So this is "
            + "where a pack sets the choices its players start with: updateCheck (default true), biomeTintStrength "
            + "(default 0.35, 0-1), ripeAroma (default true), animations (default auto; auto, vanilla or off), "
            + "fanPuffs (default true), terpenePuffs (default true). hempdustry-client.json says what each does.";

    /**
     * What a game runs with: the preferences, the file they come from — none on a dedicated server, which reads no
     * client file, and until a client has loaded one — and whether the last load read that file in full. When it did
     * not, the preferences running are some or all of what ran before, not what the file says.
     */
    public record State(HempdustryConfig.Client client, Optional<Path> file, boolean read) {
    }

    private static volatile State state = new State(HempdustryConfig.Client.DEFAULT, Optional.empty(), true);

    private ClientConfig() {
    }

    /**
     * The preferences this game runs with. Never {@code null}: the defaults before a client has loaded its file,
     * after a failed first load, and on a dedicated server.
     */
    public static HempdustryConfig.Client get() {
        return state.client();
    }

    /** The preferences and the file they come from. */
    public static State state() {
        return state;
    }

    /** Puts back a {@link #state()} read earlier, so a test that loads a file of its own leaves nothing behind. */
    public static void restore(State earlier) {
        state = earlier;
    }

    /** Loads {@code config/hempdustry-client.json}, started from {@code hempdustry.json} if there is none. */
    public static boolean load() {
        Path folder = Services.PLATFORM.configDir();
        return load(folder.resolve(FILE), folder.resolve(Hempdustry.MOD_ID + ".json"));
    }

    /** Re-reads the file this game loaded, if it loaded one; {@code true} when there is none to read. */
    public static boolean reload() {
        Optional<Path> file = state.file();
        return file.isEmpty() || load(file.get(), file.get().resolveSibling(Hempdustry.MOD_ID + ".json"));
    }

    /**
     * Reads {@code file}, or, when there is none, the {@code client} section of {@code legacy} (the main config file)
     * once; then writes {@code file} back out complete.
     *
     * @return whether a file was read and applied; {@code false} means the preferences already running stay, and
     *         nothing was written
     */
    public static boolean load(Path file, Path legacy) {
        boolean starting = !Files.exists(file);
        Path source = starting ? legacy : file;
        Optional<JsonObject> json = HempdustryConfig.readObject(source);
        if (!starting) {
            // The main file's own load warns about its unknown keys; this file's are this file's.
            json.ifPresent(read -> HempdustryConfig.warnUnknown(file, read, defaultJson()));
        }
        List<String> kept = new ArrayList<>();
        HempdustryConfig.Client read = json.isEmpty() ? null : HempdustryConfig.section(source,
                HempdustryConfig.merge(defaultJson(), json.get()), "client", HempdustryConfig.Client.CODEC,
                state.client(), kept);
        if (read == null || !kept.isEmpty()) {
            if (starting) {
                Hempdustry.LOGGER.error("{} was not started from {}, which could not be read in full; it will be on the"
                        + " next start or /hempdustry reload that reads it", file, legacy);
            }
            state = new State(read == null ? state.client() : read.clamped(), Optional.of(file), false);
            return false;
        }
        if (starting) {
            Hempdustry.LOGGER.info("Started {} from the client section of {}", file, legacy);
        }
        state = new State(read.clamped(), Optional.of(file), true);
        write(file, state.client());
        return true;
    }

    private static JsonObject defaultJson() {
        return CODEC.encodeStart(JsonOps.INSTANCE, HempdustryConfig.Client.DEFAULT).getOrThrow().getAsJsonObject();
    }

    private static void write(Path file, HempdustryConfig.Client client) {
        try {
            JsonObject root = CODEC.encodeStart(JsonOps.INSTANCE, client).getOrThrow().getAsJsonObject();
            HempdustryConfig.comment(root, "client", COMMENT);
            HempdustryConfig.writeJson(file, root);
        } catch (RuntimeException e) {
            Hempdustry.LOGGER.error("Could not write {}", file, e);
        }
    }
}
