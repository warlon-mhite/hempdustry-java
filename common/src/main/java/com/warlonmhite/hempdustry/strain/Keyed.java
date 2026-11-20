package com.warlonmhite.hempdustry.strain;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.Codec;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Stream;
import net.minecraft.util.StringRepresentable;

/**
 * A set of genes or weights kept as a map keyed by what each entry is, never as one field per entry: a sheet
 * gains an entry the day its key does (a fifth phenomenon, a sixth step) and no constructor or accessor changes.
 * In a file each entry is a key of its own, named as the key serializes, exactly as the fields were.
 */
final class Keyed {
    private Keyed() {
    }

    /**
     * {@code entries} as a whole, unmodifiable map in key order: a key it leaves out takes {@code absent}, so two
     * maps meaning the same thing are equal.
     */
    static <K extends Enum<K>, V> Map<K, V> complete(Class<K> type, Map<K, V> entries, Function<K, V> absent) {
        EnumMap<K, V> out = new EnumMap<>(type);
        for (K key : type.getEnumConstants()) {
            V value = entries.get(key);
            out.put(key, value != null ? value : absent.apply(key));
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * One key per constant of {@code type}, each read with {@code value}: a key the file leaves out reads as
     * {@code absent}, a key it does not know is ignored, and an entry is written only when {@code written} says so.
     * A key whose value does not read fails the whole map, as vanilla's {@code optionalFieldOf} does.
     */
    static <K extends Enum<K> & StringRepresentable, V> MapCodec<Map<K, V>> codec(
            Class<K> type, Codec<V> value, Function<K, V> absent, BiPredicate<K, V> written) {
        K[] keys = type.getEnumConstants();
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Arrays.stream(keys).map(key -> ops.createString(key.getSerializedName()));
            }

            @Override
            public <T> DataResult<Map<K, V>> decode(DynamicOps<T> ops, MapLike<T> input) {
                EnumMap<K, V> out = new EnumMap<>(type);
                for (K key : keys) {
                    T raw = input.get(key.getSerializedName());
                    if (raw == null) {
                        out.put(key, absent.apply(key));
                        continue;
                    }
                    DataResult<V> read = value.parse(ops, raw);
                    if (read.isError()) {
                        String name = key.getSerializedName();
                        return DataResult.error(() -> name + ": " + read.error().orElseThrow().message());
                    }
                    out.put(key, read.getOrThrow());
                }
                return DataResult.success(Collections.unmodifiableMap(out));
            }

            @Override
            public <T> RecordBuilder<T> encode(Map<K, V> map, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                for (K key : keys) {
                    V entry = map.containsKey(key) ? map.get(key) : absent.apply(key);
                    if (written.test(key, entry)) {
                        prefix.add(key.getSerializedName(), value.encodeStart(ops, entry));
                    }
                }
                return prefix;
            }

            @Override
            public String toString() {
                return "Keyed[" + type.getSimpleName() + "]";
            }
        };
    }

    /** A weight sheet's entries that are not zero: what a file writes of one whose missing keys read as zero. */
    static <K> boolean notZero(K key, int weight) {
        return weight != 0;
    }

    /** Every entry: what a file writes of a sheet whose missing keys read as something else than zero. */
    static <K, V> boolean always(K key, V value) {
        return true;
    }
}
