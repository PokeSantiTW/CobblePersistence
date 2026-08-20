package diosesmon.cobblepersistence.data;

import diosesmon.cobblepersistence.io.config.ConfigManager;

import java.util.Map;

/**
 * Plantilla de datos que hay que guardar del jugador en MongoDB
 */
public class PlayerData {
    // Identificadores
    private String uuid;
    private String username;
    private long firstJoin;
    private long lastUpdated;
    private int version;
    private String actualServer;
    private boolean isOnline;

    // Datos de Minecraft
    private String playerDataSnbt;
    private String advancementsJson;
    private String statsJson;
    // Posición por servidor
    private Map<String, ServerPosition> serverPosition;

    // Datos de Cobblemon
    private String partyJson;
    private String pcJson;
    private String pokedexJson;
    private String cobblemonPlayerData;

    public PlayerData() {
    }

    public PlayerData(String uuid, String username, long firstJoin, long lastUpdated, int version,
                      String actualServer, boolean isOnline, String playerDataSnbt,
                      String advancementsJson, String statsJson, Map<String, ServerPosition> serverPosition,
                      String partyJson, String pcJson, String pokedexJson, String cobblemonPlayerData) {
        this.uuid = uuid;
        this.username = username;
        this.firstJoin = firstJoin;
        this.lastUpdated = lastUpdated;
        this.version = version;
        this.actualServer = actualServer;
        this.isOnline = isOnline;
        this.playerDataSnbt = playerDataSnbt;
        this.advancementsJson = advancementsJson;
        this.statsJson = statsJson;
        this.serverPosition = serverPosition;
        this.partyJson = partyJson;
        this.pcJson = pcJson;
        this.pokedexJson = pokedexJson;
        this.cobblemonPlayerData = cobblemonPlayerData;
    }

    // Solo la cabecera principal
    public PlayerData(String uuid, String username, long firstJoin, long lastUpdated, int version,
                      String actualServer, boolean isOnline, Map<String, ServerPosition> serverPosition) {
        this.uuid = uuid;
        this.username = username;
        this.firstJoin = firstJoin;
        this.lastUpdated = lastUpdated;
        this.version = version;
        this.actualServer = actualServer;
        this.isOnline = isOnline;
        this.serverPosition = serverPosition;
    }

    public String getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public long getFirstJoin() {
        return firstJoin;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public int getVersion() {
        return version;
    }

    public String getPlayerDataSnbt() {
        return playerDataSnbt;
    }

    public String getAdvancementsJson() {
        return advancementsJson;
    }

    public String getStatsJson() {
        return statsJson;
    }

    public Map<String, ServerPosition> getServerPosition() {
        return serverPosition;
    }

    public ServerPosition getActualServerPosition() {
        return serverPosition.get(ConfigManager.getConfig().getIdentifier());
    }

    public String getActualServer() {
        return actualServer;
    }

    public String getCobblemonPlayerData() {
        return cobblemonPlayerData;
    }

    public String getPartyJson() {
        return partyJson;
    }

    public String getPcJson() {
        return pcJson;
    }

    public String getPokedexJson() {
        return pokedexJson;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setFirstJoin(long firstJoin) {
        this.firstJoin = firstJoin;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public void setPlayerDataSnbt(String playerDataSnbt) {
        this.playerDataSnbt = playerDataSnbt;
    }

    public void setAdvancementsJson(String advancementsJson) {
        this.advancementsJson = advancementsJson;
    }

    public void setStatsJson(String statsJson) {
        this.statsJson = statsJson;
    }

    public void setServerPosition(Map<String, ServerPosition> serverPosition) {
        this.serverPosition = serverPosition;
    }

    public void setActualServer(String actualServer) {
        this.actualServer = actualServer;
    }

    public void setOnline(boolean online) {
        isOnline = online;
    }

    public void setCobblemonPlayerData(String cobblemonPlayerData) {
        this.cobblemonPlayerData = cobblemonPlayerData;
    }

    public void setPartyJson(String partyJson) {
        this.partyJson = partyJson;
    }

    public void setPcJson(String pcJson) {
        this.pcJson = pcJson;
    }

    public void setPokedexJson(String pokedexJson) {
        this.pokedexJson = pokedexJson;
    }

    public record ServerPosition(String dimension, double x, double y, double z, float yaw, float pitch) {}
}
