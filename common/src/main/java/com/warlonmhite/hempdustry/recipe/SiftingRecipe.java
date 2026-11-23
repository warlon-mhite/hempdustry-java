package com.warlonmhite.hempdustry.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.block.custom.SiftingBoxBlock;
import com.warlonmhite.hempdustry.block.custom.SiftingBoxBlock.Content;
import com.warlonmhite.hempdustry.strain.internal.FileFields;
import com.warlonmhite.hempdustry.strain.ResinStep;
import com.warlonmhite.hempdustry.strain.ResinTexture;
import com.warlonmhite.hempdustry.strain.Strain;
import com.warlonmhite.hempdustry.strain.internal.Resins;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/**
 * What one piece of something is worth on the Sifting Box's screen: which screen it goes on, and how far it
 * moves it.
 *
 * <pre>{@code
 * {
 *   "type": "hempdustry:sifting",
 *   "ingredient": "#hempdustry:siftable/flower",
 *   "screen": "plant",
 *   "levels": 1.0,
 *   "resin_levels": 1.0
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code ingredient}: what the box takes.</li>
 *   <li>{@code screen} ({@code plant} or {@code kief}, default {@code plant}): which pass it belongs to. Plant
 *       matter and powder never share a screen, and only a plant screen can be washed.</li>
 *   <li>{@code levels} (0 to {@value SiftingBoxBlock#FULL_LEVEL}): how far one piece moves the screen, the whole
 *       part for certain and the fraction as a roll. A screen is full at {@value SiftingBoxBlock#FULL_LEVEL}, so
 *       a piece at 1 fills a batch in seven and one at 0.15 in about 47. A wash multiplies it by its jacket.</li>
 *   <li>{@code resin_levels} (optional, same range): a bud that carries its plant's resin step moves this
 *       many levels times the step's sieve factor ({@code genetics}' {@code resin.sieve}, for its strain's
 *       texture) instead. A strain's bud with no step written on it carries the one its stamp leaves unwritten:
 *       very rich in {@code #hempdustry:siftable/resinous} (a 2.1 Beldía bud sieves as a very rich dry one, two
 *       levels, as it always has), average in any other tag. Anything that is no strain's bud carries no step and
 *       moves {@code levels}. Left out, a step changes nothing.</li>
 * </ul>
 *
 * <p><b>The recipe names an input, never an output.</b> What a batch becomes is the box's process: a dry plant
 * screen gives kief, a washed one bubble hash, a kief screen filtered kief, {@value SiftingBoxBlock#YIELD} at a
 * time ({@link SiftingBoxBlock#yieldOf}). A batch mixes whatever shares its screen, so an output per input would
 * leave a mixed batch with no answer, and a ready box broken drops its batch by the same rule from its loot
 * table. {@link #assemble} answers the dry yield so a reader of the recipe sees what the screen makes.
 *
 * <p><b>A piece two recipes name takes the one it would sieve fastest by</b> ({@link #levelsFor}: a bud's step
 * read where the recipe reads one, else {@code levels}), then the lowest id. That keeps what the box did before its
 * rates were data — a Beldía bud in both the resinous and the flower tags sieved by the resinous rate — and a pack's
 * recipe at a flat rate never hides what a richer bud's step is worth under the mod's own.
 *
 * <p><b>With no recipe naming it, the box takes nothing</b>: a pack that removes every sifting recipe has a box
 * that sifts nothing, as removing every infusing recipe leaves an Infuser that takes nothing. A batch already in
 * a box can still be taken out, since collecting asks no recipe.
 */
