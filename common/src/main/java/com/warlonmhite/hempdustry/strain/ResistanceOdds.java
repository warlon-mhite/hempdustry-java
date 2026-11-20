package com.warlonmhite.hempdustry.strain;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * A strain's odds for one {@link StressPhenomenon}: the weight of each {@link Resistance} step a plant of it
 * rolls at planting. The weights need not add up to 100.
 *
 * <p>Most strains name only their most likely step, and the odds come from one shared spread ({@link #spread}):
 * 60 on the step, 25 one step away, 15 two away, each share split between both sides. Away from the ends, a
 * half that would fall off the scale lands on its end step; at an end step, the other side takes it all.
 *
 * <table>
 *   <tr><th>most likely</th><th>VW / W / N / R / VR</th></tr>
 *   <tr><td>very weak</td><td>60 / 25 / 15 / 0 / 0</td></tr>
 *   <tr><td>weak</td><td>19 / 62 / 12 / 7 / 0</td></tr>
 *   <tr><td>neutral</td><td>7 / 12 / 62 / 12 / 7</td></tr>
 *   <tr><td>resistant</td><td>0 / 7 / 12 / 62 / 19</td></tr>
 *   <tr><td>very resistant</td><td>0 / 0 / 15 / 25 / 60</td></tr>
 * </table>
 *
 * <p>Written in a strain's file either as that step, {@code "light": "resistant"}, or as a weight per step,
 * {@code "light": {"very_weak": 0, "weak": 5, "neutral": 20, "resistant": 60, "very_resistant": 15}} (a weight
 * left out is 0, and at least one must be more). Odds equal to a step's spread are written as the step.
 *
 * @param weights the weight of each step, one entry per step; a step the map leaves out weighs nothing
 */
public record ResistanceOdds(Map<Resistance, Integer> weights) {

    /** The shared spread's shares: on the step, one step away, two steps away. */
    private static final int[] SPREAD = {60, 25, 15};

    private static final Codec<ResistanceOdds> WEIGHTS_CODEC = Keyed.codec(Resistance.class, Codec.intRange(0, 1000),
                    step -> 0, Keyed::notZero)
            .xmap(ResistanceOdds::new, ResistanceOdds::weights).codec()
            .validate(odds -> odds.weights.values().stream().anyMatch(weight -> weight > 0) ? DataResult.success(odds)
                    : DataResult.error(() -> "resistance weights that add up to 0 roll nothing: give a step some weight, or leave the phenomenon out for the shared spread"));

    public static final Codec<ResistanceOdds> CODEC = Codec.either(Resistance.CODEC, WEIGHTS_CODEC).xmap(
            either -> either.map(ResistanceOdds::spread, odds -> odds),
            odds -> odds.mostLikelyIfSpread().<Either<Resistance, ResistanceOdds>>map(Either::left)
                    .orElseGet(() -> Either.right(odds)));

    public ResistanceOdds {
        weights = Keyed.complete(Resistance.class, weights, step -> 0);
    }

    /**
     * The odds a strain whose most likely step is {@code mostLikely} rolls from, by the shared spread: what a file
     * naming only the step means.
     */
    public static ResistanceOdds spread(Resistance mostLikely) {
        Resistance[] steps = Resistance.values();
        int centre = mostLikely.ordinal();
        int last = steps.length - 1;
        boolean end = centre == 0 || centre == last;
        int[] weights = new int[last + 1];
        for (int away = 1; away < SPREAD.length; away++) {
            int below = centre - away;
            int above = centre + away;
            if (end) {
                // At an end step the other side takes the whole share.
                weights[below >= 0 ? below : above] += SPREAD[away];
            } else {
                // Each side takes half; a half that falls off the scale lands on its end step.
                int half = SPREAD[away] / 2;
                weights[Math.max(0, below)] += half;
                weights[Math.min(last, above)] += half;
            }
        }
        int rest = 100;
        for (int weight : weights) {
            rest -= weight;
        }
        weights[centre] += rest;
        EnumMap<Resistance, Integer> odds = new EnumMap<>(Resistance.class);
        for (Resistance step : steps) {
            odds.put(step, weights[step.ordinal()]);
        }
        return new ResistanceOdds(odds);
    }

    /** The weight of {@code step}. */
    public int weight(Resistance step) {
        return weights.get(step);
    }

    /** The step whose spread these odds are, if they are one: how the odds are written back. */
    private Optional<Resistance> mostLikelyIfSpread() {
        for (Resistance step : Resistance.values()) {
            if (spread(step).equals(this)) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }
}
