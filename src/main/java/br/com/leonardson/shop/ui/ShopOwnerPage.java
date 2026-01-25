package br.com.leonardson.shop.ui;

import br.com.leonardson.Main;
import br.com.leonardson.shop.ShopNpcRegistry;
import br.com.leonardson.shop.ShopTrade;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class ShopOwnerPage extends InteractiveCustomUIPage<ShopOwnerPage.ShopOwnerEventData> {
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

        appendAction(commandBuilder, eventBuilder, 0, ACTION_ADD, "Add a new trade");
        appendAction(commandBuilder, eventBuilder, 1, ACTION_CANCEL, "Cancel without changes");
        appendAction(commandBuilder, eventBuilder, 2, ACTION_SAVE, "Save and preview buyer view");

        appendTradeList(commandBuilder);
    }

    private void appendAction(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, int index, String action, String description) {
        String selector = "#WarpList[" + index + "]";
        commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
        commandBuilder.set(selector + " #Name.Text", action);
        commandBuilder.set(selector + " #World.Text", description);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, EventData.of("Action", action), false);
    }

    private void appendTradeList(UICommandBuilder commandBuilder) {
        ShopNpcRegistry registry = Main.getInstance().getShopNpcRegistry();
        List<ShopTrade> trades = registry.getPendingTrades(this.playerRef.getUuid());
        if (trades == null || trades.isEmpty()) {
            appendTradeEntry(commandBuilder, 4, "No trades yet", "Use Add to open slots.");
            return;
        }

        int startIndex = 3;
        for (int i = 0; i < trades.size(); i++) {
            ShopTrade trade = trades.get(i);
            String title = "Trade " + (i + 1);
            String details = formatTrade(trade);
            appendTradeEntry(commandBuilder, startIndex + i, title, details);
        }
    }

    private void appendTradeEntry(UICommandBuilder commandBuilder, int index, String title, String details) {
        String selector = "#WarpList[" + index + "]";
        commandBuilder.append("#WarpList", "Pages/WarpEntryButton.ui");
        commandBuilder.set(selector + " #Name.Text", title);
        commandBuilder.set(selector + " #World.Text", details);
    }

    private String formatTrade(ShopTrade trade) {
        return trade.inputQty() + "x " + trade.inputItemId() + " -> " + trade.outputQty() + "x " + trade.outputItemId();
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
            case ACTION_ADD -> {
                openTradeEditor(ref, store, playerComponent);
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

    private void openTradeEditor(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull Player playerComponent) {
        playerComponent.getPageManager().openCustomPage(ref, store, new ShopTradeEditPage(this.playerRef, this.shopId, this.shopName));
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
