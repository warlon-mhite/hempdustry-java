package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.block.custom.PlantStress;
import com.warlonmhite.hempdustry.block.custom.SiftingBoxBlock;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.compat.ViewerRecipes;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.ResinTexture;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.internal.Bands;
import com.warlonmhite.hempdustry.strain.internal.Hardiness;
import com.warlonmhite.hempdustry.strain.internal.Resins;
import com.warlonmhite.hempdustry.strain.internal.Tiers;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;

/**
 * {@code genetics/default.json}: the numbers of the bud tiers, the potency bands, the resin steps and the resistance
 * steps as data. The shipped file is the code's old constants exactly, a file may hold any part of them, the bounds
 * that keep a tier from buying a level refuse a file that crosses them, and the rules and the viewer pages read the
 * file they are handed.
 */
public final class GeneticsGameTest {

    /**
     * The built-in genetics are the numbers the rules had in code before the file, one by one, and the file the mod
     * ships reads as exactly those.
     */
    public static void theGeneticsAreTheAgreedOnes(GameTestHelper context) {
        Genetics built = Genetics.BUILT_IN;
        Genetics.TierTable tiers = built.tiers();
        context.assertTrue(tiers.mids().equals(new Genetics.Tier(60, -10, 1.5F, 0.75F, 1.0F, 1.0F))
                        && tiers.dank().equals(new Genetics.Tier(35, 4, 1.0F, 1.0F, 0.75F, 0.75F))
                        && tiers.topShelf().equals(new Genetics.Tier(0, 6, 0.5F, 1.25F, 0.5F, 0.5F))
                        && tiers.epic().equals(new Genetics.Tier(-6, 2, 0.0F, 1.5F, 0.0F, 0.25F)),
                "the tiers are not Mids 60/-10, Dank 35/+4, Top Shelf 0/+6, Epic -6/+2 with 2.2's factors: " + tiers);
        context.assertTrue(built.bandOffsets().equals(new Genetics.BandOffsets(-1, 1, 2)),
                "the band offsets are not low -1, high +1, extra-high +2: " + built.bandOffsets());
        for (PotencyBand band : PotencyBand.values()) {
            context.assertTrue(Bands.offset(built, band) == band.offset(),
                    "the built-in " + band + " bakes at " + Bands.offset(built, band) + ", not its place " + band.offset());
        }
        Genetics.Resin resin = built.resin();
        context.assertTrue(resin.sieve().equals(new Genetics.Steps(0.5F, 0.75F, 1.5F, 2.0F))
                        && resin.rubOneIn().equals(new Genetics.RubOdds(8, 6, 3, 2))
                        && resin.sieveShare().equals(new Genetics.Shares(1.0F, 0.5F, 0.5F))
                        && resin.rubShare().equals(new Genetics.Shares(0.0F, 0.5F, 1.0F)),
                "the resin is not sieve x0.5/x0.75/x1.5/x2, rub 1 in 8/6/3/2, shares dry 1/0, even 1/2, sticky 1/2 and 1: "
                        + resin);
        Genetics.Resistance resistance = built.resistance();
        context.assertTrue(resistance.stressFactor().equals(new Genetics.Hardy<>(2.0F, 1.5F, 0.5F, 0.25F))
                        && resistance.lightLossAge().equals(new Genetics.Hardy<>(3, 3, 4, 4))
                        && resistance.ignoresLostLight().equals(new Genetics.Ignores(0.5F, 0.75F)),
                "the resistance is not x2/x1.5/x1/2/x1/4, light lost from 3/3/4/4, shrugged 1/2 and 3/4: " + resistance);
        // The middle of each ladder is no number of the file's.
        context.assertTrue(Hardiness.factor(built, Resistance.NEUTRAL) == 1.0F
                        && Hardiness.lightLossAge(built, Resistance.NEUTRAL) == 4
                        && Resins.sieveFactor(built, ResinStep.AVERAGE, ResinTexture.STICKY) == 1.0F
                        && Resins.rubChance(built, ResinStep.AVERAGE, ResinTexture.DRY) == 1.0F / 4,
                "neutral or average does not read x1, age 4, x1 and 1 in 4");

        Genetics shipped = Genetics.of(context.getLevel().registryAccess());
        context.assertTrue(shipped.equals(built), "the shipped genetics/default.json is not the built-in genetics: " + shipped);
        context.succeed();
    }

