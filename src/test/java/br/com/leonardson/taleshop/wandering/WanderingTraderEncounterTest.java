package br.com.leonardson.taleshop.wandering;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WanderingTraderEncounterTest {
    private WanderingTradeDefinition offer(String id, int uses) {
        return new WanderingTradeDefinition(id, "Coin", 2, "Potion", 3, uses);
    }

    @Test
    void limitsPurchasesPerOfferAndRestoresRemainingStock() {
        var encounter = new WanderingTraderEncounter("npc", Long.MAX_VALUE, List.of(offer("one", 5), offer("two", 2)));
        for (int i = 0; i < 5; i++) assertTrue(encounter.consumeUse("one"));
        assertFalse(encounter.consumeUse("one"));
        assertEquals(2, encounter.offer("two").remaining());

        var restored = WanderingTraderEncounter.restore(encounter.id(), "npc", Long.MAX_VALUE,
                List.of(offer("one", 5), offer("two", 2)), Map.of("one", 1, "two", 0));
        assertEquals(1, restored.offer("one").remaining());
        assertEquals(0, restored.offer("two").remaining());
    }

    @Test
    void rejectsInvalidOfferAndDuplicateIds() {
        assertThrows(IllegalArgumentException.class, () -> offer("", 5));
        assertThrows(IllegalArgumentException.class, () -> offer("one", 0));
        var config = new WanderingTraderConfig();
        var first = row("same");
        var second = row("same");
        config.trades = List.of(first, second);
        assertThrows(IllegalArgumentException.class, config::validate);
    }

    private static WanderingTraderConfig.TradeData row(String id) {
        var data = new WanderingTraderConfig.TradeData();
        data.id = id; data.inputItemId = "Coin"; data.inputQuantity = 1;
        data.outputItemId = "Potion"; data.outputQuantity = 1;
        return data;
    }
}
