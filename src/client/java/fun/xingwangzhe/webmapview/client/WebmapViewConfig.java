package fun.xingwangzhe.webmapview.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Konfiguration der Mod: liegt in config/webmapview.json und kann entweder
 * direkt im Texteditor oder ueber den ModMenu-Config-Screen bearbeitet werden.
 *
 * Aufbau:
 * {
 *   "mapUrl": "squaremap-demo.jpenilla.xyz",
 *   "urls": ["squaremap-demo.jpenilla.xyz"],
 *   "enabled": true,
 *   "preloadEngine": true
 * }
 */
public final class WebmapViewConfig {

    public static final String FILE_NAME = "webmapview.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Alte Dateinamen (vor 1.3.0) - werden beim ersten Start migriert. */
    private static final String LEGACY_URLS_FILE = "urls.txt";
    private static final String LEGACY_DEFAULT_FILE = "default_url.txt";

    /** Aktuell angezeigte Map-URL (ohne https:// und ohne Query-Parameter). */
    public String mapUrl = "squaremap-demo.jpenilla.xyz";

    /** Alle gespeicherten URLs - erscheinen im Config-Screen als Auswahlliste. */
    public List<String> urls = new ArrayList<>();

    /** false = Mod ist deaktiviert und oeffnet keinen Browser. */
    public boolean enabled = true;

    /**
     * true = die Chromium-Engine (MCEF Modern) wird schon beim Spielstart im
     * Hintergrund geladen/entpackt, damit der Browser sofort aufgeht.
     * false = erst beim ersten Oeffnen des Browsers laden.
     */
    public boolean preloadEngine = true;

    private static WebmapViewConfig instance;

    private WebmapViewConfig() {
    }

    /** Liefert die geladene Instanz (laedt beim ersten Zugriff). */
    public static WebmapViewConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** Speichert die aktuelle Konfiguration nach config/webmapview.json. */
    public static void save() {
        if (instance == null) {
            return;
        }
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            System.err.println("[webmapview] Konfiguration konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    /** Liest die Konfiguration neu von der Platte (nach manueller Bearbeitung). */
    public static void reload() {
        instance = load();
        UrlManager.applyConfig();
    }

    private static WebmapViewConfig load() {
        Path path = configPath();
        WebmapViewConfig config = null;

        if (Files.exists(path)) {
            try (BufferedReader reader = Files.newBufferedReader(path)) {
                config = GSON.fromJson(reader, WebmapViewConfig.class);
            } catch (IOException | JsonSyntaxException e) {
                System.err.println("[webmapview] Konfiguration fehlerhaft, nutze Standardwerte: " + e.getMessage());
            }
        }

        if (config == null) {
            config = new WebmapViewConfig();
            config.migrateLegacyFiles();
            instance = config;
            save();
            return config;
        }

        // Fehlende oder null-Felder reparieren, damit nie NPEs entstehen
        if (config.mapUrl == null || config.mapUrl.isBlank()) {
            config.mapUrl = "squaremap-demo.jpenilla.xyz";
        }
        if (config.urls == null) {
            config.urls = new ArrayList<>();
        }
        if (!config.urls.contains(config.mapUrl)) {
            config.urls.add(config.mapUrl);
        }
        return config;
    }

    /**
     * Uebernimmt die Dateien der alten Version (urls.txt / default_url.txt),
     * damit beim Upgrade nichts verloren geht.
     */
    private void migrateLegacyFiles() {
        Path dir = FabricLoader.getInstance().getConfigDir();

        Path legacyUrls = dir.resolve(LEGACY_URLS_FILE);
        if (Files.exists(legacyUrls)) {
            try {
                for (String line : Files.readAllLines(legacyUrls)) {
                    String url = line.trim();
                    if (!url.isEmpty() && !urls.contains(url)) {
                        urls.add(url);
                    }
                }
            } catch (IOException e) {
                System.err.println("[webmapview] urls.txt konnte nicht gelesen werden: " + e.getMessage());
            }
        }

        Path legacyDefault = dir.resolve(LEGACY_DEFAULT_FILE);
        if (Files.exists(legacyDefault)) {
            try {
                String line = Files.readAllLines(legacyDefault).stream().findFirst().orElse(null);
                if (line != null && !line.isBlank()) {
                    mapUrl = line.trim();
                }
            } catch (IOException e) {
                System.err.println("[webmapview] default_url.txt konnte nicht gelesen werden: " + e.getMessage());
            }
        }

        if (!urls.contains(mapUrl)) {
            urls.add(mapUrl);
        }
    }

    /** Pfad der Konfigurationsdatei (config/webmapview.json). */
    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
