package com.warlonmhite.hempdustry.fabric;

import com.warlonmhite.hempdustry.network.PlayerGesture;
import com.warlonmhite.hempdustry.network.ServerSettings;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.entity.ModBlockEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;

/** Fabric's entry point: the mod, as the rest of the code registers it. */
public class HempdustryFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        Hempdustry.registerDynamicRegistries();
        Hempdustry.init();
        PayloadTypeRegistry.clientboundPlay().register(PlayerGesture.TYPE, PlayerGesture.CODEC);
        // The server's displayed values go out while the connection is configured, before the recipe viewers build
        // their pages, and again in play after /hempdustry reload.
        PayloadTypeRegistry.clientboundConfiguration().register(ServerSettings.TYPE, ServerSettings.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ServerSettings.TYPE, ServerSettings.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register((handler, server) -> {
            if (ServerConfigurationNetworking.canSend(handler, ServerSettings.TYPE)) {
                handler.addTask(ServerSettings.task());
            }
        });
        // Every other block entity of the mod is a container, which the transfer API wraps on its own;
        // the Stash Jar is not, so it says what it is.
        ItemStorage.SIDED.registerForBlockEntity((jar, side) -> jar.transferHandler(FabricStashJarStorage::new), ModBlockEntities.STASH_JAR);
        // A Dehumidifier's tank gives its water to a pipe, as a water cauldron does.
        FluidStorage.SIDED.registerForBlockEntity((tank, side) -> tank.transferHandler(FabricDehumidifierStorage::new),
                ModBlockEntities.DEHUMIDIFIER);
    }
}
