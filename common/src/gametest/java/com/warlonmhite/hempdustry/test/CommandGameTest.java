package com.warlonmhite.hempdustry.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.ModBlocks;
import com.warlonmhite.hempdustry.block.custom.Defoliation;
import com.warlonmhite.hempdustry.block.custom.GrowLight;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.config.ClientConfig;
import com.warlonmhite.hempdustry.config.ConfigReport;
import com.warlonmhite.hempdustry.config.HempdustryConfig;
import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.item.ModItems;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.QualityGene;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.StressPhenomenon;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import com.warlonmhite.hempdustry.balance.GrowClimate;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.SingleThreadedRandomSource;

/**
 * {@code /hempdustry inspect} and {@code /hempdustry config}, run as a command is: through the server's dispatcher,
 * from a source that keeps what it is told. Each line is compared as its translation key with its arguments, so the
 * facts are checked whatever language a client reads them in.
 */
public final class CommandGameTest {
    private static final BlockPos SOIL = new BlockPos(1, 1, 1);
    private static final BlockPos CROP = SOIL.above();
    private static final BlockPos JAR = new BlockPos(4, 1, 4);

    private CommandGameTest() {
    }

    /**
     * A flowering Purple Kush in a dry Grow Pot: its genes as set, its next step's stress from the dry bed at a
     * neutral plant's odds, and its growth free (a dry pot slows a plant, never stops it); with plant stress off, it
     * says so instead. A top half answers as its root does. A Stash
     * Jar tells what it holds, an empty block says there is nothing there, and only level 2 runs the command.
     */
    public static void inspectTellsAPlantsFacts(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        context.setBlock(SOIL, ModBlocks.GROW_POT.defaultBlockState());
        context.setBlock(CROP, Defoliation.unworked(ModBlocks.INDICA_CROP.defaultBlockState())
                .setValue(BlockStateProperties.AGE_7, 5).setValue(GrowLight.PROPERTY, GrowLight.NATURAL));
        BlockPos root = context.absolutePos(CROP);
        // A random tick with every roll missed grows the upper half in and nothing else.
        world.getBlockState(root).randomTick(world, root, new SingleThreadedRandomSource(0L) {
            @Override
            public float nextFloat() {
                return 0.999F;
            }
        });
        if (!(world.getBlockEntity(root) instanceof PlantRecordBlockEntity record)) {
            throw context.assertionException(Component.literal("the test plant has no record"));
        }
        record.setBand(PotencyBand.HIGH);
        record.setGene(QualityGene.GOOD);
        record.setResin(ResinStep.RICH);
        record.setResistances(ResistanceGenes.NEUTRAL.with(StressPhenomenon.MILDEW, Resistance.VERY_RESISTANT));
        String at = " " + root.getX() + " " + root.getY() + " " + root.getZ();
        BlockPos top = root.above();
        String atTop = " " + top.getX() + " " + top.getY() + " " + top.getZ();

        List<String> said = run(context, "hempdustry inspect" + at, LevelBasedPermissionSet.GAMEMASTER);
        expectLine(context, said, "commands.hempdustry.inspect.genes(hempdustry.potency.high(),good,hempdustry.resin.rich())");
        expectLine(context, said,
                "commands.hempdustry.inspect.resistances(neutral,neutral,neutral,very_resistant)");
        expectLine(context, said,
                "commands.hempdustry.inspect.stress.risk(commands.hempdustry.inspect.one_in(3),dry_ground)");
        expectLine(context, said, "commands.hempdustry.inspect.growth.ok()");
        expectLine(context, said, "commands.hempdustry.inspect.light(natural,natural)");
        context.assertTrue(said.getFirst().startsWith("commands.hempdustry.inspect.plant(block.hempdustry.indica_crop(),")
                        && said.getFirst().endsWith(",hempdustry:indica,5,7)"),
                "the first line names the plant, its strain and its age: " + said.getFirst());
        // The top half answers for the plant, from its root.
        List<String> fromTop = run(context, "hempdustry inspect" + atTop, LevelBasedPermissionSet.GAMEMASTER);
        context.assertTrue(fromTop.equals(said), "inspecting the top half told " + fromTop + ", the root " + said);

        ConfigGameTest.withConfig("{ \"world\": { \"plantStress\": false } }", () -> {
            List<String> off = run(context, "hempdustry inspect" + at, LevelBasedPermissionSet.GAMEMASTER);
            expectLine(context, off, "commands.hempdustry.inspect.stress.off(world.plantStress)");
            context.assertFalse(off.stream().anyMatch(line -> line.startsWith("commands.hempdustry.inspect.stress.risk")),
                    "a stress risk was told with world.plantStress off: " + off);
        });
        // Turned off by the world's game rule instead, it names the rule.
        GameRuleGameTest.withRule(context, ModGameRules.PLANT_STRESS, false, () -> {
            List<String> off = run(context, "hempdustry inspect" + at, LevelBasedPermissionSet.GAMEMASTER);
            expectLine(context, off, "commands.hempdustry.inspect.stress.off(hempdustry:plant_stress)");
            context.assertFalse(off.stream().anyMatch(line -> line.startsWith("commands.hempdustry.inspect.stress.risk")),
                    "a stress risk was told with the plant_stress game rule off: " + off);
        });

        context.setBlock(JAR, ModBlocks.STASH_JAR);
        BlockPos jarPos = context.absolutePos(JAR);
        if (!(world.getBlockEntity(jarPos) instanceof StashJarBlockEntity jar)) {
            throw context.assertionException(Component.literal("the Stash Jar has no block entity"));
        }
        jar.insert(new ItemStack(ModItems.INDICA_BUDS, 5));
        List<String> jarSaid = run(context, "hempdustry inspect " + jarPos.getX() + " " + jarPos.getY() + " " + jarPos.getZ(),
                LevelBasedPermissionSet.GAMEMASTER);
        expectLine(context, jarSaid, "commands.hempdustry.inspect.jar(block.hempdustry.stash_jar()," + jarPos.getX() + " "
                + jarPos.getY() + " " + jarPos.getZ() + ",5,128,1)");
        expectLine(context, jarSaid, "commands.hempdustry.inspect.jar.entry(5,item.hempdustry.indica_buds())");

        BlockPos empty = context.absolutePos(new BlockPos(6, 1, 1));
        List<String> nothing = run(context, "hempdustry inspect " + empty.getX() + " " + empty.getY() + " " + empty.getZ(),
                LevelBasedPermissionSet.GAMEMASTER);
        context.assertTrue(nothing.size() == 1 && nothing.getFirst().contains("commands.hempdustry.inspect.none("),
                "inspecting air told " + nothing);

        refused(context, "hempdustry inspect" + at);
        context.succeed();
    }

