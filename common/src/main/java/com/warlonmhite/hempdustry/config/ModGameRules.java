package com.warlonmhite.hempdustry.config;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.platform.Services;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.gamerules.GameRuleTypeVisitor;
import org.jetbrains.annotations.Nullable;

/**
 * The mod's game rules: five of the config's on/off switches that a world can also turn off for itself, with
 * {@code /gamerule} or the game's Game Rules screen, under a Hempdustry heading of their own. Each is on by default and
 * can only take a feature away: the feature runs where the config file <b>and</b> the world's rule both leave it on, so
 * a server's file stays the ceiling and one world of it can be made quieter than the rest.
 *
 * <p>Every gate on one of these five features asks {@link Switch#on(Level)} (or {@link Switch#on()} where no world is in
 * hand), never the config key alone. Game rules belong to the server in this Minecraft — a level's rules are its
 * server's, one set per save, kept in its {@code level.dat} — so a call with no world in hand reads the running
 * server's, which is the same set. Only a client with no server of its own falls back to the config alone; nothing a
 * client does reads these.
 *
 * <p>The ids are a frozen surface: a world stores them.
 */
public final class ModGameRules {
    /** The heading the rules sit under in the Game Rules screen; a heading is stored nowhere, only shown. */
    public static final GameRuleCategory CATEGORY =
            GameRuleCategory.register(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, Hempdustry.MOD_ID));

    public static final GameRule<Boolean> EFFECTS = register("effects");
    public static final GameRule<Boolean> GREEN_OUTS = register("green_outs");
    public static final GameRule<Boolean> CREEPERS_SEEK_HEMP = register("creepers_seek_hemp");
    public static final GameRule<Boolean> MILDEW = register("mildew");
    public static final GameRule<Boolean> PLANT_STRESS = register("plant_stress");

    @Nullable
    private static volatile MinecraftServer running;

    private ModGameRules() {
    }

    /**
     * One feature behind a config key and a game rule of the same meaning. {@link #on(Level)} is the one answer to
     * whether it runs.
     */
    public enum Switch {
        EFFECTS("effects.enabled", config -> config.effects().enabled()),
        GREEN_OUTS("effects.greenOut", config -> config.effects().greenOut()),
        CREEPERS_SEEK_HEMP("world.creepersSeekHemp", config -> config.world().creepersSeekHemp()),
        MILDEW("world.mildew", config -> config.world().mildew()),
        PLANT_STRESS("world.plantStress", config -> config.world().plantStress());

        private final String configKey;
        private final Predicate<HempdustryConfig> config;

        Switch(String configKey, Predicate<HempdustryConfig> config) {
            this.configKey = configKey;
            this.config = config;
        }

        /** The config key, {@code section.key}, as the file and {@code /hempdustry config} name it. */
        public String configKey() {
            return configKey;
        }

        /** The game rule beside the key. */
        public GameRule<Boolean> rule() {
            return switch (this) {
                case EFFECTS -> ModGameRules.EFFECTS;
                case GREEN_OUTS -> ModGameRules.GREEN_OUTS;
                case CREEPERS_SEEK_HEMP -> ModGameRules.CREEPERS_SEEK_HEMP;
                case MILDEW -> ModGameRules.MILDEW;
                case PLANT_STRESS -> ModGameRules.PLANT_STRESS;
            };
        }

        /** Whether the feature runs in {@code level}: the config key and that world's game rule both on. */
        public boolean on(@Nullable Level level) {
            return configOn() && ruleOn(level == null ? running : level.isClientSide() ? null : level.getServer());
        }

        /** Whether the feature runs, with no world in hand: the config key and the running server's rule. */
        public boolean on() {
            return on(null);
        }

        /** The config key alone, read per use so {@code /hempdustry reload} takes. */
        public boolean configOn() {
            return config.test(HempdustryConfig.get());
        }

        /**
         * What turns the feature off, for a line that says so of a feature found off: the config key when the file does
         * (the file wins), else the game rule's id.
         */
        public String offBy() {
            return configOn() ? rule().id() : configKey;
        }

        /** The game rule alone, on {@code server}'s worlds; on with no server to ask. */
        public boolean ruleOn(@Nullable MinecraftServer server) {
            return server == null || server.getGameRules().get(rule());
        }
    }

    private static GameRule<Boolean> register(String name) {
        return Registry.register(BuiltInRegistries.GAME_RULE, Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, name),
                new GameRule<>(CATEGORY, GameRuleType.BOOL, BoolArgumentType.bool(), GameRuleTypeVisitor::visitBoolean,
                        Codec.BOOL, on -> on ? 1 : 0, true, FeatureFlagSet.of()));
    }

    /** Registers the rules (class load) and keeps {@code running} to the server that is running, from start to stop. */
    public static void registerGameRules() {
        Hempdustry.LOGGER.info("Registering Game Rules for " + Hempdustry.MOD_ID);
        Services.EVENTS.onServerStarted(server -> running = server);
        Services.EVENTS.onServerStopped(server -> running = null);
    }
}
