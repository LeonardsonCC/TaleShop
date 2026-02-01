package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.config.PluginConfig;
import br.com.leonardson.taleshop.shop.trade.Trade;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ShopRegistry {
    public static final int MAX_TRADES = 20;

    private final ShopStorage storage;

    public ShopRegistry(@Nonnull Path dataDirectory) {
        this(dataDirectory, new PluginConfig());
    }

    public ShopRegistry(@Nonnull Path dataDirectory, @Nonnull PluginConfig config) {
        if (config.isUsingSqliteStorage()) {
            this.storage = new SqliteShopStorage(dataDirectory);
        } else {
            this.storage = new JsonShopStorage(dataDirectory);
        }
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
        return storage.createShop(ownerId, ownerName, name);
    }

    @Nonnull
    public synchronized Shop renameShop(@Nonnull String ownerId, @Nonnull String currentName, @Nonnull String newName) {
        return storage.renameShop(ownerId, currentName, newName);
    }

    public synchronized void deleteShop(@Nonnull String ownerId, @Nonnull String name) {
        storage.deleteShop(ownerId, name);
    }

    @Nonnull
    public synchronized Shop getShop(@Nonnull String ownerId, @Nonnull String name) {
        return storage.getShop(ownerId, name);
    }

    @Nonnull
    public synchronized String getTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        return storage.getTraderUuid(ownerId, name);
    }

    public synchronized void setTraderUuid(@Nonnull String ownerId, @Nonnull String name, @Nonnull String traderUuid) {
        storage.setTraderUuid(ownerId, name, traderUuid);
    }

    public synchronized void clearTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        storage.clearTraderUuid(ownerId, name);
    }

    @Nonnull
    public synchronized List<Shop> listShops(@Nonnull String ownerId) {
        return storage.listShops(ownerId);
    }

    @Nonnull
    public synchronized List<Shop> listAllShops() {
        return storage.listAllShops();
    }

    @Nullable
    public synchronized Shop findShopByTraderUuid(@Nonnull String traderUuid) {
        return storage.findShopByTraderUuid(traderUuid);
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
        return storage.addTrade(ownerId, shopName, inputItemId, inputQuantity, outputItemId, outputQuantity);
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
        storage.updateTrade(ownerId, shopName, tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity);
    }

    public synchronized void removeTrade(@Nonnull String ownerId, @Nonnull String shopName, int tradeId) {
        storage.removeTrade(ownerId, shopName, tradeId);
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

    public synchronized void close() {
        storage.close();
    }
}
