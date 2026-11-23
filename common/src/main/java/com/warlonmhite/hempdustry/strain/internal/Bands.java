package com.warlonmhite.hempdustry.strain.internal;

import com.warlonmhite.hempdustry.balance.Genetics;
import com.warlonmhite.hempdustry.component.ModComponents;
import com.warlonmhite.hempdustry.strain.Potency;
import com.warlonmhite.hempdustry.strain.PotencyBand;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** What a {@link PotencyBand} does, and what a {@link Potency} sheet makes of it. */
public final class Bands {
    /** Vanilla's harmful-effect red: an extra-high bud greens out a pipe of two. */
    public static final int EXTRA_HIGH_COLOR = 0xFF5555;

    private Bands() {
    }

    /** A sheet at the default base with these odds, low first. */
    public static Potency odds(int low, int mid, int high, int extraHigh) {
        return new Potency(Potency.DEFAULT_BASE, Map.of(PotencyBand.LOW, low, PotencyBand.MID, mid,
                PotencyBand.HIGH, high, PotencyBand.EXTRA_HIGH, extraHigh));
    }

    /**
     * The decarboxylated hemp a bud of {@code band} adds to (or takes from) a mid one's, as the world's
     * {@code genetics} has it: built in, low −1, high +1, extra-high +2, {@link PotencyBand#offset}. Mid is always 0,
     * so a bud from before bands bakes as it did. {@link PotencyBand#offset} itself stays the band's place on the
     * ladder, what a Stash Jar averages its buds' bands by.
     */
    public static int offset(Genetics genetics, PotencyBand band) {
        Genetics.BandOffsets offsets = genetics.bandOffsets();
        return switch (band) {
            case LOW -> offsets.low();
            case MID -> 0;
            case HIGH -> offsets.high();
            case EXTRA_HIGH -> offsets.extraHigh();
        };
    }

    /** Decarboxylated hemp in one bud of {@code band}: the sheet's base moved by the band, never below one. */
    public static int decarbCount(Genetics genetics, Potency potency, PotencyBand band) {
        return Math.max(1, potency.base() + offset(genetics, band));
    }

    /** Whether a plant of {@code band} marks its leaves {@link PotencyBand#HIGH}. */
    public static boolean marksLeaf(PotencyBand band) {
        return band == PotencyBand.HIGH || band == PotencyBand.EXTRA_HIGH;
    }

    /**
     * Writes {@code band} on {@code stack}; mid writes nothing. Returns the stack. See
     * {@link ModComponents#putDefaulted}.
     */
    public static ItemStack stamp(ItemStack stack, PotencyBand band) {
        return ModComponents.putDefaulted(stack, ModComponents.POTENCY_BAND, band, PotencyBand.MID);
    }

    /** The tooltip line: grey, but red for extra-high. */
    public static Component tooltipLine(PotencyBand band) {
        Component line = Component.translatable(band.getTranslationKey());
        return band == PotencyBand.EXTRA_HIGH
                ? line.copy().withColor(EXTRA_HIGH_COLOR)
                : line.copy().withStyle(ChatFormatting.GRAY);
    }
}
