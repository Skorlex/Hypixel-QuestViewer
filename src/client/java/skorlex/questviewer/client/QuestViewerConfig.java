package skorlex.questviewer.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import skorlex.questviewer.QuestViewer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class QuestViewerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), QuestViewer.MOD_ID + ".json");
    private static QuestViewerConfig instance = new QuestViewerConfig();

    // The newly decoupled config variables that QuestViewerClient is looking for
    public boolean notificationsEnabled = true;
    public float dailyPitch = 1.2F;
    public float weeklyPitch = 1.2F;

    public static QuestViewerConfig getInstance() {
        return instance;
    }

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

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            QuestViewer.LOGGER.error("Failed to save config", e);
        }
    }
}