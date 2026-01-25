package br.com.leonardson.shop.ui;

import br.com.leonardson.Main;
import br.com.leonardson.shop.ShopTrade;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;

public class ShopBuyerPage extends InteractiveCustomUIPage<ShopBuyerPage.ShopBuyerEventData> {
    private final long shopId;
    private final String shopName;

    public ShopBuyerPage(@Nonnull PlayerRef playerRef, long shopId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ShopBuyerEventData.CODEC);
        this.shopId = shopId;
        this.shopName = shopName;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Pages/BarterPage.ui");
        commandBuilder.set("#ShopTitle.Text", shopName);

        commandBuilder.clear("#TradeGrid");
        List<ShopTrade> trades = Main.getInstance().getShopNpcRegistry().getSavedTrades(shopId);

        for (int i = 0; i < trades.size(); i++) {
            ShopTrade trade = trades.get(i);
            String selector = "#TradeGrid[" + i + "]";
            commandBuilder.append("#TradeGrid", "Pages/BarterTradeRow.ui");

            commandBuilder.set(selector + " #OutputSlot.ItemId", trade.outputItemId());
            commandBuilder.set(selector + " #OutputQuantity.Text", trade.outputQty() > 1 ? String.valueOf(trade.outputQty()) : "");
            commandBuilder.set(selector + " #InputSlot.ItemId", trade.inputItemId());
            commandBuilder.set(selector + " #InputQuantity.Text", trade.inputQty() > 1 ? String.valueOf(trade.inputQty()) : "");

            commandBuilder.set(selector + " #Stock.Visible", false);
            commandBuilder.set(selector + " #OutOfStockOverlay.Visible", false);
            commandBuilder.set(selector + " #TradeButton.Disabled", false);

            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #TradeButton",
                EventData.of("TradeIndex", String.valueOf(i)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ShopBuyerEventData data) {
        if (data.tradeIndex < 0) {
            return;
        }

        List<ShopTrade> trades = Main.getInstance().getShopNpcRegistry().getSavedTrades(shopId);
        if (data.tradeIndex >= trades.size()) {
            return;
        }

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        ShopTrade trade = trades.get(data.tradeIndex);
        if (!ItemModule.exists(trade.inputItemId()) || !ItemModule.exists(trade.outputItemId())) {
            playerComponent.sendMessage(Message.raw("This trade is invalid."));
            return;
        }

        Inventory inventory = playerComponent.getInventory();
        CombinedItemContainer container = inventory.getCombinedHotbarFirst();
        int playerHas = countItemsInContainer(container, trade.inputItemId());
        if (playerHas < trade.inputQty()) {
            playerComponent.sendMessage(Message.raw("You don't have enough items."));
            return;
        }

        removeItemsFromContainer(container, trade.inputItemId(), trade.inputQty());

        ItemStack outputStack = new ItemStack(trade.outputItemId(), trade.outputQty());
        ItemStackTransaction transaction = container.addItemStack(outputStack);
        ItemStack remainder = transaction.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            int addedQty = outputStack.getQuantity() - remainder.getQuantity();
            if (addedQty > 0) {
                playerComponent.notifyPickupItem(ref, outputStack.withQuantity(addedQty), null, store);
            }
            ItemUtils.dropItem(ref, remainder, store);
        } else {
            playerComponent.notifyPickupItem(ref, outputStack, null, store);
        }
    }

    private int countItemsInContainer(@Nonnull ItemContainer container, @Nonnull String itemId) {
        int count = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack itemStack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(itemStack) && itemId.equals(itemStack.getItemId())) {
                count += itemStack.getQuantity();
            }
        }
        return count;
    }

    private void removeItemsFromContainer(@Nonnull ItemContainer container, @Nonnull String itemId, int quantity) {
        int remaining = quantity;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (remaining <= 0) {
                return;
            }
            ItemStack itemStack = container.getItemStack(slot);
            if (ItemStack.isEmpty(itemStack) || !itemId.equals(itemStack.getItemId())) {
                continue;
            }
            int toRemove = Math.min(remaining, itemStack.getQuantity());
            container.removeItemStackFromSlot(slot, itemStack, toRemove);
            remaining -= toRemove;
        }
    }

    public static class ShopBuyerEventData {
        public static final com.hypixel.hytale.codec.builder.BuilderCodec<ShopBuyerEventData> CODEC = com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                ShopBuyerEventData.class, ShopBuyerEventData::new
        )
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("TradeIndex", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.tradeIndex = Integer.parseInt(s), entry -> String.valueOf(entry.tradeIndex))
            .add()
            .build();

        private int tradeIndex = -1;

        public ShopBuyerEventData() {
        }
    }
}
