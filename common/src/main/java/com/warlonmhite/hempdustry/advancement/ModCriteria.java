package com.warlonmhite.hempdustry.advancement;

import com.warlonmhite.hempdustry.Hempdustry;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Custom advancement criteria (triggers). Registered eagerly at class-load; {@link #registerCriteria()}
 * only forces that init, mirroring the mod's other holder classes.
 */
public class ModCriteria {

    public static final SmokeCriterion SMOKE = Registry.register(
            BuiltInRegistries.TRIGGER_TYPES,
            Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "smoke"),
            new SmokeCriterion());

    public static final HarvestHempCriterion HARVEST_HEMP = Registry.register(
            BuiltInRegistries.TRIGGER_TYPES,
            Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "harvest_hemp"),
            new HarvestHempCriterion());

    public static final EdibleKicksInCriterion EDIBLE_KICKS_IN = Registry.register(
            BuiltInRegistries.TRIGGER_TYPES,
            Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "edible_kicks_in"),
            new EdibleKicksInCriterion());

    /**
     * A sculk sensor or a Warden let a vibration of yours go because of Hush: vanilla's
     * {@code avoid_vibration} (Sneak 100) in the same shape, for the smoke instead of the sneak. Fired
     * where vanilla fires that one, by a listener that can, so a vibration only a shrieker or an allay
     * would have heard does not count.
     */
    public static final PlayerTrigger HUSH_AVOIDED_VIBRATION = Registry.register(
            BuiltInRegistries.TRIGGER_TYPES,
            Identifier.fromNamespaceAndPath(Hempdustry.MOD_ID, "hush_avoided_vibration"),
            new PlayerTrigger());

    public static void registerCriteria() {
        Hempdustry.LOGGER.info("Registering Advancement Criteria for " + Hempdustry.MOD_ID);
    }
}
