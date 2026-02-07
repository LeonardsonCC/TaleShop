package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.shop.trade.Trade;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class JsonShopStorage implements ShopStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataDirectory;
    private final Path storageFile;
    private final Map<String, Map<String, JsonShop>> shopsByOwner = new HashMap<>();

    public JsonShopStorage(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.storageFile = dataDirectory.resolve("shops.json");
        load();
    }

    private void load() {
        if (!Files.exists(storageFile)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(storageFile)) {
            JsonData data = GSON.fromJson(reader, JsonData.class);
            if (data == null || data.shops == null) {
                return;
            }
            for (JsonShop shop : data.shops) {
                if (shop == null || shop.ownerId == null || shop.name == null) {
                    continue;
                }
                String ownerId = shop.ownerId;
                String shopKey = normalizeName(shop.name);
                shopsByOwner.computeIfAbsent(ownerId, key -> new HashMap<>())
                    .put(shopKey, shop);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load JSON storage", e);
        }
    }

    private void save() {
        try {
            Files.createDirectories(dataDirectory);
            JsonData data = new JsonData();
            for (Map<String, JsonShop> ownerShops : shopsByOwner.values()) {
                data.shops.addAll(ownerShops.values());
            }
            try (Writer writer = Files.newBufferedWriter(storageFile)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save JSON storage", e);
        }
    }

    @Nonnull
    @Override
    public synchronized Shop createShop(
        @Nonnull String ownerId,
        @Nonnull String ownerName,
        @Nonnull String name,
        boolean isAdmin
    ) {
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner id is required.");
        }
        String trimmedName = name.trim();
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("Shop name is required.");
        }

        String shopKey = normalizeName(trimmedName);
        Map<String, JsonShop> ownerShops = shopsByOwner.computeIfAbsent(ownerId, key -> new HashMap<>());
        JsonShop existing = ownerShops.get(shopKey);
        if (existing != null) {
            return toShop(existing);
        }

        JsonShop shop = new JsonShop();
        shop.ownerId = ownerId;
        shop.ownerName = ownerName;
        shop.name = trimmedName;
        shop.traderUuid = "";
        shop.admin = isAdmin;
        shop.trades = new ArrayList<>();
        ownerShops.put(shopKey, shop);
        save();

        return toShop(shop);
    }

    @Nonnull
    @Override
    public synchronized Shop renameShop(@Nonnull String ownerId, @Nonnull String currentName, @Nonnull String newName) {
        String trimmedCurrent = currentName.trim();
        String trimmedNew = newName.trim();
        if (trimmedCurrent.isBlank() || trimmedNew.isBlank()) {
            throw new IllegalArgumentException("Shop name is required.");
        }

        String currentKey = normalizeName(trimmedCurrent);
        String newKey = normalizeName(trimmedNew);

        Map<String, JsonShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null || !ownerShops.containsKey(currentKey)) {
            throw new IllegalArgumentException("Shop not found: " + currentName);
        }

        if (!currentKey.equals(newKey) && ownerShops.containsKey(newKey)) {
            throw new IllegalArgumentException("You already have a shop named '" + trimmedNew + "'.");
        }

        JsonShop shop = ownerShops.remove(currentKey);
        shop.name = trimmedNew;
        ownerShops.put(newKey, shop);
        save();

        return toShop(shop);
    }

    @Override
    public synchronized void deleteShop(@Nonnull String ownerId, @Nonnull String name) {
        Map<String, JsonShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null || ownerShops.remove(normalizeName(name)) == null) {
            throw new IllegalArgumentException("Shop not found: " + name);
        }
        if (ownerShops.isEmpty()) {
            shopsByOwner.remove(ownerId);
        }
        save();
    }

    @Nonnull
    @Override
    public synchronized Shop getShop(@Nonnull String ownerId, @Nonnull String name) {
        JsonShop shop = getShopInternal(ownerId, name);
        return toShop(shop);
    }

    @Nonnull
    @Override
    public synchronized String getTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        JsonShop shop = getShopInternal(ownerId, name);
        return shop.traderUuid == null ? "" : shop.traderUuid;
    }

    @Override
    public synchronized void setTraderUuid(@Nonnull String ownerId, @Nonnull String name, @Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            throw new IllegalArgumentException("Trader uuid is required.");
        }

        JsonShop shop = getShopInternal(ownerId, name);
        shop.traderUuid = traderUuid;
        save();
    }

    @Override
    public synchronized void clearTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        JsonShop shop = getShopInternal(ownerId, name);
        shop.traderUuid = "";
        save();
    }

    @Nonnull
    @Override
    public synchronized List<Shop> listShops(@Nonnull String ownerId) {
        Map<String, JsonShop> ownerShops = shopsByOwner.getOrDefault(ownerId, Map.of());
        List<Shop> shops = new ArrayList<>();
        for (JsonShop shop : ownerShops.values()) {
            shops.add(toShop(shop));
        }
        shops.sort(Comparator.comparing(Shop::name, String.CASE_INSENSITIVE_ORDER));
        return shops;
    }

    @Nonnull
    @Override
    public synchronized List<Shop> listAllShops() {
        List<Shop> shops = new ArrayList<>();
        for (Map<String, JsonShop> ownerShops : shopsByOwner.values()) {
            for (JsonShop shop : ownerShops.values()) {
                shops.add(toShop(shop));
            }
        }
        shops.sort(Comparator.comparing(Shop::name, String.CASE_INSENSITIVE_ORDER));
        return shops;
    }

    @Nullable
    @Override
    public synchronized Shop findShopByTraderUuid(@Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            return null;
        }
        for (Map<String, JsonShop> ownerShops : shopsByOwner.values()) {
            for (JsonShop shop : ownerShops.values()) {
                if (traderUuid.equals(shop.traderUuid)) {
                    return toShop(shop);
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public synchronized Trade addTrade(
        @Nonnull String ownerId,
        @Nonnull String shopName,
        @Nonnull String inputItemId,
        int inputQuantity,
        @Nonnull String outputItemId,
        int outputQuantity
    ) {
        validateItem(inputItemId, "Input item");
        validateItem(outputItemId, "Output item");
        validateQuantity(inputQuantity);
        validateQuantity(outputQuantity);

        JsonShop shop = getShopInternal(ownerId, shopName);
        if (shop.trades.size() >= ShopRegistry.MAX_TRADES) {
            throw new IllegalArgumentException("Shop already has the maximum of " + ShopRegistry.MAX_TRADES + " trades.");
        }

        int tradeId = nextTradeId(shop.trades);
        JsonTrade trade = new JsonTrade();
        trade.id = tradeId;
        trade.inputItemId = inputItemId;
        trade.inputQuantity = inputQuantity;
        trade.outputItemId = outputItemId;
        trade.outputQuantity = outputQuantity;
        shop.trades.add(trade);
        save();

        return new Trade(tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity);
    }

    @Override
    public synchronized void updateTrade(
        @Nonnull String ownerId,
        @Nonnull String shopName,
        int tradeId,
        @Nonnull String inputItemId,
        int inputQuantity,
        @Nonnull String outputItemId,
        int outputQuantity
    ) {
        validateItem(inputItemId, "Input item");
        validateItem(outputItemId, "Output item");
        validateQuantity(inputQuantity);
        validateQuantity(outputQuantity);

        JsonShop shop = getShopInternal(ownerId, shopName);
        JsonTrade trade = findTrade(shop, tradeId);
        if (trade == null) {
            throw new IllegalArgumentException("Trade not found: " + tradeId);
        }
        trade.inputItemId = inputItemId;
        trade.inputQuantity = inputQuantity;
        trade.outputItemId = outputItemId;
        trade.outputQuantity = outputQuantity;
        save();
    }

    @Override
    public synchronized void removeTrade(@Nonnull String ownerId, @Nonnull String shopName, int tradeId) {
        JsonShop shop = getShopInternal(ownerId, shopName);
        JsonTrade trade = findTrade(shop, tradeId);
        if (trade == null) {
            throw new IllegalArgumentException("Trade not found: " + tradeId);
        }
        shop.trades.remove(trade);
        save();
    }

    private JsonShop getShopInternal(String ownerId, String name) {
        Map<String, JsonShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null) {
            throw new IllegalArgumentException("Shop not found: " + name);
        }
        JsonShop shop = ownerShops.get(normalizeName(name));
        if (shop == null) {
            throw new IllegalArgumentException("Shop not found: " + name);
        }
        if (shop.trades == null) {
            shop.trades = new ArrayList<>();
        }
        return shop;
    }

    private Shop toShop(JsonShop shop) {
        List<Trade> trades = new ArrayList<>();
        if (shop.trades != null) {
            for (JsonTrade trade : shop.trades) {
                if (trade == null) continue;
                trades.add(new Trade(trade.id, trade.inputItemId, trade.inputQuantity, trade.outputItemId, trade.outputQuantity));
            }
        }
        trades.sort(Comparator.comparingInt(Trade::id));
        return new Shop(
            shop.ownerId,
            shop.ownerName,
            shop.name,
            trades,
            shop.traderUuid == null ? "" : shop.traderUuid,
            shop.admin
        );
    }

    private int nextTradeId(List<JsonTrade> trades) {
        int maxId = 0;
        for (JsonTrade trade : trades) {
            if (trade != null && trade.id > maxId) {
                maxId = trade.id;
            }
        }
        return maxId + 1;
    }

    private JsonTrade findTrade(JsonShop shop, int tradeId) {
        for (JsonTrade trade : shop.trades) {
            if (trade != null && trade.id == tradeId) {
                return trade;
            }
        }
        return null;
    }

    private void validateItem(String itemId, String label) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException(label + " id is required.");
        }
    }

    private void validateQuantity(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than 0.");
        }
    }

    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void close() {
    }

    private static class JsonData {
        List<JsonShop> shops = new ArrayList<>();
    }

    private static class JsonShop {
        String ownerId;
        String ownerName;
        String name;
        String traderUuid;
        boolean admin;
        List<JsonTrade> trades = new ArrayList<>();
    }

    private static class JsonTrade {
        int id;
        String inputItemId;
        int inputQuantity;
        String outputItemId;
        int outputQuantity;
    }
}
