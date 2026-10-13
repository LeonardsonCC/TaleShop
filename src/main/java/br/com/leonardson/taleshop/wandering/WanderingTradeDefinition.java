package br.com.leonardson.taleshop.wandering;

import br.com.leonardson.taleshop.shop.trade.Trade;

/** Immutable offer configured for a wandering trader. */
public record WanderingTradeDefinition(String id, String inputItemId, int inputQuantity,
        String outputItemId, int outputQuantity, int maxUses) {
    public WanderingTradeDefinition {
        if (id == null || id.isBlank() || inputItemId == null || inputItemId.isBlank()
                || outputItemId == null || outputItemId.isBlank()) {
            throw new IllegalArgumentException("Trade id and item identifiers are required");
        }
        if (inputQuantity <= 0 || outputQuantity <= 0 || maxUses <= 0) {
            throw new IllegalArgumentException("Trade quantities and maxUses must be positive");
        }
    }

    public Trade asTrade(int index) {
        return new Trade(index, inputItemId, inputQuantity, outputItemId, outputQuantity);
    }
}
