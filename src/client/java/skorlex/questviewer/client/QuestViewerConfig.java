package skorlex.questviewer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import skorlex.questviewer.QuestViewer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Manages client-side configuration settings for QuestViewer.
 *
 * <p>Handles reading, writing, and persisting user preferences to a JSON configuration file
 * located in the Fabric client config directory.
 */
public class QuestViewerConfig {

    /** Pretty-printing Gson serializer/deserializer instance for formatted JSON formatting. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** File reference targeting the client's configuration JSON file (e.g., config/questviewer.json). */
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), QuestViewer.MOD_ID + ".json");

    /** Singleton instance holding current in-memory configuration values. */
    private static QuestViewerConfig instance = new QuestViewerConfig();

    /** Whether completion chime notification sounds are enabled. Default is true. */
    public boolean notificationsEnabled = true;

    /** Audio pitch multiplier for daily quest completion chime sounds. Default is 1.2F. */
    public float dailyPitch = 1.2F;

    /** Audio pitch multiplier for weekly/monthly quest completion chime sounds. Default is 1.2F. */
    public float weeklyPitch = 1.2F;

    /** Tracks whether the user has received the first-time welcome tutorial message in chat. Default is true. */
    public boolean firstTime = true;

    /**
     * Gets the current in-memory configuration instance.
     *
     * @return The active {@link QuestViewerConfig} instance
     */
    public static QuestViewerConfig getInstance() {
        return instance;
    }

    /**
     * Loads saved configuration settings from disk.
     *
     * <p>If the configuration file exists, it reads and parses the JSON into the active instance.
     * If no file exists, it creates one with default settings by calling {@link #save()}.
     */
    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, QuestViewerConfig.class);
            } catch (IOException e) {
                QuestViewer.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }
    }

    /**
     * Serializes and writes the current in-memory configuration settings to disk.
     */
    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            QuestViewer.LOGGER.error("Failed to save config", e);
        }
    }
}