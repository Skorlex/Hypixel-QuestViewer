package skorlex.questviewer.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedList;
import java.util.Queue;

public class SoundQueueManager {

    // The queue that holds the sounds waiting to be played
    private static final Queue<SoundType> QUEUE = new LinkedList<>();

    // The cooldown timer in game ticks
    private static int tickCooldown = 0;

    // Defines our sounds and their respective cooldowns (Length + Padding)
    public enum SoundType {
        DAILY(12),  // 600ms (12 ticks)
        WEEKLY(24); // 1200ms (24 ticks)

        public final int cooldown;

        SoundType(int cooldown) {
            this.cooldown = cooldown;
        }
    }

    /**
     * Call this from your chat listener whenever a quest completes.
     */
    public static void enqueueSound(SoundType type) {
        QUEUE.add(type);
    }

    /**
     * Registers the tick loop. Call this ONCE in your ClientModInitializer.
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