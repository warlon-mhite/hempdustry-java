package com.warlonmhite.hempdustry.strain.internal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * A data file's keys that are read with a default but always written: a strain sheet's, and the grow
 * climate's. A field made with vanilla's {@code optionalFieldOf(name, default)} is left out of a file
 * whenever it holds the default, so the mod's own files would carry none of the default numbers: change a
 * default later, and Purple Kush, every partial sheet and every datapack file would change with it,
 * silently. Written in full, the mod's own files stay what they say; a datapack's file still leaves out
 * what it likes.
 *
 * <p>Here rather than beside the strain sheets so the mod's other data files can use it without it becoming
 * something an addon is promised: nothing in this package is API.
 */
public final class FileFields {
    private FileFields() {
    }

    /** {@code name}, read as {@code fallback} when absent, and always written. */
    public static <A> MapCodec<A> written(Codec<A> codec, String name, A fallback) {
        return codec.optionalFieldOf(name).xmap(named -> named.orElse(fallback), Optional::of);
    }
}
