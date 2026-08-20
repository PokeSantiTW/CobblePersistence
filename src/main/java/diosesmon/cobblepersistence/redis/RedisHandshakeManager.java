package diosesmon.cobblepersistence.redis;

import diosesmon.cobblepersistence.io.storage.RedisManager;
import diosesmon.cobblepersistence.store.MongoPokemonStoreFactory;
import io.lettuce.core.pubsub.RedisPubSubAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class RedisHandshakeManager {
    private static final String CHANNEL = "cobblepersistence:handshake";
    private static final Map<UUID, CompletableFuture<Void>> pendingSaves = new ConcurrentHashMap<>();

    public static void init() {
        var pubSub = RedisManager.getInstance().getPubSubConnection();
        pubSub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                if (message.startsWith("SAVED:")) {
                    UUID uuid = UUID.fromString(message.split(":")[1]);
                    CompletableFuture<Void> future = pendingSaves.remove(uuid);

                    if (future != null) {
                        future.complete(null);
                    }

                    MongoPokemonStoreFactory.clearCacheForPlayer(uuid);
                }
            }
        });
        pubSub.async().subscribe(CHANNEL);
    }

    public static CompletableFuture<Void> notifySaving(UUID playerUuid) {
        String stateKey = "state:player:" + playerUuid;
        return RedisManager.getInstance().getCommands().set(stateKey, "SAVING").toFuture()
                .thenCompose(v -> RedisManager.getInstance().getCommands().publish(CHANNEL, "SAVING:" + playerUuid).toFuture())
                .thenAccept(v -> {});
    }

    public static CompletableFuture<Void> notifySaved(UUID playerUuid) {
        String stateKey = "state:player:" + playerUuid;
        return RedisManager.getInstance().getCommands().set(stateKey, "SAVED").toFuture()
                .thenCompose(v -> RedisManager.getInstance().getCommands().publish(CHANNEL, "SAVED:" + playerUuid).toFuture())
                .thenAccept(v -> {});
    }

    public static CompletableFuture<Void> waitForSave(UUID playerUuid) {
        String stateKey = "state:player:" + playerUuid;

        return RedisManager.getInstance().getCommands().get(stateKey).toFuture().toCompletableFuture().thenCompose(state -> {
            // Si no hay proceso de guardado previo o ya terminó, continuar inmediatamente
            if (state == null || "SAVED".equals(state)) {
                return CompletableFuture.completedFuture(null);
            }

            // Si está guardando, registrar la promesa y esperar a la señal PubSub
            CompletableFuture<Void> waitFuture = new CompletableFuture<>();
            pendingSaves.put(playerUuid, waitFuture);

            // Cancelar la espera tras 3 segundos por si el servidor de origen está fallando
            return waitFuture.orTimeout(3, TimeUnit.SECONDS).exceptionally(ex -> null);
        });
    }
}
