package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.player.PlayerIdentity;
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
    private static final int MAX_SHOPS_DISPLAYED = 10;
    
    private final String ownerId;
    private List<Shop> currentShops = new ArrayList<>();

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
        
        // Bind all shop row buttons
        for (int i = 1; i <= MAX_SHOPS_DISPLAYED; i++) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShopEditButton" + i,
                EventData.of("Action", "Edit:" + i),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShopDeleteButton" + i,
                EventData.of("Action", "Delete:" + i),
                false
            );
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ShopNpcButton" + i,
                EventData.of("Action", "Npc:" + i),
                false
            );
        }

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return;
        }

        List<Shop> shops = loadShops(registry);
        currentShops = shops;
        
        for (int i = 0; i < MAX_SHOPS_DISPLAYED; i++) {
            int row = i + 1;
            String rowId = "#ShopRow" + row;
            
            if (i < shops.size()) {
                Shop shop = shops.get(i);
                boolean hasNpc = shop.traderUuid() != null && !shop.traderUuid().isBlank();
                int tradeCount = shop.trades().size();
                
                commandBuilder.set(rowId + ".Visible", true);
                commandBuilder.set("#ShopName" + row + ".Text", shop.name());
                commandBuilder.set("#ShopTrades" + row + ".Text", tradeCount + "/" + ShopRegistry.MAX_TRADES);
                commandBuilder.set("#ShopNpcStatus" + row + ".Text", hasNpc ? "NPC: ●" : "NPC: ○");
                commandBuilder.set("#ShopNpcButton" + row + ".Text", hasNpc ? "Despawn" : "Spawn NPC");
            } else {
                commandBuilder.set(rowId + ".Visible", false);
            }
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

        if (data.action.startsWith("Edit:")) {
            int shopIndex = parseRowIndex(data.action);
            if (shopIndex < 0 || shopIndex >= currentShops.size()) {
                return;
            }
            Shop shop = currentShops.get(shopIndex);
            player.getPageManager().openCustomPage(ref, store, new ShopEditorPage(playerRef, ownerId, shop.name()));
            return;
        }

        if (data.action.startsWith("Delete:")) {
            int shopIndex = parseRowIndex(data.action);
            if (shopIndex < 0 || shopIndex >= currentShops.size()) {
                return;
            }
            Shop shop = currentShops.get(shopIndex);
            player.getPageManager().openCustomPage(ref, store, new ShopDeleteConfirmationPage(playerRef, ownerId, shop.name()));
            return;
        }

        if (data.action.startsWith("Npc:")) {
            int shopIndex = parseRowIndex(data.action);
            if (shopIndex < 0 || shopIndex >= currentShops.size()) {
                return;
            }
            Shop shop = currentShops.get(shopIndex);
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

    private int parseRowIndex(@Nonnull String action) {
        String[] parts = action.split(":");
        if (parts.length < 2) {
            return -1;
        }
        try {
            int row = Integer.parseInt(parts[1].trim());
            return row - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static class ShopListEventData {
        public static final BuilderCodec<ShopListEventData> CODEC = BuilderCodec.builder(ShopListEventData.class, ShopListEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .build();
        private String action;

        public ShopListEventData() {
        }
    }
}
