package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
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
import javax.annotation.Nonnull;

public class ShopDeleteConfirmationPage extends InteractiveCustomUIPage<ShopDeleteConfirmationPage.DeleteConfirmEventData> {
    private static final String PAGE_PATH = "Pages/ShopDeleteConfirmationPage.ui";
    
    private final String ownerId;
    private final String shopName;

    public ShopDeleteConfirmationPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, DeleteConfirmEventData.CODEC);
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
        
        // Bind buttons
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#ConfirmButton",
            EventData.of("Action", "Confirm"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelButton",
            EventData.of("Action", "Cancel"),
            false
        );

        // Set shop name in the confirmation message
        commandBuilder.set("#ShopNameLabel.Text", shopName);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull DeleteConfirmEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if ("Cancel".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
            return;
        }

        if ("Confirm".equals(data.action)) {
            handleDelete(ref, store, player, playerRef);
        }
    }

    private void handleDelete(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {
        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            player.sendMessage(Message.raw("Shop registry not available."));
            return;
        }

        try {
            // First, despawn any associated NPC
            String traderUuid = registry.getTraderUuid(ownerId, shopName);
            if (traderUuid != null && !traderUuid.isBlank()) {
                TraderNpc.despawnByUuid(store, traderUuid);
            }
            
            // Delete the shop
            registry.deleteShop(ownerId, shopName);
            player.sendMessage(Message.raw("Shop '" + shopName + "' has been deleted."));
            
            // Return to shop list
            player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Message.raw("Error: " + ex.getMessage()));
            player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
        } catch (Exception ex) {
            player.sendMessage(Message.raw("An unexpected error occurred: " + ex.getMessage()));
            player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
        }
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    public static class DeleteConfirmEventData {
        public static final BuilderCodec<DeleteConfirmEventData> CODEC = BuilderCodec.builder(DeleteConfirmEventData.class, DeleteConfirmEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .build();
        private String action;

        public DeleteConfirmEventData() {
        }
    }
}
