package com.warlonmhite.hempdustry.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.config.EffectPolicy;
import com.warlonmhite.hempdustry.config.HempdustryConfig;
import com.warlonmhite.hempdustry.platform.Services;
import net.minecraft.gametest.framework.GameTestHelper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A config file the mod cannot read is left alone, and the settings already running stay.
 *
 * <p>It used to be written over with every default, so one typo in a server's
 * {@code config/hempdustry.json} -- by hand, then {@code /hempdustry reload} -- quietly replaced
 * everything else the owner had set in it. Each broken file below is one way that happened.
 *
 * <p>Everything happens inside one tick, and the real file is put back in a {@code finally}: the
 * config is global, and a test that left it changed would change every other test's numbers.
 */
public final class ConfigGameTest {

    private static final String[] BROKEN = {
            "{ \"effects\": { \"maxBuffLevel\": 3, }",           // not JSON: cut off mid-edit
            "{ \"effects\": { \"maxLevel\": \"four\" } }",       // JSON, but a word where a number goes
            "{ \"effects\": 3 }",                                // a whole section replaced by a number
            "[ \"effects\" ]"                                    // JSON, but not an object
    };

    public static void aBrokenConfigIsLeftAlone(GameTestHelper context) {
        Path path = Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json");
        byte[] original = read(path);
        try {
            HempdustryConfig running = HempdustryConfig.get();
            for (String broken : BROKEN) {
                write(path, broken);
                context.assertFalse(HempdustryConfig.load(), "load() reported success on: " + broken);
                context.assertTrue(broken.equals(new String(read(path), StandardCharsets.UTF_8)),
                        "the file was written over after failing to read: " + broken);
                context.assertTrue(HempdustryConfig.get() == running,
                        "the running config changed after failing to read: " + broken);
            }

            // And a good file still goes in: a field it leaves out keeps its default, and the file
            // comes back out complete.
            write(path, "{ \"effects\": { \"maxBuffLevel\": 3 } }");
            context.assertTrue(HempdustryConfig.load(), "a valid file was refused");
            context.assertTrue(HempdustryConfig.get().effects().maxBuffLevel() == 3,
                    "maxBuffLevel 3 was not applied: " + HempdustryConfig.get().effects().maxBuffLevel());
            String rewritten = new String(read(path), StandardCharsets.UTF_8);
            context.assertTrue(rewritten.contains("\"maxLevel\"") && rewritten.contains("\"cropGrowthMultiplier\""),
                    "a valid file was not written back complete");
        } finally {
            write(path, original);
            HempdustryConfig.load();
        }
        context.succeed();
    }

    /**
     * With effects off, nothing greens a player out. A green-out strips every buff of a kind the mod
     * grants -- a potion's or a beacon's too -- and the player's saturation, then locks smoking for a
     * minute; with {@code enabled} false its own effects are filtered to nothing, so a server running
     * hemp without the drugs lost all that on a strong hit for no reason the player could see.
     */
    public static void effectsOffMeansNoGreenOut(GameTestHelper context) {
        withConfig("{ \"effects\": { \"enabled\": false } }", () -> {
            for (int oneIn = 1; oneIn <= 100; oneIn++) {
                int odds = EffectPolicy.greenOutChanceOneIn(oneIn);
                context.assertTrue(odds == 0, "with effects off a hit still greens out one in " + odds
                        + " (one in " + oneIn + " at the defaults)");
            }
        });
        context.succeed();
    }

