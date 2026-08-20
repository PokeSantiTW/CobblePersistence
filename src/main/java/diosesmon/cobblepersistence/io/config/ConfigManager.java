package diosesmon.cobblepersistence.io.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.utils.FileAsync;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Clase que se encargará de leer y guardar toda la configuración del mod. Todos los métodos son
 * asíncronos.
 */
public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ModConfig config;

    public static void initialize() {
        CompletableFuture<Boolean> futureRead = FileAsync.readFile("/config/cobblepersistence/",
                "config.json", c -> {
                    config = GSON.fromJson(c, ModConfig.class);
                });

        // Si no ha encontrado el archivo, lo crea.
        if (!futureRead.join()) {
            CobblePersistence.LOGGER.info("No se encontró config.json para CobblePersistence. Creando uno...");
            CompletableFuture<Boolean> futureWrite = write();

            if (!futureWrite.join()) {
                CobblePersistence.LOGGER.error("No se pudo escribir el archivo de config.json");
            }
        } else {
            CobblePersistence.LOGGER.info("Se ha leido correctamente el config.json");
        }
    }

    public static CompletableFuture<Boolean> write() {
        String data = GSON.toJson(new ModConfig());
        return FileAsync.writeFile("/config/cobblepersistence/", "config.json", data);
    }

    public static ModConfig getConfig() {
        return config;
    }
}
