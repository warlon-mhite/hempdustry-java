package com.warlonmhite.hempdustry.compat.wthit;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.block.custom.GrowClimateLine;
import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.CropBlock;

/**
 * WTHIT's plugin: a plant's grow climate under the crosshair ({@link GrowClimateLine}), as Jade shows it. WTHIT
 * finds it through {@code waila_plugins.json} on both loaders, and nothing else loads it.
 *
 * <p>The common half only names the line's switch, a setting of the player's own ({@code localConfig}); the line is
 * read from the world the client holds, so it shows on a server without WTHIT too.
 */
public final class HempdustryWthitPlugin implements IWailaCommonPlugin, IWailaClientPlugin {
    private static final Identifier UID = Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, GrowClimateLine.ID);

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.localConfig(UID, true);
    }

    @Override
    public void register(IClientRegistrar registrar) {
        // Every plant of the mod is a crop; the line itself tells ours from the rest.
        registrar.body(GrowClimate.INSTANCE, CropBlock.class);
    }

    private enum GrowClimate implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
            if (config.getBoolean(UID)) {
                GrowClimateLine.of(accessor.getLevel(), accessor.getPosition())
                        .ifPresent(reading -> GrowClimateLine.lines(reading).forEach(tooltip::addLine));
            }
        }
    }
}
