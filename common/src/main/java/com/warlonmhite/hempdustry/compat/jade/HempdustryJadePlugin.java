package com.warlonmhite.hempdustry.compat.jade;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.custom.GrowClimateLine;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CropBlock;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade's plugin: a plant's grow climate under the crosshair ({@link GrowClimateLine}). Jade finds it by the
 * annotation on NeoForge and by the {@code jade} entrypoint on Fabric, and nothing else loads it.
 *
 * <p>Client only: the line is read from the world the client holds, so it shows on a server without Jade too.
 * Jade gives the line a switch of its own in its plugin settings, named by its id.
 */
@WailaPlugin(Hempdustry.MOD_ID)
public final class HempdustryJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Every plant of the mod is a crop; the line itself tells ours from the rest.
        registration.registerBlockComponent(GrowClimate.INSTANCE, CropBlock.class);
    }

    private enum GrowClimate implements IBlockComponentProvider {
        INSTANCE;

        private static final Identifier UID = Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, GrowClimateLine.ID);

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            GrowClimateLine.of(accessor.getLevel(), accessor.getPosition())
                    .ifPresent(reading -> GrowClimateLine.lines(reading).forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
