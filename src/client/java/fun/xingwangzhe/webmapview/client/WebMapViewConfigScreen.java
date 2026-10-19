package fun.xingwangzhe.webmapview.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Konfigurations-Screen (erreichbar ueber ModMenu -> WebmapView -> Zahnrad
 * oder ueber die Taste fuer den Config-Screen).
 *
 * Hier lassen sich die Map-URL, die Liste der gespeicherten URLs und der
 * Aktiv-Status bearbeiten - ohne einen einzigen Chat-Command.
 * Die Werte landen in config/webmapview.json und koennen auch dort von Hand
 * editiert werden.
 */
public class WebMapViewConfigScreen extends Screen {

    /** Wie viele gespeicherte URLs gleichzeitig als Button sichtbar sind. */
    private static final int MAX_VISIBLE_URLS = 3;

    private static final int PANEL_WIDTH = 320;

    private final Screen parent;

    private EditBox mapUrlBox;
    private EditBox newUrlBox;
    private Checkbox enabledBox;

    /** Merkt sich den Text im URL-Feld, damit er beim Neuaufbau der Liste nicht verloren geht. */
    private String pendingMapUrl;

    private Component status = Component.empty();
    private int statusColor = 0xFFAAAAAA;

    public WebMapViewConfigScreen(Screen parent) {
        super(Component.translatable("config.webmapview.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int left = width / 2 - PANEL_WIDTH / 2;
        int y = 40;

        // --- Aktive Map-URL ---
        mapUrlBox = new EditBox(font, left, y + 12, PANEL_WIDTH, 20,
                Component.translatable("config.webmapview.map_url"));
        mapUrlBox.setMaxLength(512);
        String currentUrl = pendingMapUrl != null ? pendingMapUrl : UrlManager.getDefaultUrl();
        mapUrlBox.setValue(currentUrl == null ? "" : currentUrl);
        addRenderableWidget(mapUrlBox);

        // --- Gespeicherte URLs: Klick uebernimmt die URL ins Feld ---
        List<String> urls = UrlManager.getUrlList();
        int listY = y + 56;
        int shown = Math.min(urls.size(), MAX_VISIBLE_URLS);
        for (int i = 0; i < shown; i++) {
            String url = urls.get(i);
            int rowY = listY + i * 22;
            addRenderableWidget(Button.builder(Component.literal(url), button -> {
                        mapUrlBox.setValue(url);
                        pendingMapUrl = url;
                    })
                    .bounds(left, rowY, PANEL_WIDTH - 24, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.literal("x"), button -> {
                        UrlManager.removeUrlSilently(url);
                        // War die entfernte URL die aktive, zeigt das Feld nach dem
                        // Neuaufbau wieder die neue Standard-URL.
                        pendingMapUrl = url.equals(mapUrlBox.getValue()) ? null : mapUrlBox.getValue();
                        status = Component.translatable("config.webmapview.removed", url);
                        statusColor = 0xFFFFAA00;
                        rebuildWidgets();
                    })
                    .bounds(left + PANEL_WIDTH - 20, rowY, 20, 20)
                    .build());
        }

        // --- Neue URL hinzufuegen ---
        int addY = listY + shown * 22 + 16;
        newUrlBox = new EditBox(font, left, addY, PANEL_WIDTH - 96, 20,
                Component.translatable("config.webmapview.new_url"));
        newUrlBox.setMaxLength(512);
        newUrlBox.setHint(Component.translatable("config.webmapview.new_url.hint"));
        addRenderableWidget(newUrlBox);

        addRenderableWidget(Button.builder(Component.translatable("config.webmapview.add"), button -> {
                    String value = newUrlBox.getValue().trim();
                    if (value.isEmpty()) {
                        status = Component.translatable("config.webmapview.error.empty");
                        statusColor = 0xFFFF5555;
                        return;
                    }
                    if (UrlManager.addUrlSilently(value)) {
                        pendingMapUrl = value;
                        status = Component.translatable("config.webmapview.added", value);
                        statusColor = 0xFF55FF55;
                        rebuildWidgets();
                    } else {
                        pendingMapUrl = mapUrlBox.getValue();
                        status = Component.translatable("config.webmapview.exists", value);
                        statusColor = 0xFFFFAA00;
                    }
                })
                .bounds(left + PANEL_WIDTH - 88, addY, 88, 20)
                .build());

        // --- Mod aktiv / inaktiv ---
        enabledBox = Checkbox.builder(Component.translatable("config.webmapview.enabled"), font)
                .pos(left, addY + 30)
                .selected(UrlManager.isEnabled())
                .build();
        addRenderableWidget(enabledBox);

        // --- Speichern / Abbrechen ---
        int buttonsY = height - 52;
        addRenderableWidget(Button.builder(Component.translatable("config.webmapview.save"), button -> save())
                .bounds(left, buttonsY, 156, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(left + 164, buttonsY, 156, 20)
                .build());
    }

    /** Uebernimmt die Eingaben dauerhaft in config/webmapview.json. */
    private void save() {
        String url = mapUrlBox.getValue().trim();
        if (url.isEmpty()) {
            status = Component.translatable("config.webmapview.error.empty");
            statusColor = 0xFFFF5555;
            return;
        }
        pendingMapUrl = null;
        UrlManager.setEnabledSilently(enabledBox != null && enabledBox.selected());
        UrlManager.setDefaultUrlSilently(url);
        status = Component.translatable("config.webmapview.saved", UrlManager.getDefaultUrl());
        statusColor = 0xFF55FF55;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Abdunkeln
        graphics.fill(0, 0, width, height, 0xC0000000);

        int left = width / 2 - PANEL_WIDTH / 2;
        graphics.centeredText(font, title, width / 2, 20, 0xFFFFFFFF);

        graphics.text(font, Component.translatable("config.webmapview.map_url"), left, 40, 0xFFAAAAAA);

        List<String> urls = UrlManager.getUrlList();
        int listY = 40 + 56;
        if (urls.isEmpty()) {
            graphics.text(font, Component.translatable("config.webmapview.saved_urls.empty"), left, listY - 8,
                    0xFF777777);
        } else {
            graphics.text(font, Component.translatable("config.webmapview.saved_urls"), left, listY - 8, 0xFFAAAAAA);
        }

        int shown = Math.min(urls.size(), MAX_VISIBLE_URLS);
        int addLabelY = listY + shown * 22 + 4;
        graphics.text(font, Component.translatable("config.webmapview.new_url"), left, addLabelY, 0xFFAAAAAA);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, status, width / 2, height - 34, statusColor);
        }

        graphics.centeredText(font,
                Component.translatable("config.webmapview.file", WebmapViewConfig.configPath().toString()),
                width / 2, height - 12, 0xFF777777);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }
}
