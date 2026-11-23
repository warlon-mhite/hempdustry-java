package com.warlonmhite.hempdustry.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.config.HempdustryConfig;
import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.config.ModGameRules.Switch;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRule;

/**
 * The mod's game rules: five switches a world can turn off for itself, each read beside its config key. What each
 * switch then does in the world is tested where its feature is, a rule turned off beside the config key turned off.
 */
public final class GameRuleGameTest {
    private GameRuleGameTest() {
    }

    /**
     * Each switch's rule is in the game's registry under the mod's name, on by default, under the mod's heading, and
     * {@code /gamerule} reads and sets it by that name. The ids are a world's: a renamed one is a lost setting.
     */
    public static void theGameRulesAreRegistered(GameTestHelper context) {
        Map<Switch, String> ids = Map.of(Switch.EFFECTS, "hempdustry:effects", Switch.GREEN_OUTS, "hempdustry:green_outs",
                Switch.MOBS_DRAWN_TO_HEMP, "hempdustry:mobs_drawn_to_hemp", Switch.MILDEW, "hempdustry:mildew",
                Switch.PLANT_STRESS, "hempdustry:plant_stress");
        MinecraftServer server = context.getLevel().getServer();
        int ours = 0;
        for (GameRule<?> rule : BuiltInRegistries.GAME_RULE) {
            ours += rule.getIdentifier().getNamespace().equals(Hempdustry.MOD_ID) ? 1 : 0;
        }
        context.assertTrue(ours == Switch.values().length, ours + " game rules of the mod's are registered, not one a switch");
        for (Switch on : Switch.values()) {
            GameRule<Boolean> rule = on.rule();
            context.assertTrue(rule.id().equals(ids.get(on)), on + "'s game rule is " + rule.id() + ", not " + ids.get(on));
            context.assertTrue(rule.defaultValue() && server.getGameRules().get(rule),
                    rule.id() + " is not on by default in a new world");
            context.assertTrue(rule.category().equals(ModGameRules.CATEGORY),
                    rule.id() + " sits under " + rule.category().id() + ", not the mod's heading");
            context.assertTrue(rule.getDescriptionId().equals("gamerule." + rule.id().replace(':', '.')),
                    rule.id() + " names itself by " + rule.getDescriptionId());
            withRule(context, rule, true, () -> {
                gamerule(context, rule.id() + " false");
                context.assertFalse(server.getGameRules().get(rule), "/gamerule " + rule.id() + " false did not take");
                context.assertFalse(on.on(context.getLevel()), "/gamerule " + rule.id() + " false left " + on + " on");
            });
        }
        context.assertTrue(ModGameRules.CATEGORY.id().equals(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID,
                Hempdustry.MOD_ID)), "the rules' heading is " + ModGameRules.CATEGORY.id());
        context.succeed();
    }

    /**
     * A switch is on only where the config key and the world's rule both leave it on: the rule turned off turns it off
     * with the key on, and the key turned off wins over a rule left on. With no world in hand the running server's
     * rules are read, which are the world's.
     */
    public static void theConfigAndTheGameRuleBothDecide(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        Map<String, String> findings = new TreeMap<>();
        for (Switch on : Switch.values()) {
            for (boolean config : List.of(true, false)) {
                for (boolean rule : List.of(true, false)) {
                    String json = "{ \"" + on.configKey().replace(".", "\": { \"") + "\": " + config + " } }";
                    ConfigGameTest.withConfig(json, () -> withRule(context, on.rule(), rule, () -> {
                        boolean want = config && rule;
                        if (on.configOn() != config) {
                            findings.put(on + " config " + config, "the test's config file read as " + on.configOn());
                        }
                        if (on.on(world) != want || on.on() != want) {
                            findings.put(on + " config " + config + " rule " + rule, "reads " + on.on(world)
                                    + " in the world and " + on.on() + " with no world, not " + want);
                        }
                        String offBy = want ? null : config ? on.rule().id() : on.configKey();
                        if (!want && !on.offBy().equals(offBy)) {
                            findings.put(on + " config " + config + " rule " + rule, "says it is off by " + on.offBy());
                        }
                    }));
                }
            }
        }
        context.assertTrue(HempdustryConfig.get().effects().enabled(), "the config was not put back");
        Sweep.report(context, "The config and the game rule both decide", findings, Map.of());
        context.succeed();
    }

    /**
     * Runs {@code body} with {@code rule} at {@code value} on the test server's worlds, and puts the rule back as it
     * was whatever happens: game rules are the server's, and a later test must find them at their defaults.
     */
    static void withRule(GameTestHelper context, GameRule<Boolean> rule, boolean value, Runnable body) {
        MinecraftServer server = context.getLevel().getServer();
        boolean was = server.getGameRules().get(rule);
        try {
            server.getGameRules().set(rule, value, server);
            body.run();
        } finally {
            server.getGameRules().set(rule, was, server);
        }
    }

    private static void gamerule(GameTestHelper context, String arguments) {
        MinecraftServer server = context.getLevel().getServer();
        try {
            server.getCommands().getDispatcher().execute("gamerule " + arguments,
                    server.createCommandSourceStack().withSuppressedOutput());
        } catch (CommandSyntaxException e) {
            throw context.assertionException(Component.literal("/gamerule " + arguments + " was refused: " + e.getMessage()));
        }
    }
}
