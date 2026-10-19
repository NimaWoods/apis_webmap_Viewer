package fun.xingwangzhe.webmapview.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Verwaltet die Map-URLs. Die Daten liegen seit 1.3.0 in config/webmapview.json
 * (siehe {@link WebmapViewConfig}) und sind ueber ModMenu oder den Texteditor aenderbar.
 */
public class UrlManager {

    /** Aktuell genutzte Map-URL. */
    public static String defaultUrl = "squaremap-demo.jpenilla.xyz";

    /** false = Mod deaktiviert. */
    public static boolean webmapview = true;

    private static List<String> urlList = new ArrayList<>();

    static {
        applyConfig();
    }

    /** Uebernimmt die Werte aus der Konfiguration in die statischen Felder. */
    public static void applyConfig() {
        WebmapViewConfig config = WebmapViewConfig.get();
        defaultUrl = config.mapUrl;
        webmapview = config.enabled;
        urlList = new ArrayList<>(config.urls);
    }

    /**
     * Fuegt eine URL hinzu (inkl. Speichern in der Konfiguration).
     *
     * @param url die URL
     * @return true, wenn sie neu war
     */
    public static boolean addUrl(String url) {
        String cleaned = normalize(url);
        if (cleaned.isEmpty()) {
            return false;
        }
        if (urlList.contains(cleaned)) {
            sendFeedback(Component.translatable("feedback.url.exists", cleaned));
            return false;
        }
        urlList.add(cleaned);
        persist();
        sendFeedback(Component.translatable("feedback.url.added", cleaned));
        return true;
    }

    /**
     * Setzt die aktive Map-URL. Die URL wird bei Bedarf in die Liste aufgenommen.
     *
     * @param url die URL
     */
    public static void setDefaultUrl(String url) {
        String cleaned = normalize(url);
        if (cleaned.isEmpty()) {
            sendFeedback(Component.translatable("feedback.url.not_found", String.valueOf(url)));
            return;
        }
        if (!urlList.contains(cleaned)) {
            urlList.add(cleaned);
        }
        defaultUrl = cleaned;
        persist();
        sendFeedback(Component.translatable("feedback.default.url.updated", cleaned));
    }

    /** Setzt die aktive Map-URL ohne Chat-Feedback (fuer den Config-Screen). */
    public static void setDefaultUrlSilently(String url) {
        String cleaned = normalize(url);
        if (cleaned.isEmpty()) {
            return;
        }
        if (!urlList.contains(cleaned)) {
            urlList.add(cleaned);
        }
        defaultUrl = cleaned;
        persist();
    }

    /** Liefert eine Kopie der gespeicherten URLs. */
    public static List<String> getUrlList() {
        return new ArrayList<>(urlList);
    }

    /** Liefert die aktive Map-URL. */
    public static String getDefaultUrl() {
        return defaultUrl;
    }

    /** Entfernt eine URL aus der Liste. */
    public static void removeUrl(String url) {
        String cleaned = normalize(url);
        if (urlList.remove(cleaned)) {
            if (defaultUrl != null && defaultUrl.equals(cleaned)) {
                defaultUrl = urlList.isEmpty() ? "" : urlList.get(0);
            }
            persist();
            sendFeedback(Component.translatable("feedback.url.removed", cleaned));
        } else {
            sendFeedback(Component.translatable("feedback.url.not_found", cleaned));
        }
    }

    /**
     * Fuegt eine URL hinzu, ohne Chat-Feedback (fuer den Config-Screen).
     *
     * @param url die URL
     * @return true, wenn sie neu war
     */
    public static boolean addUrlSilently(String url) {
        String cleaned = normalize(url);
        if (cleaned.isEmpty() || urlList.contains(cleaned)) {
            return false;
        }
        urlList.add(cleaned);
        persist();
        return true;
    }

    /** Entfernt eine URL ohne Chat-Feedback (fuer den Config-Screen). */
    public static void removeUrlSilently(String url) {
        String cleaned = normalize(url);
        if (!urlList.remove(cleaned)) {
            return;
        }
        if (defaultUrl != null && defaultUrl.equals(cleaned)) {
            defaultUrl = urlList.isEmpty() ? "" : urlList.get(0);
        }
        persist();
    }

    /** Schaltet die Mod an/aus und speichert das in der Konfiguration. */
    public static void setEnabledSilently(boolean enabled) {
        webmapview = enabled;
        persist();
    }

    /** true, wenn die Mod aktiv ist. */
    public static boolean isEnabled() {
        return webmapview;
    }

    /** Uebernimmt die aktuelle Liste in die Konfigurationsdatei. */
    private static void persist() {
        WebmapViewConfig config = WebmapViewConfig.get();
        config.urls = new ArrayList<>(urlList);
        config.mapUrl = defaultUrl;
        config.enabled = webmapview;
        WebmapViewConfig.save();
    }
    /** Entfernt Leerzeichen und ein evtl. mitgeschriebenes https:// . */
    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        String cleaned = url.trim();
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned;
    }

    /** Baut aus der Basis-URL die komplette squaremap-URL inkl. Spielerposition. */
    public static String fullUrl(String baseUrl) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || !webmapview) {
            sendFeedback(Component.translatable("feedback.player_or_world_not_available"));
            return baseUrl;
        }

        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();

        String worldName = client.level.dimension().identifier().toString();
        if (Objects.equals(worldName, "minecraft:overworld")) {
            worldName = "world";
        } else if (Objects.equals(worldName, "minecraft:the_nether")) {
            worldName = "world_nether";
        } else if (Objects.equals(worldName, "minecraft:the_end")) {
            worldName = "world_the_end";
        }

        StringBuilder fullUrlBuilder = new StringBuilder();
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            fullUrlBuilder.append("https://");
        }
        fullUrlBuilder.append(baseUrl).append("/");
        if (!baseUrl.contains("?")) {
            fullUrlBuilder.append("?");
        } else {
            fullUrlBuilder.append("&");
        }
        fullUrlBuilder.append("x=").append(playerX)
                .append("&z=").append(playerZ)
                .append("&zoom=").append("4")
                .append("&world=").append(worldName);

        return fullUrlBuilder.toString();
    }

    /** Chat-Feedback als reiner Text. */
    public static void sendFeedback(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal(message));
        }
    }

    /** Chat-Feedback als uebersetzbare Komponente. */
    public static void sendFeedback(Component textMessage) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(textMessage);
        }
    }
}