    /**
     * Every config value with its source: one set in the file reads from the file with its default beside it, the
     * rest read default. Every entry of the mod's six registries is the mod's own here, which also holds the
     * loader's answer to which pack is ours; and only level 2 runs the command.
     */
    public static void configTellsEachValueAndItsSource(GameTestHelper context) {
        // The loader's answer to which pack is the mod's, on an entry the mod ships.
        Registry<GrowClimate> climates = context.getLevel().registryAccess().lookupOrThrow(GrowClimate.REGISTRY_KEY);
        Optional<KnownPack> climatePack = climates.registryKeySet().stream().findFirst()
                .flatMap(climates::registrationInfo).flatMap(RegistrationInfo::knownPackInfo);
        context.assertTrue(climatePack.isPresent() && Services.PLATFORM.isOwnPack(climatePack.get()),
                "the shipped grow climate came from " + climatePack + ", which the platform does not call the mod's own");
        ConfigGameTest.withConfig("{ \"effects\": { \"maxLevel\": 6 } }", () -> {
            List<String> said = run(context, "hempdustry config", LevelBasedPermissionSet.GAMEMASTER);
            expectLine(context, said, "commands.hempdustry.config.settings(config/" + Hempdustry.MOD_ID + ".json,1)");
            expectLine(context, said, "commands.hempdustry.config.value.file(effects.maxLevel,6,4)");
            expectLine(context, said, "commands.hempdustry.config.value.default(effects.maxBuffLevel,2)");
            expectLine(context, said, "commands.hempdustry.config.value.default(world.plantStress,true)");
            // A dedicated server reads no client file: the client preferences are each player's, and it says so.
            expectLine(context, said, "commands.hempdustry.config.client.none(config/" + ClientConfig.FILE + ")");
            context.assertTrue(said.stream().noneMatch(line -> line.contains("(client.")),
                    "a dedicated server told client preferences: " + said);
            long values = said.stream().filter(line -> line.startsWith("commands.hempdustry.config.value.")).count();
            context.assertTrue(values == ConfigReport.settings().size() && values >= 29,
                    values + " config values told, of " + ConfigReport.settings().size());
            expectLine(context, said, "commands.hempdustry.config.registry(hempdustry:grow_climate,1)");
        });
        // A game hosting its own world tells the preferences it runs with, from its own client file.
        ConfigGameTest.withClientConfig("{ \"client\": { \"animations\": \"off\" } }", () -> {
            List<String> said = run(context, "hempdustry config", LevelBasedPermissionSet.GAMEMASTER);
            expectLine(context, said, "commands.hempdustry.config.client(config/" + ClientConfig.FILE + ",1)");
            expectLine(context, said, "commands.hempdustry.config.value.file(client.animations,off,auto)");
            expectLine(context, said, "commands.hempdustry.config.value.default(client.fanPuffs,true)");
            context.assertTrue(said.stream().noneMatch(line -> line.startsWith("commands.hempdustry.config.client.none")),
                    "a game with its own client file said it reads none: " + said);
        });
        for (ConfigReport.RegistryReport registry : ConfigReport.registries(context.getLevel().getServer())) {
            context.assertTrue(registry.size() > 0 && registry.fromOtherPacks().isEmpty(),
                    registry.registry() + " holds " + registry.size() + " entries, these from other packs: "
                            + registry.fromOtherPacks());
        }
        context.assertTrue(ConfigReport.registries(context.getLevel().getServer()).size() == ConfigReport.REGISTRIES.size(),
                "not every registry of the mod was reported");
        context.assertFalse(Services.PLATFORM.isOwnPack(KnownPack.vanilla("core")), "vanilla's pack counted as the mod's");
        context.assertFalse(Services.PLATFORM.isOwnPack(new KnownPack("neoforge", "mod/another", "1")),
                "another mod's pack counted as the mod's");
        context.assertFalse(Services.PLATFORM.isOwnPack(new KnownPack("vanilla", "another", "1")),
                "another mod's pack counted as the mod's");
        refused(context, "hempdustry config");
        context.succeed();
    }

