package diosesmon.cobblepersistence.redis;

import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.io.config.ConfigManager;
import diosesmon.cobblepersistence.io.storage.RedisManager;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class RedisLockManager {
    private static final Map<UUID, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final long LOCK_TTL_MS = 5000; // 5s TTL para el lock

    // Lock
    public static CompletableFuture<Boolean> acquireLock(UUID playerUuid) {
        String lockKey = "lock:player:" + playerUuid;
        SetArgs args = SetArgs.Builder.nx().px(LOCK_TTL_MS);

        return RedisManager.getInstance().getCommands().set(lockKey, ConfigManager.getConfig().getIdentifier(), args)
                .toFuture()
                .thenApply("OK"::equals);
    }

    // Renovación del TTL cada 3 segundos
    public static void startHeartbeat(UUID playerUuid) {
        String lockKey = "lock:player:" + playerUuid;

        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                // Renovar el TTL
                RedisManager.getInstance().getCommands()
                        .expire(lockKey, java.time.Duration.ofMillis(10000))
                        .toFuture()
                        .thenAccept(success -> {
                            if (!success) {
                                CobblePersistence.LOGGER.warn("No se pudo renovar el TTL del lock para: " + playerUuid);
                            }
                        });

            } catch (Throwable t) {
                // CRÍTICO: Capturar Throwable evita que el ScheduledExecutorService muera
                CobblePersistence.LOGGER.error("Error crítico dentro del Heartbeat de " + playerUuid, t);
            }
        }, 3, 3, TimeUnit.SECONDS);

        heartbeats.put(playerUuid, task);
    }

    // Detener Heartbeat y Liberar Lock. Hace ambas cosas a la vez
    public static CompletableFuture<Boolean> releaseLock(UUID playerUuid) {
        stopHeartbeat(playerUuid);
        String lockKey = "lock:player:" + playerUuid;
        CobblePersistence.LOGGER.info("Borrando lock para " + playerUuid);
        // Solo se borra si el valor coincide con el id del servidor de la config
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        return RedisManager.getInstance().getCommands()
                .<Long>eval(script, ScriptOutputType.INTEGER, new String[]{lockKey}, ConfigManager.getConfig().getIdentifier())
                .next()
                .toFuture()
                .thenApply(Long.valueOf(1)::equals);
    }

    public static void stopHeartbeat(UUID playerUuid) {
        ScheduledFuture<?> task = heartbeats.remove(playerUuid);
        if (task != null) {
            task.cancel(true);
            CobblePersistence.LOGGER.info("Heartbeat detenido para: " + playerUuid);
        }
    }

    public static void stopAllHeartbeats() {
        heartbeats.values().forEach(task -> task.cancel(true));
        heartbeats.clear();
        scheduler.shutdown();
    }
}
