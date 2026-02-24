package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.permission.PermissionUtil;
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
import javax.annotation.Nonnull;

public class TraderMenuPage extends InteractiveCustomUIPage<TraderMenuPage.MenuEventData> {
    private static final String PAGE_PATH = "Pages/TraderMenuPage.ui";
    private final String ownerId;
    private final String shopName;

    public TraderMenuPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, MenuEventData.CODEC);
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
        
        // Bind shop action buttons
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#PreviewShopButton",
            EventData.of("Action", "Preview"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#EditTradesButton",
            EventData.of("Action", "Edit"),
            false
        );
        
        // Bind trader management buttons
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#RenameTraderButton",
            EventData.of("Action", "RenameTrader"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DespawnTraderButton",
            EventData.of("Action", "DespawnTrader"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#DeleteTraderButton",
            EventData.of("Action", "DeleteTrader"),
            false
        );

        commandBuilder.set("#TitleLabel.Text", shopName);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull MenuEventData data) {
        if (data.action == null || data.action.isBlank()) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        ShopRegistry registry = resolveRegistry();
        Shop shop = null;
        if (registry != null) {
            try {
                shop = registry.getShop(ownerId, shopName);
            } catch (IllegalArgumentException ignored) {
            }
        }
        String playerOwnerId = PlayerIdentity.resolveOwnerId(player);
        boolean canManage = ownerId.equals(playerOwnerId)
            || (shop != null && shop.isAdmin() && PermissionUtil.hasAdminManagePermission(player));
        if (!canManage) {
            player.getPageManager().openCustomPage(ref, store, new ShopBuyerPage(playerRef, ownerId, shopName));
            return;
        }

        if ("Preview".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopBuyerPage(playerRef, ownerId, shopName));
            return;
        }

        if ("Edit".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new TradeListPage(playerRef, ownerId, shopName));
            return;
        }

        if ("RenameTrader".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopEditorPage(playerRef, ownerId, shopName, true));
            return;
        }

        if ("DespawnTrader".equals(data.action)) {
            handleDespawnTrader(ref, store, player, playerRef);
            return;
        }

        if ("DeleteTrader".equals(data.action)) {
            player.getPageManager().openCustomPage(ref, store, new ShopDeleteConfirmationPage(playerRef, ownerId, shopName, true));
            return;
        }
    }

    private void handleDespawnTrader(
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

        Shop shop = registry.getShop(ownerId, shopName);
        if (shop == null) {
            player.sendMessage(Message.raw("Shop not found."));
            return;
        }

        String traderUuid = shop.traderUuid();
        if (traderUuid == null || traderUuid.isBlank()) {
            player.sendMessage(Message.raw("No trader to despawn."));
            return;
        }

        boolean despawned = TraderNpc.despawnByUuid(store, traderUuid);
        if (despawned) {
            registry.clearTraderUuid(ownerId, shopName);
            player.sendMessage(Message.raw("Trader despawned successfully."));
        } else {
            player.sendMessage(Message.raw("Failed to despawn trader."));
        }
        
        // Redirect to shop list
        player.getPageManager().openCustomPage(ref, store, new ShopListPage(playerRef, ownerId));
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    public static class MenuEventData {
        public static final BuilderCodec<MenuEventData> CODEC = BuilderCodec.builder(MenuEventData.class, MenuEventData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .build();
        private String action;

        public MenuEventData() {
        }
    }
}