public record SiftingRecipe(Ingredient ingredient, Content screen, float levels, Optional<Float> resinLevels)
        implements Recipe<SingleRecipeInput> {

    private static final Codec<Float> LEVELS = Codec.floatRange(0.0F, SiftingBoxBlock.FULL_LEVEL);
    private static final Codec<Content> SCREEN = StringRepresentable.fromEnum(Content::values);

    /**
     * The most levels one piece of {@code stack} moves a screen at full rate, or 0 for none: its resin step's
     * when it carries one ({@link #stepOf}) and this recipe reads it, else {@link #levels}.
     */
    public float levelsFor(ItemStack stack, HolderLookup.Provider registries) {
        if (resinLevels.isPresent()) {
            Optional<ResinStep> step = stepOf(stack, registries);
            if (step.isPresent()) {
                return resinLevels.get() * Resins.sieveFactor(Genetics.of(registries), step.get(), textureOf(stack, registries));
            }
        }
        return levels;
    }

    /**
     * The resin step a piece sieves by: the one written on it, else for a strain's bud the step its stamp leaves
     * unwritten ({@link Resins#legacy}: very rich in {@code #hempdustry:siftable/resinous}, average in any other tag,
     * so such a bud stacks with the ones harvested before resin), else none. Every bud of a strain carries a step,
     * so every one is read by the same rule at a recipe with {@code resin_levels}.
     */
    public static Optional<ResinStep> stepOf(ItemStack stack, HolderLookup.Provider registries) {
        Optional<ResinStep> written = Resins.written(stack);
        if (written.isPresent()) {
            return written;
        }
        return Strain.fromBuds(registries, stack.getItem()).isPresent()
                ? Optional.of(Resins.legacy(stack)) : Optional.empty();
    }

    /** The resin texture of the strain whose buds {@code stack} is, even for anything else. */
    private static ResinTexture textureOf(ItemStack stack, HolderLookup.Provider registries) {
        return Strain.fromBuds(registries, stack.getItem())
                .map(Holder::value)
                .map(strain -> strain.traits().resin().texture())
                .orElse(ResinTexture.EVEN);
    }

    /** The recipe the box at {@code level} sifts {@code stack} by, if any names it. */
    public static Optional<SiftingRecipe> of(Level level, ItemStack stack) {
        return pick(ModRecipes.allOfType(level, ModRecipes.SIFTING_TYPE), stack, level.registryAccess())
                .map(RecipeHolder::value);
    }

    /**
     * Of {@code recipes}, the one {@code stack} sifts by: the most levels it would actually move ({@link #levelsFor}),
     * then the lowest id.
     */
    public static Optional<RecipeHolder<SiftingRecipe>> pick(Collection<RecipeHolder<SiftingRecipe>> recipes, ItemStack stack,
                                                             HolderLookup.Provider registries) {
        return recipes.stream()
                .filter(holder -> holder.value().ingredient().test(stack))
                .max(Comparator.<RecipeHolder<SiftingRecipe>>comparingDouble(holder -> holder.value().levelsFor(stack, registries))
                        .thenComparing(holder -> holder.id().identifier(), Comparator.reverseOrder()));
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level world) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return new ItemStack(SiftingBoxBlock.yieldOf(screen, false), SiftingBoxBlock.YIELD);
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(ingredient);
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    /** Never shown: the book knows only the crafting types. */
    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<? extends SiftingRecipe> getSerializer() {
        return ModRecipes.SIFTING;
    }

    @Override
    public RecipeType<? extends SiftingRecipe> getType() {
        return ModRecipes.SIFTING_TYPE;
    }

    public static final MapCodec<SiftingRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("ingredient").forGetter(SiftingRecipe::ingredient),
            FileFields.written(SCREEN, "screen", Content.PLANT).forGetter(SiftingRecipe::screen),
            LEVELS.fieldOf("levels").forGetter(SiftingRecipe::levels),
            LEVELS.optionalFieldOf("resin_levels").forGetter(SiftingRecipe::resinLevels)
    ).apply(instance, SiftingRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SiftingRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, SiftingRecipe::ingredient,
            ByteBufCodecs.idMapper(i -> Content.values()[i], Content::ordinal), SiftingRecipe::screen,
            ByteBufCodecs.FLOAT, SiftingRecipe::levels,
            ByteBufCodecs.optional(ByteBufCodecs.FLOAT), SiftingRecipe::resinLevels,
            SiftingRecipe::new);
}
