package com.warlonmhite.hempdustry.strain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.Hempdustry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Says in the log when a datapack replaces one of the mod's own strains with a file that leaves out a key
 * the mod's file sets: most often a copy made from an older version, which then reads the default where
 * the mod meant something else (a Beldía file copied from 2.1 reads THC-dominant, with no terpene and the
 * default sheets). A file may leave those keys out on purpose, so this warns and changes nothing.
 */
public final class StrainOverrides {
    /** The keys a file of 2.1 never had, each with what a strain reads it as. */
    private static final Map<String, Function<Strain, Object>> KEYS_SINCE_2_2 = Map.of(
            "chemotype", strain -> strain.traits().chemotype(),
            "terpene", strain -> strain.traits().terpene(),
            "smoke_color", strain -> strain.traits().smokeColor(),
            "potency", strain -> strain.traits().potency(),
            "jar_color", strain -> strain.traits().jarColour(),
            "plant", strain -> List.of(strain.traits().quality(), strain.traits().resistances(), strain.traits().resin()));

    private StrainOverrides() {
    }

    /**
     * Checks every strain the mod ships against the file that loaded, once the server is up. The file that loaded is
     * the highest whose strain is the one running, not merely the highest: a loader's load conditions can turn the top
     * file off (NeoForge's {@code neoforge:conditions}, Fabric's {@code fabric:load_conditions}), and blaming it
     * warned on every start about a file the game never read.
     */
    public static void report(MinecraftServer server) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Registry<Strain> strains = server.registryAccess().lookupOrThrow(Strain.REGISTRY_KEY);
        for (ResourceKey<Strain> key : ModStrains.BUILT_IN) {
            Identifier file = Identifier.fromNamespaceAndPath(key.identifier().getNamespace(),
                    Hempdustry.MOD_ID + "/strain/" + key.identifier().getPath() + ".json");
            List<Resource> stack = server.getResourceManager().getResourceStack(file);
            Strain running = strains.getValue(key);
            if (stack.size() < 2 || running == null) {
                continue;
            }
            try {
                // Lowest first: the mod's own file at the bottom.
                List<JsonObject> files = new ArrayList<>();
                for (Resource resource : stack) {
                    files.add(read(resource));
                }
                int loaded = loadedFile(files, running, ops);
                if (loaded <= 0) {
                    continue;
                }
                List<String> missing = missingKeys(files.get(loaded), files.getFirst(), ops);
                if (!missing.isEmpty()) {
                    Hempdustry.LOGGER.warn("Strain {} is replaced by pack {}, whose file has no {}; it reads the "
                                    + "default for each, where Hempdustry's own file sets them. A file copied from an "
                                    + "older version? Copy those keys over from the mod's file to keep them.",
                            key.identifier(), stack.get(loaded).sourcePackId(), String.join(", ", missing));
                }
            } catch (IOException | RuntimeException e) {
                // A file the registry could not read never got here; one this cannot read is no reason to stop.
                Hempdustry.LOGGER.debug("Could not compare strain file {} with the mod's own", file, e);
            }
        }
    }

    /**
     * Which of {@code files} (one strain's, lowest first, the mod's own at 0) the strain {@code running} came from:
     * the highest that reads as it, or -1 when none does (something changed it after loading). A file turned off
     * by a load condition reads as something else and is passed over.
     */
    public static int loadedFile(List<JsonObject> files, Strain running, HolderLookup.Provider registries) {
        return loadedFile(files, running, RegistryOps.create(JsonOps.INSTANCE, registries));
    }

    private static int loadedFile(List<JsonObject> files, Strain running, RegistryOps<JsonElement> ops) {
        for (int index = files.size() - 1; index >= 0; index--) {
            if (Strain.CODEC.parse(ops, files.get(index)).result().filter(running::equals).isPresent()) {
                return index;
            }
        }
        return -1;
    }

    /**
     * The keys of 2.2 that {@code override} leaves out and {@code ours} sets to something else than leaving
     * them out reads as, in key order. Both are strain files; a key the override names, even to the default,
     * is its own choice and never listed.
     */
    public static List<String> missingKeys(JsonObject override, JsonObject ours, HolderLookup.Provider registries) {
        return missingKeys(override, ours, RegistryOps.create(JsonOps.INSTANCE, registries));
    }

    private static List<String> missingKeys(JsonObject override, JsonObject ours, RegistryOps<JsonElement> ops) {
        Strain theirs = Strain.CODEC.parse(ops, override).getOrThrow();
        Strain mine = Strain.CODEC.parse(ops, ours).getOrThrow();
        List<String> missing = new ArrayList<>();
        for (String key : KEYS_SINCE_2_2.keySet().stream().sorted().toList()) {
            Function<Strain, Object> read = KEYS_SINCE_2_2.get(key);
            if (!override.has(key) && ours.has(key) && !Objects.equals(read.apply(theirs), read.apply(mine))) {
                missing.add(key);
            }
        }
        return missing;
    }

    private static JsonObject read(Resource resource) throws IOException {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
