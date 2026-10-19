package fun.xingwangzhe.webmapview.client;

import net.dimaskama.mcef.api.MCEFApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

public class WebmapviewClient implements ClientModInitializer {

    /**
     * Eigene Keybind-Kategorie (1.21.9+ / 26.3: {@link KeyMapping.Category} statt String).
     */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("webmapview", "main"));

    private KeyMapping keyMapping;
    private static WebmapviewClient instance;
    private static long lastResourcePackCheckTime = 0;

    // Engine-Vorladen beim Spielstart (Download/Entpacken von Chromium)
    private static boolean enginePreloadStarted = false;
    private static MCEFApi.Initialization engineInitialization;
    private static MCEFApi.Initialization.Stage lastLoggedStage = null;
    private static int lastLoggedPercent = -1;
    private static long lastEngineLogTime = 0;
    public static KeyMapping getToggleKeyMapping() {
        return instance != null ? instance.keyMapping : null;
    }

    public static boolean isToggleKey(KeyEvent event) {
        KeyMapping mapping = getToggleKeyMapping();
        if (mapping != null) {
            return mapping.matches(event);
        }
        return false;
    }

    public static boolean isResourcePackFullyLoaded() {
        try {
            Minecraft minecraft = Minecraft.getInstance();

            if (minecraft.getResourceManager() == null) {
                return false;
            }

            if (minecraft.getTextureManager() == null) {
                return false;
            }

            if (minecraft.font == null) {
                return false;
            }

            if (minecraft.getWindow() == null || minecraft.getWindow().handle() == 0) {
                return false;
            }

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastResourcePackCheckTime < 3000) {
                return false;
            }

            lastResourcePackCheckTime = currentTime;
            return true;

        } catch (Exception e) {
            System.err.println(Component.translatable("debug.resource_pack.check_failed", e.getMessage()).getString());
            return false;
        }
    }

    @Override
    public void onInitializeClient() {
        instance = this;

        // Konfiguration frueh laden: legt beim ersten Start config/webmapview.json an,
        // damit die Datei direkt im configs-Ordner steht und editierbar ist.
        UrlManager.applyConfig();
        System.out.println("[webmapview] Konfiguration: " + WebmapViewConfig.configPath());
        System.out.println("[webmapview] Beim ersten Start laedt MCEF Modern einmalig die "
                + "Chromium-Engine (~34 MB) nach config/mcef-modern/ (entpackt ~357 MB).");
        // Kein Einstellen der Map-URL per Command mehr: die Konfiguration liegt in
        // config/webmapview.json und wird ueber ModMenu bzw. diesen Screen bearbeitet.
        // /webmapview ist nur ein Shortcut, der den Config-Screen oeffnet.
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommands.literal("webmapview")
                                .executes(context -> {
                                    Minecraft minecraft = Minecraft.getInstance();
                                    minecraft.execute(() -> minecraft.gui.setScreen(
                                            new WebMapViewConfigScreen(minecraft.gui.screen())));
                                    return 1;
                                })
                )
        );

        keyMapping = new KeyMapping(
                "key.webmapview.open_basic_browser",
                InputConstants.KEY_H,
                CATEGORY
        );

        KeyMappingHelper.registerKeyMapping(keyMapping);

        // Chromium-Engine (MCEF Modern) schon beim Spielstart im Hintergrund laden,
        // damit der einmalige Download (~34 MB) nicht erst beim Oeffnen des Browsers
        // passiert. Laeuft auf dem ForkJoinPool - der Render-Thread blockiert nicht.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!enginePreloadStarted) {
                enginePreloadStarted = true;
                startEnginePreload();
            }
            logEngineProgress();
        });

        final Minecraft minecraft = Minecraft.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (keyMapping.consumeClick()) {
                if (minecraft.gui.screen() instanceof BasicBrowser) {
                    minecraft.gui.setScreen(null);
                }
                else {
                    if (!isResourcePackFullyLoaded()) {
                        if (minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.translatable("browser.resource_pack.not_loaded"));
                        }
                        System.out.println(Component.translatable("debug.key_handler.resource_not_ready").getString());
                        return;
                    }

                    try {
                        minecraft.gui.setScreen(new BasicBrowser(Component.translatable("browser.title")));
                    } catch (Exception e) {
                        System.err.println(Component.translatable("debug.key_handler.creation_failed", e.getMessage()).getString());
                        if (minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.translatable("browser.creation.failed"));
                        }
                    }
                }
            }
        });
    }

    /**
     * Startet die MCEF-Modern-Initialisierung direkt beim Spielstart, damit der
     * einmalige Chromium-Download nicht erst beim Oeffnen des Browsers anfaellt.
     * MCEFApi.initialize() ist idempotent: der Browser bekommt spaeter dieselbe
     * Initialization und wartet nur noch auf das fertige Future.
     */
    private static void startEnginePreload() {
        if (!WebmapViewConfig.get().preloadEngine) {
            System.out.println("[webmapview] Engine-Vorladen deaktiviert "
                    + "(preloadEngine=false in " + WebmapViewConfig.configPath() + ").");
            return;
        }

        try {
            engineInitialization = MCEFApi.initialize();
        } catch (Throwable t) {
            System.err.println("[webmapview] Engine konnte nicht vorgeladen werden: " + t.getMessage());
            return;
        }

        System.out.println("[webmapview] Lade Chromium-Engine im Hintergrund vor "
                + "(erster Start: ~34 MB Download, danach ~357 MB entpackt) ...");

        engineInitialization.getFuture().whenComplete((api, error) -> {
            if (error != null) {
                System.err.println("[webmapview] Engine-Vorladen fehlgeschlagen: " + error.getMessage());
            } else {
                System.out.println("[webmapview] Chromium-Engine bereit - der Browser oeffnet jetzt sofort.");
            }
        });
    }

    /** Loggt den Fortschritt des Vorladens (Stufenwechsel oder +10 %), hoechstens alle 2 s. */
    private static void logEngineProgress() {
        MCEFApi.Initialization initialization = engineInitialization;
        if (initialization == null || initialization.isDone()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastEngineLogTime < 2000) {
            return;
        }

        MCEFApi.Initialization.Stage stage = initialization.getStage();
        int percent = Math.round(initialization.getPercentage());
        boolean stageChanged = stage != lastLoggedStage;
        boolean percentJumped = percent - lastLoggedPercent >= 10;

        if (!stageChanged && !percentJumped) {
            return;
        }

        lastEngineLogTime = now;
        lastLoggedStage = stage;
        lastLoggedPercent = percent;
        System.out.println("[webmapview] Chromium-Engine: " + stage + " " + percent + " %");
    }
}