    /**
     * A switch the file leaves on and the world's game rule turns off is told as off, from the rule, which it names;
     * so is it in the start-up line. A switch the file turns off is the file's, whatever the rule says.
     */
    public static void configTellsAGameRuleThatTurnsASwitchOff(GameTestHelper context) {
        MinecraftServer server = context.getLevel().getServer();
        GameRuleGameTest.withRule(context, ModGameRules.EFFECTS, false, () -> {
            List<String> said = run(context, "hempdustry config", LevelBasedPermissionSet.GAMEMASTER);
            expectLine(context, said, "commands.hempdustry.config.settings(config/" + Hempdustry.MOD_ID + ".json,1)");
            expectLine(context, said, "commands.hempdustry.config.value.game_rule(effects.enabled,false,hempdustry:effects)");
            expectLine(context, said, "commands.hempdustry.config.value.default(effects.greenOut,true)");
            String line = ConfigReport.differences(ConfigReport.settings(server), List.of()).orElse("");
            context.assertTrue(line.contains("effects.enabled=false (default true, game rule hempdustry:effects)")
                    && !line.contains("greenOut"), "a game rule turning effects off logged as: " + line);
            context.assertTrue(ConfigReport.settings().stream().noneMatch(ConfigReport.Setting::changed),
                    "the file's own values took the game rule in: " + ConfigReport.settings());
        });
        ConfigGameTest.withConfig("{ \"world\": { \"mildew\": false } }", () ->
                GameRuleGameTest.withRule(context, ModGameRules.MILDEW, false, () -> {
                    List<String> said = run(context, "hempdustry config", LevelBasedPermissionSet.GAMEMASTER);
                    expectLine(context, said, "commands.hempdustry.config.value.file(world.mildew,false,true)");
                    context.assertTrue(said.stream().noneMatch(line -> line.contains("value.game_rule")),
                            "a switch the file turned off was told as the game rule's: " + said);
                }));
        context.assertTrue(ConfigReport.differences(ConfigReport.settings(server), List.of()).isEmpty(),
                "with the rules back on a server on the defaults logged "
                        + ConfigReport.differences(ConfigReport.settings(server), List.of()));
        context.succeed();
    }

