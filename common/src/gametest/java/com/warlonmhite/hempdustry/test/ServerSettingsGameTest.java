package com.warlonmhite.hempdustry.test;

import com.mojang.authlib.GameProfile;
import com.warlonmhite.hempdustry.block.entity.custom.DecarboxylatorBlockEntity;
import com.warlonmhite.hempdustry.block.entity.custom.HempPressBlockEntity;
import com.warlonmhite.hempdustry.compat.ViewerRecipes;
import com.warlonmhite.hempdustry.config.HempdustryConfig;
import com.warlonmhite.hempdustry.config.ShownConfig;
import com.warlonmhite.hempdustry.network.ServerSettings;
import com.warlonmhite.hempdustry.platform.Services;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;

/**
 * The server's own machine timings, sent to clients ({@link ServerSettings}) and shown from there ({@link ShownConfig}).
 *
 * <p>What a server can check is checked here: the payload is built from the running config and comes back off the
 * wire as it went on; the configuration step hands the connection exactly that payload and asks for no answer; what a
 * client shows follows what it was sent and leaves its own file alone; and a player whose connection has no channel
 * for the mod is sent nothing. That a real client receives it while configuring, before its recipe viewer builds its
 * pages, and forgets it when a connection starts being configured or drops while it is, only a client shows.
 */
public final class ServerSettingsGameTest {
    private ServerSettingsGameTest() {
    }

    /**
     * The payload carries the running config, survives the wire, and is what the configuration step sends. A client
     * shows what it was sent -- clamped as the file is, a key it was not sent from its own file, a key it does not know
     * passed over -- while its own config stays as it was, and shows its own again once it forgets.
     */
    public static void theServersTimingsReachWhatAClientShows(GameTestHelper context) {
        ServerLevel world = context.getLevel();
        ShownConfig.forget();
        try {
            ConfigGameTest.withConfig("{ \"world\": { \"machineSpeedMultiplier\": 2.5 },"
                    + " \"infuser\": { \"minTimeTicks\": 1200, \"fullTimeTicks\": 4000 } }", () -> {
                ServerSettings sent = ServerSettings.of(HempdustryConfig.get());
                context.assertTrue(sent.values().equals(Map.of(ServerSettings.MACHINE_SPEED, 2.5,
                                ServerSettings.INFUSER_MIN, 1200.0, ServerSettings.INFUSER_FULL, 4000.0)),
                        "the payload does not carry the running config: " + sent);
                ByteBuf wire = Unpooled.buffer();
                ServerSettings.CODEC.encode(wire, sent);
                ServerSettings read = ServerSettings.CODEC.decode(wire);
                context.assertTrue(read.equals(sent) && wire.readableBytes() == 0,
                        "the payload came off the wire as " + read + ", not " + sent);

                ConfigurationTask step = ServerSettings.task();
                List<Packet<?>> packets = new ArrayList<>();
                step.start(packets::add);
                context.assertTrue(packets.size() == 1 && packets.getFirst() instanceof ClientboundCustomPayloadPacket custom
                                && custom.payload().equals(sent),
                        "the configuration step sent " + packets + ", not the running config");
                context.assertTrue(step.tick(), "the configuration step waits for an answer no client sends");
            });

            // The file is back to its defaults: a client that heard 2.5 still shows the server's numbers.
            HempdustryConfig own = HempdustryConfig.get();
            ShownConfig.receive(new ServerSettings(Map.of(ServerSettings.MACHINE_SPEED, 2.5,
                    ServerSettings.INFUSER_MIN, 1200.0, ServerSettings.INFUSER_FULL, 4000.0)));
            context.assertTrue(ShownConfig.machineSpeedMultiplier() == 2.5
                            && ShownConfig.infuser().equals(new HempdustryConfig.Infuser(1200, 4000)),
                    "a client shows " + ShownConfig.machineSpeedMultiplier() + " and " + ShownConfig.infuser()
                            + " after hearing 2.5, 1200 and 4000");
            context.assertTrue(HempdustryConfig.get() == own, "hearing the server's values changed the client's own config");
            expectNote(context, ViewerRecipes.decarboxylating(world).getFirst().notes(),
                    String.valueOf(DecarboxylatorBlockEntity.cookTime(2.5) / 20));
            expectNote(context, ViewerRecipes.pressing(world).getFirst().notes(),
                    String.valueOf(HempPressBlockEntity.pressTime(2.5) / 20));
            expectNote(context, ViewerRecipes.infusing(world).getFirst().notes(), "1", "3");

            // A server that sends past the range is clamped as a file would be (1e9, a value the log gate allows the
            // clamp line for); one that sends less leaves the rest
            // to the client's own file, and a key no build knows is passed over.
            ShownConfig.receive(new ServerSettings(Map.of(ServerSettings.MACHINE_SPEED, 1.0E9, "world.notYetAKey", 1.0)));
            context.assertTrue(ShownConfig.machineSpeedMultiplier() == 20.0,
                    "a sent 1e9 is shown as " + ShownConfig.machineSpeedMultiplier() + ", not clamped to 20");
            context.assertTrue(ShownConfig.infuser().equals(own.infuser()),
                    "infuser times nobody sent are shown as " + ShownConfig.infuser() + ", not the client's own");

            ShownConfig.forget();
            context.assertTrue(ShownConfig.machineSpeedMultiplier() == own.world().machineSpeedMultiplier()
                            && ShownConfig.infuser().equals(own.infuser()),
                    "a client that left the server still shows what it sent");
        } finally {
            ShownConfig.forget();
        }
        context.succeed();
    }

    /**
     * Nobody without the mod's channel is sent the values: not a player whose connection never agreed to it (a test
     * player, as a client without the mod), alone or among every player after a reload, and not a fake player.
     * NeoForge throws on a payload sent to such a connection.
     */
    public static void theTimingsPassOverPlayersWithoutTheChannel(GameTestHelper context) {
        Players.Watched watched = Players.watched(context);
        ServerPlayer machine = TestPlatform.INSTANCE.fakePlayer(context.getLevel(),
                new GameProfile(UUID.randomUUID(), "test-machine"));
        watched.sent();
        ServerSettings settings = ServerSettings.of(HempdustryConfig.get());
        Services.NETWORK.sendTo(watched.player(), settings);
        Services.NETWORK.sendTo(machine, settings);
        ServerSettings.sendToAll(context.getLevel().getServer());
        List<Packet<?>> got = watched.sent();
        context.assertTrue(got.stream().noneMatch(packet -> packet instanceof ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof ServerSettings),
                "a player without the mod's channel was sent the server's timings");
        context.succeed();
    }

    /** {@code notes} holds a line whose arguments are {@code args}, in order. */
    private static void expectNote(GameTestHelper context, List<Component> notes, String... args) {
        for (Component note : notes) {
            if (note.getContents() instanceof TranslatableContents translatable
                    && List.of(translatable.getArgs()).containsAll(List.of(args))
                    && translatable.getArgs().length >= args.length) {
                return;
            }
        }
        throw context.assertionException(Component.literal("no viewer note shows " + List.of(args) + " in " + notes));
    }
}
