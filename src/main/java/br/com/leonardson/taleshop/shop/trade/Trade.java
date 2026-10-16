package br.com.leonardson.taleshop.shop.trade;

/** Null stack data denotes an existing ID-only offer (also used by commands). */
public record Trade(int id, String inputItemId, int inputQuantity, String outputItemId, int outputQuantity,
                    String inputStackData, String outputStackData) {
    public Trade(int id, String inputItemId, int inputQuantity, String outputItemId, int outputQuantity) {
        this(id, inputItemId, inputQuantity, outputItemId, outputQuantity, null, null);
    }
}
