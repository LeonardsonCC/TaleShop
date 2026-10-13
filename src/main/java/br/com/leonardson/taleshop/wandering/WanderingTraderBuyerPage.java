package br.com.leonardson.taleshop.wandering;

import br.com.leonardson.taleshop.shop.trade.Trade;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
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
import java.util.List;
import javax.annotation.Nonnull;

/** Buyer UI for a single wandering-trader appearance. */
public final class WanderingTraderBuyerPage extends InteractiveCustomUIPage<WanderingTraderBuyerPage.EventData> {
    private final WanderingTraderService service;
    private final String encounterId;

    public WanderingTraderBuyerPage(PlayerRef player, WanderingTraderService service, String encounterId) {
        super(player, CustomPageLifetime.CanDismissOrCloseThroughInteraction, EventData.CODEC);
        this.service = service;
        this.encounterId = encounterId;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commands,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        commands.append("Pages/BarterPage.ui");
        commands.set("#ShopTitle.Text", service.displayName());
        commands.clear("#TradeGrid");
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inventory = player == null ? null : player.getInventory().getCombinedHotbarFirst();
        List<WanderingTraderEncounter.Offer> offers = service.offers(encounterId);
        for (int i = 0; i < offers.size(); i++) {
            var offer = offers.get(i);
            var trade = offer.definition();
            String selector = "#TradeGrid[" + i + "]";
            commands.append("#TradeGrid", "Pages/BarterTradeRow.ui");
            commands.set(selector + " #OutputSlot.ItemId", trade.outputItemId());
            commands.set(selector + " #OutputQuantity.Text", trade.outputQuantity() > 1 ? String.valueOf(trade.outputQuantity()) : "");
            commands.set(selector + " #InputSlot.ItemId", trade.inputItemId());
            commands.set(selector + " #InputQuantity.Text", trade.inputQuantity() > 1 ? String.valueOf(trade.inputQuantity()) : "");
            int have = count(inventory, trade.inputItemId());
            boolean canPay = have >= trade.inputQuantity();
            commands.set(selector + " #InputSlotBorder.Background", canPay ? "#2a5a3a" : "#5a2a2a");
            commands.set(selector + " #HaveNeedLabel.Text", "Have: " + have);
            commands.set(selector + " #HaveNeedLabel.Style.TextColor", canPay ? "#3d913f" : "#962f2f");
            commands.set(selector + " #Stock.Visible", true);
            commands.set(selector + " #Stock.Text", offer.remaining() + " / " + offer.maxUses());
            commands.set(selector + " #OutOfStockOverlay.Visible", offer.remaining() == 0);
            commands.set(selector + " #TradeButton.Disabled", !canPay || offer.remaining() == 0);
            events.addEventBinding(CustomUIEventBindingType.Activating, selector + " #TradeButton",
                    com.hypixel.hytale.server.core.ui.builder.EventData.of("TradeId", trade.id()), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null || data.tradeId == null || data.tradeId.isBlank()) return;
        var encounter = service.encounter(encounterId);
        var offer = encounter == null ? null : encounter.offer(data.tradeId);
        if (offer == null || !service.isActive(encounterId)) {
            playerRef.sendMessage(Message.raw("This trader has left."));
            return;
        }
        if (!service.canInteract(encounterId, ref, store)) {
            playerRef.sendMessage(Message.raw("Move closer to the trader to trade.")); return;
        }
        var trade = offer.definition();
        if (!ItemModule.exists(trade.inputItemId()) || !ItemModule.exists(trade.outputItemId())) {
            playerRef.sendMessage(Message.raw("This trade is unavailable.")); return;
        }
        CombinedItemContainer inventory = player.getInventory().getCombinedHotbarFirst();
        ItemStack payment = new ItemStack(trade.inputItemId(), trade.inputQuantity());
        ItemStack reward = new ItemStack(trade.outputItemId(), trade.outputQuantity());
        if (offer.remaining() <= 0 || !inventory.canRemoveItemStack(payment)) {
            playerRef.sendMessage(Message.raw(offer.remaining() <= 0 ? "This offer is sold out." : "You don't have enough items."));
            refresh(ref, store, player, playerRef); return;
        }
        ItemStackTransaction removed = inventory.removeItemStack(payment);
        if (!removed.succeeded()) { playerRef.sendMessage(Message.raw("You don't have enough items.")); return; }
        if (!inventory.canAddItemStack(reward)) {
            inventory.addItemStack(payment);
            playerRef.sendMessage(Message.raw("Your inventory has no room for this trade."));
            refresh(ref, store, player, playerRef); return;
        }
        if (!service.consumeUse(encounterId, data.tradeId)) {
            inventory.addItemStack(payment);
            playerRef.sendMessage(Message.raw("This offer is no longer available.")); return;
        }
        ItemStackTransaction added = inventory.addItemStack(reward);
        if (!added.succeeded() || (added.getRemainder() != null && !added.getRemainder().isEmpty())) {
            service.refundUse(encounterId, data.tradeId);
            inventory.addItemStack(payment);
            playerRef.sendMessage(Message.raw("Trade failed; your payment was returned.")); return;
        }
        Player.notifyPickupItem(ref, reward, null, store);
        refresh(ref, store, player, playerRef);
    }

    private void refresh(Ref<EntityStore> ref, Store<EntityStore> store, Player player, PlayerRef playerRef) {
        player.getPageManager().openCustomPage(ref, store, new WanderingTraderBuyerPage(playerRef, service, encounterId));
    }

    private static int count(ItemContainer container, String itemId) {
        if (container == null) return 0;
        int total = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(stack) && itemId.equals(stack.getItemId())) total += stack.getQuantity();
        }
        return total;
    }

    public static final class EventData {
        public static final com.hypixel.hytale.codec.builder.BuilderCodec<EventData> CODEC =
                com.hypixel.hytale.codec.builder.BuilderCodec.builder(EventData.class, EventData::new)
                        .append(new com.hypixel.hytale.codec.KeyedCodec<>("TradeId", com.hypixel.hytale.codec.Codec.STRING),
                                (data, value) -> data.tradeId = value, data -> data.tradeId).add().build();
        private String tradeId;
        public EventData() {}
    }
}
