package com.warlonmhite.hempdustry.config;

import net.minecraft.util.StringRepresentable;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.entity.custom.InfuserBlockEntity;
import com.warlonmhite.hempdustry.platform.Services;
import net.minecraft.util.Mth;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The server's balance knobs — {@code config/hempdustry.json}.
 *
 * <h2>What is here, and what deliberately is not</h2>
 *
 * This file is for <b>numbers and switches</b>. Three kinds of thing are kept out of it on purpose
 * (see {@code roadmap.md} D14 for the full argument):
 *
 * <ul>
 *   <li><b>Anything a datapack already does.</b> Recipes, tags, advancements, worldgen placement and
 *       loot <em>tables</em> are datapack territory, and a knob here would fight them. <b>Strain
 *       effects are a datapack registry</b> — {@code data/<ns>/hempdustry/strain/<id>.json} — not a config
 *       section.</li>
 *   <li><b>Anything that would contradict the mod's own premise.</b> There is no switch that gives a
 *       raw bud an effect: <em>heat activates, raw plant does nothing</em>. Nor one that lets dose 1
 *       green you out, or shrinks duration as the amplifier rises.</li>
 *   <li><b>Strain display names.</b> They resolve client-side from a lang key; a server sending a
 *       literal string would override all eight locales with one language. A server resource pack is
 *       the vanilla answer, and it works today.</li>
 * </ul>
 *
 * <h2>Server-side, and that is not an accident</h2>
 *
 * Effects, cooldowns, growth and loot are all applied server-side, and <b>the machine screens' timings
 * ride on each block's {@code ContainerData}</b> — the GUI reads the values the block is actually
 * using, so a client with a different file (or no file) still draws a truthful bar. <b>Three values
 * reach a client in a payload of their own</b>, {@code ServerSettings}: {@code world.machineSpeedMultiplier}
 * and the two {@code infuser} times, which the recipe viewers' pages and the Hemp Press's platen show
 * with no block to ask. They are sent while the connection is configured and again after
 * {@code /hempdustry reload}, and held on the client apart from its own file ({@code ShownConfig}); no
 * server ever writes a client's file. Nothing else of this file leaves the server.
 *
 * <h2>Reading it</h2>
 *
 * Missing fields fall back to the default rather than failing, so a half-written file still loads and
 * an upgrade that adds a knob needs no migration. Values outside their sane range are <b>clamped and
 * logged</b>, not rejected — a server should never fail to boot over a config typo. A value of the wrong
 * type is logged and <b>only that value</b> keeps what was running; every other value in the file is
 * applied, and the file is <b>left exactly as it is</b> so the typo can be fixed. A file that cannot be
 * read at all — not valid JSON — is applied not at all and left as it is too; see {@link #load()}.
 *
 * <p>{@code /hempdustry reload} re-reads it in place.
 *
 * <p>A client's own preferences moved to a file of their own in 2.2, {@code config/hempdustry-client.json}
 * ({@link ClientConfig}), so that a modpack update replacing this file keeps each player's choices. The
 * {@code client} section here stays, read and written, and starts that file the first time there is none.
 */
public record HempdustryConfig(Client client, Effects effects, World world, Infuser infuser, Loot loot) {

    public static final HempdustryConfig DEFAULT = new HempdustryConfig(
            Client.DEFAULT, Effects.DEFAULT, World.DEFAULT, Infuser.DEFAULT, Loot.DEFAULT);

    public static final Codec<HempdustryConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Client.CODEC.fieldOf("client").forGetter(HempdustryConfig::client),
            Effects.CODEC.fieldOf("effects").forGetter(HempdustryConfig::effects),
            World.CODEC.fieldOf("world").forGetter(HempdustryConfig::world),
            Infuser.CODEC.fieldOf("infuser").forGetter(HempdustryConfig::infuser),
            Loot.CODEC.fieldOf("loot").forGetter(HempdustryConfig::loot)
    ).apply(instance, HempdustryConfig::new));

    private static volatile HempdustryConfig instance = DEFAULT;

    /**
     * The loaded config. Never {@code null}: before {@link #load()} runs, and after a failed load,
     * this is {@link #DEFAULT}, so every caller can read it without a guard.
     */
    public static HempdustryConfig get() {
        return instance;
    }

    /**
     * Reads the file, writing a complete, commented one back out afterwards. Safe to call again —
     * that is what {@code /hempdustry reload} does.
     *
     * <h2>Why the codec's fields are required, and missing ones still work</h2>
     *
     * The obvious shape — {@code optionalFieldOf(name, default)} everywhere — has a trap:
     * <b>DFU omits any field whose value equals its default when encoding</b>, so a freshly generated
     * config writes out as {@code &#123;&#125;} and an admin never sees a knob they could turn. The
     * fields are therefore required, and tolerance is provided at the other end: whatever the file
     * contains is <b>merged over the defaults</b> before decoding, so a missing section, a missing
     * field, or a config written by an older version all still load. Comments and any other unknown
     * keys are ignored by the decoder and simply rewritten.
     *
     * <h2>A file it cannot read is never written over</h2>
     *
     * Invalid JSON, or JSON that is not an object at all: the error is logged, nothing in the file is
     * applied, and <b>the file is left untouched</b>. Rewriting it would replace every setting in it
     * with its default over one typo, which is what this used to do. The config already running stays
     * — the defaults at start-up, the last good read on a {@code /hempdustry reload}. An empty file is
     * not an error: it is read as "all defaults" and written out in full, which is the way to get a
     * fresh one.
     *
     * <h2>A bad value costs that value alone</h2>
     *
     * A value of the wrong type ({@code "debuffs": "false"}, a word where a number goes) or a section
     * that is not an object keeps what was running for that value or section alone, logged by name;
     * every other value in the file is applied. A server set to industrial hemp only must not come up
     * with every high back on because of a typo in its loot section, which is what reading the file as
     * one piece did. The file is still left as it is, so the typo can be found and fixed.
     *
     * @return whether the file was read and applied in full; {@code false} means it was left alone, and
     *         some or all of what was running stays ({@link #loadFile()} tells which)
     */
    public static boolean load() {
        return loadFile().applied();
    }

    /**
     * What {@link #load()} made of the file: applied in full, applied but for the values {@link #kept()} names, or
     * not read at all.
     */
    public record Outcome(boolean read, List<String> kept) {
        /** Whether every value in the file was applied. */
        public boolean applied() {
            return read && kept.isEmpty();
        }
    }

    /** Reads the file as {@link #load()} does, and says what came of it. */
    public static Outcome loadFile() {
        Path path = Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json");
        Optional<JsonObject> file = readObject(path);
        if (file.isEmpty()) {
            return new Outcome(false, List.of());
        }
        warnUnknown(path, file.get(), defaultJson());
        JsonObject merged = merge(defaultJson(), file.get());
        HempdustryConfig running = instance;
        List<String> kept = new ArrayList<>();
        HempdustryConfig read = new HempdustryConfig(
                section(path, merged, "client", Client.CODEC, running.client(), kept),
                section(path, merged, "effects", Effects.CODEC, running.effects(), kept),
                section(path, merged, "world", World.CODEC, running.world(), kept),
                section(path, merged, "infuser", Infuser.CODEC, running.infuser(), kept),
                section(path, merged, "loot", Loot.CODEC, running.loot(), kept));
        instance = read.clamped();
        if (kept.isEmpty()) {
            write(path, instance);
        } else {
            Hempdustry.LOGGER.error("{}: {} could not be read and kept what was running; everything else in the file"
                    + " was applied, and the file is left as it is so it can be fixed", path, String.join(", ", kept));
        }
        return new Outcome(true, List.copyOf(kept));
    }

    /**
     * The JSON object in {@code path}: an empty one when there is no file or the file is empty, and nothing — the
     * error logged — when it is not JSON or not an object. The client's own file is read the same way
     * ({@link ClientConfig}).
     */
    static Optional<JsonObject> readObject(Path path) {
        if (!Files.exists(path)) {
            return Optional.of(new JsonObject());
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = new GsonBuilder().create().fromJson(reader, JsonElement.class);
            if (json == null) {
                return Optional.of(new JsonObject());
            }
            if (!json.isJsonObject()) {
                Hempdustry.LOGGER.error("{} is not a JSON object — nothing in it was applied, and it is"
                        + " left as it is", path);
                return Optional.empty();
            }
            return Optional.of(json.getAsJsonObject());
        } catch (IOException | RuntimeException e) {
            Hempdustry.LOGGER.error("Could not read {} — nothing in it was applied, and it is left as it is",
                    path, e);
            return Optional.empty();
        }
    }

    /**
     * Logs a WARN for every key of {@code file} that {@code known} does not have: a misspelled setting is otherwise read
     * as its default without a word, and gone from the file the next time it is written ({@code creepersSeekhemp}
     * running as {@code true}).
     */
    static void warnUnknown(Path path, JsonObject file, JsonObject known) {
        for (String key : unknownKeys(file, known)) {
            Hempdustry.LOGGER.warn("{}: {} is not a setting this version knows, so it changes nothing (misspelled?)"
                    + " and is left out when the file is written again", path, key);
        }
    }

    /**
     * Every key of {@code given}, at any depth, that {@code known} does not have, as a dotted path
     * ({@code world.creepersSeekhemp}). A {@code _comment} is never unknown, nor a key holding a colon (a loader's
     * {@code neoforge:conditions} or {@code fabric:load_conditions}); nothing under an unknown key is listed again.
     */
    public static List<String> unknownKeys(JsonElement given, JsonElement known) {
        List<String> unknown = new ArrayList<>();
        unknownKeys("", given, known, unknown);
        return unknown;
    }

    private static void unknownKeys(String at, JsonElement given, JsonElement known, List<String> unknown) {
        if (!(given instanceof JsonObject values) || !(known instanceof JsonObject keys)) {
            return;
        }
        for (var entry : values.entrySet()) {
            String key = entry.getKey();
            if (key.equals("_comment") || key.contains(":")) {
                continue;
            }
            if (!keys.has(key)) {
                unknown.add(at + key);
            } else {
                unknownKeys(at + key + ".", entry.getValue(), keys.get(key), unknown);
            }
        }
    }

    /**
     * Section {@code name} of {@code merged} (a file laid over the defaults) decoded by {@code codec}. A value the
     * codec refuses keeps {@code running}'s value and is added to {@code kept} as {@code section.key}; a section that
     * is not an object at all keeps {@code running} whole and is added by its name. Each is logged with why.
     */
    static <T> T section(Path path, JsonObject merged, String name, Codec<T> codec, T running, List<String> kept) {
        JsonElement given = merged.get(name);
        var whole = codec.parse(JsonOps.INSTANCE, given);
        if (whole.result().isPresent()) {
            return whole.result().get();
        }
        JsonObject base = codec.encodeStart(JsonOps.INSTANCE, running).getOrThrow().getAsJsonObject();
        if (!(given instanceof JsonObject values)) {
            Hempdustry.LOGGER.error("Bad value in {}: {} is {}, not a section of settings — it keeps what was running",
                    path, name, given);
            kept.add(name);
            return running;
        }
        JsonObject good = base.deepCopy();
        for (var entry : values.entrySet()) {
            if (!base.has(entry.getKey())) {
                continue;
            }
            JsonObject one = base.deepCopy();
            one.add(entry.getKey(), entry.getValue());
            var error = codec.parse(JsonOps.INSTANCE, one).error();
            if (error.isPresent()) {
                Hempdustry.LOGGER.error("Bad value in {}: {}.{} is {} ({}) — it keeps what was running, {}", path, name,
                        entry.getKey(), entry.getValue(), error.get().message(), base.get(entry.getKey()));
                kept.add(name + "." + entry.getKey());
            } else {
                good.add(entry.getKey(), entry.getValue());
            }
        }
        return codec.parse(JsonOps.INSTANCE, good).getOrThrow();
    }

    /** The defaults as JSON, which is the base every file is read on top of. */
    private static JsonObject defaultJson() {
        return CODEC.encodeStart(JsonOps.INSTANCE, DEFAULT).getOrThrow().getAsJsonObject();
    }

    /**
     * {@code overrides} laid over {@code base}, one level into each section. Anything the file does
     * not mention keeps its default, which is what lets a knob be added in a later version without a
     * migration step.
     */
    static JsonObject merge(JsonObject base, JsonElement overrides) {
        if (!(overrides instanceof JsonObject object)) {
            return base;
        }
        JsonObject out = base.deepCopy();
        for (var entry : object.entrySet()) {
            JsonElement existing = out.get(entry.getKey());
            if (existing instanceof JsonObject nested && entry.getValue() instanceof JsonObject override) {
                out.add(entry.getKey(), merge(nested, override));
            } else {
                out.add(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    /** Every value pulled into its sane range, complaining about any it had to move. */
    private HempdustryConfig clamped() {
        return new HempdustryConfig(client.clamped(), effects.clamped(), world.clamped(), infuser.clamped(), loot.clamped());
    }

    /**
     * Writes the config back out, with a {@code _comment} line in each section.
     *
     * <p>Rewriting on every load is what keeps the file complete: a knob added in a later version
     * appears in the file at its default rather than staying invisible until someone reads the source.
     * Unknown fields are ignored by the codec, so the comments survive the round trip.
     */
    private static void write(Path path, HempdustryConfig config) {
        try {
            JsonElement encoded = CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow();
            JsonObject root = encoded.getAsJsonObject();
            // Each key is named with its default and, for a number, the range it is clamped to, so a
            // pack maker reading the file needs nothing else. every_config_key_is_described_in_its_comment
            // holds each one to the code.
            comment(root, "client", ClientConfig.LEGACY_COMMENT);
            comment(root, "effects", "enabled (default true): false is 'industrial hemp only': no drug effects anywhere. "
                    + "maxLevel (default 4, 1-10) caps every effect the mod applies; maxBuffLevel (default 2, 1-10) "
                    + "caps only the helpful ones (Speed, Resistance, Absorption...), so a big dose still costs its "
                    + "full Hunger. Raise maxBuffLevel for a stronger mod in a modded pack. "
                    + "durationMultiplier (default 1.0, 0.05-10.0) scales how long the effects last, "
                    + "smoked and eaten alike: an edible's whole high, and Regeneration's own short run with it, so the "
                    + "heal it gives scales too. "
                    + "cooldownMultiplier (default 1.0, 0.0-10.0) scales the use cooldowns; 0 is no cooldown. "
                    + "greenOutChanceMultiplier (default 1.0, 0.0-100.0) scales the odds of a green-out; 0 disables "
                    + "green-outs, as do greenOut=false and enabled=false. "
                    + "onsetMultiplier (default 1.0, 0.05-10.0) scales how long an edible takes to come up. "
                    + "greenOut (default true): false stops a bad hit replacing its effects. "
                    + "nausea (default true): false never applies nausea. munchies (default true): false never "
                    + "applies Hunger. debuffs (default true): false takes away every harmful effect, and the ones "
                    + "in the #hempdustry:counts_as_harmful effect tag (Glowing). "
                    + "A world can turn enabled and greenOut off for itself alone with its game rules "
                    + "hempdustry:effects and hempdustry:green_outs (/gamerule, or the Game Rules screen); false "
                    + "here turns them off in every world, whatever the rules say.");
            comment(root, "world", "cropGrowthMultiplier (default 1.0, 0.05-20.0) and machineSpeedMultiplier "
                    + "(default 1.0, 0.05-20.0) are speeds: 2.0 is twice as fast. "
                    + "machineSpeed drives the Decarboxylator and the Hemp Press; the Infuser has its own section. "
                    + "Whether bees pollinate hemp is the #minecraft:bee_growables tag, not a setting here. "
                    + "creepersSeekHemp (default true): false stops every mob hemp draws: creepers now and then "
                    + "wandering into grown, open-sky hemp fields, endermen into Ender Haze and drowned to ripe Maui "
                    + "Wowie; a harvested Ender Haze plant provoking the endermen nearby; an opened Stash Jar drawing "
                    + "creepers; and loose buds in a player's inventory letting creepers notice them from further "
                    + "away. Which mobs come to the fields and to a stash is the entity tags "
                    + "#hempdustry:drawn_to_hemp and #hempdustry:drawn_to_stash (creepers alone by default). The goals "
                    + "are still added to the mobs, they simply never start, so it takes effect on the next "
                    + "/hempdustry reload. "
                    + "mildew (default true): false keeps powdery mildew out of roofed grows: no plant catches it, "
                    + "and a plant that has it is clean again on its next random tick. "
                    + "plantStress (default true): false stops flowering hemp being stressed into seeding its own "
                    + "buds: no overfeeding, overwatering, dry bed, stagnant tray, bone-dry field or lost light does it, "
                    + "a plant that loses its lamp only falls back to the sun's light, and a plant that is stressed "
                    + "already is healthy again on its next random tick. "
                    + "A world can turn creepersSeekHemp, mildew and plantStress off for itself alone with its game "
                    + "rules hempdustry:mobs_drawn_to_hemp, hempdustry:mildew and hempdustry:plant_stress; false "
                    + "here turns them off in every world, whatever the rules say.");
            comment(root, "infuser", "Ticks; 20 ticks = 1 second. minTimeTicks (default 6000, 20-31980) is the "
                    + "earliest a batch can be pulled, 5 minutes; fullTimeTicks (default 18000, 40-32000, and at "
                    + "least minTimeTicks + 20) a full simmer, 15 minutes. The ceiling, 32000 (about 26 minutes), "
                    + "is as long a simmer as the screen can be told about.");
            comment(root, "loot", "The mod's additions to vanilla loot tables, each with a switch of its own. "
                    + "grassSeeds (default true): hemp seeds from broken tall grass and large ferns. "
                    + "chestSeeds (default true): seed stashes in chests: dungeons, mineshafts, mansions, outposts, "
                    + "shipwrecks, plains, taiga and desert village houses, the desert temple, the Nether's fortresses "
                    + "and bastions, end cities, abandoned camps, the Ancient City's ice box and ocean ruins; most "
                    + "strains that grow wild in one place only are found this way too. "
                    + "chestBuds (default true): buds in Ancient City chests, buried treasure and bastion treasure, and "
                    + "schwag in shipwreck supply chests. "
                    + "archaeologySeeds (default true): seeds in brushed suspicious sand and gravel (desert well and "
                    + "temple, trail ruins, warm ocean ruins) and in a sniffer's dig. "
                    + "barterSeeds (default true): Nether seeds among a piglin's barters. "
                    + "shipwreckFiber (default true): hemp fibre and canvas in shipwreck supply chests. "
                    + "fisherBait (default true): toasted hemp seeds in a village fisher's chest. "
                    + "fishingJunk (default true): hemp fibre among fishing junk. "
                    + "discs (default true): the mod's music discs in dungeon and mansion chests. "
                    + "enabled (default true): false removes all of them, whatever the switches say. "
                    + "chanceMultiplier (default 1.0, 0.0-10.0) scales every chance they are added with; each "
                    + "chance stays between 0 and 1. Loot tables are built when the server starts and on /reload, so "
                    + "a change to this section takes effect on the next /reload or restart; /hempdustry reload "
                    + "alone does not rebuild them. Creepers dropping the two discs is the "
                    + "#minecraft:creeper_drop_music_discs tag, not a setting here.");
            writeJson(path, root);
        } catch (RuntimeException e) {
            Hempdustry.LOGGER.error("Could not write {}", path, e);
        }
    }

    /** Writes {@code root} to {@code path}, pretty-printed; a failure is logged, never thrown. */
    static void writeJson(Path path, JsonObject root) {
        try {
            Files.createDirectories(path.getParent());
            // disableHtmlEscaping, or every "=" and "'" in the comments is written as \u003d and
            // \u0027 — valid JSON, unreadable prose.
            Files.writeString(path,
                    new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            Hempdustry.LOGGER.error("Could not write {}", path, e);
        }
    }

    /**
     * Puts a section's comment first and its keys in alphabetical order.
     *
     * <p>Sorting matters more than it looks: a codec encodes into an unordered map, so without this
     * the knobs come out shuffled — and differently between runs — which makes a diff of two servers'
     * configs unreadable. Alphabetical also matches what the datagen'd JSON in {@code src/generated}
     * does, so the whole project's JSON reads the same way. {@code _comment} leads because {@code _}
     * sorts before every lower-case letter.
     */
    static void comment(JsonObject root, String section, String text) {
        if (root.get(section) instanceof JsonObject object) {
            JsonObject commented = new JsonObject();
            commented.addProperty("_comment", text);
            object.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .forEach(entry -> commented.add(entry.getKey(), entry.getValue()));
            root.add(section, commented);
        }
    }

    private static double clampDouble(String name, double value, double min, double max) {
        double clamped = Mth.clamp(value, min, max);
        if (clamped != value) {
            Hempdustry.LOGGER.warn("Config {} was {}, clamped to {}", name, value, clamped);
        }
        return clamped;
    }

    private static int clampInt(String name, int value, int min, int max) {
        int clamped = Mth.clamp(value, min, max);
        if (clamped != value) {
            Hempdustry.LOGGER.warn("Config {} was {}, clamped to {}", name, value, clamped);
        }
        return clamped;
    }

    /**
     * A <b>client's</b> own preferences. A game reads them from its own {@code config/hempdustry-client.json}
     * ({@link ClientConfig#get()}), which a modpack update that replaces {@code hempdustry.json} leaves alone.
     *
     * <p>{@code hempdustry.json} keeps this section, read and written as it always was, and it stays so: it is part
     * of a file every release wrote, and a key of it is never taken away. What it does now is start a game's
     * {@code hempdustry-client.json} the first time there is none ({@link ClientConfig#load()}); after that it is
     * read for nothing, and {@link HempdustryConfig#client()} is not what a client runs with. A dedicated server
     * reads neither. These values are sent nowhere, in either direction: a server never sends a client its
     * preferences, and the values it does send ({@code ServerSettings}) are held apart from both files.
     *
     * @param updateCheck       whether this client asks Modrinth for a newer release on start-up
     * @param biomeTintStrength  how far the crops and wild flowers are pulled towards the local
     *                           grass colour, {@code 0} (paint as drawn) to {@code 1} (full vanilla
     *                           grass tint). See {@code HempdustryClient#biomeTint}
     * @param ripeAroma          whether this client draws the wisps a ripe plant gives off. Vanilla's
     *                           Particles setting already thins them out or hides them with every
     *                           other ambient particle; this hides only these. See {@code RipeAroma}
     * @param animations         how this client animates a player smoking, coughing, sniffing a jar or
     *                           kicking a footbag: {@code auto} in the style of whatever animates the rest
     *                           of the body (Not Enough Animations, Fresh Animations' player pack, or
     *                           vanilla), {@code vanilla} always vanilla's rigid style, {@code off} not at all
     * @param fanPuffs           whether this client draws the puffs of air a running Grow Fan lets off down its
     *                           lane. The fan blows the same either way; see {@code GrowFanBlock#animateTick}
     * @param terpenePuffs       whether this client draws the puff a Stash Jar lets off when a hand opens it. The
     *                           server sends it whatever this says, to every player near enough; each client
     *                           decides for itself whether to play it. See {@code TerpenePuffParticle}
     */
    public record Client(boolean updateCheck, double biomeTintStrength, boolean ripeAroma, Animations animations,
                         boolean fanPuffs, boolean terpenePuffs) {

        /**
         * 0.35 is the whole point of the knob's existence: enough of the biome to stop a bright
         * lime sativa shouting over a taiga, little enough that the two strains still read as
         * different plants and the gold pistils stay gold. A full 1.0 is vanilla's grass behaviour
         * and collapses both strains onto the same green.
         */
        public static final Client DEFAULT = new Client(true, 0.35, true, Animations.AUTO, true, true);

        public static final Codec<Client> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("updateCheck").forGetter(Client::updateCheck),
                Codec.DOUBLE.fieldOf("biomeTintStrength").forGetter(Client::biomeTintStrength),
                Codec.BOOL.fieldOf("ripeAroma").forGetter(Client::ripeAroma),
                Animations.CODEC.fieldOf("animations").forGetter(Client::animations),
                Codec.BOOL.fieldOf("fanPuffs").forGetter(Client::fanPuffs),
                Codec.BOOL.fieldOf("terpenePuffs").forGetter(Client::terpenePuffs)
        ).apply(instance, Client::new));

        Client clamped() {
            return new Client(updateCheck,
                    clampDouble("client.biomeTintStrength", biomeTintStrength, 0.0, 1.0), ripeAroma, animations,
                    fanPuffs, terpenePuffs);
        }

        /** {@link Client#animations()}: whose style the player animations take, or none. */
        public enum Animations implements StringRepresentable {
            AUTO("auto"),
            VANILLA("vanilla"),
            OFF("off");

            public static final Codec<Animations> CODEC = StringRepresentable.fromEnum(Animations::values);

            private final String name;

            Animations(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return name;
            }
        }
    }

    /**
     * What smoking and edibles do to a player.
     *
     * @param enabled                  master switch; {@code false} is "industrial hemp only"
     * @param maxLevel                 highest level any mod effect may reach — the PvP knob
     * @param maxBuffLevel             highest level a <em>beneficial</em> effect may reach. Two by
     *                                 default, a beacon's ceiling: without it a bong of three buds is
     *                                 Haste III in the first hour. A dose past it, or past
     *                                 {@code maxLevel} where that is lower, buys duration instead
     *                                 (see {@code SmokeContents#effects}); costs are not capped by it
     * @param durationMultiplier       scales every effect's duration, smoking and edibles alike: a smoked hit's
     *                                 effects, an edible's high, and the lengths an edible's effect has per tier of
     *                                 its own (Regeneration's), which scale its heal with it. Uniform, so a stronger
     *                                 tier never lasts shorter than a weaker one
     * @param cooldownMultiplier       scales the per-device use cooldowns
     * @param greenOutChanceMultiplier scales green-out odds; {@code 0} disables them
     * @param onsetMultiplier          scales every quality's come-up window (45 s–4:20 built in)
     * @param greenOut                 whether a bad hit can replace its effects entirely
     * @param nausea                   whether nausea is ever applied — an accessibility switch
     * @param munchies                 whether Hunger is applied
     * @param debuffs                  whether harmful effects are applied at all, and those in
     *                                 {@code #hempdustry:counts_as_harmful}
     */
    public record Effects(boolean enabled, int maxLevel, int maxBuffLevel, double durationMultiplier,
                          double cooldownMultiplier, double greenOutChanceMultiplier,
                          double onsetMultiplier, boolean greenOut, boolean nausea,
                          boolean munchies, boolean debuffs) {

        public static final Effects DEFAULT =
                new Effects(true, 4, 2, 1.0, 1.0, 1.0, 1.0, true, true, true, true);

        public static final Codec<Effects> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(Effects::enabled),
                Codec.INT.fieldOf("maxLevel").forGetter(Effects::maxLevel),
                Codec.INT.fieldOf("maxBuffLevel").forGetter(Effects::maxBuffLevel),
                Codec.DOUBLE.fieldOf("durationMultiplier").forGetter(Effects::durationMultiplier),
                Codec.DOUBLE.fieldOf("cooldownMultiplier").forGetter(Effects::cooldownMultiplier),
                Codec.DOUBLE.fieldOf("greenOutChanceMultiplier").forGetter(Effects::greenOutChanceMultiplier),
                Codec.DOUBLE.fieldOf("onsetMultiplier").forGetter(Effects::onsetMultiplier),
                Codec.BOOL.fieldOf("greenOut").forGetter(Effects::greenOut),
                Codec.BOOL.fieldOf("nausea").forGetter(Effects::nausea),
                Codec.BOOL.fieldOf("munchies").forGetter(Effects::munchies),
                Codec.BOOL.fieldOf("debuffs").forGetter(Effects::debuffs)
        ).apply(instance, Effects::new));

        Effects clamped() {
            return new Effects(enabled,
                    clampInt("effects.maxLevel", maxLevel, 1, 10),
                    clampInt("effects.maxBuffLevel", maxBuffLevel, 1, 10),
                    clampDouble("effects.durationMultiplier", durationMultiplier, 0.05, 10.0),
                    clampDouble("effects.cooldownMultiplier", cooldownMultiplier, 0.0, 10.0),
                    clampDouble("effects.greenOutChanceMultiplier", greenOutChanceMultiplier, 0.0, 100.0),
                    clampDouble("effects.onsetMultiplier", onsetMultiplier, 0.05, 10.0),
                    greenOut, nausea, munchies, debuffs);
        }
    }

    /**
     * <p><b>Bee pollination is deliberately not here.</b> Whether bees advance hemp is decided by
     * membership of {@code #minecraft:bee_growables} — a tag, which a datapack already controls
     * completely. A config switch for it would be the mod fighting the datapack, which is the first
     * of the three filters at the top of this file.
     *
     * <p><b>{@code creepersSeekHemp} is here and the bee switch is not, and the difference is the
     * rule.</b> Bee pollination is a tag; this one is a goal added to a vanilla mob by a mixin, so
     * there is nothing else an admin could turn it off with. It also changes how dangerous a
     * player's farm is, which is exactly the kind of decision a server owner should get to make
     * rather than inherit.
     *
     * @param cropGrowthMultiplier     growth speed for both crops; 2.0 grows twice as fast
     * @param machineSpeedMultiplier   the Decarboxylator's and the Hemp Press's speed (the Infuser has its own section)
     * @param creepersSeekHemp         whether hemp draws mobs at all: creepers (any mob in {@code #hempdustry:drawn_to_hemp})
     *                                 now and then wander into open hemp fields, endermen into Ender Haze and drowned to
     *                                 ripe Maui Wowie, harvesting Ender Haze provokes the endermen, an opened Stash Jar
     *                                 draws creepers (any mob in {@code #hempdustry:drawn_to_stash}), and they notice a
     *                                 player carrying loose buds from further away. The name is 2.1's and frozen with
     *                                 the file; the world's game rule beside it is {@code hempdustry:mobs_drawn_to_hemp}
     * @param mildew                   whether hemp under a roof can catch powdery mildew. A switch for
     *                                 the same reason as the creepers: it is behaviour in code, with no
     *                                 tag or table a datapack could empty instead
     * @param plantStress              whether a flowering plant can be stressed, by its ground, its feeding or a
     *                                 lost light ({@code PlantStress}). Off, a stressed plant is healthy again on
     *                                 its next random tick, as a mildewed one is with {@code mildew} off. The
     *                                 grow climate's odds can make stress rarer; only this takes it away
     */
    public record World(double cropGrowthMultiplier, double machineSpeedMultiplier, boolean creepersSeekHemp,
                        boolean mildew, boolean plantStress) {

        public static final World DEFAULT = new World(1.0, 1.0, true, true, true);

        public static final Codec<World> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.DOUBLE.fieldOf("cropGrowthMultiplier").forGetter(World::cropGrowthMultiplier),
                Codec.DOUBLE.fieldOf("machineSpeedMultiplier").forGetter(World::machineSpeedMultiplier),
                Codec.BOOL.fieldOf("creepersSeekHemp").forGetter(World::creepersSeekHemp),
                Codec.BOOL.fieldOf("mildew").forGetter(World::mildew),
                Codec.BOOL.fieldOf("plantStress").forGetter(World::plantStress)
        ).apply(instance, World::new));

        World clamped() {
            return new World(
                    clampDouble("world.cropGrowthMultiplier", cropGrowthMultiplier, 0.05, 20.0),
                    clampDouble("world.machineSpeedMultiplier", machineSpeedMultiplier, 0.05, 20.0),
                    creepersSeekHemp, mildew, plantStress);
        }
    }

    /**
     * The simmer's two clocks, in ticks.
     *
     * <p>They are absolute rather than a multiplier because the Quality maths reads both, and because
     * {@code fullTime} must stay above {@code minTime} — a relationship that is easier to state and to
     * validate on the numbers themselves. <b>The screen reads these off the block's property
     * delegate</b>, so the bar and its marks stay honest whatever they are set to.
     */
    public record Infuser(int minTimeTicks, int fullTimeTicks) {

        /**
         * Seeded from the machine's own constants rather than repeating the numbers, so the default
         * and the documented behaviour cannot drift apart. Both are compile-time {@code static final
         * int}s, so this inlines and loads no class — which matters, because this record is built
         * while {@link #load()} runs and that is the first thing {@code onInitialize} does.
         */
        public static final Infuser DEFAULT =
                new Infuser(InfuserBlockEntity.MIN_TIME, InfuserBlockEntity.FULL_TIME);

        public static final Codec<Infuser> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("minTimeTicks").forGetter(Infuser::minTimeTicks),
                Codec.INT.fieldOf("fullTimeTicks").forGetter(Infuser::fullTimeTicks)
        ).apply(instance, Infuser::new));

        /**
         * <b>The ceiling is what the screen can receive, not what the machine can count.</b>
         *
         * <p>Both numbers ride to the client on the block's {@code ContainerData}, and
         * {@code ClientboundContainerSetDataPacket} writes every property with
         * {@code writeShort} — so anything above {@link Short#MAX_VALUE} arrives as a negative
         * number and the bar, the minimum notch and the next-grade mark all go wrong. The block
         * itself would keep perfect time; the player would simply be shown nonsense, with nothing
         * in any log to say why.
         *
         * <p>{@value #MAX_TIME} rather than 32767 exactly, to leave the reader in no doubt that it
         * is a chosen bound. It is still a 26-minute simmer, over four times the default, and
         * {@code progress} is bounded by {@code fullTime()} so it cannot overflow either.
         */
        Infuser clamped() {
            int min = clampInt("infuser.minTimeTicks", minTimeTicks, 20, MAX_TIME - 20);
            int full = clampInt("infuser.fullTimeTicks", fullTimeTicks, min + 20, MAX_TIME);
            return new Infuser(min, full);
        }

        /** Longest simmer the screen can be told about. See {@link #clamped()}. */
        private static final int MAX_TIME = 32_000;
    }

    /**
     * The additions to vanilla's loot tables. All read as the tables load ({@code ModLootTableModifiers#modify}), so a
     * change waits for the next {@code /reload} or a restart; {@code /hempdustry reload} rebuilds no table.
     *
     * @param enabled          whether the mod adds anything to vanilla loot tables at all
     * @param chanceMultiplier scales every injected chance; the results are still clamped to 0–1
     * @param grassSeeds       hemp seeds from tall grass and large ferns
     * @param chestSeeds       every seed stash the mod puts in a chest, the shipwreck's fibre-crop seeds included
     * @param chestBuds        buds in the Ancient City's, buried treasure's and a bastion's treasure chests, and
     *                         schwag in a shipwreck's supply chest
     * @param archaeologySeeds seeds among what a brushed suspicious block and a sniffer's dig give
     * @param barterSeeds      Nether seeds among a piglin's barters
     * @param shipwreckFiber   hemp fibre and canvas in a shipwreck's supply chest
     * @param fisherBait       toasted hemp seeds in a village fisher's chest
     * @param fishingJunk      hemp fibre among fishing junk
     * @param discs            the mod's music discs in dungeon and mansion chests
     */
    public record Loot(boolean enabled, double chanceMultiplier, boolean grassSeeds, boolean chestSeeds,
                       boolean chestBuds, boolean archaeologySeeds, boolean barterSeeds, boolean shipwreckFiber,
                       boolean fisherBait, boolean fishingJunk, boolean discs) {

        public static final Loot DEFAULT = new Loot(true, 1.0, true, true, true, true, true, true, true, true, true);

        public static final Codec<Loot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.fieldOf("enabled").forGetter(Loot::enabled),
                Codec.DOUBLE.fieldOf("chanceMultiplier").forGetter(Loot::chanceMultiplier),
                Codec.BOOL.fieldOf("grassSeeds").forGetter(Loot::grassSeeds),
                Codec.BOOL.fieldOf("chestSeeds").forGetter(Loot::chestSeeds),
                Codec.BOOL.fieldOf("chestBuds").forGetter(Loot::chestBuds),
                Codec.BOOL.fieldOf("archaeologySeeds").forGetter(Loot::archaeologySeeds),
                Codec.BOOL.fieldOf("barterSeeds").forGetter(Loot::barterSeeds),
                Codec.BOOL.fieldOf("shipwreckFiber").forGetter(Loot::shipwreckFiber),
                Codec.BOOL.fieldOf("fisherBait").forGetter(Loot::fisherBait),
                Codec.BOOL.fieldOf("fishingJunk").forGetter(Loot::fishingJunk),
                Codec.BOOL.fieldOf("discs").forGetter(Loot::discs)
        ).apply(instance, Loot::new));

        Loot clamped() {
            return new Loot(enabled, clampDouble("loot.chanceMultiplier", chanceMultiplier, 0.0, 10.0), grassSeeds,
                    chestSeeds, chestBuds, archaeologySeeds, barterSeeds, shipwreckFiber, fisherBait, fishingJunk, discs);
        }
    }
}
