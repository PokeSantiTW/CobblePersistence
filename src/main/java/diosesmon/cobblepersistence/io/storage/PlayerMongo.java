package diosesmon.cobblepersistence.io.storage;

import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.reactivestreams.client.MongoCollection;
import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.data.PlayerData;
import diosesmon.cobblepersistence.serializer.PlayerSerializer;
import diosesmon.cobblepersistence.sync.SessionManager;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerMongo {

    public static CompletableFuture<Boolean> savePlayerData(PlayerData data) {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            MongoCollection<Document> collection = MongoManager.getInstance().getMongoCollection();

            Bson filter = Filters.and(
                    Filters.eq("_id", data.getUuid()),
                    Filters.eq("version", data.getVersion())
            );

            Document documentPlayerData = PlayerSerializer.toDocument(data);
            SessionManager.PlayerSession session = SessionManager.getSession(UUID.fromString(data.getUuid()));

            // Se asigna una nueva versión al documento a guardar
            int newVersion = data.getVersion() + 1;
            documentPlayerData.put("version", newVersion);

            return Mono.from(collection.replaceOne(filter, documentPlayerData))
                    .flatMap(result -> {
                        if (result.getMatchedCount() > 0) {
                            if (session != null) session.setVersion(newVersion);
                            return Mono.just(true);
                        }
                        // Si no encontró nada, manejamos el error. Hacemos esto por el control de versiones.
                        return insertConflict(data, session, documentPlayerData, collection);
                    })
                    .onErrorReturn(false)
                    .toFuture();
        } catch (Exception e) {
            e.printStackTrace();
            future.complete(false);
            return future;
        }
    }

    /**
     * Con este método comprobamos dos posibles escenarios:
     * 1. El jugador directamente no existe en la base de datos, es su primera vez.
     * 2. Si existe, pero no con la versión correcta. Conflicto de versiones.
     * @return
     */
    private static Mono<Boolean> insertConflict(PlayerData data, SessionManager.PlayerSession session,
                                                Document document, MongoCollection<Document> collection) {

        Bson filterOnlyUuid = Filters.and(
                Filters.eq("_id", data.getUuid())
        );

        return Mono.from(collection.find(filterOnlyUuid).first()).flatMap(exists -> {
            // Si entra aquí es que el jugador existe, pero no coincide versión
            CobblePersistence.LOGGER.error("Hay conflicto de versiones para el jugador " + data.getUsername());
            return Mono.just(false);
        }).switchIfEmpty(Mono.defer(() -> {
            // Si entra aquí es que el jugador es nuevo.
            document.put("version", 1);
            return Mono.from(collection.insertOne(document)).map(
                    insertOneResult -> {
                        if (session != null) session.setVersion(1);
                        return true;
                    }
            );
        }));
    }

    public static CompletableFuture<Optional<PlayerData>> loadPlayerData(String uuid) {

        CompletableFuture<Optional<PlayerData>> future = new CompletableFuture<>();

        try {
            MongoCollection<Document> collection = MongoManager.getInstance().getMongoCollection();

            Bson filter = Filters.and(
                    Filters.eq("_id", uuid)
            );

            return Mono.from(collection.find(filter).first())
                    .map(document -> Optional.of(PlayerSerializer.fromDocument(document)))
                    .defaultIfEmpty(Optional.empty())
                    .onErrorResume(e -> {
                        CobblePersistence.LOGGER.error("Error al leer los datos desde MongoDB de la UUID " + uuid, e);
                        return Mono.just(Optional.empty());
                    })
                    .toFuture();
        } catch (Exception e) {
            e.printStackTrace();
            future.complete(null);
            return future;
        }
    }

}
