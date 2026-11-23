package com.warlonmhite.hempdustry.mixin.client;

import com.warlonmhite.hempdustry.config.ShownConfig;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forgets what a server sent of its config ({@link ShownConfig}) whenever a connection starts being configured, and
 * when one drops while it is.
 *
 * <p>The values arrive in the configuration phase, before the player is in a world, and the loaders' leave-the-world
 * hooks fire only for a connection that reached play. A connection that drops between the two (a failed join) would
 * otherwise keep the first server's values for the next one, and a server that sends none (a build without the
 * payload, a server without the mod) would be shown with them. Every configuration, the first of a new connection
 * and a later one a server asks for alike, builds this listener before any of its packets are read, so the values
 * held from then on are only ever that server's. Vanilla's own class, so one hook serves both loaders.
 */
@Mixin(ClientConfigurationPacketListenerImpl.class)
public abstract class ClientConfigurationPacketListenerImplMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void hempdustry$forgetTheLastServer(CallbackInfo ci) {
        ShownConfig.forget();
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void hempdustry$forgetOnDroppingOut(DisconnectionDetails details, CallbackInfo ci) {
        ShownConfig.forget();
    }
}
