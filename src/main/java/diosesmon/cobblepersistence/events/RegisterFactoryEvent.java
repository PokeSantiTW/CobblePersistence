package diosesmon.cobblepersistence.events;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.store.MongoPokemonStoreFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public class RegisterFactoryEvent {
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // Reemplazamos la fábrica por defecto de Cobblemon por la de MongoDB
            Cobblemon.INSTANCE.getStorage().registerFactory(Priority.HIGHEST, new MongoPokemonStoreFactory());
            CobblePersistence.LOGGER.info("StoreFactory de Cobblemon reemplazada por MongoPokemonStoreFactory.");
        });
    }
}
