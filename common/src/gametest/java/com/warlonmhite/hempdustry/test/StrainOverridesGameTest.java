package com.warlonmhite.hempdustry.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.warlonmhite.hempdustry.strain.StrainOverrides;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;

/** A datapack file replacing one of the mod's strains is held against the mod's own file, key by key. */
public final class StrainOverridesGameTest {
    private StrainOverridesGameTest() {
    }

    /**
     * Beldía's file as 2.1 wrote it (no chemotype, terpene, potency or plant) lacks what the mod's own file
     * sets; the mod's own file lacks nothing; a key named, even to the default, is the file's own choice.
     */
    public static void aStrainFileFromAnOlderVersionIsNamed(GameTestHelper context) {
        JsonObject ours = modsOwn(context, "beldia");
        JsonObject old = ours.deepCopy();
        for (String key : List.of("chemotype", "terpene", "smoke_color", "potency", "jar_color", "plant")) {
            old.remove(key);
        }
        List<String> missing = StrainOverrides.missingKeys(old, ours, context.getLevel().registryAccess());
        context.assertTrue(missing.equals(List.of("chemotype", "plant", "potency", "terpene")),
                "a 2.1 copy of Beldía's file lacks " + missing + ", not [chemotype, plant, potency, terpene]");

        context.assertTrue(StrainOverrides.missingKeys(ours, ours, context.getLevel().registryAccess()).isEmpty(),
                "the mod's own Beldía file lacks something of itself");

        JsonObject chosen = old.deepCopy();
        chosen.addProperty("chemotype", "thc_dominant");
        context.assertTrue(!StrainOverrides.missingKeys(chosen, ours, context.getLevel().registryAccess()).contains("chemotype"),
                "a file naming the default chemotype is still told it lacks one");

        // Purple Kush's file is written in full: its numbers are its own, not whatever the default becomes.
        JsonObject kush = modsOwn(context, "indica");
        for (String key : List.of("chemotype", "potency", "plant")) {
            context.assertTrue(kush.has(key), "Purple Kush's own file leaves out " + key);
        }
        context.assertTrue(kush.getAsJsonObject("plant").has("grow_quality") && kush.getAsJsonObject("potency").has("base"),
                "Purple Kush's sheets are not written in full: " + kush);
        context.succeed();
    }

    private static JsonObject modsOwn(GameTestHelper context, String strain) {
        Identifier id = Identifier.fromNamespaceAndPath("hempdustry", "hempdustry/strain/" + strain + ".json");
        Resource resource = context.getLevel().getServer().getResourceManager().getResourceStack(id).getFirst();
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
