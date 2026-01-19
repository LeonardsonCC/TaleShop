package br.com.leonardson.shop.ui;

import br.com.leonardson.Main;
import br.com.leonardson.shop.ShopNpcRegistry;
import br.com.leonardson.shop.ShopTrade;
import br.com.leonardson.shop.ui.ShopBuyerPage;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

public class ShopOwnerPage extends InteractiveCustomUIPage<ShopOwnerPage.ShopOwnerEventData> {
    private static final String ACTION_CONFIRM = "Confirm";
    private static final String ACTION_ADD = "Add";
    private static final String ACTION_CANCEL = "Cancel";
    private static final String ACTION_SAVE = "Save";

    private final long shopId;
    private final String shopName;
    public ShopOwnerPage(@Nonnull PlayerRef playerRef, long shopId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, ShopOwnerEventData.CODEC);
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
        commandBuilder.append("Pages/WarpListPage.ui");
        commandBuilder.set("#SearchInput.Visible", false);
        commandBuilder.clear("#WarpList");

        appendAction(commandBuilder, eventBuilder, 0, ACTION_CONFIRM, "Confirm current input/output");
        appendAction(commandBuilder, eventBuilder, 1, ACTION_ADD, "Add trade and clear slots");
        appendAction(commandBuilder, eventBuilder, 2, ACTION_CANCEL, "Cancel without changes");
        appendAction(commandBuilder, eventBuilder, 3, ACTION_SAVE, "Save and preview buyer view");
    }

    private void appendAction(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, int index, String action, String description) {
        String selector = "#WarpList[" + index + "]";
        commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
        commandBuilder.set(selector + " #Name.Text", action);
        commandBuilder.set(selector + " #World.Text", description);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ShopOwnerEventData data) {
        if (data.action == null) {
            return;
        }

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        ShopNpcRegistry registry = Main.getInstance().getShopNpcRegistry();
        UUID playerId = this.playerRef.getUuid();

        switch (data.action) {
            case ACTION_CONFIRM -> {
                if (tryAddTrade(registry, playerComponent)) {
                    playerComponent.sendMessage(Message.raw("Trade added."));
                }
            }
            case ACTION_ADD -> {
                clearSlots(registry);
                openInputOutputWindow(ref, store, playerComponent);
            }
            case ACTION_CANCEL -> {
                registry.discardPending(playerId);
                playerComponent.getPageManager().setPage(ref, store, Page.None);
            }
            case ACTION_SAVE -> {
                registry.savePending(playerId);
                playerComponent.getPageManager().openCustomPage(ref, store, new ShopBuyerPage(this.playerRef, this.shopId, this.shopName));
            }
        }
    }

    private boolean tryAddTrade(ShopNpcRegistry registry, Player playerComponent) {
        SimpleItemContainer container = registry.getOrCreateContainer(this.shopId);
        ItemStack input = container.getItemStack((short) 0);
        ItemStack output = container.getItemStack((short) 1);

        if (input == null || input.isEmpty() || output == null || output.isEmpty()) {
            playerComponent.sendMessage(Message.raw("Place input in slot 1 and output in slot 2."));
            return false;
        }

        ShopTrade trade = new ShopTrade(input.getItemId(), input.getQuantity(), output.getItemId(), output.getQuantity());
        registry.addPendingTrade(this.playerRef.getUuid(), trade);
        return true;
    }

    private void clearSlots(ShopNpcRegistry registry) {
        SimpleItemContainer container = registry.getOrCreateContainer(this.shopId);
        container.setItemStackForSlot((short) 0, ItemStack.EMPTY, false);
        container.setItemStackForSlot((short) 1, ItemStack.EMPTY, false);
    }

    private void openInputOutputWindow(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Player playerComponent) {
        ShopNpcRegistry registry = Main.getInstance().getShopNpcRegistry();
        SimpleItemContainer container = registry.getOrCreateContainer(this.shopId);
        com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow window =
            new com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow(container);
        playerComponent.sendMessage(Message.raw("Place input in slot 1 and output in slot 2. Close the window to return."));
        if (playerComponent.getPageManager().setPageWithWindows(ref, store, Page.Bench, true, window)) {
            window.registerCloseEvent(event -> playerComponent.getPageManager().openCustomPage(ref, store, new ShopOwnerPage(this.playerRef, this.shopId, this.shopName)));
        } else {
            playerComponent.sendMessage(Message.raw("Could not open item slots."));
        }
    }


    public static class ShopOwnerEventData {
        public static final com.hypixel.hytale.codec.builder.BuilderCodec<ShopOwnerEventData> CODEC = com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                ShopOwnerEventData.class, ShopOwnerEventData::new
        )
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("Action", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.action = s, entry -> entry.action)
            .add()
            .build();

        private String action;

        public ShopOwnerEventData() {
        }
    }
}
