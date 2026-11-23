package com.warlonmhite.hempdustry.fabric.client;

import com.warlonmhite.hempdustry.client.anim.Gestures;
import com.warlonmhite.hempdustry.network.PlayerGesture;
import com.warlonmhite.hempdustry.config.ShownConfig;
import com.warlonmhite.hempdustry.network.ServerSettings;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.warlonmhite.hempdustry.HempdustryClient;
import com.warlonmhite.hempdustry.item.ModItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;

/** Fabric's client entry point. */
public class HempdustryFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        HempdustryClient.init();
        ClientPlayNetworking.registerGlobalReceiver(PlayerGesture.TYPE, (payload, context) -> Gestures.receive(payload));
        ClientConfigurationNetworking.registerGlobalReceiver(ServerSettings.TYPE, (payload, context) -> ShownConfig.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ServerSettings.TYPE, (payload, context) -> ShownConfig.receive(payload));
        // Fabric's armour hook draws the flip-flops in place of the boots layer.
        ArmorRenderer.register(FlipFlopsRenderer::new, ModItems.FLIP_FLOPS);
    }
}
