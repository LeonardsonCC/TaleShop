package br.com.leonardson.shop;

import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShopNpcRegistry {
    public record ShopLink(long shopId, String ownerId, String shopName) {}
    private record PendingEdit(long shopId, List<ShopTrade> trades) {}

    private final Map<UUID, ShopLink> npcToShop = new ConcurrentHashMap<>();
    private final Map<Long, SimpleItemContainer> shopContainers = new ConcurrentHashMap<>();
    private final Map<Long, List<ShopTrade>> shopTrades = new ConcurrentHashMap<>();
    private final Map<UUID, PendingEdit> pendingEdits = new ConcurrentHashMap<>();

    public void registerNpc(UUID npcUuid, Shop shop) {
        if (npcUuid == null || shop == null) {
            return;
        }
        npcToShop.put(npcUuid, new ShopLink(shop.getId(), shop.getPlayerId(), shop.getShopName()));
    }

    public ShopLink getShopByNpc(UUID npcUuid) {
        return npcUuid == null ? null : npcToShop.get(npcUuid);
    }

    public SimpleItemContainer getOrCreateContainer(long shopId) {
        return shopContainers.computeIfAbsent(shopId, id -> new SimpleItemContainer((short) 2));
    }

    public List<ShopTrade> getSavedTrades(long shopId) {
        return shopTrades.getOrDefault(shopId, List.of());
    }

    public List<ShopTrade> beginEdit(long shopId, UUID playerId) {
        List<ShopTrade> base = new ArrayList<>(getSavedTrades(shopId));
        PendingEdit edit = new PendingEdit(shopId, new CopyOnWriteArrayList<>(base));
        pendingEdits.put(playerId, edit);
        return edit.trades;
    }

    public List<ShopTrade> getPendingTrades(UUID playerId) {
        PendingEdit edit = pendingEdits.get(playerId);
        return edit == null ? null : edit.trades;
    }

    public void addPendingTrade(UUID playerId, ShopTrade trade) {
        PendingEdit edit = pendingEdits.get(playerId);
        if (edit != null && trade != null) {
            edit.trades.add(trade);
        }
    }

    public void discardPending(UUID playerId) {
        if (playerId != null) {
            pendingEdits.remove(playerId);
        }
    }

    public void savePending(UUID playerId) {
        if (playerId == null) {
            return;
        }
        PendingEdit edit = pendingEdits.remove(playerId);
        if (edit != null) {
            shopTrades.put(edit.shopId, new CopyOnWriteArrayList<>(edit.trades));
        }
    }
}
