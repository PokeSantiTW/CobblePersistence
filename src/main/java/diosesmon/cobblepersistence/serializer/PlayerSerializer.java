package diosesmon.cobblepersistence.serializer;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.CobblemonNetwork;
import com.cobblemon.mod.common.api.pokedex.PokedexManager;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.api.storage.player.GeneralPlayerData;
import com.cobblemon.mod.common.api.storage.player.PlayerDataExtension;
import com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyReferencePacket;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import diosesmon.cobblepersistence.CobblePersistence;
import diosesmon.cobblepersistence.data.PlayerData;
import diosesmon.cobblepersistence.io.config.ConfigManager;
import diosesmon.cobblepersistence.sync.SessionManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.level.Level;
import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * En esta clase estarán los métodos necesarios para el serializado/deserializado de los datos del jugador, al igual
 * que la estructura de MongoDB. Los métodos son asíncronos, pues son tareas pesadas que deben ir fuera del main-thread.
 */
public class PlayerSerializer {

    public static CompletableFuture<PlayerData> serialize(MinecraftServer server, ServerPlayer player, boolean isOnline) {
        return CompletableFuture.supplyAsync(() -> {
            // Identificadores y versionado
            UUID playerUuid = player.getUUID();
            String username = player.getGameProfile().getName();
            long lastUpdated = System.currentTimeMillis();
            SessionManager.PlayerSession session = SessionManager.getSession(playerUuid);
            int currentVersion = session != null ? session.getVersion() : 1;
            long firstJoin = session != null ? session.getFirstJoin() : 0;

            // ----
            // Empiezo a sacar los datos de Minecraft
            String playerDataSnbt = player.saveWithoutId(new CompoundTag()).toString();
            String statsJson = player.getStats().toJson();
            String advancementsJson = advancementsToJson(player);
            // Posicion
            Map<String, PlayerData.ServerPosition> positionMap;
            if (session != null) {
                positionMap = session.getPositions();
            } else {
                positionMap = new HashMap<>();
            }
            positionMap.put(ConfigManager.getConfig().getIdentifier(), new PlayerData.ServerPosition(
                    player.level().dimension().location().toString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot()
            ));

            // ----
            // Ahora saco los datos de Cobblemon
            PlayerPartyStore playerPartyStore = Cobblemon.INSTANCE.getStorage().getParty(player);
            PCStore pcStore = Cobblemon.INSTANCE.getStorage().getPC(player);
            PokedexManager pokedexManager = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);

            String partyJson = playerPartyStore.saveToJSON(new JsonObject(), player.registryAccess()).toString();
            String pcJson = pcStore.saveToJSON(new JsonObject(), player.registryAccess()).toString();
            String pokedexJson = PokedexManager.Companion.getCODEC().encodeStart(JsonOps.INSTANCE, pokedexManager)
                    .getOrThrow().toString();
            String cobblemonPlayerData = genericDataToJson(player);

            return new PlayerData(
                    playerUuid.toString(),
                    username,
                    firstJoin,
                    lastUpdated,
                    currentVersion,
                    ConfigManager.getConfig().getIdentifier(),
                    isOnline,
                    playerDataSnbt,
                    advancementsJson,
                    statsJson,
                    positionMap,
                    partyJson,
                    pcJson,
                    pokedexJson,
                    cobblemonPlayerData
            );
        }, server);
    }

    private static String advancementsToJson(ServerPlayer player) {
        JsonObject headAdvancements = new JsonObject();

        player.getAdvancements().progress.forEach(((holder, progress) -> {
            if (progress.hasProgress()) {
                JsonObject advancement = new JsonObject();
                JsonObject criteria = new JsonObject();

                for (String c : progress.getCompletedCriteria()) {
                    criteria.addProperty(c, true);
                }

                advancement.add("criteria", criteria);
                advancement.addProperty("done", progress.isDone());

                headAdvancements.add(holder.id().toString(), advancement);
            }
        }));

        return new Gson().toJson(headAdvancements);
    }

    private static String genericDataToJson(ServerPlayer player) {
        GeneralPlayerData genericData = Cobblemon.playerDataManager.getGenericData(player);

        JsonObject genericJson = new JsonObject();
        genericJson.addProperty("uuid", genericData.getUuid().toString());
        genericJson.addProperty("starterPrompted", genericData.getStarterPrompted());
        genericJson.addProperty("starterSelected", genericData.getStarterSelected());
        if (genericData.getStarterUUID() != null) {
            genericJson.addProperty("starterUUID", genericData.getStarterUUID().toString());
        }

        JsonArray keyItemsArray = new JsonArray();
        for (ResourceLocation loc : genericData.getKeyItems()) {
            keyItemsArray.add(loc.toString());
        }
        genericJson.add("keyItems", keyItemsArray);

        JsonObject extraData = new JsonObject();
        for (Map.Entry<String, PlayerDataExtension> entries : genericData.getExtraData().entrySet()) {
            extraData.add(entries.getKey(), entries.getValue().serialize());
        }
        genericJson.add("extraData", extraData);

        return genericJson.toString();
    }

