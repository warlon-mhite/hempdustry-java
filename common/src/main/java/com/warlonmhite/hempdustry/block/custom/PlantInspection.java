package com.warlonmhite.hempdustry.block.custom;

import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.balance.GrowClimate;
import com.warlonmhite.hempdustry.block.entity.custom.PlantRecordBlockEntity;
import com.warlonmhite.hempdustry.config.ModGameRules;
import com.warlonmhite.hempdustry.strain.BudTier;
import com.warlonmhite.hempdustry.strain.Resistance;
import com.warlonmhite.hempdustry.strain.ResistanceGenes;
import com.warlonmhite.hempdustry.strain.StressPhenomenon;
import com.warlonmhite.hempdustry.strain.internal.Hardiness;
import com.warlonmhite.hempdustry.strain.internal.Strains;
import com.warlonmhite.hempdustry.strain.internal.Tiers;
import com.warlonmhite.hempdustry.util.ModTags;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What {@code /hempdustry inspect} says about a plant of the mod: its strain and age, the genes its record rolled at
 * planting, the tier odds of a harvest now, its light and its records, and <b>why</b> it stands as it does: what its
 * next growth step would stress it with, its odds of mildew, and what holds its growth back.
 *
 * <p>Every fact is read the way the plant's own rules read it, from the same methods, and nothing is changed or rolled:
 * a gene a record has not rolled yet reads "not rolled" rather than being rolled by the asking. Only the server knows
 * the record, so this runs there. A value that is data vocabulary (a resistance step, a light record, a mildew state)
 * is shown by its id, as a datapack and the debug screen write it; the sentences around them are translated.
 */
public final class PlantInspection {
    private static final String KEY = "commands.hempdustry.inspect.";

    private PlantInspection() {
    }

