package diosesmon.cobblepersistence;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.storage.factory.PokemonStoreFactory;
import diosesmon.cobblepersistence.events.PlayerConnectionEvent;
import diosesmon.cobblepersistence.events.PlayerDisconnectEvent;
import diosesmon.cobblepersistence.events.RegisterFactoryEvent;
import diosesmon.cobblepersistence.events.ServerStoppingEvent;
import diosesmon.cobblepersistence.io.config.ConfigManager;
import diosesmon.cobblepersistence.io.config.ModConfig;
import diosesmon.cobblepersistence.io.storage.MongoManager;
import diosesmon.cobblepersistence.io.storage.RedisManager;
import diosesmon.cobblepersistence.redis.RedisHandshakeManager;
import diosesmon.cobblepersistence.redis.RedisLockManager;
import diosesmon.cobblepersistence.store.MongoPokemonStoreFactory;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import org.bson.Document;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class CobblePersistence implements ModInitializer {
	public static final String MOD_ID = "cobblepersistence";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConfigManager.initialize();

		RegisterFactoryEvent.register();

		serverStarted();
	}

	public void serverStarted() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			boolean mongoOk = MongoManager.getInstance().initialize();
			boolean redisOk = RedisManager.getInstance().initialize();
			if (!mongoOk || !redisOk) {
				LOGGER.error("No hay conexión con MongoDB o Redis. Comprueba las credenciales en config.json.");
				server.stopServer(); // Para el servidor de forma segura
			} else {
				RedisHandshakeManager.init();

				PlayerConnectionEvent.register();
				PlayerDisconnectEvent.register();

				ServerStoppingEvent.register();

				LOGGER.info("CobblePersistence has been initialized!");
			}
		});
	}
}