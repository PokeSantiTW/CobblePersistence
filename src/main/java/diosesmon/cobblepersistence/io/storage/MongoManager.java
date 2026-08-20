package diosesmon.cobblepersistence.io.storage;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.io.config.ConfigManager;
import org.bson.Document;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * Manager de la conexión con MongoDB y configuración de pool de conexiones, timeout...
 */
public class MongoManager {

    private static MongoManager instance;

    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private MongoCollection<Document> mongoCollection;

    private MongoManager() {}

    public static MongoManager getInstance() {
        if (instance == null) {
            instance = new MongoManager();
        }
        return instance;
    }

    public boolean initialize() {
        try {
            ConnectionString connectionString = new ConnectionString(ConfigManager.getConfig().getMongo().getUri());

            MongoClientSettings settings = MongoClientSettings.builder()
                    .applyConnectionString(connectionString)
                    .applyToConnectionPoolSettings(builder -> {
                        builder.minSize(ConfigManager.getConfig().getMongo().getMinPoolSize())
                                .maxSize(ConfigManager.getConfig().getMongo().getMaxPoolSize())
                                .maxWaitTime(5, TimeUnit.SECONDS);
                    })
                    .build();

            this.mongoClient = MongoClients.create(settings);
            this.mongoDatabase = mongoClient.getDatabase(ConfigManager.getConfig().getMongo().getDatabase());
            // El índice de la colección "players" será el "_id" automático que crea el motor de MongoDB
            this.mongoCollection = this.mongoDatabase.getCollection("players");
            return true;
        } catch (Exception e) {
            CobblePersistence.LOGGER.error("No se ha podido iniciar la conexión con MongoDB", e);
            return false;
        }
    }

    public MongoDatabase getDatabase() {
        return mongoDatabase;
    }

    public MongoClient getClient() {
        return mongoClient;
    }

    public MongoCollection<Document> getMongoCollection() {
        return mongoCollection;
    }

    public void close() {
        if (mongoClient != null) {
            mongoClient.close();
        }
    }
}
