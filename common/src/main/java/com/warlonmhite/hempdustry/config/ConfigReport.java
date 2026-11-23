package com.warlonmhite.hempdustry.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.balance.DeviceStats;
import com.warlonmhite.hempdustry.balance.EdibleBundle;
import com.warlonmhite.hempdustry.balance.GreenOut;
import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.platform.Services;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.Terpene;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.StringRepresentable;

/**
 * What a server runs with that is not the mod's own default: each config value and where it comes from, and every
 * entry of the mod's datapack registries that a pack other than the mod's own supplied. {@code /hempdustry config}
 * prints it.
 */
public final class ConfigReport {
    /** The six datapack registries, in the order the mod registers them. */
    public static final List<ResourceKey<? extends Registry<?>>> REGISTRIES = List.of(
            Terpene.REGISTRY_KEY, Strain.REGISTRY_KEY, DeviceStats.REGISTRY_KEY, EdibleBundle.REGISTRY_KEY,
            GreenOut.REGISTRY_KEY, GrowClimate.REGISTRY_KEY);

    private ConfigReport() {
    }

    /**
     * Where a value the server runs with comes from. The config file is read over the defaults and written back
     * whole, so a value equal to its default reads {@link #DEFAULT} wherever it was written, and one that differs
     * came from the file. A source a later build adds (a game rule that overrides the file) is one more constant.
     */
    public enum Source implements StringRepresentable {
        DEFAULT("default"),
        FILE("file");

        private final String name;

        Source(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** One config value: its {@code section.key}, the value running, its default, and where it comes from. */
    public record Setting(String key, String value, String defaultValue, Source source) {
        public boolean changed() {
            return source != Source.DEFAULT;
        }
    }

    /** Every value of the running config, section by section and key by key, both in alphabetical order. */
    public static List<Setting> settings() {
        return settings(HempdustryConfig.get());
    }

    /** Every value of {@code config}, against the defaults. */
    public static List<Setting> settings(HempdustryConfig config) {
        JsonObject running = encode(config);
        JsonObject defaults = encode(HempdustryConfig.DEFAULT);
        List<Setting> settings = new ArrayList<>();
        running.keySet().stream().sorted().forEach(section -> {
            JsonObject values = running.getAsJsonObject(section);
            JsonObject fallback = defaults.getAsJsonObject(section);
            values.keySet().stream().sorted().forEach(key -> {
                String value = text(values.get(key));
                String defaultValue = text(fallback.get(key));
                settings.add(new Setting(section + "." + key, value, defaultValue,
                        value.equals(defaultValue) ? Source.DEFAULT : Source.FILE));
            });
        });
        return settings;
    }

    private static JsonObject encode(HempdustryConfig config) {
        return HempdustryConfig.CODEC.encodeStart(JsonOps.INSTANCE, config).getOrThrow().getAsJsonObject();
    }

    private static String text(JsonElement value) {
        return value == null ? "" : value.isJsonPrimitive() ? value.getAsString() : value.toString();
    }

    /**
     * Where an entry of one of the mod's registries came from. {@link #OWN}: the mod's own pack.
     * {@link #REPLACED}: another pack put its own file over one the mod ships. {@link #ADDED}: a pack added an entry
     * the mod does not ship.
     */
    public enum Origin implements StringRepresentable {
        OWN("own"),
        REPLACED("replaced"),
        ADDED("added");

        private final String name;

        Origin(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** One registry entry another pack supplied: which, how, and the pack whose file won. */
    public record Entry(Identifier id, Origin origin, String pack) {
    }

    /** One of the mod's registries: how many entries it holds, and those another pack supplied. */
    public record RegistryReport(Identifier registry, int size, List<Entry> fromOtherPacks) {
    }

    /**
     * Each of the mod's registries as the server loaded them. Each entry carries the pack it was read from (its
     * {@link RegistrationInfo}); an entry from a pack that is not the mod's own is looked up in the server's data
     * to name the pack whose file won and to tell whether the mod ships one beneath it.
     */
    public static List<RegistryReport> registries(MinecraftServer server) {
        List<RegistryReport> reports = new ArrayList<>();
        ResourceManager resources = server.getResourceManager();
        for (ResourceKey<? extends Registry<?>> key : REGISTRIES) {
            server.registryAccess().lookup(key).ifPresent(registry -> reports.add(report(key, registry, resources)));
        }
        return reports;
    }

    private static <T> RegistryReport report(ResourceKey<? extends Registry<?>> key, Registry<T> registry,
                                             ResourceManager resources) {
        List<Entry> others = new ArrayList<>();
        registry.registryKeySet().stream().sorted(Comparator.comparing(ResourceKey::identifier)).forEach(entry -> {
            Optional<KnownPack> pack = registry.registrationInfo(entry).flatMap(RegistrationInfo::knownPackInfo);
            if (pack.isPresent() && Services.PLATFORM.isOwnPack(pack.get())) {
                return;
            }
            Identifier id = entry.identifier();
            List<Resource> stack = resources.getResourceStack(Identifier.fromNamespaceAndPath(id.getNamespace(),
                    Registries.elementsDirPath(key) + "/" + id.getPath() + ".json"));
            boolean ours = stack.stream().anyMatch(file -> file.knownPackInfo().map(Services.PLATFORM::isOwnPack).orElse(false));
            String winner = stack.isEmpty() ? pack.map(KnownPack::id).orElse("?") : stack.getLast().sourcePackId();
            others.add(new Entry(id, ours ? Origin.REPLACED : Origin.ADDED, winner));
        });
        return new RegistryReport(key.identifier(), registry.size(), others);
    }
}
