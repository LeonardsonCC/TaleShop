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
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ShopBuyerPage extends InteractiveCustomUIPage<ShopBuyerPage.ShopBuyerEventData> {
    private static final int STOCK_RADIUS_BLOCKS = 2;
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
        if (traderRef == null || !traderRef.isValid()) {
            return Collections.emptyList();
        }

        TransformComponent traderTransform = store.getComponent(traderRef, TransformComponent.getComponentType());
        if (traderTransform == null) {
            return Collections.emptyList();
        }

        return resolveNearbyItemContainers(traderTransform, store, traderRef);
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
        Vector3d position = new Vector3d(traderTransform.getPosition());
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
        return entityStore == null ? null : entityStore.getWorld();
    }

    @Nullable
    private Ref<EntityStore> resolveTraderRef(@Nonnull Store<EntityStore> store, @Nonnull Shop shop) {
        String traderUuid = shop.traderUuid();
        if (traderUuid == null || traderUuid.isBlank()) {
            return null;
        }
        UUID uuid = parseUuid(traderUuid);
        if (uuid == null) {
            return null;
        }
        EntityStore entityStore = store.getExternalData();
        return entityStore == null ? null : entityStore.getRefFromUUID(uuid);
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
            ItemStackTransaction transaction = container.addItemStack(stack);
            ItemStack remainder = transaction.getRemainder();
            remaining = remainder == null ? 0 : remainder.getQuantity();
        }
    }

    private int resolveMaxStackSize(@Nonnull String itemId) {
        try {
            ItemStack stack = new ItemStack(itemId, 1);
            int max = stack.getMaxStackSize();
            return max > 0 ? max : 64;
        } catch (RuntimeException ignored) {
        }
        return 64;
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
