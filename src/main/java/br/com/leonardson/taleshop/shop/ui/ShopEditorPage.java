package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.player.PlayerIdentity;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
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
import javax.annotation.Nullable;

public class ShopEditorPage extends InteractiveCustomUIPage<ShopEditorPage.ShopEditorEventData> {
    private static final String PAGE_PATH = "Pages/ShopEditorPage.ui";
    
    private final String ownerId;
    private final String currentShopName; // null for create mode, non-null for edit mode
    private final boolean isEditMode;

    public ShopEditorPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nullable String currentShopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, ShopEditorEventData.CODEC);
        this.ownerId = ownerId;
        this.currentShopName = currentShopName;
        this.isEditMode = currentShopName != null && !currentShopName.isBlank();
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(PAGE_PATH);
        
        // Bind buttons - Save button also captures TextField value
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SaveButton",
            new EventData().append("Action", "Save").append("@ShopName", "#ShopNameInput.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelButton",
            EventData.of("Action", "Cancel"),
            false
        );

        // Set title based on mode
        String title = isEditMode ? "Rename Shop" : "Create New Shop";
        commandBuilder.set("#TitleLabel.Text", title);
        
        // Pre-fill shop name in edit mode
        if (isEditMode) {
            commandBuilder.set("#ShopNameInput.Value", currentShopName);
        } else {
            commandBuilder.set("#ShopNameInput.Value", "");
        }
        
        // Hide error label initially
        commandBuilder.set("#ErrorLabel.Visible", false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ShopEditorEventData data) {
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

        if ("Save".equals(data.action)) {
            handleSave(ref, store, player, playerRef, data.text);
            return;
        }
    }

    private void handleSave(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nullable String shopName
    ) {
        if (shopName == null || shopName.trim().isBlank()) {
            player.sendMessage(Message.raw("Shop name cannot be empty."));
            return;
        }

        String trimmedName = shopName.trim();
        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            player.sendMessage(Message.raw("Shop registry not available."));
            return;
        }

        try {
            if (isEditMode) {
                // Rename existing shop
                registry.renameShop(ownerId, currentShopName, trimmedName);
                player.sendMessage(Message.raw("Shop renamed to '" + trimmedName + "'."));
            } else {
                // Create new shop
                String ownerName = PlayerIdentity.resolveDisplayName(player);
                Shop shop = registry.createShop(ownerId, ownerName, trimmedName);
                player.sendMessage(Message.raw("Shop '" + shop.name() + "' created successfully."));
            }
            
            // Return to shop list
            player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Message.raw("Error: " + ex.getMessage()));
            ex.printStackTrace();
        } catch (Exception ex) {
            player.sendMessage(Message.raw("An unexpected error occurred: " + ex.getMessage()));
            ex.printStackTrace();
        }
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    public static class ShopEditorEventData {
        public static final BuilderCodec<ShopEditorEventData> CODEC = BuilderCodec.builder(ShopEditorEventData.class, ShopEditorEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .append(new KeyedCodec<>("@ShopName", Codec.STRING), (data, s) -> data.text = s, data -> data.text)
            .add()
            .build();
        private String action;
        private String text;

        public ShopEditorEventData() {
        }
    }
}
