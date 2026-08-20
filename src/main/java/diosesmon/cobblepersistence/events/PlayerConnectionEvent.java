package diosesmon.cobblepersistence.events;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyReferencePacket;
import com.mojang.authlib.GameProfile;
import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.data.PlayerData;
import diosesmon.cobblepersistence.io.config.ConfigManager;
import diosesmon.cobblepersistence.io.storage.PlayerMongo;
import diosesmon.cobblepersistence.redis.RedisHandshakeManager;
import diosesmon.cobblepersistence.redis.RedisLockManager;
import diosesmon.cobblepersistence.serializer.PlayerSerializer;
import diosesmon.cobblepersistence.store.MongoPokemonStoreFactory;
import diosesmon.cobblepersistence.sync.SessionManager;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerConnectionEvent {

    // Caché temporal para los eventos de QUERY_START y JOIN
    private static final Map<UUID, PlayerData> PRELOADED_DATA = new ConcurrentHashMap<>();

    public static void register() {

        // Fase Asíncrona de conexión. Descarga los datos de MongoDB.
        ServerLoginConnectionEvents.QUERY_START.register(
                (handler, server, sender, sync) -> {
                    if (handler.authenticatedProfile == null) {
                        CobblePersistence.LOGGER.error("Authenticated Profile ha sido nulo. No puede continuar.");
                        return;
                    }

                    UUID uuid = handler.authenticatedProfile.getId();

                    sync.waitFor(
                            // Esperamos a que el servidor de origen termine de guardar los datos. Activamos el handshake.
                            RedisHandshakeManager.waitForSave(uuid)
                                    // Se intenta tomar el lock en Redis
                                    .thenCompose(v -> RedisLockManager.acquireLock(uuid))
                                    // 3. Evaluar Lock
                                    .thenCompose(acquired -> {
                                        if (!acquired) {
                                            handler.disconnect(Component.literal("Sesión ya activa en otro servidor"));
                                            return CompletableFuture.failedFuture(
                                                    new IllegalStateException("Lock denegado en Redis para " + uuid)
                                            );
                                        }
                                        // Si llega aquí, es que ha obtenido el lock. Inicio el heartbeat
                                        RedisLockManager.startHeartbeat(uuid);
                                        return PlayerMongo.loadPlayerData(uuid.toString());
                                    })
                                    // Procesar y guardar PlayerData en Mongo
                                    .thenCompose(optPlayerData -> {
                                        PlayerData playerData;
                                        if (optPlayerData.isPresent()) {
                                            // No es la primera vez del jugador
                                            playerData = optPlayerData.get();
                                            playerData.setLastUpdated(System.currentTimeMillis());
                                            playerData.setOnline(true);
                                            CobblePersistence.LOGGER.info("Datos descargados en QUERY_START para " + playerData.getUsername());
                                        } else {
                                            // Primera vez del jugador en el servidor
                                            playerData = new PlayerData(
                                                    uuid.toString(),
                                                    handler.getUserName(),
                                                    System.currentTimeMillis(),
                                                    System.currentTimeMillis(),
                                                    1,
                                                    ConfigManager.getConfig().getIdentifier(),
                                                    true,
                                                    new ConcurrentHashMap<>()
                                            );
                                        }

                                        PRELOADED_DATA.put(uuid, playerData);
                                        SessionManager.createSession(uuid, playerData.getVersion(), playerData.getServerPosition(), playerData.getFirstJoin());

                                        return PlayerMongo.savePlayerData(playerData);
                                    })
                                    // Confirmación final
                                    .thenAccept(ok -> {
                                        if (ok) {
                                            CobblePersistence.LOGGER.info("Datos guardados exitosamente para " + uuid);
                                        } else {
                                            CobblePersistence.LOGGER.error("Error en el guardado al conectar de " + uuid);
                                        }
                                    })
                                    .exceptionally(throwable -> {
                                        CobblePersistence.LOGGER.warn("Proceso de login cancelado o fallido para " + uuid + ": " + throwable.getMessage());
                                        handler.disconnect(Component.literal("El proceso de login ha fallado"));
                                        return null;
                                    })
                    );
                });

        // Procesar los datos. Esto toca hacerlo en el Main-Thread.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();

            PlayerData data = PRELOADED_DATA.remove(player.getUUID());
            if (data != null) {
                PlayerSerializer.deserialize(player, data);

                server.execute(() -> {
                    PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);
                    PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);

                    party.sendTo(player);
                    CobblemonNetwork.INSTANCE.sendPacketToPlayer(
                            player,
                            new SetPartyReferencePacket(party.getUuid())
                    );

                    pc.sendTo(player);
                });
            }
        });
    }
}
