package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * What a strain's resin is like, and so which route its extra resin pays: the sieve (dry), the rub (sticky), or
 * half of each (even). Hash makers tell good washers (dry heads that snap off the plant) from greasy, sticky resin
 * that rubs and presses well. A strain's, fixed for now. Only the <em>bonus</em> of a rich plant is shared out; the
 * penalty of a low one applies in full on both routes.
 */
public enum ResinTexture implements StringRepresentable {
    DRY("dry"),
    EVEN("even"),
    STICKY("sticky");

    public static final Codec<ResinTexture> CODEC = StringRepresentable.fromEnum(ResinTexture::values);

    private final String name;

    ResinTexture(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
