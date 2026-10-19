package fun.xingwangzhe.webmapview.client;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandler;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static fun.xingwangzhe.webmapview.client.UrlManager.sendFeedback;

// Ursprünglich kopiert von https://github.com/CinemaMod/mcef-fabric-example-mod
// Portiert auf Minecraft 26.3 + MCEF Modern (net.dimaskama)

public class BasicBrowser extends Screen {
    private static final int BROWSER_DRAW_OFFSET = 20;

    private MCEFBrowser browser;
    private boolean isInitializing = false;
    private boolean initializationFailed = false;

    // Fortschritt der MCEF-(JCEF/Chromium-)Initialisierung, nur fuer die Anzeige
    private volatile MCEFApi.Initialization mcefInitialization;

    // Statuspruefung fuer Ressourcenpakete
    private boolean resourcesReloading = false;
    private long lastResourceReloadTime = 0;

    // Automatische Wiederherstellung
    private static int retryCount = 0;
    private static final int MAX_RETRY_COUNT = 2;
    private boolean isAutoRecovering = false;

    // Steuerung der periodischen JavaScript-Injektion
    private volatile boolean shouldStopInjection = false;
    private volatile boolean injectionSuccessful = false;

    public BasicBrowser(Component title) {
        super(title);
        // Konfiguration bei jedem Oeffnen neu einlesen, damit Aenderungen in
        // config/webmapview.json ohne Spielneustart wirksam werden.
        try {
            WebmapViewConfig.reload();
        } catch (Exception e) {
            System.err.println("[webmapview] Konfiguration konnte nicht neu geladen werden: " + e.getMessage());
        }
    }

    @Override
    protected void init() {
        super.init();

        if (browser == null && !isInitializing) {
            // Lokalen HTTP-Server starten (Port 8080)
            API.startLocalHttpServer(8080, List.of()); // Start mit leerer Spielerliste

            if (isResourcePackReady()) {
                initializeBrowserAsync();
            } else {
                // Wenn die Ressourcen noch nicht bereit sind, Initialisierung verzoegern
                waitForResourcePackAndInitialize();
            }
        }
    }

    /**
     * Prueft, ob Ressourcenpakete vollstaendig geladen sind.
     */
    private boolean isResourcePackReady() {
        try {
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
            if (currentTime - lastResourceReloadTime < 2000) { // 2 Sekunden warten
                return false;
            }

            if (resourcesReloading) {
                return false;
            }

            return true;
        } catch (Exception e) {
            System.err.println(Component.translatable("debug.resource_pack.check_failed", e.getMessage()).getString());
            return false;
        }
    }

    // Einheitliche JavaScript-Injektion
    private void injectJavaScriptContent() {
        if (browser != null) {
            try {
                String jsCode = """
                (function() {
                    console.log('JavaScript注入成功: ' + new Date().toISOString());

                    // 劫持 fetch 方法
                    const originalFetch = window.fetch;
                    window.fetch = async function(...args) {
                        const [url] = args;
                        //console.log('fetch 被调用，URL:', url);
                        if (typeof url === 'string' && url.includes('/players.json')) {
                            //console.log('拦截到 /players.json 请求');

                            try {
                                // 获取本地API节点数据 (使用硬编码地址)
                                const localResponse = await originalFetch("http://localhost:8080/local-players.json");
                                const localData = await localResponse.json();
                                //console.log('本地API数据:', localData);

                                // 获取服务器原始数据
                                const serverResponse = await originalFetch.apply(this, args);
                                const serverData = await serverResponse.json();
                                //console.log('服务器原始数据:', serverData);

                                // 合并数据 - 优先使用本地API节点的数据
                                const mergedData = {
                                    ...serverData,
                                    ...localData,
                                    players: [
                                        ...(serverData.players || []),
                                        ...(localData.players || []).filter(localPlayer => 
                                            !(serverData.players || []).some(serverPlayer => serverPlayer.name === localPlayer.name
                                            )
                                        )
                                    ]
                                };

                                //console.log('合并后数据:', mergedData);
                                // 打印完整的title/players.json数据
                                //console.log('完整的合并后title/players.json数据:', JSON.stringify(mergedData, null, 2));

                                // 返回合并后的数据
                                const updatedResponse = new Response(JSON.stringify(mergedData), {
                                    status: serverResponse.status,
                                    statusText: serverResponse.statusText,
                                    headers: serverResponse.headers
                                });
                                
                                return updatedResponse;
                            } catch (error) {
                                //console.error('数据合并失败:', error);
                                // 如果合并失败，返回原始服务器数据
                                return originalFetch.apply(this, args);
                            }
                        }
                        return originalFetch.apply(this, args);
                    };

                    //console.log('fetch 方法已劫持');
                })();
                """;

                startPeriodicJavaScriptInjection(jsCode);
            } catch (Exception e) {
                System.err.println("[BasicBrowser] JavaScript-Injektion fehlgeschlagen: " + e.getMessage());
            }
        }
    }

