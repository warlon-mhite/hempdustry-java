package com.warlonmhite.hempdustry.strain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.warlonmhite.hempdustry.Hempdustry;
import net.minecraft.core.HolderLookup;
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
            "chemotype", Strain::chemotype,
            "terpene", Strain::terpene,
            "smoke_color", Strain::smokeColor,
            "potency", Strain::potency,
            "jar_color", Strain::jarColour,
            "plant", Strain::plant);

    private StrainOverrides() {
    }

    /** Checks every strain the mod ships against the file that won, once the server is up. */
    public static void report(MinecraftServer server) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        for (ResourceKey<Strain> key : ModStrains.BUILT_IN) {
            Identifier file = Identifier.fromNamespaceAndPath(key.identifier().getNamespace(),
                    Hempdustry.MOD_ID + "/strain/" + key.identifier().getPath() + ".json");
            List<Resource> stack = server.getResourceManager().getResourceStack(file);
            if (stack.size() < 2) {
                continue;
            }
            // Lowest first: the mod's own file at the bottom, the one that won on top.
            Resource ours = stack.getFirst();
            Resource winner = stack.getLast();
            try {
                List<String> missing = missingKeys(read(winner), read(ours), ops);
                if (!missing.isEmpty()) {
                    Hempdustry.LOGGER.warn("Strain {} is replaced by pack {}, whose file has no {}; it reads the "
                                    + "default for each, where Hempdustry's own file sets them. A file copied from an "
                                    + "older version? Copy those keys over from the mod's file to keep them.",
                            key.identifier(), winner.sourcePackId(), String.join(", ", missing));
                }
            } catch (IOException | RuntimeException e) {
                // A file the registry could not read never got here; one this cannot read is no reason to stop.
                Hempdustry.LOGGER.debug("Could not compare strain file {} with the mod's own", file, e);
            }
        }
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
