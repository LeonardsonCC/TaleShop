package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.shop.trade.Trade;
import br.com.leonardson.taleshop.shop.trade.TradeItems;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Native display messages are safe UI text; raw stack metadata is never a visual slot payload. */
public final class ShopTradePreview {
    private ShopTradePreview() {}

    public static ItemStack output(Trade trade, List<ItemContainer> stock, boolean admin) {
        if (admin || trade.outputStackData() != null) return TradeItems.outputStack(trade);
        ItemStack next = firstMatch(stock, stack -> TradeItems.matchesOutput(trade, stack));
        return next == null ? TradeItems.outputStack(trade) : next;
    }

    public static ItemStack input(Trade trade, ItemContainer buyer) {
        if (trade.inputStackData() != null || buyer == null) return TradeItems.inputStack(trade);
        ItemStack next = firstMatch(List.of(buyer), stack -> TradeItems.matchesInput(trade, stack));
        return next == null ? TradeItems.inputStack(trade) : next;
    }

    private static ItemStack firstMatch(List<ItemContainer> containers, Predicate<ItemStack> matches) {
        // Same order as TradeExchange.take: show the next real variant for legacy ID-only offers.
        for (ItemContainer container : containers) {
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (matches.test(stack)) return stack;
            }
        }
        return null;
    }

    public static void render(UICommandBuilder commands, String selector, Trade trade,
                              ItemContainer buyer, List<ItemContainer> stock, boolean admin) {
        ItemStack output = output(trade, stock, admin);
        ItemStack input = input(trade, buyer);
        // Keep the ID-only icon path that avoids the historical CustomUI quality/metadata crash.
        commands.set(selector + " #OutputSlot.ItemId", trade.outputItemId());
        commands.set(selector + " #InputSlot.ItemId", trade.inputItemId());
        commands.set(selector + " #TradeButton.TooltipTextSpans", tooltip(trade, input, output, admin));
    }

    public static Message tooltip(Trade trade, ItemStack input, ItemStack output, boolean admin) {
        Message tooltip = Message.empty().insert(Message.raw("Receive\n").bold(true))
            .insert(details(output, trade.outputQuantity()));
        if (!admin && trade.outputStackData() == null) {
            tooltip.insert(Message.raw("\nStock variants may differ; showing the next item.").color("#aab4c0"));
        }
        tooltip.insert(Message.raw("\n\nPay\n").bold(true)).insert(details(input, trade.inputQuantity()));
        if (trade.inputStackData() == null) {
            tooltip.insert(Message.raw("\nAny variant of this item is accepted.").color("#aab4c0"));
        }
        return tooltip;
    }

    private static Message details(ItemStack stack, int quantity) {
        Message details = Message.empty().insert(stack.getDisplayName()).insert(Message.raw(" × " + quantity));
        Message description = stack.getDisplayDescription();
        if (description != null) details.insert("\n").insert(description);
        if (stack.getMaxDurability() > 0) {
            details.insert(Message.raw(String.format(Locale.ROOT, "\nDurability: %.1f / %.1f",
                stack.getDurability(), stack.getMaxDurability())));
        }
        if (ItemQuality.getAssetStore() != null) {
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(stack.getQualityIndex());
            if (quality != null && quality.isVisibleQualityLabel() && quality.getLocalizationKey() != null) {
                details.insert("\n").insert(Message.translation(quality.getLocalizationKey()));
            }
        }
        return details;
    }
}
