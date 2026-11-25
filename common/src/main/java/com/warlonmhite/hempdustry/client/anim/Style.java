package com.warlonmhite.hempdustry.client.anim;

import com.warlonmhite.hempdustry.Hempdustry;
import com.warlonmhite.hempdustry.client.anim.compat.ModelFeatures;
import com.warlonmhite.hempdustry.config.ClientConfig;
import com.warlonmhite.hempdustry.platform.Services;
import net.minecraft.client.model.EntityModel;
import org.jetbrains.annotations.Nullable;

/**
 * Whose style the player animations take. They take the style of whatever animates the rest of the
 * body, so that each one feels native in the setup it plays in; another mod's or pack's files are
 * never edited, and where one of them animates a move itself, theirs wins.
 *
 * <ul>
 *   <li><b>Vanilla</b>: rigid arms, a held pose that follows the look while an item is used, the
 *       swing's curve for any short move, no easing.</li>
 *   <li><b>Eased</b>, beside Not Enough Animations: the same poses, eased in and out the way it eases
 *       its eating — its own arm smoothing does the arms, and the head is eased here.</li>
 *   <li><b>Fresh</b>, beside Fresh Animations' player pack (through Entity Model Features): only the
 *       arms and head are ours, the pack keeps the body, legs and breathing, with a little softness
 *       at the end of a move. With both mods, the style is Not Enough Animations', as the bridge
 *       between them gives eating to it.</li>
 * </ul>
 *
 * <p>Both mods are compiled against one build and found at run time by their id only, so another build of
 * either may lack what this one calls. Such a call fails with a {@link LinkageError}; the mod's style is then
 * left off until the game restarts, with one warning, and the animations play as if it were not installed.
 */
public enum Style {
    VANILLA,
    EASED,
    FRESH;

    private static volatile boolean NOT_ENOUGH_ANIMATIONS = Services.PLATFORM.isModLoaded("notenoughanimations");
    private static volatile boolean MODEL_FEATURES = Services.PLATFORM.isModLoaded("entity_model_features");

    /**
     * The style this client animates a body in, or {@code null} when the player turned the
     * animations off.
     *
     * @param packAnimatesModel whether Entity Model Features animates the model being posed
     */
    public static @Nullable Style now(boolean packAnimatesModel) {
        return switch (ClientConfig.get().animations()) {
            case OFF -> null;
            case VANILLA -> VANILLA;
            case AUTO -> NOT_ENOUGH_ANIMATIONS ? EASED : packAnimatesModel ? FRESH : VANILLA;
        };
    }

    /** Whether the moves only a richer setup adds are drawn: a hand cupping the pipe, a hand to the mouth. */
    public boolean rich() {
        return this != VANILLA;
    }

    public static boolean notEnoughAnimations() {
        return NOT_ENOUGH_ANIMATIONS;
    }

    public static boolean modelFeatures() {
        return MODEL_FEATURES;
    }

    /** Whether Entity Model Features animates {@code model}; never, once a call into it has failed. */
    public static boolean modelFeaturesAnimate(EntityModel<?> model) {
        if (!MODEL_FEATURES) {
            return false;
        }
        try {
            return ModelFeatures.animates(model);
        } catch (LinkageError | RuntimeException e) {
            modelFeaturesFailed(e);
            return false;
        }
    }

    /** A call into Not Enough Animations failed: its style is left off from now on. */
    public static void notEnoughAnimationsFailed(Throwable e) {
        if (NOT_ENOUGH_ANIMATIONS) {
            NOT_ENOUGH_ANIMATIONS = false;
            Hempdustry.LOGGER.warn("This build of Not Enough Animations is not the one the mod knows; "
                    + "smoking is animated as without it", e);
        }
    }

    /** A call into Entity Model Features failed: its style is left off from now on. */
    public static void modelFeaturesFailed(Throwable e) {
        if (MODEL_FEATURES) {
            MODEL_FEATURES = false;
            Hempdustry.LOGGER.warn("This build of Entity Model Features is not the one the mod knows; "
                    + "smoking is animated as without it", e);
        }
    }
}
