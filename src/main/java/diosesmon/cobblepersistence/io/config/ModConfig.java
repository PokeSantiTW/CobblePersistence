package diosesmon.cobblepersistence.io.config;

/**
 * Clase que guardará todos los datos que luego se usarán a la hora de configurar. Datos de conexiones,
 * módulos, opciones de guardado, etc.
 */
public class ModConfig {

    private String identifier;
    private MongoConfig mongo;
    private RedisConfig redis;

    public ModConfig() {
        this.identifier = "ServerA";
        this.mongo = new MongoConfig();
        this.redis = new RedisConfig();
    }

    public String getIdentifier() {
        return identifier;
    }

    public MongoConfig getMongo() {
        return mongo;
    }

    public RedisConfig getRedis() {
        return redis;
    }

    public static class MongoConfig {
        private String connection = "localhost:27017";
        private String user = "root";
        private String password = "<PASSWORD>";
        private String database = "cobblemon";
        private int minPoolSize = 5;
        private int maxPoolSize = 30;

        public String getConnection() {
            return connection;
        }

        public String getPassword() {
            return password;
        }

        public String getUser() {
            return user;
        }

        public String getDatabase() {
            return database;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public int getMinPoolSize() {
            return minPoolSize;
        }

        public String getUri() {
            return "mongodb://"+user+":"+password+"@"+connection+"/";
        }
    }

    public static class RedisConfig {
        private String hostname = "localhost";
        private int port = 6379;
        private String user = "default";
        private String password = "<PASSWORD>";
        private int timeoutSeconds = 5;

        public String getHostname() {
            return hostname;
        }

        public int getPort() {
            return port;
        }

        public String getUser() {
            return user;
        }

        public String getPassword() {
            return password;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }
    }

}