    /**
     * Startet die periodische JavaScript-Injektion.
     */
    private void startPeriodicJavaScriptInjection(String jsCode) {
        // Laufenden Task ggf. zuerst stoppen
        shouldStopInjection = true;
        injectionSuccessful = false;
        shouldStopInjection = false;

        CompletableFuture.runAsync(() -> {
            int attempts = 0;
            while (!shouldStopInjection && !injectionSuccessful && attempts < 30) { // max. 30 Versuche
                attempts++;
                int finalAttempts = attempts;
                minecraft.execute(() -> {
                    if (!shouldStopInjection && browser != null) {
                        try {
                            CefBrowser cefBrowser = browser.getCefBrowser();
                            cefBrowser.executeJavaScript(jsCode, cefBrowser.getURL(), 0);

                            System.out.println("[BasicBrowser] JavaScript injiziert (Versuch #" + finalAttempts + ")");
                        } catch (Exception e) {
                            System.err.println("[BasicBrowser] JavaScript-Injektion fehlerhaft (Versuch #" + finalAttempts + "): " + e.getMessage());
                        }
                    }
                });

                try {
                    Thread.sleep(1000); // ein Versuch pro Sekunde
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            if (injectionSuccessful) {
                System.out.println("[BasicBrowser] JavaScript-Injektion abgeschlossen");
            } else if (attempts >= 30) {
                System.err.println("[BasicBrowser] JavaScript-Injektion Zeitueberschreitung");
            }
        });
    }

    /**
     * Wartet, bis die Ressourcen geladen sind, und initialisiert dann den Browser.
     */
    private void waitForResourcePackAndInitialize() {
        resourcesReloading = true;
        System.out.println(Component.translatable("debug.resource_pack.waiting").getString());

        CompletableFuture.runAsync(() -> {
            try {
                int maxRetries = 30; // max. 15 Sekunden (30 * 500ms)
                int retryCount = 0;

                while (retryCount < maxRetries && !isResourcePackReady()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    retryCount++;
                }

                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(1000); // zusaetzliche Stabilisierung
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                minecraft.execute(() -> {
                    resourcesReloading = false;
                    lastResourceReloadTime = System.currentTimeMillis();

                    if (isResourcePackReady()) {
                        System.out.println(Component.translatable("debug.resource_pack.loaded").getString());
                        initializeBrowserAsync();
                    } else {
                        System.err.println(Component.translatable("debug.resource_pack.timeout").getString());
                        initializationFailed = true;
                    }
                });

            } catch (Exception e) {
                resourcesReloading = false;
                initializationFailed = true;
                System.err.println(Component.translatable("debug.resource_pack.error", e.getMessage()).getString());
            }
        });
    }

    /**
     * Initialisiert MCEF (JCEF/Chromium) und erstellt anschliessend den Browser.
     */
    private void initializeBrowserAsync() {
        isInitializing = true;
        initializationFailed = false;

        CompletableFuture.runAsync(() -> {
            try {
                // MCEF Modern initialisieren bzw. auf laufende Initialisierung warten.
                // Beim ersten Start wird Chromium (JCEF) heruntergeladen - das kann dauern.
                MCEFApi.Initialization initialization = MCEFApi.initialize();
                mcefInitialization = initialization;

                MCEFApi api = initialization.getFuture().join();

                if (api == null) {
                    System.err.println(Component.translatable("debug.browser.mcef_null").getString());
                    minecraft.execute(this::handleInitializationFailure);
                    return;
                }

                String url = UrlManager.fullUrl(UrlManager.defaultUrl);
                sendFeedback(url);
                boolean transparent = false;

                // Browser im Client-Thread erzeugen
                minecraft.execute(() -> {
                    try {
                        browser = api.createBrowser(url, transparent);
                        if (browser != null) {
                            resizeBrowser();
                            isInitializing = false;
                            retryCount = 0;
                            System.out.println(Component.translatable("debug.browser.initialization.success").getString());

                            // Konsolenausgaben der Seite in das Spiel-Log spiegeln
                            browser.getCefBrowser().getClient().addDisplayHandler(new ConsoleMessageHandler());

                            injectJavaScriptContent();
                        } else {
                            System.err.println(Component.translatable("debug.browser.mcef_null").getString());
                            handleInitializationFailure();
                        }
                    } catch (Exception e) {
                        System.err.println(Component.translatable("debug.browser.init_failed_main", e.getMessage()).getString());
                        handleInitializationFailure();
                    }
                });
            } catch (Exception e) {
                System.err.println(Component.translatable("debug.browser.init_failed_async", e.getMessage()).getString());
                minecraft.execute(this::handleInitializationFailure);
            }
        });
    }

    /**
     * Behandelt einen Initialisierungsfehler und entscheidet ueber die automatische Wiederherstellung.
     */
    private void handleInitializationFailure() {
        isInitializing = false;

        if (retryCount < MAX_RETRY_COUNT && !isAutoRecovering) {
            retryCount++;
            System.out.println(Component.translatable("debug.browser.auto_recovery_starting", retryCount, MAX_RETRY_COUNT).getString());

            if (minecraft.player != null) {
                minecraft.player.sendSystemMessage(Component.translatable("browser.initialization.failed.auto_recovery", retryCount, MAX_RETRY_COUNT));
            }

            startAutoRecovery();
        } else {
            initializationFailed = true;
            isAutoRecovering = false;

            if (retryCount >= MAX_RETRY_COUNT) {
                System.err.println(Component.translatable("debug.browser.max_retries", MAX_RETRY_COUNT).getString());
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.translatable("browser.initialization.final_failure", MAX_RETRY_COUNT));
                    minecraft.player.sendSystemMessage(Component.translatable("browser.initialization.manual_fix_required"));
                }
            } else {
                System.err.println(Component.translatable("debug.browser.initialization.failed").getString());
                if (minecraft.player != null) {
                    minecraft.player.sendSystemMessage(Component.translatable("browser.initialization.mcef_check"));
                }
            }
        }
    }