    /**
     * A client section written before the puff switches existed still loads: what it sets is applied, the two
     * switches it has never heard of come in on, and the file is written back with them in it.
     */
    public static void aClientSectionWithoutThePuffSwitchesLoads(GameTestHelper context) {
        withConfig("{ \"client\": { \"_comment\": \"an older build's comment\", \"updateCheck\": false,"
                + " \"biomeTintStrength\": 0.5, \"ripeAroma\": false } }", () -> {
            HempdustryConfig.Client client = HempdustryConfig.get().client();
            context.assertTrue(!client.updateCheck() && client.biomeTintStrength() == 0.5 && !client.ripeAroma(),
                    "an older client section was not applied: " + client);
            context.assertTrue(client.fanPuffs() && client.terpenePuffs(),
                    "switches an older file never named are not on: " + client);
            String rewritten = new String(read(Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json")),
                    StandardCharsets.UTF_8);
            context.assertTrue(rewritten.contains("\"fanPuffs\": true") && rewritten.contains("\"terpenePuffs\": true"),
                    "the file was not written back with the new switches in it");
        });
        context.succeed();
    }

    /**
     * A world section written before {@code plantStress} existed still loads: what it sets is applied, the switch it
     * has never heard of comes in on, and the file is written back with it in it.
     */
    public static void aWorldSectionWithoutPlantStressLoads(GameTestHelper context) {
        withConfig("{ \"world\": { \"_comment\": \"an older build's comment\", \"cropGrowthMultiplier\": 2.0,"
                + " \"machineSpeedMultiplier\": 1.5, \"creepersSeekHemp\": false, \"mildew\": false } }", () -> {
            HempdustryConfig.World world = HempdustryConfig.get().world();
            context.assertTrue(world.cropGrowthMultiplier() == 2.0 && world.machineSpeedMultiplier() == 1.5
                    && !world.creepersSeekHemp() && !world.mildew(), "an older world section was not applied: " + world);
            context.assertTrue(world.plantStress(), "plantStress, which an older file never named, is not on: " + world);
            String rewritten = new String(read(Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json")),
                    StandardCharsets.UTF_8);
            context.assertTrue(rewritten.contains("\"plantStress\": true"),
                    "the file was not written back with plantStress in it");
        });
        context.succeed();
    }

    /**
     * A loot section written before its nine switches existed still loads: what it sets is applied, every switch it has
     * never heard of comes in on, and the file is written back with them in it.
     */
    public static void aLootSectionWithoutItsSwitchesLoads(GameTestHelper context) {
        withConfig("{ \"loot\": { \"_comment\": \"an older build's comment\", \"enabled\": true, \"chanceMultiplier\": 0.5 } }", () -> {
            HempdustryConfig.Loot loot = HempdustryConfig.get().loot();
            context.assertTrue(loot.enabled() && loot.chanceMultiplier() == 0.5, "an older loot section was not applied: " + loot);
            context.assertTrue(loot.grassSeeds() && loot.chestSeeds() && loot.chestBuds() && loot.archaeologySeeds()
                            && loot.barterSeeds() && loot.shipwreckFiber() && loot.fisherBait() && loot.fishingJunk() && loot.discs(),
                    "switches an older file never named are not on: " + loot);
            String rewritten = new String(read(Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json")),
                    StandardCharsets.UTF_8);
            for (String key : new String[]{"grassSeeds", "chestSeeds", "chestBuds", "archaeologySeeds", "barterSeeds",
                    "shipwreckFiber", "fisherBait", "fishingJunk", "discs"}) {
                context.assertTrue(rewritten.contains("\"" + key + "\": true"), "the file was not written back with " + key + " in it");
            }
        });
        context.succeed();
    }

    /**
     * Every key the config file holds is named in its section's {@code _comment} with its default, and a number with
     * the range it is clamped to: {@code key (default 4, 1-10)}. A pack maker reads the file, not the source, and a
     * comment written by hand drifts the day a default or a bound moves.
     *
     * <p>The defaults are what an empty file writes back out; the ranges are what a file with every number far below
     * and far above its bounds is clamped to, so the test reads both off the code that applies them.
     */
    public static void everyConfigKeyIsDescribedInItsComment(GameTestHelper context) {
        Path path = Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json");
        byte[] original = read(path);
        Map<String, String> findings = new TreeMap<>();
        int keys = 0;
        try {
            JsonObject defaults = written(path, new JsonObject());
            JsonObject bottom = written(path, everyNumber(defaults, -1_000_000_000, -1.0E9));
            JsonObject top = written(path, everyNumber(defaults, 1_000_000_000, 1.0E9));
            for (var section : defaults.entrySet()) {
                JsonObject values = section.getValue().getAsJsonObject();
                String comment = values.has("_comment") ? values.get("_comment").getAsString() : "";
                for (var key : values.entrySet()) {
                    if (key.getKey().equals("_comment")) {
                        continue;
                    }
                    keys++;
                    String where = section.getKey() + "." + key.getKey();
                    Matcher said = Pattern.compile("(?<![A-Za-z])" + Pattern.quote(key.getKey())
                            + " \\(default ([^,;)]+)(?:, ([0-9.]+)-([0-9.]+))?").matcher(comment);
                    if (!said.find()) {
                        findings.put(where, "is not named in the comment as \"" + key.getKey() + " (default …)\"");
                        continue;
                    }
                    JsonPrimitive value = key.getValue().getAsJsonPrimitive();
                    if (!same(value, said.group(1))) {
                        findings.put(where, "the comment gives its default as " + said.group(1) + ", the code " + value);
                    }
                    if (value.isNumber()) {
                        JsonElement min = bottom.getAsJsonObject(section.getKey()).get(key.getKey());
                        JsonElement max = top.getAsJsonObject(section.getKey()).get(key.getKey());
                        if (said.group(2) == null) {
                            findings.put(where, "the comment gives no range; the code clamps it to " + min + "-" + max);
                        } else if (!same(min.getAsJsonPrimitive(), said.group(2))
                                || !same(max.getAsJsonPrimitive(), said.group(3))) {
                            findings.put(where, "the comment gives its range as " + said.group(2) + "-" + said.group(3)
                                    + ", the code clamps it to " + min + "-" + max);
                        }
                    } else if (said.group(2) != null) {
                        findings.put(where, "is not a number, but the comment gives it a range");
                    }
                }
            }
        } finally {
            write(path, original);
            HempdustryConfig.load();
        }
        Sweep.atLeast(context, "config keys described", keys, 35);
        Sweep.report(context, "Every config key is described in its comment", findings, Map.of());
        context.succeed();
    }

    /** Loads {@code json} as the config and reads back the file the mod wrote for it. */
    private static JsonObject written(Path path, JsonObject json) {
        write(path, json.toString());
        if (!HempdustryConfig.load()) {
            throw new IllegalStateException("the test config was refused: " + json);
        }
        return JsonParser.parseString(new String(read(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    /**
     * {@code config} with every number replaced, a whole one by {@code whole} and any other by {@code fraction}, and
     * the comments left out. The sentinels are the ones the log gate lets through as a clamp warning.
     */
    private static JsonObject everyNumber(JsonObject config, int whole, double fraction) {
        JsonObject out = new JsonObject();
        for (var section : config.entrySet()) {
            JsonObject values = new JsonObject();
            for (var key : section.getValue().getAsJsonObject().entrySet()) {
                JsonElement value = key.getValue();
                if (key.getKey().equals("_comment")) {
                    continue;
                }
                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                    boolean integral = !value.getAsString().contains(".") && !value.getAsString().contains("E");
                    value = integral ? new JsonPrimitive(whole) : new JsonPrimitive(fraction);
                }
                values.add(key.getKey(), value);
            }
            out.add(section.getKey(), values);
        }
        return out;
    }

    /** Whether {@code said}, as a comment writes it, is {@code value}: numbers compared as numbers. */
    private static boolean same(JsonPrimitive value, String said) {
        if (value.isNumber()) {
            try {
                return Double.parseDouble(said) == value.getAsDouble();
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return value.getAsString().equals(said);
    }

    /**
     * Runs {@code body} with {@code json} as the config, and puts the real file back afterwards
     * whatever happens -- for a test elsewhere that needs a setting other than the default.
     */
    static void withConfig(String json, Runnable body) {
        Path path = Services.PLATFORM.configDir().resolve(Hempdustry.MOD_ID + ".json");
        byte[] original = read(path);
        try {
            write(path, json);
            if (!HempdustryConfig.load()) {
                throw new IllegalStateException("the test config was refused: " + json);
            }
            body.run();
        } finally {
            write(path, original);
            HempdustryConfig.load();
        }
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, String text) {
        write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void write(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