//    private static String pokedexToJson(ServerPlayer player) {
//        PokedexManager pokedexManager = Cobblemon.playerDataManager.getPokedexData(player);
//        Map<ResourceLocation, SpeciesDexRecord> records = pokedexManager.getSpeciesRecords();
//
//        CompoundTag mainTag = new CompoundTag();
//        for (Map.Entry<ResourceLocation, SpeciesDexRecord> entry : records.entrySet()) {
//            ResourceLocation speciesId = entry.getKey();
//            SpeciesDexRecord record = entry.getValue();
//
//            CompoundTag recordTag = new CompoundTag();
//            recordTag.putBoolean();
//        }
//    }

    public static Document toDocument(PlayerData data) {
        Document document = new Document();

        // Bloque de identificadores
        document.put("_id", data.getUuid());
        document.put("username", data.getUsername());
        document.put("firstJoin", data.getFirstJoin());
        document.put("lastUpdated", data.getLastUpdated());
        document.put("version", data.getVersion());
        document.put("actualServer", data.getActualServer());
        document.put("isOnline", data.isOnline());

        // Bloque de datos de Minecraft
        Document minecraftDocument = new Document();
        minecraftDocument.put("playerData", data.getPlayerDataSnbt());

        if (data.getStatsJson() != null) {
            minecraftDocument.put("stats", Document.parse(data.getStatsJson()));
        }

        if (data.getAdvancementsJson() != null) {
            minecraftDocument.put("advancements", Document.parse(data.getAdvancementsJson()));
        }

        if (data.getServerPosition() != null) {
            Document positionsDocument = new Document();
            for (Map.Entry<String, PlayerData.ServerPosition> entry : data.getServerPosition().entrySet()) {
                PlayerData.ServerPosition pos = entry.getValue();

                Document serverPositionDocument = new Document()
                        .append("dimension", pos.dimension())
                        .append("x", pos.x())
                        .append("y", pos.y())
                        .append("z", pos.z())
                        .append("yaw", pos.yaw())
                        .append("pitch", pos.pitch());

                positionsDocument.append(entry.getKey(), serverPositionDocument);
            }
            minecraftDocument.put("positions", positionsDocument);
        }

        document.put("minecraftData", minecraftDocument);

        // Bloque de datos de Cobblemon
        Document cobblemonDocument = new Document();
        if (data.getPartyJson() != null) {
            cobblemonDocument.put("party", Document.parse(data.getPartyJson()));
        }
        if (data.getPcJson() != null) {
            cobblemonDocument.put("pc", Document.parse(data.getPcJson()));
        }
        if (data.getPokedexJson() != null) {
            cobblemonDocument.put("pokedex", Document.parse(data.getPokedexJson()));
        }
        cobblemonDocument.put("genericData", data.getCobblemonPlayerData());

        document.put("cobblemonData", cobblemonDocument);

        return document;
    }

    public static void deserialize(ServerPlayer player, PlayerData data) {
        if (data == null) return;

        // Se inyectan los datos de Minecraft
        if (data.getPlayerDataSnbt() != null && !data.getPlayerDataSnbt().isEmpty()) {
            try {
                CompoundTag playerDataTag = TagParser.parseTag(data.getPlayerDataSnbt());
                player.load(playerDataTag);
            } catch (Exception e) {
                CobblePersistence.LOGGER.error("Error al parsear Player Data NBT para " + data.getUsername(), e);
            }
        }
        // Se inyectan más datos de Minecraft como las Stats y Advancements.
        if (data.getStatsJson() != null && !data.getStatsJson().isEmpty()) {
            try {
                ServerStatsCounter stats = player.getStats();
                stats.parseLocal(player.getServer().getFixerUpper(), data.getStatsJson());
                stats.sendStats(player);
            } catch (Exception e) {
                CobblePersistence.LOGGER.error("Error al aplicar stats para " + data.getUsername(), e);
            }
        }
        if (data.getAdvancementsJson() != null && !data.getAdvancementsJson().isEmpty()) {
            deserializeAdvancements(player, data);
        }
        // Se aplica la posición correspondiente al jugador
        PlayerData.ServerPosition position = data.getActualServerPosition();
        if (position != null) {
            try {
                ResourceLocation worldLoc = ResourceLocation.parse(position.dimension());
                ResourceKey<Level> worldKey = ResourceKey.create(Registries.DIMENSION, worldLoc);
                ServerLevel targetLevel = player.getServer().getLevel(worldKey);

                if (targetLevel != null) {
                    player.teleportTo(
                            targetLevel,
                            position.x(),
                            position.y(),
                            position.z(),
                            position.yaw(),
                            position.pitch()
                    );
                }
            } catch (Exception e) {
                CobblePersistence.LOGGER.error("Error al aplicar zona de aparición a " + data.getUsername(), e);
            }
        }

        // Se inyectan los datos de Cobblemon.
        if (data.getPartyJson() != null && !data.getPartyJson().isEmpty()) {
            try {
                JsonObject partyJson = JsonParser.parseString(data.getPartyJson()).getAsJsonObject();
                PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(player);

                party.loadFromJSON(partyJson, player.registryAccess());
                party.initialize();

                party.sendTo(player);
                CobblemonNetwork.INSTANCE.sendPacketToPlayer(
                        player,
                        new SetPartyReferencePacket(party.getUuid())
                );
            } catch (Exception e) {
                CobblePersistence.LOGGER.error("Error al aplicar Pokémon de Equipo a " + data.getUsername(), e);
            }
        }
        if (data.getPcJson() != null && !data.getPcJson().isEmpty()) {
            try {
                JsonObject pcJson = JsonParser.parseString(data.getPcJson()).getAsJsonObject();
                PCStore pc = Cobblemon.INSTANCE.getStorage().getPC(player);

                // Inyectar JSON de Mongo sobre el PC vacío
                pc.loadFromJSON(pcJson, player.registryAccess());
                pc.initialize();
            } catch (Exception e) {
                CobblePersistence.LOGGER.error("Error al aplicar Pokémon de PC a " + data.getUsername(), e);
            }
        }
        if (data.getPokedexJson() != null && !data.getPokedexJson().isEmpty()) {
            try {
                JsonElement json = JsonParser.parseString(data.getPokedexJson());
                PokedexManager loaded = PokedexManager.Companion.getCODEC()
                        .parse(JsonOps.INSTANCE, json)
                        .getOrThrow();
                PokedexManager current = Cobblemon.INSTANCE.getPlayerDataManager().getPokedexData(player);
                current.getSpeciesRecords().clear();
                current.getSpeciesRecords().putAll(
                        loaded.getSpeciesRecords()
                );
            } catch (Exception e) {
                CobblePersistence.LOGGER.error("Error al aplicar Pokedex a " + data.getUsername(), e);
            }
        }
        if (data.getCobblemonPlayerData() != null && !data.getCobblemonPlayerData().isEmpty()) {
            deserializeGenericData(player, data);
        }

        // Sincronizar
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.slotsChanged(player.getInventory());
    }

    /**
     * Debido a que deserializar desde un JSON los logros es más complejo, se hace en este method aparte.
     */
    public static void deserializeAdvancements(ServerPlayer player, PlayerData data) {
        try {
            JsonObject jsonObject = JsonParser.parseString(data.getAdvancementsJson()).getAsJsonObject();
            PlayerAdvancements playerAdvancements = player.getAdvancements();
            ServerAdvancementManager manager = player.getServer().getAdvancements();

            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                String advancementId = entry.getKey();
                ResourceLocation loc = ResourceLocation.parse(advancementId);
                AdvancementHolder holder = manager.get(loc);

                if (holder != null) {
                    AdvancementProgress progress = playerAdvancements.getOrStartProgress(holder);
                    JsonObject advancementData = entry.getValue().getAsJsonObject();

                    // Recorremos los criterios completados guardados en el JSON
                    if (advancementData.has("criteria")) {
                        JsonObject criteria = advancementData.getAsJsonObject("criteria");
                        for (Map.Entry<String, JsonElement> criterion : criteria.entrySet()) {
                            String criterionName = criterion.getKey();
                            progress.grantProgress(criterionName);
                        }
                    }
                }
            }

            playerAdvancements.setPlayer(player);
        } catch (Exception e) {
            CobblePersistence.LOGGER.error("Error al aplicar logros a " + data.getUsername(), e);
        }
    }

    public static void deserializeGenericData(ServerPlayer player, PlayerData data) {
        try {
            JsonObject json = JsonParser.parseString(data.getCobblemonPlayerData()).getAsJsonObject();
            GeneralPlayerData genericData = Cobblemon.playerDataManager.getGenericData(player);

            // --- Estado del Starter ---
            if (json.has("starterPrompted")) genericData.setStarterPrompted(json.get("starterPrompted").getAsBoolean());
            if (json.has("starterLocked")) genericData.setStarterLocked(json.get("starterLocked").getAsBoolean());
            if (json.has("starterSelected")) genericData.setStarterSelected(json.get("starterSelected").getAsBoolean());

            if (json.has("starterUUID") && !json.get("starterUUID").isJsonNull()) {
                genericData.setStarterUUID(UUID.fromString(json.get("starterUUID").getAsString()));
            }

            // --- Tema de Batalla ---
            if (json.has("battleTheme") && !json.get("battleTheme").isJsonNull()) {
                genericData.setBattleTheme(ResourceLocation.tryParse(json.get("battleTheme").getAsString()));
            }

            // --- Objetos Clave (Key Items) ---
            if (json.has("keyItems")) {
                JsonArray keyItemsArray = json.getAsJsonArray("keyItems");
                genericData.getKeyItems().clear();
                for (JsonElement element : keyItemsArray) {
                    ResourceLocation loc = ResourceLocation.tryParse(element.getAsString());
                    if (loc != null) {
                        genericData.getKeyItems().add(loc);
                    }
                }
            }

            if (json.has("extraData")) {
                JsonObject extraDataObj = json.getAsJsonObject("extraData");
                var currentExtraData = genericData.getExtraData();

                extraDataObj.entrySet().forEach(entry -> {
                    String key = entry.getKey();
                    if (entry.getValue().isJsonObject()) {
                        JsonObject extJson = entry.getValue().getAsJsonObject();
                        var extension = currentExtraData.get(key);

                        if (extension != null) {
                            extension.deserialize(extJson);
                        }
                    }
                });
            }

            // Sincronizar actualización de GeneralPlayerData con el cliente
            genericData.sendToPlayer(player);
        } catch (Exception e) {
            CobblePersistence.LOGGER.error("Error al aplicar generic data a " + data.getUsername(), e);
        }
    }

    public static PlayerData fromDocument(Document document) {
        PlayerData data = new PlayerData();
        data.setUuid(document.getString("_id"));
        data.setUsername(document.getString("username"));
        data.setLastUpdated(document.getLong("lastUpdated"));
        data.setVersion(document.getInteger("version", 0));
        data.setFirstJoin(document.getLong("firstJoin"));
        data.setActualServer(document.getString("actualServer"));
        data.setOnline(document.getBoolean("isOnline", false));

        // Bloque de datos de Minecraft
        Document minecraftData = document.get("minecraftData", Document.class);
        if (minecraftData != null) {
            data.setPlayerDataSnbt(minecraftData.getString("playerData"));

            Document statsDoc = minecraftData.get("stats", Document.class);
            if (statsDoc != null) data.setStatsJson(statsDoc.toJson());

            Document advancementsDoc = minecraftData.get("advancements", Document.class);
            if (advancementsDoc != null) data.setAdvancementsJson(advancementsDoc.toJson());

            // Deserializar mapa de posiciones por servidor.
            Document positionsDoc = minecraftData.get("positions", Document.class);
            if (positionsDoc != null) {
                Map<String, PlayerData.ServerPosition> positionsMap = new ConcurrentHashMap<>();
                for (String serverId : positionsDoc.keySet()) {
                    Document posDoc = positionsDoc.get(serverId, Document.class);
                    if (posDoc != null) {
                        positionsMap.put(serverId, new PlayerData.ServerPosition(
                                posDoc.getString("dimension"),
                                posDoc.getDouble("x"),
                                posDoc.getDouble("y"),
                                posDoc.getDouble("z"),
                                posDoc.getDouble("yaw").floatValue(),
                                posDoc.getDouble("pitch").floatValue()
                        ));
                    }
                }
                data.setServerPosition(positionsMap);
            }
        }

        // Bloque de datos de Cobblemon
        Document cobblemonData = document.get("cobblemonData", Document.class);
        if (cobblemonData != null) {
            Document partyDoc = cobblemonData.get("party", Document.class);
            if (partyDoc != null) data.setPartyJson(partyDoc.toJson());

            Document pcDoc = cobblemonData.get("pc", Document.class);
            if (pcDoc != null) data.setPcJson(pcDoc.toJson());

            Document pokedexDoc = cobblemonData.get("pokedex", Document.class);
            if (pokedexDoc != null) data.setPokedexJson(pokedexDoc.toJson());

            data.setCobblemonPlayerData(cobblemonData.getString("genericData"));
        }

        return data;
    }
}
