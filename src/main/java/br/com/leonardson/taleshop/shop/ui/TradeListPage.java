package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.trade.Trade;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TradeListPage extends InteractiveCustomUIPage<TradeListPage.TradeListEventData> {
    private static final String PAGE_PATH = "Pages/TradeListPage.ui";
    private static final String TRADE_ROWS_SELECTOR = "#TradeRows";
    private static final String ROW_TEMPLATE_PATH = "Pages/TradeRow.ui";
    
    private final String ownerId;
    private final String shopName;

    public TradeListPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, TradeListEventData.CODEC);
        this.ownerId = ownerId;
        this.shopName = shopName;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(PAGE_PATH);
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#AddTradeButton",
            EventData.of("Action", "Add"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#BackButton",
            EventData.of("Action", "Back"),
            false
        );

        commandBuilder.set("#TitleLabel.Text", shopName + " Trades");

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return;
        }

        List<Trade> trades = loadTrades(registry);
        
        // Clear the list first
        commandBuilder.clear(TRADE_ROWS_SELECTOR);
        
        // Dynamically append trade rows
        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            
            // Append the template
            commandBuilder.append(TRADE_ROWS_SELECTOR, ROW_TEMPLATE_PATH);
            
            // Build the selector for this row using array notation
            String rowSelector = TRADE_ROWS_SELECTOR + "[" + i + "]";
            
            // Set data for this row
            commandBuilder.set(rowSelector + " #TradeInputQty.Text", "x" + trade.inputQuantity());
            commandBuilder.set(rowSelector + " #TradeOutputQty.Text", "x" + trade.outputQuantity());
            commandBuilder.set(rowSelector + " #TradeInputSlot.Slots", buildTradeSlot(trade.inputItemId(), trade.inputQuantity()));
            commandBuilder.set(rowSelector + " #TradeOutputSlot.Slots", buildTradeSlot(trade.outputItemId(), trade.outputQuantity()));
            
            // Bind events for this row's buttons
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowSelector + " #TradeEditButton",
                new EventData().append("Action", "Edit").append("TradeIndex", String.valueOf(i)),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowSelector + " #TradeDeleteButton",
                new EventData().append("Action", "Delete").append("TradeIndex", String.valueOf(i)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull TradeListEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if ("Add".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new TradeEditorPage(playerRef, ownerId, shopName, null));
            return;
        }

        if ("Back".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new TraderMenuPage(playerRef, ownerId, shopName));
            return;
        }

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return;
        }

        List<Trade> trades = loadTrades(registry);
        
        // Parse trade index from event data
        int tradeIndex = -1;
        if (data.tradeIndex != null) {
            try {
                tradeIndex = Integer.parseInt(data.tradeIndex);
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        
        if (tradeIndex < 0 || tradeIndex >= trades.size()) {
            return;
        }
        
        Trade trade = trades.get(tradeIndex);

        if ("Edit".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new TradeEditorPage(playerRef, ownerId, shopName, trade.id()));
            return;
        }

        if ("Delete".equals(data.action)) {
            try {
                registry.removeTrade(ownerId, shopName, trade.id());
            } catch (IllegalArgumentException ignored) {
                return;
            }
            player.getPageManager().openCustomPage(ref, store, new TradeListPage(playerRef, ownerId, shopName));
        }
    }

    private List<Trade> loadTrades(@Nonnull ShopRegistry registry) {
        try {
            Shop shop = registry.getShop(ownerId, shopName);
            return new ArrayList<>(shop.trades());
        } catch (IllegalArgumentException ex) {
            return new ArrayList<>();
        }
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    private ItemGridSlot[] buildTradeSlot(@Nonnull String itemId, int quantity) {
        ItemStack itemStack = createItemStack(itemId, quantity);
        ItemGridSlot slot = itemStack == null ? new ItemGridSlot() : new ItemGridSlot(itemStack);
        slot.setActivatable(false);
        return new ItemGridSlot[]{slot};
    }

    @Nullable
    private ItemStack createItemStack(@Nonnull String itemId, int quantity) {
        try {
            return new ItemStack(itemId, quantity);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    public static class TradeListEventData {
        public static final BuilderCodec<TradeListEventData> CODEC = BuilderCodec.builder(TradeListEventData.class, TradeListEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action).add()
            .append(new KeyedCodec<>("TradeIndex", Codec.STRING), (data, s) -> data.tradeIndex = s, data -> data.tradeIndex).add()
            .build();
        private String action;
        private String tradeIndex;

        public TradeListEventData() {
        }
    }
}
