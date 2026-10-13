package br.com.leonardson.taleshop.wandering;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class WanderingTraderConfigTest {
    @Test
    void missingOptionsUseDefaultsAndOutputUsesDocumentedNames() {
        WanderingTraderConfig config = new Gson().fromJson("{\"Trades\":[]}", WanderingTraderConfig.class);
        config.validate();
        assertFalse(config.enabled);
        assertEquals(5, config.defaultMaxUses);
        assertEquals(600, config.attemptIntervalSeconds);
        String json = new Gson().toJson(config);
        assertTrue(json.contains("\"Enabled\""));
        assertTrue(json.contains("\"DefaultMaxUses\""));
    }

    @Test
    void validatesChanceDistancesAndPerOfferOverrides() {
        WanderingTraderConfig config = new WanderingTraderConfig();
        config.trades.add(row("first", null));
        config.trades.add(row("second", 3));
        config.validate();
        assertEquals(5, config.validatedTrades().get(0).maxUses());
        assertEquals(3, config.validatedTrades().get(1).maxUses());
        config.spawnChance = Double.NaN;
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    private static WanderingTraderConfig.TradeData row(String id, Integer maxUses) {
        var row = new WanderingTraderConfig.TradeData();
        row.id = id; row.inputItemId = "Coin"; row.inputQuantity = 2;
        row.outputItemId = "Potion"; row.outputQuantity = 1; row.maxUses = maxUses;
        return row;
    }
}
