package br.com.leonardson.taleshop.shop.trade;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.json.JsonMode;
import org.bson.json.JsonWriterSettings;

/** SDK stack codec; kept separate from the storage DTO and ID-only command API. */
public final class TradeItems {
    private static final JsonWriterSettings BSON_JSON = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build();

    private TradeItems() {}

    public static String encode(ItemStack stack) {
        return ItemStack.CODEC.encode(stack.withQuantity(1), new ExtraInfo()).toJson(BSON_JSON);
    }

    public static ItemStack decode(String data, String itemId, int quantity) {
        if (data == null) return new ItemStack(itemId, quantity);
        ItemStack stack = ItemStack.CODEC.decode(BsonDocument.parse(data), new ExtraInfo());
        if (ItemStack.isEmpty(stack) || !itemId.equals(stack.getItemId())) {
            throw new IllegalArgumentException("Invalid trade item data.");
        }
        return stack.withQuantity(quantity);
    }

    public static ItemStack inputStack(Trade trade) {
        return decode(trade.inputStackData(), trade.inputItemId(), trade.inputQuantity());
    }

    public static ItemStack outputStack(Trade trade) {
        return decode(trade.outputStackData(), trade.outputItemId(), trade.outputQuantity());
    }

    public static boolean matchesInput(Trade trade, ItemStack stack) {
        return matches(stack, trade.inputItemId(), trade.inputStackData());
    }

    public static boolean matchesOutput(Trade trade, ItemStack stack) {
        return matches(stack, trade.outputItemId(), trade.outputStackData());
    }

    private static boolean matches(ItemStack stack, String id, String data) {
        return !ItemStack.isEmpty(stack) && id.equals(stack.getItemId())
            && (data == null || decode(data, id, 1).isStackableWith(stack));
    }
}
