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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
    private String inputItemId = null;
    private String outputItemId = null;

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
        commandBuilder.set("#InputSlot.Slots", buildSelectionSlot(player.getInventory(), firstSelectedSlot, inputItemId, inputQuantity));
        commandBuilder.set("#OutputSlot.Slots", buildSelectionSlot(player.getInventory(), secondSelectedSlot, outputItemId, outputQuantity));
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
            // Deselecting the first slot (input)
            firstSelectedSlot = NO_SELECTION;
            inputItemId = null;
            inputQuantity = 0;
        } else if (slotIndex == secondSelectedSlot) {
            // Deselecting the second slot (output)
            secondSelectedSlot = NO_SELECTION;
            outputItemId = null;
            outputQuantity = 0;
        } else if (firstSelectedSlot == NO_SELECTION) {
            // Selecting first slot (input)
            firstSelectedSlot = slotIndex;
            inputItemId = getSelectionItemId(player.getInventory(), firstSelectedSlot);
            inputQuantity = getSelectionQuantity(player.getInventory(), firstSelectedSlot);
        } else if (secondSelectedSlot == NO_SELECTION) {
            // Selecting second slot (output)
            secondSelectedSlot = slotIndex;
            outputItemId = getSelectionItemId(player.getInventory(), secondSelectedSlot);
            outputQuantity = getSelectionQuantity(player.getInventory(), secondSelectedSlot);
        } else {
            // Both slots occupied, move second to first and set new second
            firstSelectedSlot = secondSelectedSlot;
            inputItemId = outputItemId;
            inputQuantity = outputQuantity;
            
            secondSelectedSlot = slotIndex;
            outputItemId = getSelectionItemId(player.getInventory(), secondSelectedSlot);
            outputQuantity = getSelectionQuantity(player.getInventory(), secondSelectedSlot);
        }

        UICommandBuilder commandBuilder = new UICommandBuilder();
        commandBuilder.set("#InventoryGrid.Slots", buildSlots(player.getInventory()));
        commandBuilder.set("#InputSlot.Slots", buildSelectionSlot(player.getInventory(), firstSelectedSlot, inputItemId, inputQuantity));
        commandBuilder.set("#OutputSlot.Slots", buildSelectionSlot(player.getInventory(), secondSelectedSlot, outputItemId, outputQuantity));
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
        inputItemId = trade.inputItemId();
        outputItemId = trade.outputItemId();
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

    private ItemGridSlot[] buildSelectionSlot(@Nonnull Inventory inventory, int selectedIndex, @Nullable String fallbackItemId, int fallbackQuantity) {
        ItemGridSlot slot = new ItemGridSlot();
        if (selectedIndex != NO_SELECTION) {
            ItemContainer combinedContainer = inventory.getCombinedEverything();
            if (selectedIndex >= 0 && selectedIndex < combinedContainer.getCapacity()) {
                ItemStack itemStack = combinedContainer.getItemStack((short) selectedIndex);
                if (isRenderableItem(itemStack)) {
                    slot = new ItemGridSlot(toDisplayItem(itemStack));
                }
            }
        } else if (fallbackItemId != null && !fallbackItemId.isBlank()) {
            // If no slot selected but we have a fallback itemId (from editing), create item from it
            ItemStack itemStack = createItemStack(fallbackItemId, fallbackQuantity);
            if (itemStack != null && isRenderableItem(itemStack)) {
                slot = new ItemGridSlot(toDisplayItem(itemStack));
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

    @Nullable
    private ItemStack createItemStack(@Nonnull String itemId, int quantity) {
        ItemStack itemStack = tryCreateItemStack(itemId, quantity);
        if (itemStack == null) {
            itemStack = tryCreateItemStack(itemId);
        }
        if (itemStack == null) {
            itemStack = tryCreateItemStackWithConstructor(itemId, quantity);
        }
        if (itemStack == null) {
            itemStack = tryCreateItemStackWithConstructor(itemId);
        }
        if (itemStack == null) {
            return null;
        }
        return applyQuantity(itemStack, quantity);
    }

    @Nullable
    private ItemStack tryCreateItemStack(@Nonnull String itemId, int quantity) {
        String[] methodNames = new String[]{"of", "create", "from", "fromItemId", "fromId"};
        for (String name : methodNames) {
            ItemStack stack = invokeFactory(name, itemId, quantity);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack tryCreateItemStack(@Nonnull String itemId) {
        String[] methodNames = new String[]{"of", "create", "from", "fromItemId", "fromId"};
        for (String name : methodNames) {
            ItemStack stack = invokeFactory(name, itemId);
            if (stack != null) {
                return stack;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack tryCreateItemStackWithConstructor(@Nonnull String itemId, int quantity) {
        return invokeConstructor(itemId, quantity);
    }

    @Nullable
    private ItemStack tryCreateItemStackWithConstructor(@Nonnull String itemId) {
        return invokeConstructor(itemId);
    }

    @Nullable
    private ItemStack invokeFactory(@Nonnull String name, Object... args) {
        ItemStack direct = invokeStaticFactory(name, args);
        if (direct != null) {
            return direct;
        }
        if (args.length == 2) {
            Object swapped = args[0];
            args[0] = args[1];
            args[1] = swapped;
            ItemStack swappedResult = invokeStaticFactory(name, args);
            if (swappedResult != null) {
                return swappedResult;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack invokeStaticFactory(@Nonnull String name, Object... args) {
        for (java.lang.reflect.Method method : ItemStack.class.getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            if (!ItemStack.class.isAssignableFrom(method.getReturnType())) {
                continue;
            }
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            Object[] converted = convertArguments(args, method.getParameterTypes());
            if (converted == null) {
                continue;
            }
            try {
                Object result = method.invoke(null, converted);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                continue;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack invokeConstructor(Object... args) {
        for (var ctor : ItemStack.class.getConstructors()) {
            Object[] converted = convertArguments(args, ctor.getParameterTypes());
            if (converted == null) {
                continue;
            }
            try {
                Object result = ctor.newInstance(converted);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (ReflectiveOperationException ignored) {
                continue;
            }
        }
        return null;
    }

    @Nullable
    private Object[] convertArguments(Object[] args, Class<?>[] params) {
        if (params.length != args.length) {
            return null;
        }
        Object[] converted = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object value = convertArgument(args[i], params[i]);
            if (value == null && args[i] != null) {
                return null;
            }
            converted[i] = value;
        }
        return converted;
    }

    @Nullable
    private Object convertArgument(Object arg, Class<?> paramType) {
        if (arg == null) {
            return null;
        }
        Class<?> boxed = boxType(paramType);
        if (boxed.isInstance(arg)) {
            return arg;
        }
        if (arg instanceof Number number) {
            if (boxed == Integer.class) {
                return number.intValue();
            }
            if (boxed == Short.class) {
                return number.shortValue();
            }
            if (boxed == Long.class) {
                return number.longValue();
            }
            if (boxed == Double.class) {
                return number.doubleValue();
            }
            if (boxed == Float.class) {
                return number.floatValue();
            }
            if (boxed == Byte.class) {
                return number.byteValue();
            }
        }
        if (boxed == String.class) {
            return String.valueOf(arg);
        }
        if (boxed == CharSequence.class) {
            return String.valueOf(arg);
        }
        if (arg instanceof String text) {
            Object built = tryBuildFromString(paramType, text);
            if (built != null) {
                return built;
            }
        }
        return null;
    }

    private Class<?> boxType(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == char.class) {
            return Character.class;
        }
        return type;
    }

    @Nullable
    private Object tryBuildFromString(Class<?> type, String value) {
        try {
            var ctor = type.getConstructor(String.class);
            return ctor.newInstance(value);
        } catch (ReflectiveOperationException ignored) {
            // continue
        }
        String[] methodNames = new String[]{"of", "from", "valueOf", "fromString", "parse"};
        for (String name : methodNames) {
            try {
                java.lang.reflect.Method method = type.getMethod(name, String.class);
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                return method.invoke(null, value);
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                continue;
            }
        }
        return null;
    }

    private ItemStack applyQuantity(@Nonnull ItemStack itemStack, int quantity) {
        ItemStack updated = invokeQuantityBuilder(itemStack, quantity, "withQuantity", "withAmount", "withCount");
        if (updated != null) {
            return updated;
        }
        if (invokeSetter(itemStack, quantity, "setQuantity", "setAmount", "setCount")) {
            return itemStack;
        }
        return itemStack;
    }

    @Nullable
    private ItemStack invokeQuantityBuilder(@Nonnull ItemStack itemStack, int quantity, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method method = itemStack.getClass().getMethod(name, int.class);
                Object result = method.invoke(itemStack, quantity);
                if (result instanceof ItemStack stack) {
                    return stack;
                }
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                continue;
            }
        }
        return null;
    }

    private boolean invokeSetter(@Nonnull ItemStack itemStack, int quantity, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method method = itemStack.getClass().getMethod(name, int.class);
                method.invoke(itemStack, quantity);
                return true;
            } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
                continue;
            }
        }
        return false;
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
