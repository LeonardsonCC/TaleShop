package br.com.leonardson.shop.ui;

import br.com.leonardson.Main;
import br.com.leonardson.shop.ShopNpcRegistry;
import br.com.leonardson.shop.ShopTrade;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ShopTradeEditPage extends InteractiveCustomUIPage<ShopTradeEditPage.ShopTradeEditEventData> {
    private static final String ACTION_CONFIRM = "Confirm";
    private static final String ACTION_CANCEL = "Cancel";
    private static final String TYPE_SELECT = "SelectItem";
    private static final int DEFAULT_QTY = 1;

    private final long shopId;
    private final String shopName;
    private String inputItemId;
    private String outputItemId;
    private int inputQty = DEFAULT_QTY;
    private int outputQty = DEFAULT_QTY;
    private boolean selectingInput = true;

    public ShopTradeEditPage(@Nonnull PlayerRef playerRef, long shopId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismiss, ShopTradeEditEventData.CODEC);
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
        commandBuilder.append("Pages/TradeEditPage.ui");
        selectingInput = inputItemId == null || outputItemId != null;
        commandBuilder.set("#InputQty.Value", String.valueOf(inputQty));
        commandBuilder.set("#OutputQty.Value", String.valueOf(outputQty));
        applySlotState(commandBuilder, "#InputSlot", inputItemId);
        applySlotState(commandBuilder, "#OutputSlot", outputItemId);
        commandBuilder.set("#SelectionHint.Text", resolveSelectionHint());
        commandBuilder.set("#ConfirmButton.Disabled", !isTradeReady());

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent != null) {
            ItemGridSlot[] inventorySlots = buildInventorySlots(playerComponent.getInventory());
            commandBuilder.set("#InventoryGrid.Slots", inventorySlots);
        }

        EventData confirmData = new EventData()
            .append("Action", ACTION_CONFIRM)
            .append("InputQty", "#InputQty.Value")
            .append("OutputQty", "#OutputQty.Value");
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmButton", confirmData, false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#CancelButton", EventData.of("Action", ACTION_CANCEL), false);
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, "#InventoryGrid", EventData.of("Type", TYPE_SELECT), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ShopTradeEditEventData data) {
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        ShopNpcRegistry registry = Main.getInstance().getShopNpcRegistry();
        if (data.action != null) {
            switch (data.action) {
                case ACTION_CONFIRM -> {
                    if (inputItemId == null || outputItemId == null) {
                        playerComponent.sendMessage(Message.raw("Drop input and output items first."));
                        return;
                    }
                    int resolvedInputQty = resolveQuantity(data.inputQty, inputQty);
                    int resolvedOutputQty = resolveQuantity(data.outputQty, outputQty);
                    if (resolvedInputQty <= 0 || resolvedOutputQty <= 0) {
                        playerComponent.sendMessage(Message.raw("Quantities must be at least 1."));
                        return;
                    }
                    ShopTrade trade = new ShopTrade(inputItemId, resolvedInputQty, outputItemId, resolvedOutputQty);
                    registry.addPendingTrade(this.playerRef.getUuid(), trade);
                    playerComponent.sendMessage(Message.raw("Trade added."));
                    playerComponent.getPageManager().openCustomPage(ref, store, new ShopOwnerPage(this.playerRef, this.shopId, this.shopName));
                }
                case ACTION_CANCEL -> playerComponent.getPageManager().openCustomPage(ref, store, new ShopOwnerPage(this.playerRef, this.shopId, this.shopName));
            }
            return;
        }

        if (data.type == null) {
            return;
        }

        String itemId = data.itemStackId != null ? data.itemStackId : data.itemId;
        if (itemId == null || itemId.isBlank()) {
            return;
        }

        if (!TYPE_SELECT.equals(data.type)) {
            return;
        }

        if (selectingInput) {
            inputItemId = itemId;
            if (outputItemId != null) {
                outputItemId = null;
            }
            selectingInput = false;
        } else {
            outputItemId = itemId;
            selectingInput = true;
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        applySlotState(commandBuilder, "#InputSlot", inputItemId);
        applySlotState(commandBuilder, "#OutputSlot", outputItemId);
        commandBuilder.set("#SelectionHint.Text", resolveSelectionHint());
        commandBuilder.set("#ConfirmButton.Disabled", !isTradeReady());
        sendUpdate(commandBuilder, false);
    }

    private int resolveQuantity(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void applySlotState(UICommandBuilder commandBuilder, String slotSelector, String itemId) {
        if (itemId == null) {
            commandBuilder.set(slotSelector + ".Slots", new ItemGridSlot[] { new ItemGridSlot() });
            return;
        }

        ItemGridSlot slot = new ItemGridSlot(new ItemStack(itemId, 1));
        commandBuilder.set(slotSelector + ".Slots", new ItemGridSlot[] { slot });
    }

    private ItemGridSlot[] buildInventorySlots(@Nonnull Inventory inventory) {
        CombinedItemContainer container = inventory.getCombinedHotbarFirst();
        List<ItemGridSlot> slots = new ArrayList<>();
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack itemStack = container.getItemStack(slot);
            if (ItemStack.isEmpty(itemStack)) {
                continue;
            }
            slots.add(new ItemGridSlot(itemStack));
        }
        if (slots.isEmpty()) {
            return new ItemGridSlot[] { new ItemGridSlot() };
        }
        return slots.toArray(new ItemGridSlot[0]);
    }

    private boolean isTradeReady() {
        return inputItemId != null && outputItemId != null;
    }

    private String resolveSelectionHint() {
        if (inputItemId == null) {
            return "Select the input item";
        }
        if (outputItemId == null) {
            return "Select the output item";
        }
        return "Ready to confirm the trade";
    }

    public static class ShopTradeEditEventData {
        public static final com.hypixel.hytale.codec.builder.BuilderCodec<ShopTradeEditEventData> CODEC = com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                ShopTradeEditEventData.class, ShopTradeEditEventData::new
        )
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("Action", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.action = s, entry -> entry.action)
            .add()
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("Type", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.type = s, entry -> entry.type)
            .add()
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("InputQty", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.inputQty = s, entry -> entry.inputQty)
            .add()
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("OutputQty", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.outputQty = s, entry -> entry.outputQty)
            .add()
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("ItemId", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.itemId = s, entry -> entry.itemId)
            .add()
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("ItemStackId", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.itemStackId = s, entry -> entry.itemStackId)
            .add()
            .build();

        private String action;
        private String type;
        private String inputQty;
        private String outputQty;
        private String itemId;
        private String itemStackId;

        public ShopTradeEditEventData() {
        }
    }
}
