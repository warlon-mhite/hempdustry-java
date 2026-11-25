package com.warlonmhite.hempdustry.client.anim;

import com.warlonmhite.hempdustry.network.PlayerGesture;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The gestures this client is playing, by entity: a cough, a sniff, a kick. Each is timed off the
 * entity's own age, which is what its render state carries, so it plays at the game's pace and
 * freezes with it.
 */
public final class Gestures {
    /** Two jolts of the head, 3 ticks each, with a beat between them. */
    public static final int COUGH_TICKS = 9;
    /** The head dips to the jar and comes back. */
    public static final int SNIFF_TICKS = 8;
    /** The knee comes up and down over the length of vanilla's arm swing (6 ticks, {@code SwingAnimation#DEFAULT}). */
    public static final int KICK_TICKS = 6;

    private static final Int2ObjectOpenHashMap<Running> RUNNING = new Int2ObjectOpenHashMap<>();

    private Gestures() {
    }

    /** A gesture the server sent. Runs on the client's main thread. */
    public static void receive(PlayerGesture gesture) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Entity entity = client.level.getEntity(gesture.entityId());
            if (entity != null) {
                start(entity, gesture.kind());
            }
        }
    }

    public static void start(Entity entity, PlayerGesture.Kind kind) {
        RUNNING.put(entity.getId(), new Running(kind, entity.tickCount));
    }

    /**
     * How far through {@code kind} the entity {@code id} is at {@code age}, from {@code 0} to {@code 1},
     * or a negative number when it is not playing it.
     */
    public static float progress(int id, PlayerGesture.Kind kind, float age) {
        Running running = running(id, age);
        if (running == null || running.kind != kind) {
            return -1.0F;
        }
        return Math.max(0.0F, (age - running.start) / length(kind));
    }

    private static @Nullable Running running(int id, float age) {
        Running running = RUNNING.get(id);
        if (running != null && (age - running.start > length(running.kind) || age < running.start - 1)) {
            // Done, or the id now belongs to someone else (a respawn, a new entity).
            RUNNING.remove(id);
            return null;
        }
        return running;
    }

    public static int length(PlayerGesture.Kind kind) {
        return switch (kind) {
            case COUGH -> COUGH_TICKS;
            case SNIFF -> SNIFF_TICKS;
            case KICK -> KICK_TICKS;
        };
    }

    /** Forgets every gesture: a new world has new entities under the same ids. */
    public static void clear() {
        RUNNING.clear();
    }

    private record Running(PlayerGesture.Kind kind, int start) {
    }
}
