package diosesmon.cobblepersistence.events;

import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.io.storage.PlayerMongo;
import diosesmon.cobblepersistence.redis.RedisHandshakeManager;
import diosesmon.cobblepersistence.redis.RedisLockManager;
import diosesmon.cobblepersistence.serializer.PlayerSerializer;
import diosesmon.cobblepersistence.store.MongoPokemonStoreFactory;
import diosesmon.cobblepersistence.sync.SessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerDisconnectEvent {
    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (ServerStoppingEvent.shuttingDown) return;

            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();

            saveData(uuid, server, player);
        });
    }

    public static CompletableFuture<Void> saveData(UUID uuid, MinecraftServer server, ServerPlayer player) {
        return RedisHandshakeManager.notifySaving(uuid)
                .thenCompose(v -> PlayerSerializer.serialize(server, player, false))
                .thenCompose(PlayerMongo::savePlayerData)
                .thenCompose(ok -> {
                    if (ok) {
                        SessionManager.removeSession(player.getUUID());
                        CobblePersistence.LOGGER.info("Datos guardados exitosamente: " + player.getGameProfile().getName());
                    } else {
                        CobblePersistence.LOGGER.error("Error en el guardado al desconectar de " + player.getGameProfile().getName());
                    }
                    RedisLockManager.releaseLock(uuid);
                    return RedisHandshakeManager.notifySaved(uuid); // Se notifica que ya se guardaron los datos
                })
                .exceptionally(throwable -> {
                    CobblePersistence.LOGGER.error("Error al guardar/desconectar a " + player.getScoreboardName(), throwable);
                    RedisLockManager.releaseLock(uuid);
                    MongoPokemonStoreFactory.clearCacheForPlayer(uuid);
                    return null;
                });
    }
}
