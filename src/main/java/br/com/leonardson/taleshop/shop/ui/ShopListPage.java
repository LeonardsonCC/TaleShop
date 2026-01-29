package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.TraderNpc;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

public class ShopListPage extends InteractiveCustomUIPage<ShopListPage.ShopListEventData> {
    private static final String PAGE_PATH = "Pages/ShopListPage.ui";
    private static final String SHOP_ROWS_SELECTOR = "#ShopRows";
    private static final String ROW_TEMPLATE_PATH = "Pages/ShopRow.ui";
    
    private final String ownerId;

    public ShopListPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId) {
        super(playerRef, CustomPageLifetime.CanDismiss, ShopListEventData.CODEC);
        this.ownerId = ownerId;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(PAGE_PATH);
        
        // Bind create button
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CreateShopButton",
            EventData.of("Action", "Create"),
            false
        );

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return;
        }

        List<Shop> shops = loadShops(registry);
        
        // Clear the list first
        commandBuilder.clear(SHOP_ROWS_SELECTOR);
        
        // Dynamically append shop rows
        for (int i = 0; i < shops.size(); i++) {
            Shop shop = shops.get(i);
            
            // Append the template
            commandBuilder.append(SHOP_ROWS_SELECTOR, ROW_TEMPLATE_PATH);
            
            // Build the selector for this row using array notation
            String rowSelector = SHOP_ROWS_SELECTOR + "[" + i + "]";
            
            boolean hasNpc = shop.traderUuid() != null && !shop.traderUuid().isBlank();
            int tradeCount = shop.trades().size();
            
            // Set data for this row
            commandBuilder.set(rowSelector + " #ShopName.Text", shop.name());
            commandBuilder.set(rowSelector + " #ShopTrades.Text", tradeCount + "/" + ShopRegistry.MAX_TRADES);
            commandBuilder.set(rowSelector + " #ShopNpcStatus.Text", hasNpc ? "NPC: ●" : "NPC: ○");
            commandBuilder.set(rowSelector + " #ShopNpcButton.Text", hasNpc ? "Despawn" : "Spawn NPC");
            
            // Bind events for this row's buttons
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowSelector + " #ShopEditButton",
                new EventData().append("Action", "Edit").append("ShopIndex", String.valueOf(i)),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowSelector + " #ShopDeleteButton",
                new EventData().append("Action", "Delete").append("ShopIndex", String.valueOf(i)),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                rowSelector + " #ShopNpcButton",
                new EventData().append("Action", "Npc").append("ShopIndex", String.valueOf(i)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ShopListEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if ("Create".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopEditorPage(playerRef, ownerId, null));
            return;
        }

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return;
        }

        List<Shop> shops = loadShops(registry);
        
        // Parse shop index from event data
        int shopIndex = -1;
        if (data.shopIndex != null) {
            try {
                shopIndex = Integer.parseInt(data.shopIndex);
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        
        if (shopIndex < 0 || shopIndex >= shops.size()) {
            return;
        }
        
        Shop shop = shops.get(shopIndex);

        if ("Edit".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopEditorPage(playerRef, ownerId, shop.name()));
            return;
        }

        if ("Delete".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopDeleteConfirmationPage(playerRef, ownerId, shop.name()));
            return;
        }

        if ("Npc".equals(data.action)) {
            handleNpcToggle(ref, store, player, playerRef, shop);
            return;
        }
    }

    private void handleNpcToggle(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull Shop shop
    ) {
        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            player.sendMessage(Message.raw("Shop registry not available."));
            return;
        }

        boolean hasNpc = shop.traderUuid() != null && !shop.traderUuid().isBlank();
        
        if (hasNpc) {
            // Despawn NPC
            boolean despawned = TraderNpc.despawnByUuid(store, shop.traderUuid());
            if (despawned) {
                registry.clearTraderUuid(ownerId, shop.name());
                player.sendMessage(Message.raw("NPC despawned for " + shop.name()));
            } else {
                player.sendMessage(Message.raw("Failed to despawn NPC. It may have already been removed."));
            }
        } else {
            // Spawn NPC
            TraderNpc traderNpc = new TraderNpc(shop.name());
            try {
                traderNpc.spawn(store, ref);
                String traderUuid = traderNpc.getUuid(store);
                if (traderUuid != null && !traderUuid.isBlank()) {
                    registry.setTraderUuid(ownerId, shop.name(), traderUuid);
                    player.sendMessage(Message.raw("NPC spawned for " + shop.name()));
                } else {
                    player.sendMessage(Message.raw("NPC spawned but UUID not available."));
                }
            } catch (IllegalStateException ex) {
                player.sendMessage(Message.raw("Failed to spawn NPC: " + ex.getMessage()));
                return;
            }
        }
        
        // Refresh the page to update button states
        player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
    }

    private List<Shop> loadShops(@Nonnull ShopRegistry registry) {
        try {
            return new ArrayList<>(registry.listShops(ownerId));
        } catch (Exception ex) {
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

    public static class ShopListEventData {
        public static final BuilderCodec<ShopListEventData> CODEC = BuilderCodec.builder(ShopListEventData.class, ShopListEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action).add()
            .append(new KeyedCodec<>("ShopIndex", Codec.STRING), (data, s) -> data.shopIndex = s, data -> data.shopIndex).add()
            .build();
        private String action;
        private String shopIndex;

        public ShopListEventData() {
        }
    }
}
