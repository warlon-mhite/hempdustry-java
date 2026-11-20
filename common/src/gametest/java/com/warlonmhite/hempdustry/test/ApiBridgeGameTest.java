package com.warlonmhite.hempdustry.test;

import com.warlonmhite.hempdustry.item.custom.SmokeContents;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.Chemotype;
import com.warlonmhite.hempdustry.strain.ModStrains;
import com.warlonmhite.hempdustry.strain.Strain;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * What 2.0 let an addon call on a strain and a load, still answering as 2.0 did.
 *
 * <p>{@code scripts/api_additive.py} proves every 2.0 member still links; this proves the bridges
 * say the right thing. 2.0 had no strain without a plant, no effect held to the exhale and no effect
 * that might not land, so a bridge has to decide what each of those looks like in 2.0's terms.
 */
@SuppressWarnings("deprecation")
public final class ApiBridgeGameTest {

    public static void theTwoZeroApiStillAnswers(GameTestHelper context) {
        Registry<Strain> strains = context.getLevel().registryAccess().lookupOrThrow(Strain.REGISTRY_KEY);
        Strain indica = strains.getOrThrow(ModStrains.INDICA).value();

        // 2.0's constructor and accessors: a plant strain, with the defaults 2.0 behaved by.
        Strain built = new Strain(indica.translationKey(), indica.color(), indica.modelIndex(),
                indica.seeds(), indica.buds(), indica.flower(), indica.smokeEffects());
        context.assertTrue(built.seedItem().equals(indica.seedItem()) && built.wildFlower().equals(indica.wildFlower()),
                "2.0's constructor lost Purple Kush's seeds or flower: " + built);
        context.assertTrue(built.greenOutFactor() == 1.0F && built.coughFactor() == 1.0F && built.dosePerItem() == 1,
                "2.0's constructor did not take 2.0's defaults: " + built);

        // A strain 2.0 never had, with no plant, answers air rather than null.
        Strain hashish = strains.getOrThrow(ModStrains.HASHISH).value();
        context.assertTrue(hashish.seeds() == Items.AIR && hashish.flower() == Blocks.AIR,
                "hashish's 2.0 seeds()/flower() are " + hashish.seeds() + " / " + hashish.flower() + ", not air");

        // effects(dose, duration) is the whole hit: the exhale's effects and the chance ones too.
        assertHas(context, strains.getOrThrow(ModStrains.BELDIA).value().effects(1, 100),
                MobEffects.BLINDNESS, "Beldía's exhale Blindness, from Strain#effects(int, int)");
        assertHas(context, strains.getOrThrow(ModStrains.SCHWAG).value().effects(1, 100),
                MobEffects.POISON, "schwag's 60% Poison, from Strain#effects(int, int)");
        assertHas(context, SmokeContents.of(strains.getOrThrow(ModStrains.BELDIA), 1).effects(100),
                MobEffects.BLINDNESS, "Beldía's exhale Blindness, from SmokeContents#effects(int)");
        assertHas(context, SmokeContents.of(strains.getOrThrow(ModStrains.SCHWAG), 1).effects(100),
                MobEffects.POISON, "schwag's 60% Poison, from SmokeContents#effects(int)");
        context.succeed();
    }

    /**
     * 2.1's constructors build a strain with the default traits, as every file 2.1 wrote reads, and a strain's
     * later traits come in one block: a strain built 2.1's way and given the registered one's traits is the
     * registered one.
     */
    public static void theTwoOneConstructorsReadTheDefaultTraits(GameTestHelper context) {
        Registry<Strain> strains = context.getLevel().registryAccess().lookupOrThrow(Strain.REGISTRY_KEY);
        Strain beldia = strains.getOrThrow(ModStrains.BELDIA).value();
        Strain built = new Strain(beldia.translationKey(), beldia.color(), beldia.modelIndex(), beldia.seedItem(),
                beldia.buds(), beldia.wildFlower(), beldia.greenOutFactor(), beldia.dosePerItem(), beldia.smokeEffects(),
                beldia.coughFactor());
        context.assertValueEqual(Strain.Traits.DEFAULT, built.traits(), "a strain built with 2.1's constructor");
        context.assertValueEqual(Chemotype.THC_DOMINANT, built.traits().chemotype(), "2.1's default chemotype");
        context.assertValueEqual(beldia, built.withTraits(beldia.traits()), "2.1's Beldía given its traits");
        SmokeContents.Entry entry = new SmokeContents.Entry(strains.getOrThrow(ModStrains.SCHWAG), 2);
        context.assertTrue(entry.stamp() == Chemotype.THC_DOMINANT && entry.potency() == 0 && entry.tier() == BudTier.DANK,
                "2.1's entry constructor is not unstamped, mid and Dank: " + entry);
        context.succeed();
    }

    /**
     * A strain may have no registry id (a bred one, from the breeding update): a load of one reads and smokes as a
     * load of its registered twin, its name included, so nothing on the way from a load to the player asks for an id.
     */
    public static void aStrainWithNoIdSmokesLikeItsTwin(GameTestHelper context) {
        Registry<Strain> strains = context.getLevel().registryAccess().lookupOrThrow(Strain.REGISTRY_KEY);
        for (ResourceKey<Strain> key : List.of(ModStrains.SATIVA, ModStrains.BELDIA, ModStrains.CHERRY_WINE,
                ModStrains.HASHISH)) {
            Holder<Strain> named = strains.getOrThrow(key);
            Holder<Strain> unnamed = Holder.direct(named.value());
            context.assertTrue(unnamed.unwrapKey().isEmpty(), "a direct holder has an id");
            SmokeContents twin = new SmokeContents(List.of(new SmokeContents.Entry(named, 2, Chemotype.BALANCED, 1,
                    BudTier.TOP_SHELF)));
            SmokeContents bred = new SmokeContents(List.of(new SmokeContents.Entry(unnamed, 2, Chemotype.BALANCED, 1,
                    BudTier.TOP_SHELF)));
            for (boolean exhale : new boolean[] {false, true}) {
                context.assertValueEqual(describe(twin.effects(700, exhale, () -> 0L)),
                        describe(bred.effects(700, exhale, () -> 0L)), key.identifier() + " with no id, exhale " + exhale);
            }
            context.assertValueEqual(twin.loadName(), bred.loadName(), key.identifier() + "'s name with no id");
            context.assertValueEqual(twin.color(), bred.color(), key.identifier() + "'s colour with no id");
            context.assertValueEqual(twin.entries().getFirst().chemotype(), bred.entries().getFirst().chemotype(),
                    key.identifier() + "'s chemotype with no id");
        }
        context.succeed();
    }

    private static List<String> describe(List<MobEffectInstance> effects) {
        return effects.stream().map(effect -> effect.getEffect().getRegisteredName() + " " + effect.getAmplifier() + " "
                + effect.getDuration()).toList();
    }

    private static void assertHas(GameTestHelper context, List<MobEffectInstance> effects,
                                  Holder<MobEffect> effect, String what) {
        ResourceKey<MobEffect> key = effect.unwrapKey().orElseThrow();
        context.assertTrue(effects.stream().anyMatch(instance -> instance.getEffect().is(key)),
                what + " is missing: " + effects);
    }
}
