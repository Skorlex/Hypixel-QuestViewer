package skorlex.questviewer.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Manages sequential playback and queuing of quest completion sound effects.
 *
 * <p>Prevents overlapping audio playback when multiple quests complete simultaneously by processing
 * queued sounds sequentially with tick-based cooldown delays on the main client thread.
 */
public class SoundQueueManager {

    /** First-in, first-out (FIFO) queue storing pending sound types waiting to be played. */
    private static final Queue<SoundType> QUEUE = new LinkedList<>();

    /** Remaining cooldown duration in game ticks before the next queued sound can play. */
    private static int tickCooldown = 0;

    /**
     * Enumeration defining supported quest chime sound types and their required audio duration cooldowns.
     */
    public enum SoundType {
        /** Daily quest completion chime (12 ticks = 600 ms duration). */
        DAILY(12),
        /** Weekly or monthly quest completion chime (24 ticks = 1200 ms duration). */
        WEEKLY(24);

        /** Delay duration in client ticks required after playing this sound. */
        public final int cooldown;

        /**
         * Constructs a sound type definition with its associated cooldown duration.
         *
         * @param cooldown Cooldown length in client ticks (20 ticks = 1 second)
         */
        SoundType(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    /**
     * Adds a quest completion sound request to the playback queue.
     *
     * <p>Call this method whenever a completed quest message is intercepted in chat.
     *
     * @param type The {@link SoundType} representing the quest tier completed
     */
    public static void enqueueSound(SoundType type) {
        QUEUE.add(type);
    }

    /**
     * Registers the end client tick listener with Fabric's lifecycle event system.
     *
     * <p>Must be called exactly once during client mod initialization.
     */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // If we are still waiting on a cooldown, subtract 1 tick and do nothing else
            if (tickCooldown > 0) {
                tickCooldown--;
                return;
            }

            // If cooldown is 0 and there is a sound waiting in line, play it
            if (!QUEUE.isEmpty()) {
                SoundType nextSound = QUEUE.poll();
                playSound(client, nextSound);
                tickCooldown = nextSound.cooldown; // Lock the queue with the new cooldown
            }
        });
    }

    /**
     * Triggers centered UI playback for a specific sound type via Minecraft's SoundManager.
     *
     * @param client The Minecraft client instance
     * @param type   The {@link SoundType} to play
     */
    private static void playSound(Minecraft client, SoundType type) {
        SoundEvent soundEvent = (type == SoundType.WEEKLY)
                ? QuestViewerClient.WEEKLY_CHIME_EVENT
                : QuestViewerClient.DAILY_CHIME_EVENT;

        // Pulls the pitch directly from your active config file
        float pitch = (type == SoundType.WEEKLY)
                ? QuestViewerConfig.getInstance().weeklyPitch
                : QuestViewerConfig.getInstance().dailyPitch;

        // Plays a centered UI sound to bypass panning, but forces 100.0F volume for massive gain
        client.getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, pitch, 100.0F));
    }
}