package com.warlonmhite.hempdustry.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.custom.PlantInspection;
import com.warlonmhite.hempdustry.block.entity.custom.StashJarBlockEntity;
import com.warlonmhite.hempdustry.component.StashContents;
import com.warlonmhite.hempdustry.network.ServerSettings;
import com.warlonmhite.hempdustry.platform.Services;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * {@code /hempdustry} — the server owner's commands, every one at permission level 2, the bar vanilla puts on
 * {@code /reload} and {@code /gamerule}.
 *
 * <ul>
 *   <li>{@code reload} re-reads {@code config/hempdustry.json} without a restart. The values a client shows (the machine
 *       timings) go to every player again; a recipe viewer's pages keep the old ones until the recipes are sent again
 *       ({@code /reload} or a rejoin), and the reply says so when one moved. In a game that hosts its own world it
 *       re-reads that game's {@code config/hempdustry-client.json} too ({@link ClientConfig}).</li>
 *   <li>{@code inspect [pos]} tells what the plant (or the Stash Jar) at {@code pos}, or at the block looked at,
 *       holds and why it stands as it does ({@link PlantInspection}). At level 2 because it shows the genes a
 *       player is never shown.</li>
 *   <li>{@code config} prints every config value and where it comes from, and which entries of the mod's datapack
 *       registries a pack other than the mod's own supplied ({@link ConfigReport}). The client preferences are this
 *       game's own file's in a game hosting its own world; a dedicated server reads none, and says so.</li>
 * </ul>
 *
 * <p><b>{@code reload} does not reload strains, and nothing can short of a restart.</b> Strains are a dynamic
 * registry, which the server reads once, when the world opens; vanilla's {@code /reload} keeps the
 * registries it has and only re-reads their tags. Until 2.0.3 the chat reply sent admins to
 * {@code /reload} for strains, which did nothing.
 */
public class ModCommands {
    private static final String INSPECT = "commands.hempdustry.inspect.";
    private static final String CONFIG = "commands.hempdustry.config.";