    /**
     * A genetics file may hold any part of the genetics: every key it leaves out, at the top or inside an object, reads
     * as the built-in number. The mod's own file is written in full, and a number past its bound refuses the file:
     * the bounds that keep a tier from buying a level and each step on its own side of the middle.
     */
    public static void aPartialGeneticsFileLoads(GameTestHelper context) {
        Genetics built = Genetics.BUILT_IN;
        context.assertTrue(read("{}").equals(built), "an empty genetics file is not the built-in genetics: " + read("{}"));
        Genetics epic = read("{\"tiers\": {\"epic\": {\"duration\": 2.0}}}");
        context.assertTrue(epic.tiers().epic().equals(new Genetics.Tier(-6, 2, 0.0F, 2.0F, 0.0F, 0.25F))
                        && epic.tiers().mids().equals(built.tiers().mids()) && epic.bandOffsets().equals(built.bandOffsets())
                        && epic.resin().equals(built.resin()) && epic.resistance().equals(built.resistance()),
                "a file holding only Epic's duration did not keep every other number: " + epic);
        Genetics high = read("{\"band_offsets\": {\"high\": 2}, \"resistance\": {\"light_loss_age\": {\"weak\": 2}}}");
        context.assertTrue(high.bandOffsets().equals(new Genetics.BandOffsets(-1, 2, 2))
                        && high.resistance().lightLossAge().equals(new Genetics.Hardy<>(3, 2, 4, 4))
                        && high.resistance().stressFactor().equals(built.resistance().stressFactor()),
                "a file holding one number in two objects did not keep the rest of each: " + high);

        JsonObject written = Genetics.CODEC.encodeStart(JsonOps.INSTANCE, built).getOrThrow().getAsJsonObject();
        context.assertTrue(written.keySet().equals(Set.of("tiers", "band_offsets", "resin", "resistance")),
                "the built-in genetics are not written in full: " + written.keySet());
        context.assertTrue(read(written.toString()).equals(built), "the built-in genetics do not read back as themselves");
        // Every key, one at a time, at every depth: the file without it is still the built-in genetics.
        List<List<String>> paths = new ArrayList<>();
        collect(written, new ArrayList<>(), paths);
        for (List<String> path : paths) {
            JsonObject without = written.deepCopy();
            JsonObject parent = without;
            for (String part : path.subList(0, path.size() - 1)) {
                parent = parent.getAsJsonObject(part);
            }
            parent.remove(path.getLast());
            var result = Genetics.CODEC.parse(JsonOps.INSTANCE, without);
            context.assertTrue(result.isSuccess() && result.getOrThrow().equals(built),
                    "a genetics file without " + String.join(".", path) + " is not the built-in genetics: " + result);
        }
        context.assertTrue(paths.size() >= 60, "only " + paths.size() + " genetics keys were left out one at a time");

        // Each bound, crossed by one number: a tier never takes an effect away or lifts it past the band's stretch,
        // a band stays within its side, and a step never crosses the middle of its ladder.
        Map<String, String> crossings = new TreeMap<>();
        for (String file : List.of(
                "{\"tiers\": {\"mids\": {\"duration\": 0.2}}}",
                "{\"tiers\": {\"epic\": {\"duration\": 2.5}}}",
                "{\"tiers\": {\"dank\": {\"cough\": 4.5}}}",
                "{\"tiers\": {\"top_shelf\": {\"green_out\": -0.5}}}",
                "{\"tiers\": {\"epic\": {\"quality\": 65}}}",
                "{\"band_offsets\": {\"low\": 1}}",
                "{\"band_offsets\": {\"high\": -1}}",
                "{\"band_offsets\": {\"extra_high\": 5}}",
                "{\"resin\": {\"sieve\": {\"low\": 1.25}}}",
                "{\"resin\": {\"sieve\": {\"rich\": 0.75}}}",
                "{\"resin\": {\"rub_one_in\": {\"very_low\": 3}}}",
                "{\"resin\": {\"rub_one_in\": {\"very_rich\": 5}}}",
                "{\"resin\": {\"rub_share\": {\"sticky\": 1.5}}}",
                "{\"resistance\": {\"stress_factor\": {\"weak\": 0.5}}}",
                "{\"resistance\": {\"stress_factor\": {\"resistant\": 1.5}}}",
                "{\"resistance\": {\"light_loss_age\": {\"very_weak\": 5}}}",
                "{\"resistance\": {\"light_loss_age\": {\"very_resistant\": 3}}}",
                "{\"resistance\": {\"ignores_lost_light\": {\"resistant\": 1.25}}}")) {
            if (!Genetics.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(file)).isError()) {
                crossings.put(file, "loaded");
            }
        }
        Sweep.report(context, "Every genetics bound refuses a file that crosses it", crossings, Map.of());
        context.succeed();
    }

    /**
     * The rules read the numbers they are handed, not their own: a file that moves a tier, a band, a resin step or a
     * resistance step moves what that rule answers, and the recipe viewers' resin lines name the file's range, in
     * every locale that has them.
     */
    public static void theRulesReadTheGenetics(GameTestHelper context) {
        Genetics moved = read("""
                {"tiers": {"top_shelf": {"weight": 10, "cough": 0.25, "duration": 1.75, "nausea": 0.25, "green_out": 0.0}},
                 "band_offsets": {"low": -2, "high": 3, "extra_high": 4},
                 "resin": {"sieve": {"very_low": 0.25, "very_rich": 3.0}, "rub_one_in": {"very_low": 16, "very_rich": 1},
                           "sieve_share": {"even": 1.0}},
                 "resistance": {"stress_factor": {"very_weak": 4.0, "very_resistant": 0.0},
                                "light_loss_age": {"weak": 2, "resistant": 6},
                                "ignores_lost_light": {"resistant": 0.0}}}""");
        context.assertValueEqual(Tiers.weightAt(moved, BudTier.TOP_SHELF, 1.0F), 16, "Top Shelf's weight at luck 1");
        context.assertValueEqual(Tiers.weightAt(moved, BudTier.MIDS, 1.0F), 50, "Mids' weight at luck 1, unmoved");
        context.assertTrue(Tiers.coughFactor(moved, BudTier.TOP_SHELF) == 0.25F
                        && Tiers.nauseaFactor(moved, BudTier.TOP_SHELF) == 0.25F
                        && Tiers.greenOutFactor(moved, BudTier.TOP_SHELF) == 0.0F,
                "Top Shelf's smoking factors did not follow the file");
        context.assertValueEqual(Tiers.scaleDuration(moved, BudTier.TOP_SHELF, 400), 700, "a Top Shelf entry's 400 ticks");
        context.assertValueEqual(Tiers.scaleDuration(moved, BudTier.EPIC, 400), 600, "an Epic entry's 400 ticks, unmoved");
        context.assertValueEqual(Bands.offset(moved, PotencyBand.LOW), -2, "a low bud's offset");
        context.assertValueEqual(Bands.offset(moved, PotencyBand.MID), 0, "a mid bud's offset, which no file moves");
        context.assertValueEqual(Bands.offset(moved, PotencyBand.EXTRA_HIGH), 4, "an extra-high bud's offset");
        context.assertTrue(Resins.sieveFactor(moved, ResinStep.VERY_LOW, ResinTexture.DRY) == 0.25F
                        && Resins.sieveFactor(moved, ResinStep.VERY_RICH, ResinTexture.EVEN) == 3.0F
                        && Resins.sieveFactor(moved, ResinStep.VERY_RICH, ResinTexture.STICKY) == 2.0F
                        && Resins.rubChance(moved, ResinStep.VERY_RICH, ResinTexture.STICKY) == 1.0F
                        && Resins.rubChance(moved, ResinStep.VERY_LOW, ResinTexture.STICKY) == 1.0F / 16,
                "the resin steps did not follow the file");
        context.assertTrue(Hardiness.factor(moved, Resistance.VERY_WEAK) == 4.0F
                        && Hardiness.apply(moved, Resistance.VERY_RESISTANT, 0.5F) == 0.0F
                        && Hardiness.lightLossAge(moved, Resistance.WEAK) == 2
                        // and the night leak a touchy plant takes from the same age (built in, 3)
                        && PlantStress.touchyAge(moved) == 2 && PlantStress.touchyAge(Genetics.BUILT_IN) == 3
                        && Hardiness.lightLossAge(moved, Resistance.RESISTANT) == 6
                        && !Hardiness.ignoresLostLight(moved, Resistance.RESISTANT, RandomSource.create(1L)),
                "the resistance steps did not follow the file");

        Map<String, JsonObject> locales = new TreeMap<>();
        for (Map.Entry<String, java.nio.file.Path> file : Sweep.modFiles().entrySet()) {
            if (file.getKey().startsWith("assets/hempdustry/lang/")) {
                locales.put(file.getKey().substring(file.getKey().lastIndexOf('/') + 1).replace(".json", ""),
                        Sweep.json(file.getValue()).getAsJsonObject());
            }
        }
        JsonObject english = locales.get("en_us");
        JsonObject french = locales.get("fr_fr");
        Genetics shipped = Genetics.of(context.getLevel().registryAccess());
        expect(context, english, ViewerRecipes.sieveResin(shipped, 1.0F).getFirst(), "A bud's resin moves it: about 4 to 14 per batch");
        expect(context, english, ViewerRecipes.rubResin(shipped), "A plant's resin moves it: 1 in 8 to 1 in 2");
        expect(context, french, ViewerRecipes.sieveResin(shipped, 1.0F).getFirst(), "La résine d'une fleur le change : environ 4 à 14 par fournée");
        expect(context, french, ViewerRecipes.rubResin(shipped), "La résine du plant le change : 1 sur 8 à 1 sur 2");

        float[] movedRange = Resins.sieveRange(moved);
        String movedFewest = String.valueOf(Math.round(SiftingBoxBlock.FULL_LEVEL / movedRange[1]));
        String movedMost = String.valueOf(Math.round(SiftingBoxBlock.FULL_LEVEL / movedRange[0]));
        Map<String, String> findings = new TreeMap<>();
        int checked = 0;
        for (Map.Entry<String, JsonObject> locale : locales.entrySet()) {
            for (Map.Entry<Component, List<String>> want : Map.of(
                    ViewerRecipes.sieveResin(moved, 1.0F).getFirst(), List.of(movedFewest, movedMost),
                    ViewerRecipes.rubResin(moved), List.of("16", "1")).entrySet()) {
                String key = ((TranslatableContents) want.getKey().getContents()).getKey();
                if (!locale.getValue().has(key)) continue;
                checked++;
                String text = render(locale.getValue(), want.getKey());
                if (text.contains("%") || !want.getValue().stream().allMatch(text::contains)) {
                    findings.put(locale.getKey() + " " + key, text);
                }
            }
        }
        Sweep.atLeast(context, "resin lines in the locales", checked, 16);
        Sweep.report(context, "Every resin line shows the moved genetics' range", findings, Map.of());

        // A pack may set a step to sift nothing: the sieve pages then carry a second resin line, and a dry row (rate
        // and resin lines) and a wash row (one jacket line more) must still fit the room their page asks for.
        Map<String, Genetics> sieves = Map.of("shipped", shipped, "moved", moved,
                "a step sifting nothing", read("{\"resin\": {\"sieve\": {\"very_low\": 0.0}}}"),
                "both low steps sifting nothing", read("{\"resin\": {\"sieve\": {\"very_low\": 0.0, \"low\": 0.0}}}"));
        Map<String, String> over = new TreeMap<>();
        for (Map.Entry<String, Genetics> sieve : sieves.entrySet()) {
            int resin = ViewerRecipes.sieveResin(sieve.getValue(), 1.0F).size();
            if (1 + resin > ViewerRecipes.noteLines(ViewerRecipes.SIFTING)
                    || 2 + resin > ViewerRecipes.noteLines(ViewerRecipes.ICE_O_LATOR)) {
                over.put(sieve.getKey(), resin + " resin lines");
            }
        }
        context.assertValueEqual(ViewerRecipes.sieveResin(sieves.get("a step sifting nothing"), 1.0F).size(), 2,
                "resin lines where one step sifts nothing");
        Sweep.report(context, "Every sieve row fits its page at any genetics", over, Map.of());
        context.succeed();
    }

    private static void expect(GameTestHelper context, JsonObject lang, Component line, String expected) {
        String text = render(lang, line);
        context.assertTrue(text.equals(expected), "a resin line at the shipped genetics reads \"" + text + "\", not \""
                + expected + "\"");
    }

    /** {@code line} as {@code lang} formats it, its translated arguments filled in the same way. */
    private static String render(JsonObject lang, Component line) {
        if (!(line.getContents() instanceof TranslatableContents contents)) {
            return line.getString();
        }
        Object[] args = contents.getArgs().clone();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Component nested) {
                args[i] = render(lang, nested);
            }
        }
        return String.format(Locale.ROOT, lang.get(contents.getKey()).getAsString(), args);
    }

    private static void collect(JsonObject object, List<String> prefix, List<List<String>> out) {
        for (String key : object.keySet()) {
            List<String> path = new ArrayList<>(prefix);
            path.add(key);
            out.add(path);
            if (object.get(key).isJsonObject()) {
                collect(object.getAsJsonObject(key), path, out);
            }
        }
    }

    /**
     * {@code Genetics.running()}, what a rule reached with no world in hand reads ({@code SmokeContents#effects}): on
     * the server's thread the server's genetics, on any other thread the built-in ones, never the server's read from
     * there, and wherever the client answers (on its own thread, in a world) the client's, as its server synced them.
     * That the client answers only on its own thread and in a world only a client shows.
     */
    public static void rulesWithoutAWorldReadTheirThreadsGenetics(GameTestHelper context) {
        HolderLookup.Provider real = context.getLevel().registryAccess();
        Genetics servers = Genetics.of(real);
        context.assertTrue(servers != Genetics.BUILT_IN, "the test world's genetics are the built-in object, not its file");
        context.assertTrue(Genetics.running() == servers, "on the server's thread a rule does not read the server's genetics");

        Genetics[] elsewhere = new Genetics[1];
        Thread other = new Thread(() -> elsewhere[0] = Genetics.running(), "hempdustry-gametest-genetics");
        other.start();
        try {
            other.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw context.assertionException(Component.literal("interrupted waiting for the other thread"));
        }
        context.assertTrue(elsewhere[0] == Genetics.BUILT_IN,
                "off the server's thread a rule read " + elsewhere[0] + ", not the built-in genetics");

        Genetics synced = read("{\"band_offsets\": {\"low\": -2, \"high\": 3, \"extra_high\": 4}}");
        MappedRegistry<Genetics> clients = new MappedRegistry<>(Genetics.REGISTRY_KEY, Lifecycle.stable());
        Registry.register(clients, Genetics.DEFAULT, synced);
        HolderLookup.Provider client = new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return real.listRegistryKeys();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Optional<? extends HolderLookup.RegistryLookup<T>> lookup(
                    ResourceKey<? extends Registry<? extends T>> key) {
                return key.equals(Genetics.REGISTRY_KEY)
                        ? Optional.of((HolderLookup.RegistryLookup<T>) (Object) clients)
                        : real.lookup(key);
            }
        };
        Supplier<HolderLookup.Provider> before = Genetics.clientRegistries;
        try {
            Genetics.clientRegistries = () -> client;
            context.assertTrue(Genetics.running() == synced, "where the client answers, a rule does not read its genetics");
            Genetics.clientRegistries = () -> null;
            context.assertTrue(Genetics.running() == servers,
                    "where the client does not answer, the server's thread no longer reads the server's genetics");
        } finally {
            Genetics.clientRegistries = before;
        }
        context.succeed();
    }

    private static Genetics read(String json) {
        return Genetics.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private GeneticsGameTest() {
    }
}
