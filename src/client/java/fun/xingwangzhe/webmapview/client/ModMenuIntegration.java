package fun.xingwangzhe.webmapview.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * ModMenu-Anbindung: sorgt fuer das Zahnrad-Symbol neben der Mod in der Mod-Liste.
 * Dadurch ist keine Konfiguration per Chat-Command mehr notwendig.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return WebMapViewConfigScreen::new;
    }
}