    /**
     * Startet die automatische Wiederherstellung: Ressourcenpakete neu laden (F3+T) und erneut versuchen.
     */
    private void startAutoRecovery() {
        isAutoRecovering = true;

        System.out.println(Component.translatable("debug.browser.auto_recovery_begin").getString());

        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(500);

                minecraft.execute(() -> {
                    try {
                        triggerResourcePackReload();
                        waitForRecoveryAndRetry();
                    } catch (Exception e) {
                        System.err.println(Component.translatable("debug.browser.auto_recovery_failed", e.getMessage()).getString());
                        isAutoRecovering = false;
                        initializationFailed = true;
                    }
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                isAutoRecovering = false;
                initializationFailed = true;
            }
        });
    }

    /**
     * Laedt die Ressourcenpakete neu (entspricht F3+T).
     */
    private void triggerResourcePackReload() {
        try {
            System.out.println(Component.translatable("debug.browser.resource_reload_trigger").getString());

            resourcesReloading = true;
            lastResourceReloadTime = System.currentTimeMillis();

            if (minecraft.getResourcePackRepository() != null) {
                minecraft.reloadResourcePacks();
                System.out.println(Component.translatable("debug.browser.resource_reload_success").getString());
            } else {
                System.err.println(Component.translatable("debug.browser.resource_manager_unavailable").getString());
                throw new RuntimeException("Ressourcenpaket-Manager nicht verfuegbar");
            }

        } catch (Exception e) {
            System.err.println(Component.translatable("debug.browser.resource_reload_failed", e.getMessage()).getString());
            throw e;
        }
    }

    /**
     * Wartet auf die Wiederherstellung und versucht die Initialisierung erneut.
     */
    private void waitForRecoveryAndRetry() {
        CompletableFuture.runAsync(() -> {
            try {
                System.out.println(Component.translatable("debug.browser.waiting_recovery").getString());

                int maxWaitTime = 60; // max. 30 Sekunden (60 * 500ms)
                int waitCount = 0;

                while (waitCount < maxWaitTime && (resourcesReloading || !isResourcePackReady())) {
                    try {
                        Thread.sleep(500);
                        waitCount++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                if (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(1000);
                }

                minecraft.execute(() -> {
                    isAutoRecovering = false;
                    resourcesReloading = false;
                    lastResourceReloadTime = System.currentTimeMillis();

                    if (isResourcePackReady()) {
                        System.out.println(Component.translatable("debug.browser.recovery_completed").getString());
                        if (minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.translatable("browser.auto_recovery.completed"));
                        }

                        initializationFailed = false;
                        initializeBrowserAsync();

                    } else {
                        System.err.println(Component.translatable("debug.browser.recovery_still_failed").getString());
                        initializationFailed = true;

                        if (minecraft.player != null) {
                            minecraft.player.sendSystemMessage(Component.translatable("browser.auto_recovery.failed"));
                        }
                    }
                });

            } catch (Exception e) {
                System.err.println(Component.translatable("debug.browser.recovery_error", e.getMessage()).getString());
                minecraft.execute(() -> {
                    isAutoRecovering = false;
                    initializationFailed = true;
                });
            }
        });
    }

    private double guiScale() {
        return Math.max(1, minecraft.getWindow().getGuiScale());
    }

    private int mouseX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET) * guiScale());
    }

    private int mouseY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET) * guiScale());
    }

    private int scaleX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET * 2) * guiScale());
    }

    private int scaleY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET * 2) * guiScale());
    }

    private void resizeBrowser() {
        if (browser != null && width > 100 && height > 100) {
            browser.resize(scaleX(width), scaleY(height));
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (browser != null) {
            resizeBrowser();
        }
    }

    @Override
    public void onClose() {
        super.onClose();
    }

    @Override
    public void removed() {
        cleanupBrowser();
        super.removed();
    }

    /**
     * Gibt Browser und lokalen HTTP-Server frei.
     */
    private void cleanupBrowser() {
        shouldStopInjection = true;

        API.stopLocalHttpServer();

        if (browser != null) {
            try {
                browser.close();
            } catch (Exception e) {
                System.err.println(Component.translatable("debug.browser.close_failed", e.getMessage()).getString());
            }
            browser = null;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        int x0 = BROWSER_DRAW_OFFSET;
        int y0 = BROWSER_DRAW_OFFSET;
        int x1 = width - BROWSER_DRAW_OFFSET;
        int y1 = height - BROWSER_DRAW_OFFSET;

        // Automatische Wiederherstellung laeuft
        if (isAutoRecovering) {
            graphics.fill(x0, y0, x1, y1, 0xFF004400);
            graphics.centeredText(font, Component.translatable("browser.auto_recovery.starting").getString(),
                    width / 2, height / 2 - 30, 0xFFFFFF);
            graphics.centeredText(font, Component.translatable("browser.auto_recovery.reloading_resources").getString(),
                    width / 2, height / 2 - 10, 0xFFFFFF);
            graphics.centeredText(font, Component.translatable("browser.auto_recovery.attempt", retryCount, MAX_RETRY_COUNT).getString(),
                    width / 2, height / 2 + 10, 0xFFAAAAAA);
            graphics.centeredText(font, Component.translatable("browser.auto_recovery.please_wait").getString(),
                    width / 2, height / 2 + 30, 0xFFAAAAAA);
            return;
        }

        // Ressourcen werden neu geladen
        if (resourcesReloading) {
            graphics.fill(x0, y0, x1, y1, 0xFF444444);
            graphics.centeredText(font, Component.translatable("browser.waiting_resource_pack").getString(),
                    width / 2, height / 2 - 10, 0xFFFFFF);
            graphics.centeredText(font, Component.translatable("browser.waiting_resource_pack.then_init").getString(),
                    width / 2, height / 2 + 10, 0xFFAAAAAA);
            return;
        }

        // Browser wird initialisiert
        if (isInitializing) {
            graphics.fill(x0, y0, x1, y1, 0xFF333333);
            graphics.centeredText(font, Component.translatable("browser.initializing").getString(),
                    width / 2, height / 2 - 10, 0xFFFFFF);

            MCEFApi.Initialization initialization = mcefInitialization;
            if (initialization != null && !initialization.isDone()) {
                Component stage = describeStage(initialization.getStage());
                // getPercentage() liefert bereits 0..100 (-1 = unbekannt) -> nicht mit 100 multiplizieren
                float rawPercentage = initialization.getPercentage();
                String progress = rawPercentage < 0.0f ? "" : " " + Math.round(rawPercentage) + "%";
                graphics.centeredText(font, stage.getString() + progress,
                        width / 2, height / 2 + 10, 0xFFAAAAAA);

                // Hinweis, dass es ein einmaliger Download ist (nur beim Herunterladen/Entpacken)
                MCEFApi.Initialization.Stage current = initialization.getStage();
                if (current == MCEFApi.Initialization.Stage.DOWNLOADING
                        || current == MCEFApi.Initialization.Stage.EXTRACTING) {
                    graphics.centeredText(font, Component.translatable("browser.mcef.stage.hint").getString(),
                            width / 2, height / 2 + 26, 0xFF777777);
                }
            }
            return;
        }

        // Initialisierung endgueltig fehlgeschlagen
        if (initializationFailed) {
            graphics.fill(x0, y0, x1, y1, 0xFF333333);
            graphics.centeredText(font, Component.translatable("browser.failed.title").getString(),
                    width / 2, height / 2 - 20, 0xFF0000);
            if (retryCount >= MAX_RETRY_COUNT) {
                graphics.centeredText(font, Component.translatable("browser.failed.tried_times", MAX_RETRY_COUNT).getString(),
                        width / 2, height / 2, 0xFFFF00);
                graphics.centeredText(font, Component.translatable("browser.failed.manual_f3t").getString(),
                        width / 2, height / 2 + 20, 0xFFFFFF);
            } else {
                graphics.centeredText(font, Component.translatable("browser.failed.check_config").getString(),
                        width / 2, height / 2 + 10, 0xFFFF00);
            }
            return;
        }

        if (browser == null) {
            graphics.centeredText(font, Component.translatable("browser.not_initialized").getString(),
                    width / 2, height / 2, 0xFF0000);
            return;
        }

        // Direktes Blitten der von MCEF Modern bereitgestellten GPU-Textur
        GpuTextureView textureView = browser.getTextureView();
        if (textureView == null) {
            graphics.fill(x0, y0, x1, y1, 0xFF333333);
            graphics.centeredText(font, Component.translatable("browser.loading").getString(),
                    width / 2, height / 2, 0xFFFFFF);
            return;
        }

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        graphics.enableScissor(x0, y0, x1, y1);
        graphics.blit(textureView, sampler, x0, y0, x1, y1, 0.0f, 1.0f, 0.0f, 1.0f);
        graphics.disableScissor();

        // Mauszeiger der Webseite uebernehmen (Link-Zeiger, Text-Cursor, ...)
        CursorType cursor = browser.getCursorType();
        if (cursor != null) {
            graphics.requestCursor(cursor);
        }
    }

    /**
     * Skaliert ein Maus-Event in den Pixelraum des Browsers.
     */
    private MouseButtonEvent toBrowserEvent(MouseButtonEvent event) {
        return new MouseButtonEvent(mouseX(event.x()), mouseY(event.y()), event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (browser != null) {
            try {
                browser.onMouseClicked(toBrowserEvent(event), doubleClick);
                browser.setFocus(true);
            } catch (Exception e) {
                // Browser-Interaktionsfehler ignorieren, damit das Spiel nicht abstuerzt
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null) {
            try {
                browser.onMouseReleased(toBrowserEvent(event));
                browser.setFocus(true);
            } catch (Exception e) {
                // ignorieren
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browser != null) {
            try {
                browser.onMouseMoved(mouseX(x), mouseY(y));
            } catch (Exception e) {
                // ignorieren
            }
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (browser != null) {
            try {
                browser.onMouseMoved(mouseX(event.x()), mouseY(event.y()));
            } catch (Exception e) {
                // ignorieren
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontalAmount, double verticalAmount) {
        if (browser != null) {
            try {
                browser.onMouseScrolled(mouseX(x), mouseY(y), verticalAmount);
            } catch (Exception e) {
                // ignorieren
            }
        }
        return super.mouseScrolled(x, y, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // ESC schliesst den Browser
        if (event.isEscape()) {
            this.onClose();
            return true;
        }

        // Der konfigurierte Toggle-Key schliesst den Browser ebenfalls
        if (WebmapviewClient.isToggleKey(event)) {
            System.out.println("[DEBUG] Toggle-Key im BasicBrowser erkannt, schliessen...");
            this.onClose();
            return true;
        }

        // Alle anderen Tasten gehen an die Webseite (Texteingabe in Formularen!)
        if (browser != null) {
            try {
                browser.onKeyPressed(event);
            } catch (Exception e) {
                // ignorieren
            }
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser != null) {
            try {
                browser.onKeyReleased(event);
            } catch (Exception e) {
                // ignorieren
            }
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (browser != null) {
            try {
                browser.onCharTyped(event);
            } catch (Exception e) {
                // ignorieren
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Spiegelt die JavaScript-Konsole in das Spiel-Log
    private static class ConsoleMessageHandler implements CefDisplayHandler {
        @Override
        public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
            System.out.printf("[Browser Console] Level: %s, Message: %s, Source: %s, Line: %d%n", level, message, source, line);
            return false;
        }

        @Override
        public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {}

        @Override
        public void onTitleChange(CefBrowser browser, String title) {}

        @Override
        public void onFullscreenModeChange(CefBrowser browser, boolean fullscreen) {}

        @Override
        public boolean onTooltip(CefBrowser browser, String text) { return false; }

        @Override
        public void onStatusMessage(CefBrowser browser, String value) {}
        @Override
        public boolean onCursorChange(CefBrowser browser, int cursorType) { return false; }
    }

    /**
     * Uebersetzt die MCEF-Initialisierungsphase in einen lesbaren Text,
     * damit man sieht, was gerade passiert (Engine-Download, Entpacken, Start).
     */
    private static Component describeStage(MCEFApi.Initialization.Stage stage) {
        if (stage == null) {
            return Component.translatable("browser.mcef.stage.not_started");
        }
        return switch (stage) {
            case DOWNLOADING -> Component.translatable("browser.mcef.stage.downloading");
            case EXTRACTING -> Component.translatable("browser.mcef.stage.extracting");
            case INSTALL -> Component.translatable("browser.mcef.stage.install");
            case INITIALIZING -> Component.translatable("browser.mcef.stage.initializing");
            default -> Component.translatable("browser.mcef.stage.not_started");
        };
    }
}
