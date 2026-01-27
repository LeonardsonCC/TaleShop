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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TradeEditorPage extends InteractiveCustomUIPage<TradeEditorPage.TradeEventData> {
    private static final String PAGE_PATH = "Pages/TaleShopInventorySelectionPage.ui";
    private static final int NO_SELECTION = -1;
    private static final int INVENTORY_GRID_COLUMNS = 9;
    private static final int INVENTORY_GRID_ROWS = 10;
    private static final int INVENTORY_GRID_CAPACITY = INVENTORY_GRID_COLUMNS * INVENTORY_GRID_ROWS;
    private static final Value<PatchStyle> FIRST_SELECTION_OVERLAY = Value.ref(PAGE_PATH, "SelectedRedOverlay");
    private static final Value<PatchStyle> SECOND_SELECTION_OVERLAY = Value.ref(PAGE_PATH, "SelectedGreenOverlay");
    private final String ownerId;
    private final String shopName;
    private final Integer tradeId;
    private int firstSelectedSlot = NO_SELECTION;
    private int secondSelectedSlot = NO_SELECTION;
    private int inputQuantity = 0;
    private int outputQuantity = 0;
    private boolean initialized = false;

    public TradeEditorPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull String ownerId,
        @Nonnull String shopName,
        @Nullable Integer tradeId
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, TradeEventData.CODEC);
        this.ownerId = ownerId;
        this.shopName = shopName;
        this.tradeId = tradeId;
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
            CustomUIEventBindingType.SlotClicking,
            "#InventoryGrid",
            new EventData().append("Type", "SlotClick"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#InputQuantityField",
            EventData.of("@InputQuantity", "#InputQuantityField.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#OutputQuantityField",
            EventData.of("@OutputQuantity", "#OutputQuantityField.Value"),
            false
        );
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

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        initializeFromTradeIfNeeded(player);

        String title = tradeId == null ? "New Trade" : "Edit Trade";
        commandBuilder.set("#TitleLabel.Text", title);
        commandBuilder.set("#InventoryGrid.Slots", buildSlots(player.getInventory()));
        commandBuilder.set("#InputSlot.Slots", buildSelectionSlot(player.getInventory(), firstSelectedSlot));
        commandBuilder.set("#OutputSlot.Slots", buildSelectionSlot(player.getInventory(), secondSelectedSlot));
        commandBuilder.set("#InputQuantityField.Value", inputQuantity);
        commandBuilder.set("#OutputQuantityField.Value", outputQuantity);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull TradeEventData data) {
        if (data.inputQuantity != null) {
            inputQuantity = Math.max(0, data.inputQuantity);
        }

        if (data.outputQuantity != null) {
            outputQuantity = Math.max(0, data.outputQuantity);
        }

        int slotIndex = resolveSlotIndex(data);
        if (slotIndex == NO_SELECTION) {
            if (data.action != null) {
                handleAction(ref, store, data.action);
            }

            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        int capacity = player.getInventory().getCombinedEverything().getCapacity();
        if (slotIndex < 0 || slotIndex >= capacity) {
            return;
        }

        if (slotIndex == firstSelectedSlot) {
            firstSelectedSlot = NO_SELECTION;
        } else if (slotIndex == secondSelectedSlot) {
            secondSelectedSlot = NO_SELECTION;
        } else if (firstSelectedSlot == NO_SELECTION) {
            firstSelectedSlot = slotIndex;
        } else if (secondSelectedSlot == NO_SELECTION) {
            secondSelectedSlot = slotIndex;
        } else {
            firstSelectedSlot = secondSelectedSlot;
            secondSelectedSlot = slotIndex;
        }

        inputQuantity = getSelectionQuantity(player.getInventory(), firstSelectedSlot);
        outputQuantity = getSelectionQuantity(player.getInventory(), secondSelectedSlot);

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#InventoryGrid.Slots", buildSlots(player.getInventory()));
        commandBuilder.set("#InputSlot.Slots", buildSelectionSlot(player.getInventory(), firstSelectedSlot));
        commandBuilder.set("#OutputSlot.Slots", buildSelectionSlot(player.getInventory(), secondSelectedSlot));
        commandBuilder.set("#InputQuantityField.Value", inputQuantity);
        commandBuilder.set("#OutputQuantityField.Value", outputQuantity);
        this.sendUpdate(commandBuilder, null, false);
    }

    private void initializeFromTradeIfNeeded(@Nonnull Player player) {
        if (initialized || tradeId == null) {
            return;
        }

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return;
        }

        Trade trade = resolveTrade(registry);
        if (trade == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        firstSelectedSlot = findSlotByItemId(inventory, trade.inputItemId());
        secondSelectedSlot = findSlotByItemId(inventory, trade.outputItemId());
        inputQuantity = trade.inputQuantity();
        outputQuantity = trade.outputQuantity();
        initialized = true;
    }

    private Trade resolveTrade(@Nonnull ShopRegistry registry) {
        try {
            Shop shop = registry.getShop(ownerId, shopName);
            for (Trade trade : shop.trades()) {
                if (trade.id() == tradeId) {
                    return trade;
                }
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    private int findSlotByItemId(@Nonnull Inventory inventory, @Nonnull String itemId) {
        ItemContainer combinedContainer = inventory.getCombinedEverything();
        int capacity = combinedContainer.getCapacity();
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = combinedContainer.getItemStack(slot);
            if (!ItemStack.isEmpty(itemStack) && itemId.equals(itemStack.getItemId())) {
                return slot;
            }
        }
        return NO_SELECTION;
    }

    private ItemGridSlot[] buildSlots(@Nonnull Inventory inventory) {
        ItemContainer combinedContainer = inventory.getCombinedEverything();
        int capacity = combinedContainer.getCapacity();
        ItemGridSlot[] slots = new ItemGridSlot[INVENTORY_GRID_CAPACITY];

        for (short slot = 0; slot < INVENTORY_GRID_CAPACITY; slot++) {
            ItemGridSlot gridSlot = new ItemGridSlot();
            if (slot < capacity) {
                ItemStack itemStack = combinedContainer.getItemStack(slot);
                if (isRenderableItem(itemStack)) {
                    gridSlot = new ItemGridSlot(toDisplayItem(itemStack));
                }
            }
            gridSlot.setActivatable(true);

            if (slot == firstSelectedSlot) {
                gridSlot.setOverlay(FIRST_SELECTION_OVERLAY);
            } else if (slot == secondSelectedSlot) {
                gridSlot.setOverlay(SECOND_SELECTION_OVERLAY);
            }

            slots[slot] = gridSlot;
        }

        return slots;
    }

    private ItemGridSlot[] buildSelectionSlot(@Nonnull Inventory inventory, int selectedIndex) {
        ItemGridSlot slot = new ItemGridSlot();
        if (selectedIndex != NO_SELECTION) {
            ItemContainer combinedContainer = inventory.getCombinedEverything();
            if (selectedIndex >= 0 && selectedIndex < combinedContainer.getCapacity()) {
                ItemStack itemStack = combinedContainer.getItemStack((short) selectedIndex);
                if (isRenderableItem(itemStack)) {
                    slot = new ItemGridSlot(toDisplayItem(itemStack));
                }
            }
        }

        slot.setActivatable(false);
        return new ItemGridSlot[]{slot};
    }

    private int getSelectionQuantity(@Nonnull Inventory inventory, int selectedIndex) {
        if (selectedIndex == NO_SELECTION) {
            return 0;
        }

        ItemContainer combinedContainer = inventory.getCombinedEverything();
        if (selectedIndex < 0 || selectedIndex >= combinedContainer.getCapacity()) {
            return 0;
        }

        ItemStack itemStack = combinedContainer.getItemStack((short) selectedIndex);
        if (!isRenderableItem(itemStack)) {
            return 0;
        }

        return itemStack.getQuantity();
    }

    @Nonnull
    private String getSelectionItemId(@Nonnull Inventory inventory, int selectedIndex) {
        if (selectedIndex == NO_SELECTION) {
            return "";
        }

        ItemContainer combinedContainer = inventory.getCombinedEverything();
        if (selectedIndex < 0 || selectedIndex >= combinedContainer.getCapacity()) {
            return "";
        }

        ItemStack itemStack = combinedContainer.getItemStack((short) selectedIndex);
        if (!isRenderableItem(itemStack)) {
            return "";
        }

        return itemStack.getItemId();
    }

    private boolean isRenderableItem(@Nonnull ItemStack itemStack) {
        if (ItemStack.isEmpty(itemStack)) {
            return false;
        }
        String itemId = itemStack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        return itemStack.getQuantity() > 0;
    }

    private ItemStack toDisplayItem(@Nonnull ItemStack itemStack) {
        if (!isRenderableItem(itemStack)) {
            return ItemStack.EMPTY;
        }
        ItemStack display = new ItemStack(itemStack.getItemId(), itemStack.getQuantity());
        double maxDurability = itemStack.getMaxDurability();
        if (maxDurability > 0) {
            display = display.withMaxDurability(maxDurability).withDurability(itemStack.getDurability());
        }
        return display;
    }

    private void handleAction(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String action) {
        Player player = store.getComponent(ref, Player.getComponentType());
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }

        if ("Cancel".equals(action)) {
            player.getPageManager().openCustomPage(ref, store, new TradeListPage(playerRef, ownerId, shopName));
            return;
        }

        if (!"Confirm".equals(action)) {
            return;
        }

        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            playerRef.sendMessage(Message.raw("Shop system is unavailable."));
            return;
        }

        Inventory inventory = player.getInventory();
        String inputItem = getSelectionItemId(inventory, firstSelectedSlot);
        String outputItem = getSelectionItemId(inventory, secondSelectedSlot);
        if (inputItem.isBlank() || outputItem.isBlank()) {
            playerRef.sendMessage(Message.raw("Select input and output items."));
            return;
        }
        if (inputQuantity <= 0 || outputQuantity <= 0) {
            playerRef.sendMessage(Message.raw("Quantities must be greater than 0."));
            return;
        }

        try {
            if (tradeId == null) {
                Trade trade = registry.addTrade(ownerId, shopName, inputItem, inputQuantity, outputItem, outputQuantity);
                playerRef.sendMessage(Message.raw("Trade added (#" + trade.id() + ")."));
            } else {
                registry.updateTrade(ownerId, shopName, tradeId, inputItem, inputQuantity, outputItem, outputQuantity);
                playerRef.sendMessage(Message.raw("Trade updated (#" + tradeId + ")."));
            }
        } catch (IllegalArgumentException ex) {
            playerRef.sendMessage(Message.raw(ex.getMessage()));
            return;
        }

        player.getPageManager().openCustomPage(ref, store, new TradeListPage(playerRef, ownerId, shopName));
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    private int resolveSlotIndex(@Nonnull TradeEventData data) {
        Integer numeric = firstNonNull(data.slot, data.slotIndex, data.index);
        if (numeric != null) {
            return numeric;
        }

        String raw = firstNonEmpty(data.slotId, data.itemStackId);
        if (raw == null || raw.isBlank()) {
            return NO_SELECTION;
        }

        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return NO_SELECTION;
        }
    }

    @Nullable
    private static String firstNonEmpty(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    @Nullable
    private static Integer firstNonNull(@Nullable Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }

        return null;
    }

    public static class TradeEventData {
        public static final BuilderCodec<TradeEventData> CODEC = BuilderCodec.builder(TradeEventData.class, TradeEventData::new)
            .append(new KeyedCodec<>("Type", Codec.STRING), (data, s) -> data.type = s, data -> data.type)
            .add()
            .append(new KeyedCodec<>("Slot", Codec.INTEGER), (data, s) -> data.slot = s, data -> data.slot)
            .add()
            .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER), (data, s) -> data.slotIndex = s, data -> data.slotIndex)
            .add()
            .append(new KeyedCodec<>("SlotId", Codec.STRING), (data, s) -> data.slotId = s, data -> data.slotId)
            .add()
            .append(new KeyedCodec<>("Index", Codec.INTEGER), (data, s) -> data.index = s, data -> data.index)
            .add()
            .append(new KeyedCodec<>("ItemStackId", Codec.STRING), (data, s) -> data.itemStackId = s, data -> data.itemStackId)
            .add()
            .append(new KeyedCodec<>("@InputQuantity", Codec.INTEGER), (data, s) -> data.inputQuantity = s, data -> data.inputQuantity)
            .add()
            .append(new KeyedCodec<>("@OutputQuantity", Codec.INTEGER), (data, s) -> data.outputQuantity = s, data -> data.outputQuantity)
            .add()
            .append(new KeyedCodec<>("Action", Codec.STRING), (data, s) -> data.action = s, data -> data.action)
            .add()
            .build();
        private String type;
        private Integer slot;
        private Integer slotIndex;
        private String slotId;
        private Integer index;
        private String itemStackId;
        private Integer inputQuantity;
        private Integer outputQuantity;
        private String action;

        public TradeEventData() {
        }
    }
}
