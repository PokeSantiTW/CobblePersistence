package diosesmon.cobblepersistence.io.storage;

import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.io.config.ConfigManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.reactive.RedisStringReactiveCommands;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;

import java.time.Duration;

/**
 * Manager de la conexión con Redis
 */
public class RedisManager {

    private static RedisManager instance;

    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisReactiveCommands<String, String> reactiveCommands;

    private RedisManager() {}

    public static RedisManager getInstance() {
        if (instance == null) {
            instance = new RedisManager();
        }
        return instance;
    }

    public boolean initialize() {
        try {
            RedisURI uri = RedisURI.builder()
                    .withAuthentication(ConfigManager.getConfig().getRedis().getUser(), ConfigManager.getConfig().getRedis().getPassword())
                    .withHost(ConfigManager.getConfig().getRedis().getHostname())
                    .withPort(ConfigManager.getConfig().getRedis().getPort())
                    .withTimeout(Duration.ofSeconds(ConfigManager.getConfig().getRedis().getTimeoutSeconds()))
                    .build();

            this.redisClient = RedisClient.create(uri);
            connection = redisClient.connect();
            this.reactiveCommands = connection.reactive();

            this.pubSubConnection = redisClient.connectPubSub();
            return true;
        } catch (Exception e) {
            CobblePersistence.LOGGER.error("No se ha podido iniciar la conexión con Redis", e);
            return false;
        }
    }

    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (pubSubConnection != null) {
            pubSubConnection.close();
        }
        if (redisClient != null) {
            redisClient.close();
        }
    }

    public RedisReactiveCommands<String, String> getCommands() {
        return reactiveCommands;
    }

    public StatefulRedisConnection<String, String> getConnection() {
        return connection;
    }

    public StatefulRedisPubSubConnection<String, String> getPubSubConnection() {
        return pubSubConnection;
    }
}
