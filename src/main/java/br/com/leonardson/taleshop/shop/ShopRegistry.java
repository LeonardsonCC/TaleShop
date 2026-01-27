package br.com.leonardson.taleshop.shop;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import br.com.leonardson.taleshop.shop.trade.Trade;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ShopRegistry {
    public static final int MAX_TRADES = 20;
    private static final String SHOP_PREFIX = "shop.";
    private static final String TRADE_PREFIX = "trade.";

    private final Path dataDirectory;
    private final Path dataFile;
    private final Map<String, Map<String, MutableShop>> shopsByOwner = new LinkedHashMap<>();

    public ShopRegistry(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.dataFile = dataDirectory.resolve("shops.properties");
        load();
    }

    @Nonnull
    public static Path resolveDataDirectory(@Nonnull JavaPlugin plugin) {
        Object result = invokeFirst(plugin, "getDataFolder", "getDataDirectory", "getDataPath");
        if (result instanceof Path path) {
            return path;
        }
        if (result instanceof java.io.File file) {
            return file.toPath();
        }
        return Paths.get(System.getProperty("user.dir"), "run", "mods", plugin.getName());
    }

    @Nonnull
    public synchronized Shop createShop(@Nonnull String ownerId, @Nonnull String ownerName, @Nonnull String name) {
        if (ownerId.isBlank()) {
            throw new IllegalArgumentException("Owner id is required.");
        }
        String trimmedName = name.trim();
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("Shop name is required.");
        }

        Map<String, MutableShop> ownerShops = shopsByOwner.computeIfAbsent(ownerId, id -> new TreeMap<>());
        String normalizedName = normalizeName(trimmedName);
        if (ownerShops.containsKey(normalizedName)) {
            // throw new IllegalArgumentException("You already have a shop named '" + trimmedName + "'.");
        }

        MutableShop shop = new MutableShop(ownerId, ownerName, trimmedName);
        ownerShops.put(normalizedName, shop);
        save();
        return shop.toShop();
    }

    @Nonnull
    public synchronized Shop renameShop(@Nonnull String ownerId, @Nonnull String currentName, @Nonnull String newName) {
        String trimmedCurrent = currentName.trim();
        String trimmedNew = newName.trim();
        if (trimmedCurrent.isBlank() || trimmedNew.isBlank()) {
            throw new IllegalArgumentException("Shop name is required.");
        }

        MutableShop shop = getMutableShop(ownerId, trimmedCurrent);
        Map<String, MutableShop> ownerShops = shopsByOwner.computeIfAbsent(ownerId, id -> new TreeMap<>());
        String currentKey = normalizeName(trimmedCurrent);
        String newKey = normalizeName(trimmedNew);
        if (!currentKey.equals(newKey) && ownerShops.containsKey(newKey)) {
            throw new IllegalArgumentException("You already have a shop named '" + trimmedNew + "'.");
        }

        ownerShops.remove(currentKey);
        shop.name = trimmedNew;
        ownerShops.put(newKey, shop);
        save();
        return shop.toShop();
    }

    public synchronized void deleteShop(@Nonnull String ownerId, @Nonnull String name) {
        Map<String, MutableShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null || ownerShops.remove(normalizeName(name)) == null) {
            throw new IllegalArgumentException("Shop not found: " + name);
        }
        if (ownerShops.isEmpty()) {
            shopsByOwner.remove(ownerId);
        }
        save();
    }

    @Nonnull
    public synchronized Shop getShop(@Nonnull String ownerId, @Nonnull String name) {
        return getMutableShop(ownerId, name).toShop();
    }

    @Nonnull
    public synchronized String getTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        return getMutableShop(ownerId, name).traderUuid;
    }

    public synchronized void setTraderUuid(@Nonnull String ownerId, @Nonnull String name, @Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            throw new IllegalArgumentException("Trader uuid is required.");
        }
        MutableShop shop = getMutableShop(ownerId, name);
        shop.traderUuid = traderUuid;
        save();
    }

    @Nonnull
    public synchronized List<Shop> listShops(@Nonnull String ownerId) {
        List<Shop> list = new ArrayList<>();
        Map<String, MutableShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null) {
            return list;
        }
        for (MutableShop shop : ownerShops.values()) {
            list.add(shop.toShop());
        }
        list.sort(Comparator.comparing(Shop::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Nonnull
    public synchronized List<Shop> listAllShops() {
        List<Shop> list = new ArrayList<>();
        for (Map<String, MutableShop> ownerShops : shopsByOwner.values()) {
            for (MutableShop shop : ownerShops.values()) {
                list.add(shop.toShop());
            }
        }
        list.sort(Comparator.comparing(Shop::name, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    @Nullable
    public synchronized Shop findShopByTraderUuid(@Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            return null;
        }
        for (Map<String, MutableShop> ownerShops : shopsByOwner.values()) {
            for (MutableShop shop : ownerShops.values()) {
                if (traderUuid.equals(shop.traderUuid)) {
                    return shop.toShop();
                }
            }
        }
        return null;
    }

    @Nonnull
    public synchronized Trade addTrade(
        @Nonnull String ownerId,
        @Nonnull String shopName,
        @Nonnull String inputItemId,
        int inputQuantity,
        @Nonnull String outputItemId,
        int outputQuantity
    ) {
        MutableShop shop = getMutableShop(ownerId, shopName);
        if (shop.trades.size() >= MAX_TRADES) {
            throw new IllegalArgumentException("Shop already has the maximum of " + MAX_TRADES + " trades.");
        }

        validateItem(inputItemId, "Input item");
        validateItem(outputItemId, "Output item");
        validateQuantity(inputQuantity);
        validateQuantity(outputQuantity);

        int tradeId = shop.nextTradeId++;
        Trade trade = new Trade(tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity);
        shop.trades.put(tradeId, trade);
        save();
        return trade;
    }

    public synchronized void updateTrade(
        @Nonnull String ownerId,
        @Nonnull String shopName,
        int tradeId,
        @Nonnull String inputItemId,
        int inputQuantity,
        @Nonnull String outputItemId,
        int outputQuantity
    ) {
        MutableShop shop = getMutableShop(ownerId, shopName);
        if (!shop.trades.containsKey(tradeId)) {
            throw new IllegalArgumentException("Trade not found: " + tradeId);
        }

        validateItem(inputItemId, "Input item");
        validateItem(outputItemId, "Output item");
        validateQuantity(inputQuantity);
        validateQuantity(outputQuantity);

        shop.trades.put(tradeId, new Trade(tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity));
        save();
    }

    public synchronized void removeTrade(@Nonnull String ownerId, @Nonnull String shopName, int tradeId) {
        MutableShop shop = getMutableShop(ownerId, shopName);
        if (shop.trades.remove(tradeId) == null) {
            throw new IllegalArgumentException("Trade not found: " + tradeId);
        }
        save();
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

    private MutableShop getMutableShop(String ownerId, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Shop name is required.");
        }
        Map<String, MutableShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null) {
            throw new IllegalArgumentException("Shop not found: " + name);
        }
        MutableShop shop = ownerShops.get(normalizeName(name));
        if (shop == null) {
            throw new IllegalArgumentException("Shop not found: " + name);
        }
        return shop;
    }

    private void load() {
        shopsByOwner.clear();
        Properties props = new Properties();
        if (!Files.exists(dataFile)) {
            return;
        }

        try (InputStream inputStream = Files.newInputStream(dataFile)) {
            props.load(inputStream);
        } catch (IOException ignored) {
            return;
        }

        Map<String, TradeBuilder> tradeBuilders = new LinkedHashMap<>();
        Map<String, String> legacyShopNames = new LinkedHashMap<>();
        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            if (key.startsWith(SHOP_PREFIX)) {
                handleShopProperty(key, value, legacyShopNames);
            } else if (key.startsWith(TRADE_PREFIX)) {
                parseTradeProperty(key, value, tradeBuilders, legacyShopNames);
            }
        }

        for (TradeBuilder builder : tradeBuilders.values()) {
            if (!builder.isComplete()) {
                continue;
            }
            MutableShop shop = getLoadedShop(builder.ownerId, builder.shopName);
            if (shop == null) {
                continue;
            }
            Trade trade = builder.build();
            shop.trades.put(trade.id(), trade);
            shop.nextTradeId = Math.max(shop.nextTradeId, trade.id() + 1);
        }
    }

    private void handleShopProperty(String key, String value, Map<String, String> legacyShopNames) {
        String remainder = key.substring(SHOP_PREFIX.length());
        String[] parts = remainder.split("\\.");
        if (parts.length == 2) {
            String ownerId = parts[0];
            String field = parts[1];
            if ("name".equals(field) && value != null && !value.isBlank()) {
                legacyShopNames.put(ownerId, value);
                getOrCreateLoadedShop(ownerId, value);
            } else if ("owner".equals(field)) {
                String shopName = legacyShopNames.getOrDefault(ownerId, "Shop");
                MutableShop shop = getOrCreateLoadedShop(ownerId, shopName);
                if (shop != null) {
                    shop.ownerName = value == null ? "" : value;
                }
            }
            return;
        }

        if (parts.length < 3) {
            return;
        }
        String ownerId = parts[0];
        String nameKey = parts[1];
        String field = parts[2];
        String decodedName = decodeName(nameKey);
        if (decodedName == null) {
            return;
        }

        MutableShop shop = getOrCreateLoadedShop(ownerId, decodedName);
        if (shop == null) {
            return;
        }
        if ("owner".equals(field)) {
            shop.ownerName = value == null ? "" : value;
        } else if ("name".equals(field) && value != null && !value.isBlank()) {
            shop.name = value;
        } else if ("traderUuid".equals(field)) {
            shop.traderUuid = value == null ? "" : value;
        }
    }

    private void parseTradeProperty(String key, String value, Map<String, TradeBuilder> tradeBuilders, Map<String, String> legacyShopNames) {
        String remainder = key.substring(TRADE_PREFIX.length());
        String[] parts = remainder.split("\\.");
        if (parts.length == 3) {
            String ownerId = parts[0];
            String tradeIdRaw = parts[1];
            String field = parts[2];
            String shopName = legacyShopNames.getOrDefault(ownerId, "Shop");
            parseTradePropertyV2(ownerId, shopName, tradeIdRaw, field, value, tradeBuilders);
            return;
        }

        if (parts.length < 4) {
            return;
        }
        String ownerId = parts[0];
        String nameKey = parts[1];
        String tradeIdRaw = parts[2];
        String field = parts[3];
        String shopName = decodeName(nameKey);
        if (shopName == null) {
            return;
        }

        parseTradePropertyV2(ownerId, shopName, tradeIdRaw, field, value, tradeBuilders);
    }

    private void parseTradePropertyV2(
        String ownerId,
        String shopName,
        String tradeIdRaw,
        String field,
        String value,
        Map<String, TradeBuilder> tradeBuilders
    ) {
        getOrCreateLoadedShop(ownerId, shopName);
        int tradeId;
        try {
            tradeId = Integer.parseInt(tradeIdRaw);
        } catch (NumberFormatException ex) {
            return;
        }

        String builderKey = ownerId + "." + normalizeName(shopName) + "." + tradeId;
        TradeBuilder builder = tradeBuilders.computeIfAbsent(
            builderKey,
            keyName -> new TradeBuilder(ownerId, shopName, tradeId, builderKey)
        );
        if ("input".equals(field)) {
            builder.inputItemId = value;
        } else if ("inputQty".equals(field)) {
            builder.inputQuantity = parseInt(value);
        } else if ("output".equals(field)) {
            builder.outputItemId = value;
        } else if ("outputQty".equals(field)) {
            builder.outputQuantity = parseInt(value);
        }
    }

    private int parseInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void save() {
        Properties props = new Properties();
        for (Map<String, MutableShop> ownerShops : shopsByOwner.values()) {
            for (MutableShop shop : ownerShops.values()) {
                String nameKey = encodeName(shop.name);
                props.setProperty(SHOP_PREFIX + shop.ownerId + "." + nameKey + ".name", shop.name);
                props.setProperty(SHOP_PREFIX + shop.ownerId + "." + nameKey + ".owner", shop.ownerName);
                if (shop.traderUuid != null && !shop.traderUuid.isBlank()) {
                    props.setProperty(SHOP_PREFIX + shop.ownerId + "." + nameKey + ".traderUuid", shop.traderUuid);
                }
                for (Trade trade : shop.trades.values()) {
                    String base = TRADE_PREFIX + shop.ownerId + "." + nameKey + "." + trade.id() + ".";
                    props.setProperty(base + "input", trade.inputItemId());
                    props.setProperty(base + "inputQty", String.valueOf(trade.inputQuantity()));
                    props.setProperty(base + "output", trade.outputItemId());
                    props.setProperty(base + "outputQty", String.valueOf(trade.outputQuantity()));
                }
            }
        }

        try {
            Files.createDirectories(dataDirectory);
            try (OutputStream outputStream = Files.newOutputStream(dataFile)) {
                props.store(outputStream, "TaleShop registry");
            }
        } catch (IOException ignored) {
        }
    }

    private static String normalizeName(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static String encodeName(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeName(String encoded) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private MutableShop getOrCreateLoadedShop(String ownerId, String shopName) {
        if (shopName == null || shopName.isBlank()) {
            return null;
        }
        Map<String, MutableShop> ownerShops = shopsByOwner.computeIfAbsent(ownerId, id -> new TreeMap<>());
        String normalized = normalizeName(shopName);
        return ownerShops.computeIfAbsent(normalized, key -> new MutableShop(ownerId, "", shopName));
    }

    private MutableShop getLoadedShop(String ownerId, String shopName) {
        Map<String, MutableShop> ownerShops = shopsByOwner.get(ownerId);
        if (ownerShops == null) {
            return null;
        }
        return ownerShops.get(normalizeName(shopName));
    }

    private static Object invokeFirst(Object target, String... methodNames) {
        for (String methodName : methodNames) {
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                continue;
            }
            try {
                method.setAccessible(true);
                return method.invoke(target);
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
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

    private static final class MutableShop {
        private final String ownerId;
        private String ownerName;
        private String name;
        private String traderUuid = "";
        private final Map<Integer, Trade> trades = new TreeMap<>();
        private int nextTradeId = 1;

        private MutableShop(String ownerId, String ownerName, String name) {
            this.ownerId = ownerId;
            this.ownerName = ownerName == null ? "" : ownerName;
            this.name = name == null ? "" : name;
        }

        private Shop toShop() {
            return new Shop(ownerId, ownerName, name, new ArrayList<>(trades.values()), traderUuid);
        }
    }

    private static final class TradeBuilder {
        private final String ownerId;
        private final String shopName;
        private final int tradeId;
        private final String key;
        private String inputItemId;
        private int inputQuantity;
        private String outputItemId;
        private int outputQuantity;

        private TradeBuilder(String ownerId, String shopName, int tradeId, String key) {
            this.ownerId = ownerId;
            this.shopName = shopName;
            this.tradeId = tradeId;
            this.key = key;
        }

        private boolean isComplete() {
            return inputItemId != null && !inputItemId.isBlank()
                && outputItemId != null && !outputItemId.isBlank()
                && inputQuantity > 0
                && outputQuantity > 0;
        }

        private Trade build() {
            return new Trade(tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity);
        }
    }
}
