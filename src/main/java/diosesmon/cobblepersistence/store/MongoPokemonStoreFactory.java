package diosesmon.cobblepersistence.store;

import com.cobblemon.mod.common.api.storage.PokemonStore;
import com.cobblemon.mod.common.api.storage.StorePosition;
import com.cobblemon.mod.common.api.storage.factory.PokemonStoreFactory;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import diosesmon.cobblepersistence.CobblePersistence;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MongoPokemonStoreFactory implements PokemonStoreFactory {

    // Mantienen las instancias vivas en RAM mientras el jugador está online
    private static final Map<UUID, PlayerPartyStore> partyCache = new ConcurrentHashMap<>();
    private static final Map<UUID, PCStore> pcCache = new ConcurrentHashMap<>();

    @Override
    public @Nullable PlayerPartyStore getPlayerParty(@NotNull UUID uuid, @NotNull RegistryAccess registryAccess) {
        // Si no existe en RAM, crea una
        return partyCache.computeIfAbsent(uuid, PlayerPartyStore::new);
    }

    @Override
    public @Nullable PCStore getPC(@NotNull UUID uuid, @NotNull RegistryAccess registryAccess) {
        return pcCache.computeIfAbsent(uuid, PCStore::new);
    }

    @Override
    public @Nullable <E extends StorePosition, T extends PokemonStore<E>> T getCustomStore(@NotNull Class<T> aClass, @NotNull UUID uuid, @NotNull RegistryAccess registryAccess) {
        return null;
    }

    @Override
    public void shutdown(@NotNull RegistryAccess registryAccess) {
        partyCache.clear();
        pcCache.clear();
    }

    @Override
    public void onPlayerDisconnect(@NotNull ServerPlayer serverPlayer) {
    }

    public static void clearCacheForPlayer(UUID uuid) {
        partyCache.remove(uuid);
        pcCache.remove(uuid);
    }
}
