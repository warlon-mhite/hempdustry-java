package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * A strain file's keys that are read with a default but always written. A field made with vanilla's
 * {@code optionalFieldOf(name, default)} is left out of a file whenever it holds the default, so the
 * mod's own strain files would carry none of the default numbers: change a default later, and Purple
 * Kush, every partial sheet and every datapack strain would change with it, silently. Written in full,
 * the mod's own sheets stay what they say; a datapack's file still leaves out what it likes.
 */
final class FileFields {
    private FileFields() {
    }

    /** {@code name}, read as {@code fallback} when absent, and always written. */
    static <A> MapCodec<A> written(Codec<A> codec, String name, A fallback) {
        return codec.optionalFieldOf(name).xmap(named -> named.orElse(fallback), Optional::of);
    }
}