    /** The lines about the plant whose block is at {@code pos}, or nothing when no plant of the mod stands there. */
    public static Optional<List<Component>> of(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof CropBlock crop) || !ownCrop(block)) {
            return Optional.empty();
        }
        BlockPos root = TallHemp.rootOf(block, world, pos, state);
        if (root == null) {
            root = pos;
        }
        BlockState plant = world.getBlockState(root);
        int age = crop.getAge(plant);
        int maxAge = crop.getMaxAge();
        List<Component> lines = new ArrayList<>();
        String strain = Strains.ofPlant(block, world.registryAccess())
                .map(holder -> holder.key().identifier().toString())
                .orElse("-");
        lines.add(Component.translatable(KEY + "plant", block.getName().withStyle(ChatFormatting.GREEN),
                position(root), value(strain), value(age), value(maxAge)));

        PlantRecordBlockEntity record = world.getBlockEntity(root) instanceof PlantRecordBlockEntity found ? found : null;
        if (record == null) {
            lines.add(Component.translatable(KEY + "no_record"));
        } else {
            genes(lines, record, plant);
        }
        light(lines, world, root, plant, record);
        stress(lines, world, root, plant, record, age, maxAge);
        mildew(lines, world, root, plant, age, maxAge);
        growth(lines, world, root, plant, age, maxAge);
        return Optional.of(lines);
    }

    /** A plant whose block is one of the mod's crops, the ones that keep its rules. */
    private static boolean ownCrop(Block block) {
        return block instanceof IndicaCropBlock || block instanceof SativaCropBlock || block instanceof EnderHazeCropBlock
                || block instanceof RuderalisCropBlock;
    }

    private static void genes(List<Component> lines, PlantRecordBlockEntity record, BlockState plant) {
        boolean buds = !(plant.getBlock() instanceof IndustrialHempCropBlock);
        Component band = buds ? record.rolledBand().<Component>map(b -> Component.translatable(b.getTranslationKey()))
                .orElseGet(PlantInspection::unrolled) : noBuds();
        Component gene = buds ? record.rolledGene().<Component>map(PlantInspection::value).orElseGet(PlantInspection::unrolled)
                : noBuds();
        Component resin = record.rolledResin().<Component>map(r -> Component.translatable(r.getTranslationKey()))
                .orElseGet(PlantInspection::unrolled);
        lines.add(Component.translatable(KEY + "genes", band, gene, resin));
        Optional<ResistanceGenes> genes = record.rolledResistances();
        lines.add(Component.translatable(KEY + "resistances",
                genes.<Component>map(g -> value(g.get(StressPhenomenon.DRY_GROUND))).orElseGet(PlantInspection::unrolled),
                genes.<Component>map(g -> value(g.get(StressPhenomenon.OVERWATERING))).orElseGet(PlantInspection::unrolled),
                genes.<Component>map(g -> value(g.get(StressPhenomenon.LIGHT))).orElseGet(PlantInspection::unrolled),
                genes.<Component>map(g -> value(g.get(StressPhenomenon.MILDEW))).orElseGet(PlantInspection::unrolled)));
        if (!buds) {
            return;
        }
        if (GrowLuck.givesMids(plant)) {
            lines.add(Component.translatable(KEY + "tiers.mids"));
            return;
        }
        if (record.rolledGene().isEmpty()) {
            return;
        }
        // No harvester: a player's own Luck would add to this, as it does at the harvest.
        float luck = GrowLuck.of(record, plant, record.getLevel(), null);
        Genetics genetics = Genetics.of(record.getLevel().registryAccess());
        int total = 0;
        for (BudTier tier : BudTier.values()) {
            total += Tiers.weightAt(genetics, tier, luck);
        }
        List<Component> odds = new ArrayList<>();
        for (BudTier tier : BudTier.values()) {
            int weight = Tiers.weightAt(genetics, tier, luck);
            if (weight > 0) {
                odds.add(Component.translatable(KEY + "tier", Component.translatable(tier.getTranslationKey()),
                        value(Math.round(100.0F * weight / Math.max(1, total)))));
            }
        }
        lines.add(Component.translatable(KEY + "tiers", value(luck), join(odds)));
    }

    private static void light(List<Component> lines, Level world, BlockPos root, BlockState plant,
                              PlantRecordBlockEntity record) {
        if (plant.hasProperty(GrowLight.PROPERTY)) {
            lines.add(Component.translatable(KEY + "light", value(plant.getValue(GrowLight.PROPERTY)),
                    value(GrowLight.over(world, root, height(plant)))));
        }
        if (record != null) {
            lines.add(Component.translatable(KEY + "records", kept(record.skyRecord()),
                    kept(record.skyRecord().map(sky -> record.workingTray())),
                    kept(record.skyRecord().map(sky -> record.ventilated())), yesNo(record.sawNight())));
        }
    }

    private static void stress(List<Component> lines, Level world, BlockPos root, BlockState plant,
                               PlantRecordBlockEntity record, int age, int maxAge) {
        if (!plant.hasProperty(GrowLight.PROPERTY)) {
            return;
        }
        GrowLight kept = plant.getValue(GrowLight.PROPERTY);
        if (kept == GrowLight.STRESSED) {
            lines.add(Component.translatable(KEY + "stressed").withStyle(ChatFormatting.RED));
            if (!PlantStress.switchedOn(world)) {
                lines.add(Component.translatable(KEY + "stress.off", value(ModGameRules.Switch.PLANT_STRESS.offBy())));
            }
            return;
        }
        if (!PlantStress.switchedOn(world)) {
            lines.add(Component.translatable(KEY + "stress.off", value(ModGameRules.Switch.PLANT_STRESS.offBy())));
            return;
        }
        if (!stressable(plant)) {
            lines.add(Component.translatable(KEY + "stress.never"));
            return;
        }
        if (age >= maxAge) {
            lines.add(Component.translatable(KEY + "stress.ripe"));
            return;
        }
        ResistanceGenes genes = PlantRecordBlockEntity.resistancesAt(world, root);
        Resistance lightStep = genes.get(StressPhenomenon.LIGHT);
        Genetics genetics = Genetics.of(world.registryAccess());
        int height = height(plant);
        int next = age + 1;
        IndicaCropBlock indica = plant.getBlock() instanceof IndicaCropBlock found ? found : null;
        // The light first, as a growth step reads it, then the ground: the same order and the same rules.
        if (indica != null && indica.shunsLight()) {
            int light = EchoOgCropBlock.lightAtTop(world, root);
            if (next >= Hardiness.lightLossAge(genetics, lightStep) && light > EchoOgCropBlock.MAX_LIGHT) {
                lines.add(Component.translatable(KEY + "stress.bright", value(light), value(EchoOgCropBlock.MAX_LIGHT))
                        .withStyle(ChatFormatting.GOLD));
                return;
            }
        } else if (indica != null && indica.leaksAtNight() && PlantStress.hours(world, root) == PlantStress.Hours.NIGHT
                && next >= PlantStress.touchyAge(genetics) && PlantStress.leaks(world, root, height)) {
            lines.add(Component.translatable(KEY + "stress.leak").withStyle(ChatFormatting.GOLD));
            return;
        } else if (indica == null || !indica.leaksAtNight() || PlantStress.hours(world, root) == PlantStress.Hours.DAY) {
            GrowLight over = GrowLight.over(world, root, height);
            if (kept.afterStep(age, over, Hardiness.lightLossAge(genetics, lightStep)) == GrowLight.STRESSED) {
                lines.add(Component.translatable(KEY + "stress.light", value(kept), value(over))
                        .withStyle(ChatFormatting.GOLD));
                return;
            }
        }
        if (next < GrowLight.FLOWERING_AGE) {
            lines.add(Component.translatable(KEY + "stress.young", value(GrowLight.FLOWERING_AGE)));
            return;
        }
        GrowClimate.Stress odds = PlantStress.odds(world);
        StressCause ground = PlantStress.ofGround(world.getBlockState(root.below()), odds);
        float chance = ground.chanceFor(genetics, genes);
        if (chance > 0.0F) {
            lines.add(Component.translatable(KEY + "stress.risk", oneIn(chance), value(ground.phenomenon()))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable(KEY + "stress.none"));
        }
    }

    /** Whether anything can stress this plant at all: it keeps a light record, and its kind takes stress. */
    private static boolean stressable(BlockState plant) {
        Block block = plant.getBlock();
        if (block instanceof IndicaCropBlock indica) {
            return indica.recordsLight();
        }
        return !(block instanceof SativaCropBlock sativa) || sativa.canBeStressed();
    }

    private static void mildew(List<Component> lines, Level world, BlockPos root, BlockState plant, int age, int maxAge) {
        if (!plant.hasProperty(Mildew.PROPERTY)) {
            return;
        }
        boolean roofed = Mildew.roofed(world, root);
        lines.add(Component.translatable(KEY + "mildew", value(plant.getValue(Mildew.PROPERTY)),
                value(Climate.air(world, root, height(plant), roofed)),
                value(Climate.humidity(world, root, roofed, Climate.onTray(world, root)))));
        if (!ModGameRules.Switch.MILDEW.on(world)) {
            lines.add(Component.translatable(KEY + "mildew.off", value(ModGameRules.Switch.MILDEW.offBy())));
        } else if (!(plant.getBlock() instanceof Mildew.Host host) || !host.catchesMildew()
                || plant.is(ModTags.Blocks.MILDEW_RESISTANT)) {
            lines.add(Component.translatable(KEY + "mildew.never"));
        } else if (plant.getValue(Mildew.PROPERTY) != Mildew.MILDEWED && age < maxAge) {
            float chance = Mildew.onsetFor(world, root, plant);
            lines.add(chance > 0.0F
                    ? Component.translatable(KEY + "mildew.risk", oneIn(chance))
                    : Component.translatable(KEY + "mildew.none"));
        }
    }

    private static void growth(List<Component> lines, Level world, BlockPos root, BlockState plant, int age, int maxAge) {
        Block block = plant.getBlock();
        if (age >= maxAge) {
            lines.add(Component.translatable(KEY + "growth.ripe").withStyle(ChatFormatting.GREEN));
            return;
        }
        boolean light = block instanceof IndicaCropBlock indica
                ? indica.lightLetsItGrow(world, root) : world.getRawBrightness(root, 0) >= 9;
        if (!light) {
            lines.add(Component.translatable(KEY + "growth.dark", value(world.getRawBrightness(root, 0)))
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        int room = block instanceof IndicaCropBlock indica ? age + indica.stepsLeft(world, root, plant)
                : block instanceof SativaCropBlock sativa ? sativa.maxAgeFor(world, root)
                : block instanceof EnderHazeCropBlock haze ? haze.maxAgeFor(world, root)
                : maxAge;
        if (room <= age) {
            lines.add(Component.translatable(KEY + "growth.held").withStyle(ChatFormatting.GOLD));
            return;
        }
        BlockState floor = world.getBlockState(block instanceof IndicaCropBlock indica && indica.hangs()
                ? root.above() : root.below());
        boolean dry = floor.getBlock() instanceof GrowMedium medium ? medium.moisture(floor) <= 0.0F
                : block instanceof IndicaCropBlock indica && indica.fieldMoisture(world, root) <= 0.0F;
        lines.add(dry
                ? Component.translatable(KEY + "growth.dry").withStyle(ChatFormatting.GOLD)
                : Component.translatable(KEY + "growth.ok"));
    }

    /** How tall the plant {@code plant} is grown, where its light is read. */
    private static int height(BlockState plant) {
        return plant.getBlock() instanceof EnderHazeCropBlock ? EnderHazeCropBlock.HEIGHT : Climate.heightOf(plant);
    }

    /** A chance as "1 in N", the way the grow climate's file writes its odds. */
    private static Component oneIn(float chance) {
        return Component.translatable(KEY + "one_in", value(Math.max(1, Math.round(1.0F / chance))));
    }

    private static Component kept(Optional<Boolean> record) {
        return record.map(PlantInspection::yesNo).orElseGet(() -> Component.translatable(KEY + "not_yet"));
    }

    private static Component yesNo(boolean yes) {
        return yes ? CommonComponents.GUI_YES : CommonComponents.GUI_NO;
    }

    private static Component unrolled() {
        return Component.translatable(KEY + "unrolled");
    }

    private static Component noBuds() {
        return Component.translatable(KEY + "no_buds");
    }

    private static Component position(BlockPos pos) {
        return value(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** A value as it is written in data: its id for an enum, the number or text otherwise. */
    static MutableComponent value(Object value) {
        String text = value instanceof StringRepresentable named ? named.getSerializedName()
                : value instanceof Float number ? String.format(Locale.ROOT, "%.1f", number)
                : String.valueOf(value);
        return Component.literal(text).withStyle(ChatFormatting.AQUA);
    }

    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }
}
