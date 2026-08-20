package diosesmon.cobblepersistence.events;

import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.io.storage.MongoManager;
import diosesmon.cobblepersistence.io.storage.RedisManager;
import diosesmon.cobblepersistence.redis.RedisLockManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServerStoppingEvent {
    // Este parámetro permite que en la función de DISCONNECT no se vuelva a ejecutar el saveData(), ya lo hacemos aquí.
    public static volatile boolean shuttingDown = false;

    public static void register() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            shuttingDown = true;
            CobblePersistence.LOGGER.info("Guardando datos de los jugadores...");
            List<ServerPlayer> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                CobblePersistence.LOGGER.info("No hay jugadores conectados de los que guardar datos");
            } else {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (ServerPlayer player : players) {
                    futures.add(
                            PlayerDisconnectEvent.saveData(player.getUUID(), server, player)
                    );
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                CobblePersistence.LOGGER.info("Datos de todos los jugadores guardados durante el apagado.");
            }

            CobblePersistence.LOGGER.info("Cerrando conexiones de base de datos...");
            RedisLockManager.stopAllHeartbeats();
            MongoManager.getInstance().close();
            RedisManager.getInstance().close();
        });
    }
}