    /**
     * The start-up log line: nothing at the defaults with only the mod's own registries; with two values set in the
     * file, each with its default and nothing else; and an entry another pack replaced or added, with the pack.
     */
    public static void theStartUpLineNamesOnlyWhatDiffers(GameTestHelper context) {
        List<ConfigReport.RegistryReport> registries = ConfigReport.registries(context.getLevel().getServer());
        context.assertTrue(ConfigReport.differences(ConfigReport.settings(HempdustryConfig.DEFAULT), registries).isEmpty(),
                "a server on the defaults logged " + ConfigReport.differences(ConfigReport.settings(HempdustryConfig.DEFAULT),
                        registries));
        ConfigGameTest.withConfig("{ \"effects\": { \"maxLevel\": 6 }, \"loot\": { \"discs\": false } }", () -> {
            String line = ConfigReport.differences(ConfigReport.settings(), registries).orElse("");
            context.assertTrue(line.contains("effects.maxLevel=6 (default 4)") && line.contains("loot.discs=false (default true)")
                            && !line.contains("maxBuffLevel") && !line.contains("datapack"),
                    "two values set in the file logged as: " + line);
        });
        List<ConfigReport.RegistryReport> packed = List.of(new ConfigReport.RegistryReport(
                Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "grow_climate"), 1,
                List.of(new ConfigReport.Entry(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "default"),
                        ConfigReport.Origin.REPLACED, "file/climate.zip"))));
        String line = ConfigReport.differences(ConfigReport.settings(HempdustryConfig.DEFAULT), packed).orElse("");
        context.assertTrue(line.contains("hempdustry:grow_climate hempdustry:default replaced by file/climate.zip")
                        && !line.contains("config values"),
                "a replaced grow climate logged as: " + line);
        context.succeed();
    }

    /**
     * {@code /hempdustry reload} tells the one who ran it that the recipe viewers keep the old machine times, when a
     * time they show moved, and only then: a reload that moves nothing a client shows, or moves only what no client
     * shows, says nothing of the viewers. A file it cannot read says only that.
     */
    public static void aReloadSaysWhenTheViewersGoStale(GameTestHelper context) {
        String viewers = "commands.hempdustry.reload.viewers()";
        ConfigGameTest.withConfig("{}", () -> {
            ConfigGameTest.writeFile("{ \"world\": { \"machineSpeedMultiplier\": 2.0 } }");
            List<String> moved = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(HempdustryConfig.get().world().machineSpeedMultiplier() == 2.0, "the reload did not take");
            context.assertTrue(moved.equals(List.of("commands.hempdustry.reload()", viewers)),
                    "a reload that moved the machine speed said " + moved);

            ConfigGameTest.writeFile("{ \"world\": { \"machineSpeedMultiplier\": 2.0, \"cropGrowthMultiplier\": 3.0 },"
                    + " \"effects\": { \"maxLevel\": 2 } }");
            List<String> unmoved = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(unmoved.equals(List.of("commands.hempdustry.reload()")),
                    "a reload that moved nothing a client shows said " + unmoved);

            ConfigGameTest.writeFile("{ \"infuser\": { \"minTimeTicks\": 2000 } }");
            List<String> infuser = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(infuser.contains(viewers), "a reload that moved the Infuser's time said " + infuser);

            // A bad value: everything else is applied, and the reply names the value that kept its old setting.
            ConfigGameTest.writeFile("{ \"effects\": { \"maxLevel\": \"four\", \"nausea\": false } }");
            List<String> partly = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(partly.equals(List.of("commands.hempdustry.reload()",
                            "commands.hempdustry.reload.kept(effects.maxLevel)", viewers)) && !HempdustryConfig.get().effects().nausea(),
                    "a reload of a file with one bad value said " + partly + " and runs with " + HempdustryConfig.get().effects());

            ConfigGameTest.writeFile("{ \"world\": ");
            List<String> broken = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(broken.equals(List.of("commands.hempdustry.reload.failed()")),
                    "a reload of a broken file said " + broken);
        });
        // In a game hosting its own world the reload re-reads its client file too, and says when it could not.
        ConfigGameTest.withClientConfig("{}", () -> ConfigGameTest.withConfig("{}", () -> {
            java.nio.file.Path file = ClientConfig.state().file().orElseThrow();
            ConfigGameTest.write(file, "{ \"client\": { \"ripeAroma\": false } }");
            List<String> read = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(read.equals(List.of("commands.hempdustry.reload()")) && !ClientConfig.get().ripeAroma(),
                    "a reload with a changed client file said " + read + " and runs with " + ClientConfig.get());
            ConfigGameTest.write(file, "{ \"client\": ");
            List<String> broken = run(context, "hempdustry reload", LevelBasedPermissionSet.GAMEMASTER);
            context.assertTrue(broken.equals(List.of("commands.hempdustry.reload()", "commands.hempdustry.reload.client_failed()"))
                            && !ClientConfig.get().ripeAroma(),
                    "a reload with a broken client file said " + broken + " and runs with " + ClientConfig.get());
        }));
        context.succeed();
    }

    /** Runs {@code command} from a source of {@code permissions}, and answers each line it was told, flattened. */
    private static List<String> run(GameTestHelper context, String command, PermissionSet permissions) {
        List<String> said = new ArrayList<>();
        try {
            context.getLevel().getServer().getCommands().getDispatcher().execute(command, source(context, said, permissions));
        } catch (CommandSyntaxException e) {
            throw context.assertionException(Component.literal("/" + command + " was refused: " + e.getMessage()));
        }
        return said;
    }

    /** The command is refused below level 2: at level 1 and with no permissions at all. */
    private static void refused(GameTestHelper context, String command) {
        for (PermissionSet permissions : List.of(LevelBasedPermissionSet.MODERATOR, PermissionSet.NO_PERMISSIONS)) {
            List<String> said = new ArrayList<>();
            try {
                context.getLevel().getServer().getCommands().getDispatcher().execute(command,
                        source(context, said, permissions));
                throw context.assertionException(Component.literal("/" + command + " ran below level 2: " + said));
            } catch (CommandSyntaxException refused) {
                // The dispatcher does not know the command at all below its level, as vanilla's own.
            }
        }
    }

    private static CommandSourceStack source(GameTestHelper context, List<String> said, PermissionSet permissions) {
        CommandSource sink = new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(flat(message));
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        return context.getLevel().getServer().createCommandSourceStack().withLevel(context.getLevel())
                .withSource(sink).withPermission(permissions);
    }

    /** A message as its keys and texts: {@code key(arg,arg)} for a translation, the text itself for a literal. */
    private static String flat(Component message) {
        StringBuilder out = new StringBuilder();
        if (message.getContents() instanceof TranslatableContents translatable) {
            out.append(translatable.getKey()).append('(');
            Object[] args = translatable.getArgs();
            for (int i = 0; i < args.length; i++) {
                out.append(i > 0 ? "," : "").append(args[i] instanceof Component part ? flat(part) : String.valueOf(args[i]));
            }
            out.append(')');
        } else if (message.getContents() instanceof PlainTextContents plain) {
            out.append(plain.text());
        }
        for (Component sibling : message.getSiblings()) {
            out.append(flat(sibling));
        }
        return out.toString();
    }

    private static void expectLine(GameTestHelper context, List<String> said, String line) {
        context.assertTrue(said.contains(line), "expected the line " + line + " among " + said);
    }
}
