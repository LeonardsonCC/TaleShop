package br.com.leonardson.taleshop.wandering;

import java.util.ArrayList;
import java.util.List;
import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;

/** Configuration loaded from WanderingTraderConfig.json. */
public final class WanderingTraderConfig {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    @SerializedName("Enabled") public boolean enabled;
    @SerializedName("NpcRole") public String npcRole = "Klops_Merchant";
    @SerializedName("DisplayName") public String displayName = "Traveling Merchant";
    @SerializedName("AttemptIntervalSeconds") public int attemptIntervalSeconds = 600;
    @SerializedName("SpawnChance") public double spawnChance = 0.25;
    @SerializedName("MinSpawnDistance") public int minSpawnDistance = 20;
    @SerializedName("MaxSpawnDistance") public int maxSpawnDistance = 40;
    @SerializedName("LifetimeSeconds") public int lifetimeSeconds = 600;
    @SerializedName("DefaultMaxUses") public int defaultMaxUses = 5;
    @SerializedName("Trades") public List<TradeData> trades = new ArrayList<>();

    public List<WanderingTradeDefinition> validatedTrades() {
        List<WanderingTradeDefinition> result = new ArrayList<>();
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (TradeData data : trades) {
            if (data == null) continue;
            if (data.id != null && !data.id.isBlank() && !ids.add(data.id))
                throw new IllegalArgumentException("Duplicate trade id: " + data.id);
            try {
                int uses = data.maxUses == null ? defaultMaxUses : data.maxUses;
                WanderingTradeDefinition trade = new WanderingTradeDefinition(data.id, data.inputItemId,
                        data.inputQuantity, data.outputItemId, data.outputQuantity, uses);
                result.add(trade);
            } catch (IllegalArgumentException invalid) {
                LOGGER.atWarning().log("Ignoring invalid wandering trader offer %s: %s", data.id, invalid.getMessage());
            }
        }
        return List.copyOf(result);
    }

    public void validate() {
        if (npcRole == null || npcRole.isBlank() || displayName == null || displayName.isBlank())
            throw new IllegalArgumentException("NpcRole and DisplayName must be non-empty");
        if (attemptIntervalSeconds <= 0 || lifetimeSeconds <= 0 || defaultMaxUses <= 0)
            throw new IllegalArgumentException("Intervals, lifetime, and default uses must be positive");
        if (!Double.isFinite(spawnChance) || spawnChance < 0 || spawnChance > 1)
            throw new IllegalArgumentException("SpawnChance must be between 0 and 1");
        if (minSpawnDistance <= 0 || maxSpawnDistance < minSpawnDistance)
            throw new IllegalArgumentException("Spawn distances are invalid");
        if (trades == null) trades = new ArrayList<>();
        validatedTrades();
    }

    public static final class TradeData {
        @SerializedName("Id") public String id;
        @SerializedName("InputItemId") public String inputItemId;
        @SerializedName("InputQuantity") public int inputQuantity;
        @SerializedName("OutputItemId") public String outputItemId;
        @SerializedName("OutputQuantity") public int outputQuantity;
        @SerializedName("MaxUses") public Integer maxUses;
    }
}
