package br.com.leonardson.taleshop.shop.ui;

import br.com.leonardson.taleshop.TaleShop;
import br.com.leonardson.taleshop.config.PluginConfig;
import br.com.leonardson.taleshop.shop.Shop;
import br.com.leonardson.taleshop.shop.ShopRegistry;
import br.com.leonardson.taleshop.shop.trade.Trade;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.meta.BlockState;
import com.hypixel.hytale.server.core.universe.world.meta.BlockStateModule;
import com.hypixel.hytale.server.core.universe.world.meta.state.ItemContainerState;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.lang.reflect.Method;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class ShopBuyerPage extends InteractiveCustomUIPage<ShopBuyerPage.ShopBuyerEventData> {
    private static final int STOCK_RADIUS_BLOCKS = 2;
    private static final int MAX_ENTITY_SCAN = 512;
    private final String ownerId;
    private final String shopName;

    public ShopBuyerPage(@Nonnull PlayerRef playerRef, @Nonnull String ownerId, @Nonnull String shopName) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, ShopBuyerEventData.CODEC);
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
        commandBuilder.append("Pages/BarterPage.ui");
        commandBuilder.set("#ShopTitle.Text", shopName);

        commandBuilder.clear("#TradeGrid");
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        ItemContainer playerInventory = null;
        if (playerComponent != null) {
            playerInventory = playerComponent.getInventory().getCombinedHotbarFirst();
        }
        Shop shop = resolveShop();
        List<ItemContainer> stockContainers = shop == null
            ? Collections.emptyList()
            : resolveNearbyContainers(store, shop);
        List<Trade> trades = shop == null ? new ArrayList<>() : new ArrayList<>(shop.trades());

        for (int i = 0; i < trades.size(); i++) {
            Trade trade = trades.get(i);
            String selector = "#TradeGrid[" + i + "]";
            commandBuilder.append("#TradeGrid", "Pages/BarterTradeRow.ui");

            commandBuilder.set(selector + " #OutputSlot.ItemId", trade.outputItemId());
            commandBuilder.set(selector + " #OutputQuantity.Text", trade.outputQuantity() > 1 ? String.valueOf(trade.outputQuantity()) : "");
            commandBuilder.set(selector + " #InputSlot.ItemId", trade.inputItemId());
            commandBuilder.set(selector + " #InputQuantity.Text", trade.inputQuantity() > 1 ? String.valueOf(trade.inputQuantity()) : "");

            int playerHas = 0;
            boolean canAfford = false;
            if (ItemModule.exists(trade.inputItemId())) {
                playerHas = playerInventory != null ? countItemsInContainer(playerInventory, trade.inputItemId()) : 0;
                canAfford = playerHas >= trade.inputQuantity();
            }
            commandBuilder.set(selector + " #InputSlotBorder.Background", canAfford ? "#2a5a3a" : "#5a2a2a");
            commandBuilder.set(selector + " #HaveNeedLabel.Text", "Have: " + playerHas);
            commandBuilder.set(selector + " #HaveNeedLabel.Style.TextColor", canAfford ? "#3d913f" : "#962f2f");

            int availableStock = countItemsInContainers(stockContainers, trade.outputItemId());
            boolean outOfStock = availableStock < trade.outputQuantity();
            commandBuilder.set(selector + " #Stock.Visible", true);
            commandBuilder.set(selector + " #Stock.Text", String.valueOf(Math.max(0, availableStock)));
            commandBuilder.set(selector + " #OutOfStockOverlay.Visible", outOfStock);
            commandBuilder.set(selector + " #TradeButton.Disabled", outOfStock || !canAfford);

            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector + " #TradeButton",
                EventData.of("TradeIndex", String.valueOf(i)),
                false
            );
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull ShopBuyerEventData data) {
        if (data.tradeIndex < 0) {
            return;
        }

        Shop shop = resolveShop();
        if (shop == null) {
            return;
        }
        List<Trade> trades = new ArrayList<>(shop.trades());
        if (data.tradeIndex >= trades.size()) {
            return;
        }

        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) {
            return;
        }

        Trade trade = trades.get(data.tradeIndex);
        if (!ItemModule.exists(trade.inputItemId()) || !ItemModule.exists(trade.outputItemId())) {
            playerComponent.sendMessage(Message.raw("This trade is invalid."));
            return;
        }

        List<ItemContainer> stockContainers = resolveNearbyContainers(store, shop);
        int availableStock = countItemsInContainers(stockContainers, trade.outputItemId());
        if (availableStock < trade.outputQuantity()) {
            playerComponent.sendMessage(Message.raw("Shop is out of stock."));
            return;
        }
        if (!hasSpaceForItems(stockContainers, trade.inputItemId(), trade.inputQuantity())) {
            playerComponent.sendMessage(Message.raw("Shop has no space for that trade."));
            return;
        }

        Inventory inventory = playerComponent.getInventory();
        CombinedItemContainer container = inventory.getCombinedHotbarFirst();
        int playerHas = countItemsInContainer(container, trade.inputItemId());
        if (playerHas < trade.inputQuantity()) {
            playerComponent.sendMessage(Message.raw("You don't have enough items."));
            return;
        }

        removeItemsFromContainer(container, trade.inputItemId(), trade.inputQuantity());
        removeItemsFromContainers(stockContainers, trade.outputItemId(), trade.outputQuantity());
        addItemsToContainers(stockContainers, trade.inputItemId(), trade.inputQuantity());

        ItemStack outputStack = new ItemStack(trade.outputItemId(), trade.outputQuantity());
        ItemStackTransaction transaction = container.addItemStack(outputStack);
        ItemStack remainder = transaction.getRemainder();
        if (remainder != null && !remainder.isEmpty()) {
            int addedQty = outputStack.getQuantity() - remainder.getQuantity();
            if (addedQty > 0) {
                playerComponent.notifyPickupItem(ref, outputStack.withQuantity(addedQty), null, store);
            }
            ItemUtils.dropItem(ref, remainder, store);
        } else {
            playerComponent.notifyPickupItem(ref, outputStack, null, store);
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerComponent.getPageManager().openCustomPage(ref, store, new ShopBuyerPage(playerRef, shop.ownerId(), shop.name()));
        }
    }

    @Nullable
    private Shop resolveShop() {
        ShopRegistry registry = resolveRegistry();
        if (registry == null) {
            return null;
        }
        try {
            return registry.getShop(ownerId, shopName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ShopRegistry resolveRegistry() {
        TaleShop plugin = TaleShop.getInstance();
        if (plugin == null) {
            return null;
        }
        return plugin.getShopRegistry();
    }

    private List<ItemContainer> resolveNearbyContainers(@Nonnull Store<EntityStore> store, @Nonnull Shop shop) {
        Ref<EntityStore> traderRef = resolveTraderRef(store, shop);
        if (traderRef == null || !isRefValid(traderRef)) {
            return Collections.emptyList();
        }

        TransformComponent traderTransform = store.getComponent(traderRef, TransformComponent.getComponentType());
        if (traderTransform == null) {
            return Collections.emptyList();
        }

        List<ItemContainer> containers = resolveNearbyItemContainers(traderTransform, store, traderRef);
        if (!containers.isEmpty()) {
            return containers;
        }

        List<Ref<EntityStore>> refs = resolveEntityRefs(store);
        if (refs.isEmpty()) {
            return Collections.emptyList();
        }

        int scanned = 0;
        for (Ref<EntityStore> ref : refs) {
            if (ref == null || !isRefValid(ref)) {
                continue;
            }
            if (ref.equals(traderRef)) {
                continue;
            }
            if (hasPlayerComponent(store, ref)) {
                continue;
            }
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) {
                continue;
            }
            if (!isWithinRadius(traderTransform, transform, STOCK_RADIUS_BLOCKS)) {
                continue;
            }
            ItemContainer container = resolveContainer(store, ref);
            if (container != null) {
                containers.add(container);
            }
            scanned++;
            if (scanned >= MAX_ENTITY_SCAN) {
                break;
            }
        }

        return containers;
    }

    private List<ItemContainer> resolveNearbyItemContainers(
        @Nonnull TransformComponent traderTransform,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> traderRef
    ) {
        World world = resolveWorld(store, traderRef);
        if (world == null) {
            return Collections.emptyList();
        }

        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        SpatialResource<Ref<ChunkStore>, ChunkStore> spatial = chunkStore.getResource(BlockStateModule.get().getItemContainerSpatialResourceType());
        Vector3d position = new Vector3d(
            getCoord(traderTransform.getPosition(), "getX", "x"),
            getCoord(traderTransform.getPosition(), "getY", "y"),
            getCoord(traderTransform.getPosition(), "getZ", "z")
        );
        double horizontalRadius = resolveHorizontalRadius(world);
        double verticalRadius = resolveVerticalRadius(world);

        if (spatial == null) {
            return scanBlockContainerStates(world, chunkStore, position, horizontalRadius, verticalRadius);
        }

        ObjectList<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
        results.clear();
        spatial.getSpatialStructure().ordered3DAxis(position, horizontalRadius, verticalRadius, horizontalRadius, results);
        if (results.isEmpty()) {
            return scanBlockContainerStates(world, chunkStore, position, horizontalRadius, verticalRadius);
        }

        double minX = position.x - horizontalRadius;
        double minY = position.y - verticalRadius;
        double minZ = position.z - horizontalRadius;
        double maxX = position.x + horizontalRadius;
        double maxY = position.y + verticalRadius;
        double maxZ = position.z + horizontalRadius;
        int limit = resolveChestLimit(world);

        List<ItemContainer> containers = new ArrayList<>();
        for (Ref<ChunkStore> ref : results) {
            BlockState state = BlockState.getBlockState(ref, chunkStore);
            if (state instanceof ItemContainerState containerState) {
                Vector3d chestPos = containerState.getCenteredBlockPosition();
                if (chestPos.x >= minX && chestPos.x <= maxX
                    && chestPos.y >= minY && chestPos.y <= maxY
                    && chestPos.z >= minZ && chestPos.z <= maxZ) {
                    containers.add(containerState.getItemContainer());
                    if (containers.size() >= limit) {
                        break;
                    }
                }
            }
        }

        if (containers.isEmpty()) {
            return scanBlockContainerStates(world, chunkStore, position, horizontalRadius, verticalRadius);
        }
        return containers;
    }

    private List<ItemContainer> scanBlockContainerStates(
        @Nonnull World world,
        @Nonnull Store<ChunkStore> chunkStore,
        @Nonnull Vector3d position,
        double horizontalRadius,
        double verticalRadius
    ) {
        int limit = resolveChestLimit(world);
        int originX = (int) Math.floor(position.x);
        int originY = (int) Math.floor(position.y);
        int originZ = (int) Math.floor(position.z);
        int radiusH = (int) Math.ceil(horizontalRadius);
        int radiusV = (int) Math.ceil(verticalRadius);

        List<ItemContainer> containers = new ArrayList<>();
        for (int x = originX - radiusH; x <= originX + radiusH; x++) {
            for (int y = originY - radiusV; y <= originY + radiusV; y++) {
                for (int z = originZ - radiusH; z <= originZ + radiusH; z++) {
                    Ref<ChunkStore> blockRef = BlockModule.getBlockEntity(world, x, y, z);
                    if (blockRef == null || !blockRef.isValid()) {
                        continue;
                    }
                    BlockState state = BlockState.getBlockState(blockRef, chunkStore);
                    if (state instanceof ItemContainerState containerState) {
                        containers.add(containerState.getItemContainer());
                        if (containers.size() >= limit) {
                            return containers;
                        }
                    }
                }
            }
        }

        return containers;
    }

    private double resolveHorizontalRadius(@Nonnull World world) {
        PluginConfig config = TaleShop.getInstance().getPluginConfig();
        if (config != null && config.isUsingFixedDistance()) {
            return config.getFixedStorageDistance();
        }
        try {
            return world.getGameplayConfig().getCraftingConfig().getBenchMaterialHorizontalChestSearchRadius();
        } catch (RuntimeException ignored) {
            return STOCK_RADIUS_BLOCKS;
        }
    }

    private double resolveVerticalRadius(@Nonnull World world) {
        PluginConfig config = TaleShop.getInstance().getPluginConfig();
        if (config != null && config.isUsingFixedDistance()) {
            return config.getFixedStorageDistance();
        }
        try {
            return world.getGameplayConfig().getCraftingConfig().getBenchMaterialVerticalChestSearchRadius();
        } catch (RuntimeException ignored) {
            return STOCK_RADIUS_BLOCKS;
        }
    }

    private int resolveChestLimit(@Nonnull World world) {
        try {
            int limit = world.getGameplayConfig().getCraftingConfig().getBenchMaterialChestLimit();
            return Math.max(1, limit);
        } catch (RuntimeException ignored) {
            return 64;
        }
    }

    @Nullable
    private World resolveWorld(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> traderRef) {
        EntityStore entityStore = store.getExternalData();
        if (entityStore != null) {
            World world = entityStore.getWorld();
            if (world != null) {
                return world;
            }
        }
        Object world = firstNonNull(
            tryInvokeForResult(store, "getWorld"),
            tryInvokeForResult(store, "getWorld", traderRef),
            tryInvokeForResult(traderRef, "getWorld")
        );
        if (world instanceof World typed) {
            return typed;
        }
        Object universe = firstNonNull(
            tryInvokeForResult(store, "getUniverse"),
            tryInvokeForResult(traderRef, "getUniverse")
        );
        if (universe == null) {
            return null;
        }
        Object defaultWorld = firstNonNull(
            tryInvokeForResult(universe, "getDefaultWorld"),
            tryInvokeForResult(universe, "getWorld"),
            tryInvokeForResult(universe, "getWorld", "default")
        );
        if (defaultWorld instanceof World typed) {
            return typed;
        }
        Object worldId = firstNonNull(
            tryInvokeForResult(traderRef, "getWorldId"),
            tryInvokeForResult(traderRef, "getWorldKey"),
            tryInvokeForResult(traderRef, "getWorldName")
        );
        if (worldId != null) {
            Object resolved = firstNonNull(
                tryInvokeForResult(universe, "getWorld", worldId),
                tryInvokeForResult(universe, "getWorldById", worldId),
                tryInvokeForResult(universe, "getWorldByKey", worldId),
                tryInvokeForResult(universe, "getWorldByName", worldId)
            );
            if (resolved instanceof World typed) {
                return typed;
            }
        }
        return null;
    }

    @Nullable
    private Ref<EntityStore> resolveTraderRef(@Nonnull Store<EntityStore> store, @Nonnull Shop shop) {
        String traderUuid = shop.traderUuid();
        if (traderUuid == null || traderUuid.isBlank()) {
            return null;
        }
        UUID uuid = parseUuid(traderUuid);
        if (uuid != null) {
            EntityStore entityStore = store.getExternalData();
            if (entityStore != null) {
                Ref<EntityStore> direct = entityStore.getRefFromUUID(uuid);
                if (direct != null) {
                    return direct;
                }
            }
        }
        Object ref = uuid == null
            ? tryInvokeForResult(store, "getEntityRef", traderUuid)
            : firstNonNull(
                tryInvokeForResult(store, "getEntityRef", uuid),
                tryInvokeForResult(store, "getEntity", uuid),
                tryInvokeForResult(store, "getEntityRef", traderUuid),
                tryInvokeForResult(store, "getEntity", traderUuid)
            );
        if (ref instanceof Ref<?> casted) {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> entityRef = (Ref<EntityStore>) casted;
            return entityRef;
        }
        return null;
    }

    private List<Ref<EntityStore>> resolveEntityRefs(@Nonnull Store<EntityStore> store) {
        Object result = firstNonNull(
            tryInvokeForResult(store, "getEntities"),
            tryInvokeForResult(store, "getEntityRefs"),
            tryInvokeForResult(store, "getAllEntities"),
            tryInvokeForResult(store, "getAllEntityRefs"),
            tryInvokeForResult(store, "getEntitiesView"),
            tryInvokeForResult(store, "getRefs")
        );
        if (result == null) {
            return Collections.emptyList();
        }

        List<Ref<EntityStore>> refs = new ArrayList<>();
        if (result instanceof Stream<?> stream) {
            stream.forEach(value -> addRefFromValue(refs, value));
            return refs;
        }
        if (result instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                addRefFromValue(refs, value);
            }
            return refs;
        }
        if (result.getClass().isArray()) {
            Object[] array = (Object[]) result;
            for (Object value : array) {
                addRefFromValue(refs, value);
            }
            return refs;
        }
        if (result instanceof Collection<?> collection) {
            for (Object value : collection) {
                addRefFromValue(refs, value);
            }
            return refs;
        }

        addRefFromValue(refs, result);
        return refs;
    }

    private void addRefFromValue(@Nonnull List<Ref<EntityStore>> refs, @Nullable Object value) {
        if (value instanceof Ref<?> ref) {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
            refs.add(entityRef);
            return;
        }
        if (value != null) {
            Object resolved = firstNonNull(
                tryInvokeForResult(value, "getRef"),
                tryInvokeForResult(value, "getReference"),
                tryInvokeForResult(value, "getEntityRef")
            );
            if (resolved instanceof Ref<?> ref) {
                @SuppressWarnings("unchecked")
                Ref<EntityStore> entityRef = (Ref<EntityStore>) ref;
                refs.add(entityRef);
            }
        }
    }

    private boolean hasPlayerComponent(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        return store.getComponent(ref, Player.getComponentType()) != null;
    }

    private boolean isWithinRadius(@Nonnull TransformComponent origin, @Nonnull TransformComponent other, int radiusBlocks) {
        Object originPos = origin.getPosition();
        Object otherPos = other.getPosition();
        if (originPos == null || otherPos == null) {
            return false;
        }
        double dx = getCoord(originPos, "getX", "x") - getCoord(otherPos, "getX", "x");
        double dy = getCoord(originPos, "getY", "y") - getCoord(otherPos, "getY", "y");
        double dz = getCoord(originPos, "getZ", "z") - getCoord(otherPos, "getZ", "z");
        double max = radiusBlocks + 0.5;
        return (dx * dx + dy * dy + dz * dz) <= (max * max);
    }

    private double getCoord(@Nonnull Object vector, @Nonnull String methodName, @Nonnull String fieldName) {
        Object value = tryInvokeForResult(vector, methodName);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            var field = vector.getClass().getField(fieldName);
            Object fieldValue = field.get(vector);
            if (fieldValue instanceof Number number) {
                return number.doubleValue();
            }
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
        }
        return 0.0;
    }

    @Nullable
    private ItemContainer resolveContainer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        Object direct = firstNonNull(
            tryInvokeForResult(store, "getItemContainer", ref),
            tryInvokeForResult(store, "getInventory", ref),
            tryInvokeForResult(store, "getContainer", ref)
        );
        ItemContainer resolved = extractItemContainer(direct);
        if (resolved != null) {
            return resolved;
        }

        String[] componentNames = new String[] {
            "com.hypixel.hytale.server.core.inventory.component.ItemContainerComponent",
            "com.hypixel.hytale.server.core.inventory.component.InventoryComponent",
            "com.hypixel.hytale.server.core.modules.inventory.component.ItemContainerComponent",
            "com.hypixel.hytale.server.core.modules.inventory.component.InventoryComponent",
            "com.hypixel.hytale.server.core.modules.entity.component.ItemContainerComponent",
            "com.hypixel.hytale.server.core.modules.entity.component.InventoryComponent",
            "com.hypixel.hytale.server.core.modules.item.component.ItemContainerComponent",
            "com.hypixel.hytale.server.core.modules.item.component.InventoryComponent",
            "com.hypixel.hytale.server.core.inventory.component.ContainerComponent",
            "com.hypixel.hytale.server.core.inventory.component.StorageComponent"
        };
        for (String className : componentNames) {
            Object component = resolveComponentByClassName(store, ref, className);
            ItemContainer container = extractItemContainer(component);
            if (container != null) {
                return container;
            }
        }

        return null;
    }

    @Nullable
    private Object resolveComponentByClassName(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String className) {
        Object componentType = tryLoadComponentType(className);
        if (componentType == null) {
            return null;
        }
        return tryInvokeForResult(store, "getComponent", ref, componentType);
    }

    @Nullable
    private Object tryLoadComponentType(@Nonnull String className) {
        try {
            Class<?> componentClass = Class.forName(className);
            Method method = findMethod(componentClass, "getComponentType");
            if (method == null) {
                return null;
            }
            method.setAccessible(true);
            return method.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private ItemContainer extractItemContainer(@Nullable Object source) {
        if (source == null) {
            return null;
        }
        if (source instanceof ItemContainer container) {
            return container;
        }
        if (source instanceof Inventory inventory) {
            ItemContainer combined = extractContainerFromInventory(inventory);
            if (combined != null) {
                return combined;
            }
        }
        Object nested = firstNonNull(
            tryInvokeForResult(source, "getItemContainer"),
            tryInvokeForResult(source, "getContainer"),
            tryInvokeForResult(source, "getInventory"),
            tryInvokeForResult(source, "getStorage"),
            tryInvokeForResult(source, "getItemStorage")
        );
        if (nested instanceof Inventory inventory) {
            ItemContainer combined = extractContainerFromInventory(inventory);
            if (combined != null) {
                return combined;
            }
        }
        if (nested instanceof ItemContainer container) {
            return container;
        }
        return null;
    }

    @Nullable
    private ItemContainer extractContainerFromInventory(@Nonnull Inventory inventory) {
        Object combined = firstNonNull(
            tryInvokeForResult(inventory, "getCombinedEverything"),
            tryInvokeForResult(inventory, "getCombinedStorage"),
            tryInvokeForResult(inventory, "getCombined"),
            tryInvokeForResult(inventory, "getContainer"),
            tryInvokeForResult(inventory, "getItemContainer")
        );
        if (combined instanceof ItemContainer container) {
            return container;
        }
        return null;
    }

    private int countItemsInContainers(@Nonnull List<ItemContainer> containers, @Nonnull String itemId) {
        int total = 0;
        for (ItemContainer container : containers) {
            total += countItemsInContainer(container, itemId);
        }
        return total;
    }

    private void removeItemsFromContainers(@Nonnull List<ItemContainer> containers, @Nonnull String itemId, int quantity) {
        int remaining = quantity;
        for (ItemContainer container : containers) {
            if (remaining <= 0) {
                return;
            }
            int before = countItemsInContainer(container, itemId);
            if (before <= 0) {
                continue;
            }
            int toRemove = Math.min(before, remaining);
            removeItemsFromContainer(container, itemId, toRemove);
            remaining -= toRemove;
        }
    }

    private boolean hasSpaceForItems(@Nonnull List<ItemContainer> containers, @Nonnull String itemId, int quantity) {
        if (quantity <= 0) {
            return true;
        }
        int maxStack = resolveMaxStackSize(itemId);
        int remaining = quantity;
        for (ItemContainer container : containers) {
            if (remaining <= 0) {
                return true;
            }
            remaining -= estimateFreeSpace(container, itemId, maxStack);
        }
        return remaining <= 0;
    }

    private int estimateFreeSpace(@Nonnull ItemContainer container, @Nonnull String itemId, int maxStack) {
        int free = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack itemStack = container.getItemStack(slot);
            if (ItemStack.isEmpty(itemStack)) {
                free += maxStack;
                continue;
            }
            if (itemId.equals(itemStack.getItemId())) {
                free += Math.max(0, maxStack - itemStack.getQuantity());
            }
        }
        return free;
    }

    private void addItemsToContainers(@Nonnull List<ItemContainer> containers, @Nonnull String itemId, int quantity) {
        int remaining = quantity;
        for (ItemContainer container : containers) {
            if (remaining <= 0) {
                return;
            }
            ItemStack stack = new ItemStack(itemId, remaining);
            ItemStackTransaction transaction;
            try {
                transaction = container.addItemStack(stack);
            } catch (RuntimeException ex) {
                Object result = tryInvokeForResult(container, "addItemStack", stack);
                if (result instanceof ItemStackTransaction tx) {
                    transaction = tx;
                } else {
                    continue;
                }
            }
            ItemStack remainder = transaction.getRemainder();
            remaining = remainder == null ? 0 : remainder.getQuantity();
        }
    }

    private int resolveMaxStackSize(@Nonnull String itemId) {
        try {
            ItemStack stack = new ItemStack(itemId, 1);
            Object value = firstNonNull(
                tryInvokeForResult(stack, "getMaxStackSize"),
                tryInvokeForResult(stack, "getMaxStack"),
                tryInvokeForResult(stack, "getMaxQuantity"),
                tryInvokeForResult(stack, "getMaxAmount")
            );
            if (value instanceof Number number) {
                int max = number.intValue();
                return max > 0 ? max : 64;
            }
        } catch (RuntimeException ignored) {
        }
        return 64;
    }

    private boolean isRefValid(@Nonnull Ref<EntityStore> ref) {
        Object value = tryInvokeForResult(ref, "isValid");
        if (value instanceof Boolean valid) {
            return valid;
        }
        return true;
    }

    @Nullable
    private UUID parseUuid(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nullable
    private Object tryInvokeForResult(@Nonnull Object target, @Nonnull String methodName, Object... args) {
        Method method = findMethodByArgs(target.getClass(), methodName, args);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private Method findMethodByArgs(@Nonnull Class<?> type, @Nonnull String name, Object... args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != args.length) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < params.length; i++) {
                    Object arg = args[i];
                    if (arg == null) {
                        continue;
                    }
                    if (!params[i].isAssignableFrom(arg.getClass())
                        && !(params[i].isPrimitive() && isWrapper(params[i], arg.getClass()))) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @Nullable
    private Method findMethod(@Nonnull Class<?> type, @Nonnull String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private boolean isWrapper(@Nonnull Class<?> primitiveType, @Nonnull Class<?> wrapperType) {
        return (primitiveType == boolean.class && wrapperType == Boolean.class)
            || (primitiveType == int.class && wrapperType == Integer.class)
            || (primitiveType == long.class && wrapperType == Long.class)
            || (primitiveType == double.class && wrapperType == Double.class)
            || (primitiveType == float.class && wrapperType == Float.class)
            || (primitiveType == short.class && wrapperType == Short.class)
            || (primitiveType == byte.class && wrapperType == Byte.class)
            || (primitiveType == char.class && wrapperType == Character.class);
    }

    @Nullable
    private Object firstNonNull(@Nullable Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private int countItemsInContainer(@Nonnull ItemContainer container, @Nonnull String itemId) {
        int count = 0;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack itemStack = container.getItemStack(slot);
            if (!ItemStack.isEmpty(itemStack) && itemId.equals(itemStack.getItemId())) {
                count += itemStack.getQuantity();
            }
        }
        return count;
    }

    private void removeItemsFromContainer(@Nonnull ItemContainer container, @Nonnull String itemId, int quantity) {
        int remaining = quantity;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            if (remaining <= 0) {
                return;
            }
            ItemStack itemStack = container.getItemStack(slot);
            if (ItemStack.isEmpty(itemStack) || !itemId.equals(itemStack.getItemId())) {
                continue;
            }
            int toRemove = Math.min(remaining, itemStack.getQuantity());
            container.removeItemStackFromSlot(slot, itemStack, toRemove);
            remaining -= toRemove;
        }
    }

    public static class ShopBuyerEventData {
        public static final com.hypixel.hytale.codec.builder.BuilderCodec<ShopBuyerEventData> CODEC = com.hypixel.hytale.codec.builder.BuilderCodec.builder(
                ShopBuyerEventData.class, ShopBuyerEventData::new
        )
            .append(new com.hypixel.hytale.codec.KeyedCodec<>("TradeIndex", com.hypixel.hytale.codec.Codec.STRING), (entry, s) -> entry.tradeIndex = Integer.parseInt(s), entry -> String.valueOf(entry.tradeIndex))
            .add()
            .build();

        private int tradeIndex = -1;

        public ShopBuyerEventData() {
        }
    }
}