    public static void registerCommands() {
        Services.EVENTS.onRegisterCommands(dispatcher -> dispatcher.register(build()));
        Hempdustry.LOGGER.info("Registering Commands for " + Hempdustry.MOD_ID);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal(Hempdustry.MOD_ID)
                // Permission is a PermissionLevel check since 1.21.10 rather than a bare int.
                .requires(Commands.hasPermission(
                        new PermissionCheck.Require(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS))))
                .then(Commands.literal("reload").executes(context -> {
                    ServerSettings before = ServerSettings.of(HempdustryConfig.get());
                    if (!HempdustryConfig.load()) {
                        // The file could not be read and was left alone; the settings already running
                        // stay. Say so, or a typo looks exactly like a reload that took.
                        context.getSource().sendFailure(Component.translatable("commands.hempdustry.reload.failed"));
                        return 0;
                    }
                    // Sent as a broadcast-to-ops message: a balance change is something the other
                    // operators online want to know happened, the way /gamerule announces itself.
                    context.getSource().sendSuccess(
                            () -> Component.translatable("commands.hempdustry.reload"), true);
                    // The clients hear the new values at once and the press's platen keeps the new rhythm,
                    // but a recipe viewer built its pages when the recipes came and keeps them until they come
                    // again. Told only to the one who reloaded, and only when a shown value moved.
                    ServerSettings.sendToAll(context.getSource().getServer());
                    if (!ServerSettings.of(HempdustryConfig.get()).equals(before)) {
                        context.getSource().sendSuccess(
                                () -> Component.translatable("commands.hempdustry.reload.viewers"), false);
                    }
                    // A game hosting its own world re-reads its own preferences too; a dedicated server has none.
                    if (!ClientConfig.reload()) {
                        context.getSource().sendFailure(Component.translatable("commands.hempdustry.reload.client_failed"));
                    }
                    return 1;
                }))
                .then(Commands.literal("inspect")
                        .executes(context -> inspect(context.getSource(), lookedAt(context.getSource())))
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(context -> inspect(context.getSource(),
                                        Optional.of(BlockPosArgument.getLoadedBlockPos(context, "pos"))))))
                .then(Commands.literal("config").executes(ModCommands::config));
    }

    /** The block the command's entity looks at, within its reach; nothing for the console or a look at the sky. */
    private static Optional<BlockPos> lookedAt(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity == null) {
            return Optional.empty();
        }
        double reach = entity instanceof Player player ? player.blockInteractionRange() : 5.0;
        HitResult hit = entity.pick(reach, 0.0F, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? Optional.of(block.getBlockPos()) : Optional.empty();
    }

    /** Says what stands at {@code pos}; the number of lines said, 0 when there was nothing to inspect. */
    private static int inspect(CommandSourceStack source, Optional<BlockPos> target) {
        if (target.isEmpty()) {
            source.sendFailure(Component.translatable(INSPECT + "no_target"));
            return 0;
        }
        BlockPos pos = target.get();
        ServerLevel world = source.getLevel();
        Optional<List<Component>> lines = PlantInspection.of(world, pos);
        if (lines.isEmpty() && world.getBlockEntity(pos) instanceof StashJarBlockEntity jar) {
            lines = Optional.of(jar(jar));
        }
        if (lines.isEmpty()) {
            source.sendFailure(Component.translatable(INSPECT + "none", pos.getX() + " " + pos.getY() + " " + pos.getZ()));
            return 0;
        }
        for (Component line : lines.get()) {
            source.sendSuccess(() -> line, false);
        }
        return lines.get().size();
    }

    /** A Stash Jar's contents: how full it is, then one line per kind it holds. */
    private static List<Component> jar(StashJarBlockEntity jar) {
        StashContents contents = jar.contents();
        BlockPos pos = jar.getBlockPos();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(INSPECT + "jar", jar.getBlockState().getBlock().getName().withStyle(ChatFormatting.GREEN),
                value(pos.getX() + " " + pos.getY() + " " + pos.getZ()), value(contents.count()),
                value(jar.capacity()), value(contents.size())));
        for (StashContents.Entry entry : contents.entries()) {
            lines.add(Component.translatable(INSPECT + "jar.entry", value(entry.count()), entry.icon().getHoverName()));
        }
        return lines;
    }

    /** Prints every config value with its source, then the mod's registries and what other packs put in them. */
    private static int config(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<ConfigReport.Setting> settings = ConfigReport.settings();
        long changed = settings.stream().filter(ConfigReport.Setting::changed).count();
        source.sendSuccess(() -> Component.translatable(CONFIG + "settings",
                value("config/" + Hempdustry.MOD_ID + ".json"), value(changed)), false);
        values(source, settings);
        Optional<ConfigReport.ClientReport> client = ConfigReport.client();
        if (client.isPresent()) {
            List<ConfigReport.Setting> preferences = client.get().settings();
            source.sendSuccess(() -> Component.translatable(CONFIG + "client",
                    value("config/" + client.get().file().getFileName()),
                    value(preferences.stream().filter(ConfigReport.Setting::changed).count())), false);
            values(source, preferences);
            settings = new ArrayList<>(settings);
            settings.addAll(preferences);
        } else {
            source.sendSuccess(() -> Component.translatable(CONFIG + "client.none", value("config/" + ClientConfig.FILE)), false);
        }
        source.sendSuccess(() -> Component.translatable(CONFIG + "registries"), false);
        for (ConfigReport.RegistryReport registry : ConfigReport.registries(source.getServer())) {
            Component line = registry.fromOtherPacks().isEmpty()
                    ? Component.translatable(CONFIG + "registry", value(registry.registry()), value(registry.size()))
                    : Component.translatable(CONFIG + "registry.other", value(registry.registry()), value(registry.size()),
                            value(registry.fromOtherPacks().size()).withStyle(ChatFormatting.GOLD));
            source.sendSuccess(() -> line, false);
            for (ConfigReport.Entry entry : registry.fromOtherPacks()) {
                Component other = Component.translatable(CONFIG + "entry." + entry.origin().getSerializedName(),
                        value(entry.id()), value(entry.pack()));
                source.sendSuccess(() -> other, false);
            }
        }
        return settings.size();
    }

    /** One line per value: where it comes from, and its default beside it when it is not the default. */
    private static void values(CommandSourceStack source, List<ConfigReport.Setting> settings) {
        for (ConfigReport.Setting setting : settings) {
            Component line = setting.changed()
                    ? Component.translatable(CONFIG + "value." + setting.source().getSerializedName(),
                            Component.literal(setting.key()), value(setting.value()).withStyle(ChatFormatting.GOLD),
                            value(setting.defaultValue()))
                    : Component.translatable(CONFIG + "value.default", Component.literal(setting.key()), value(setting.value()));
            source.sendSuccess(() -> line, false);
        }
    }

    private static net.minecraft.network.chat.MutableComponent value(Object value) {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.AQUA);
    }
}
