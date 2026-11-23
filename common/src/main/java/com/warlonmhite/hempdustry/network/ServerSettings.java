package com.warlonmhite.hempdustry.network;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.config.HempdustryConfig;
import com.warlonmhite.hempdustry.platform.Services;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;

/**
 * The server's own config values that a client shows a player: how long the Decarboxylator, the Hemp Press and the
 * Infuser take, on the recipe viewers' pages and in the press's platen. The machines run on the server's file; a
 * client reading its own would show its own numbers on someone else's server. What a client does with these is
 * {@code ShownConfig}'s.
 *
 * <p>Sent while the connection is still being configured ({@link #task()}), before the client joins the world:
 * the recipe viewers build their pages as the recipes arrive, which is before anything sent on joining would land.
 * Sent again to every player after {@code /hempdustry reload} ({@link #sendToAll}), so the press keeps the new
 * rhythm at once; the viewers' pages are built and stay as they are until the recipes are sent again.
 *
 * <p>The wire carries each value under its config key ({@code world.machineSpeedMultiplier}), not in a fixed order:
 * a build that sends one more or one fewer still talks with this one, a key it does not know is passed over, and a
 * key it did not send is the client's own. Values go through the same clamps as the file's.
 *
 * @param values each sent value, by its key in {@code config/hempdustry.json}
 */
public record ServerSettings(Map<String, Double> values) implements CustomPacketPayload {
    public static final Type<ServerSettings> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "server_settings"));

    public static final String MACHINE_SPEED = "world.machineSpeedMultiplier";
    public static final String INFUSER_MIN = "infuser.minTimeTicks";
    public static final String INFUSER_FULL = "infuser.fullTimeTicks";

    /** The configuration step that sends them, named as the payload is. */
    public static final ConfigurationTask.Type CONFIGURATION_STEP = new ConfigurationTask.Type(TYPE.id().toString());

    /** Far more keys than will ever be sent; a payload claiming more is refused rather than read. */
    private static final int MAX_KEYS = 256;

    /** Plain bytes, no registries: it is sent while configuring as well as in play. */
    public static final StreamCodec<ByteBuf, ServerSettings> CODEC =
            ByteBufCodecs.<ByteBuf, String, Double, Map<String, Double>>map(HashMap::new, ByteBufCodecs.STRING_UTF8,
                    ByteBufCodecs.DOUBLE, MAX_KEYS).map(ServerSettings::new, ServerSettings::values);

    public ServerSettings {
        values = Map.copyOf(values);
    }

    /** What {@code config} says a client should show. */
    public static ServerSettings of(HempdustryConfig config) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(MACHINE_SPEED, config.world().machineSpeedMultiplier());
        values.put(INFUSER_MIN, (double) config.infuser().minTimeTicks());
        values.put(INFUSER_FULL, (double) config.infuser().fullTimeTicks());
        return new ServerSettings(values);
    }

    /** The value sent under {@code key}, or {@code otherwise} when this one was not. */
    public double value(String key, double otherwise) {
        Double value = values.get(key);
        return value == null ? otherwise : value;
    }

    @Override
    public Type<ServerSettings> type() {
        return TYPE;
    }

    /**
     * The configuration step that sends the running config's values, for a connection that agreed to the mod's
     * channel (each loader asks before adding it). It waits for no answer: it is done on the connection's next tick.
     */
    public static ConfigurationTask task() {
        ServerSettings settings = of(HempdustryConfig.get());
        return new ConfigurationTask() {
            @Override
            public void start(Consumer<Packet<?>> sender) {
                sender.accept(new ClientboundCustomPayloadPacket(settings));
            }

            @Override
            public boolean tick() {
                return true;
            }

            @Override
            public ConfigurationTask.Type type() {
                return CONFIGURATION_STEP;
            }
        };
    }

    /** Sends the running config's values to every player on {@code server} whose client has the mod. */
    public static void sendToAll(MinecraftServer server) {
        ServerSettings settings = of(HempdustryConfig.get());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Services.NETWORK.sendTo(player, settings);
        }
    }
}
