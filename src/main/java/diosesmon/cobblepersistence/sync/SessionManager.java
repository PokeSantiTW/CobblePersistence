package diosesmon.cobblepersistence.sync;

import diosesmon.cobblepersistence.data.PlayerData;
import diosesmon.cobblepersistence.io.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {
    private static final Map<UUID, PlayerSession> SESSION_MAP = new ConcurrentHashMap<>();

    public static void createSession(UUID uuid, int version,
                                     Map<String, PlayerData.ServerPosition> positions, long firstJoin) {
        PlayerSession session = new PlayerSession(version, firstJoin);
        if (positions != null) {
            session.getPositions().putAll(positions);
        }
        SESSION_MAP.put(uuid, session);
    }

    public static PlayerSession getSession(UUID uuid) {
        return SESSION_MAP.get(uuid);
    }

    public static void removeSession(UUID uuid) {
        SESSION_MAP.remove(uuid);
    }

    public static class PlayerSession {
        private volatile int version;
        private final long firstJoin;
        private final Map<String, PlayerData.ServerPosition> positions = new ConcurrentHashMap<>();

        public PlayerSession(int version, long firstJoin) {
            this.version = version;
            this.firstJoin = firstJoin;
        }

        public int getVersion() {
            return version;
        }

        public long getFirstJoin() {
            return firstJoin;
        }

        public Map<String, PlayerData.ServerPosition> getPositions() {
            return positions;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public void updateActualServerPosition(PlayerData.ServerPosition position) {
            if (position != null) {
                this.positions.put(ConfigManager.getConfig().getIdentifier(), position);
            }
        }
    }
}
