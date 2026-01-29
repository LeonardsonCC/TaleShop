package br.com.leonardson.taleshop.shop;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import br.com.leonardson.taleshop.shop.trade.Trade;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ShopRegistry {
    public static final int MAX_TRADES = 20;

    private final Path dataDirectory;
    private final Path databaseFile;
    private Connection connection;

    public ShopRegistry(@Nonnull Path dataDirectory) {
        this.dataDirectory = dataDirectory;
        this.databaseFile = dataDirectory.resolve("shops.db");
        initializeDatabase();
        migrateFromPropertiesIfNeeded();
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

    private void initializeDatabase() {
        try {
            Files.createDirectories(dataDirectory);
            String url = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            
            // Enable foreign keys
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
            }
            
            createTables();
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Create shops table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS shops (" +
                "    owner_id TEXT NOT NULL," +
                "    name TEXT NOT NULL," +
                "    owner_name TEXT NOT NULL," +
                "    trader_uuid TEXT," +
                "    PRIMARY KEY (owner_id, name)" +
                ")"
            );

            // Create trades table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS trades (" +
                "    id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    owner_id TEXT NOT NULL," +
                "    shop_name TEXT NOT NULL," +
                "    trade_id INTEGER NOT NULL," +
                "    input_item_id TEXT NOT NULL," +
                "    input_quantity INTEGER NOT NULL," +
                "    output_item_id TEXT NOT NULL," +
                "    output_quantity INTEGER NOT NULL," +
                "    FOREIGN KEY (owner_id, shop_name) REFERENCES shops(owner_id, name) ON DELETE CASCADE," +
                "    UNIQUE (owner_id, shop_name, trade_id)" +
                ")"
            );

            // Create index for faster lookups by trader UUID
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_shops_trader_uuid ON shops(trader_uuid)"
            );
        }
    }

    private void migrateFromPropertiesIfNeeded() {
        Path propertiesFile = dataDirectory.resolve("shops.properties");
        if (!Files.exists(propertiesFile)) {
            return;
        }

        // Check if database is empty
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM shops")) {
            if (rs.next() && rs.getInt(1) > 0) {
                // Database already has data, skip migration
                return;
            }
        } catch (SQLException e) {
            // If error checking, skip migration
            return;
        }

        // Perform migration
        Properties props = new Properties();
        try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
            props.load(inputStream);
            migrateFromProperties(props);
            
            // Backup the old properties file
            Path backupFile = dataDirectory.resolve("shops.properties.backup");
            Files.move(propertiesFile, backupFile);
        } catch (IOException | SQLException e) {
            System.err.println("Failed to migrate from properties file: " + e.getMessage());
        }
    }

    private void migrateFromProperties(Properties props) throws SQLException {
        connection.setAutoCommit(false);
        try {
            // First pass: Create all shops
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("shop.")) {
                    String[] parts = key.substring(5).split("\\.");
                    if (parts.length >= 3 && "name".equals(parts[parts.length - 1])) {
                        String ownerId = parts[0];
                        String shopName = props.getProperty(key);
                        String ownerName = props.getProperty("shop." + ownerId + "." + parts[1] + ".owner", "");
                        String traderUuid = props.getProperty("shop." + ownerId + "." + parts[1] + ".traderUuid", "");
                        
                        createShopInternal(ownerId, ownerName, shopName, traderUuid);
                    }
                }
            }

            // Second pass: Add all trades
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("trade.")) {
                    String[] parts = key.substring(6).split("\\.");
                    if (parts.length >= 4) {
                        String ownerId = parts[0];
                        String shopNameEncoded = parts[1];
                        
                        // Get the shop name from the shop properties
                        String shopName = props.getProperty("shop." + ownerId + "." + shopNameEncoded + ".name");
                        if (shopName == null) continue;
                        
                        String tradeIdStr = parts[2];
                        int tradeId = Integer.parseInt(tradeIdStr);
                        
                        String base = "trade." + ownerId + "." + shopNameEncoded + "." + tradeIdStr + ".";
                        String inputItemId = props.getProperty(base + "input");
                        int inputQty = Integer.parseInt(props.getProperty(base + "inputQty", "1"));
                        String outputItemId = props.getProperty(base + "output");
                        int outputQty = Integer.parseInt(props.getProperty(base + "outputQty", "1"));
                        
                        if (inputItemId != null && outputItemId != null) {
                            addTradeInternal(ownerId, shopName, tradeId, inputItemId, inputQty, outputItemId, outputQty);
                        }
                    }
                }
            }
            
            connection.commit();
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void createShopInternal(String ownerId, String ownerName, String name, String traderUuid) throws SQLException {
        String sql = "INSERT OR IGNORE INTO shops (owner_id, name, owner_name, trader_uuid) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(name));
            pstmt.setString(3, ownerName);
            pstmt.setString(4, traderUuid.isEmpty() ? null : traderUuid);
            pstmt.executeUpdate();
        }
    }

    private void addTradeInternal(String ownerId, String shopName, int tradeId, String inputItemId, int inputQty, String outputItemId, int outputQty) throws SQLException {
        String sql = "INSERT OR IGNORE INTO trades (owner_id, shop_name, trade_id, input_item_id, input_quantity, output_item_id, output_quantity) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(shopName));
            pstmt.setInt(3, tradeId);
            pstmt.setString(4, inputItemId);
            pstmt.setInt(5, inputQty);
            pstmt.setString(6, outputItemId);
            pstmt.setInt(7, outputQty);
            pstmt.executeUpdate();
        }
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

        String normalizedName = normalizeName(trimmedName);
        
        // Check if shop already exists
        String checkSql = "SELECT 1 FROM shops WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(checkSql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizedName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    // Shop already exists, just return it
                    return getShop(ownerId, trimmedName);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check shop existence", e);
        }

        String sql = "INSERT INTO shops (owner_id, name, owner_name, trader_uuid) VALUES (?, ?, ?, NULL)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizedName);
            pstmt.setString(3, ownerName);
            pstmt.executeUpdate();
            
            return new Shop(ownerId, ownerName, normalizedName, List.of(), "");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create shop", e);
        }
    }

    @Nonnull
    public synchronized Shop renameShop(@Nonnull String ownerId, @Nonnull String currentName, @Nonnull String newName) {
        String trimmedCurrent = currentName.trim();
        String trimmedNew = newName.trim();
        if (trimmedCurrent.isBlank() || trimmedNew.isBlank()) {
            throw new IllegalArgumentException("Shop name is required.");
        }

        String currentKey = normalizeName(trimmedCurrent);
        String newKey = normalizeName(trimmedNew);
        
        if (!currentKey.equals(newKey)) {
            // Check if new name already exists
            String checkSql = "SELECT 1 FROM shops WHERE owner_id = ? AND name = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(checkSql)) {
                pstmt.setString(1, ownerId);
                pstmt.setString(2, newKey);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException("You already have a shop named '" + trimmedNew + "'.");
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check shop name", e);
            }
        }

        String sql = "UPDATE shops SET name = ? WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newKey);
            pstmt.setString(2, ownerId);
            pstmt.setString(3, currentKey);
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Shop not found: " + currentName);
            }
            
            return getShop(ownerId, newKey);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rename shop", e);
        }
    }

    public synchronized void deleteShop(@Nonnull String ownerId, @Nonnull String name) {
        String sql = "DELETE FROM shops WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(name));
            int deleted = pstmt.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("Shop not found: " + name);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete shop", e);
        }
    }

    @Nonnull
    public synchronized Shop getShop(@Nonnull String ownerId, @Nonnull String name) {
        String sql = "SELECT owner_name, trader_uuid FROM shops WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(name));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Shop not found: " + name);
                }
                
                String ownerName = rs.getString("owner_name");
                String traderUuid = rs.getString("trader_uuid");
                
                List<Trade> trades = loadTrades(ownerId, normalizeName(name));
                
                return new Shop(ownerId, ownerName, normalizeName(name), trades, traderUuid == null ? "" : traderUuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get shop", e);
        }
    }

    @Nonnull
    public synchronized String getTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        String sql = "SELECT trader_uuid FROM shops WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(name));
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Shop not found: " + name);
                }
                String traderUuid = rs.getString("trader_uuid");
                return traderUuid == null ? "" : traderUuid;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get trader UUID", e);
        }
    }

    public synchronized void setTraderUuid(@Nonnull String ownerId, @Nonnull String name, @Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            throw new IllegalArgumentException("Trader uuid is required.");
        }
        
        String sql = "UPDATE shops SET trader_uuid = ? WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, traderUuid);
            pstmt.setString(2, ownerId);
            pstmt.setString(3, normalizeName(name));
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Shop not found: " + name);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to set trader UUID", e);
        }
    }

    public synchronized void clearTraderUuid(@Nonnull String ownerId, @Nonnull String name) {
        String sql = "UPDATE shops SET trader_uuid = NULL WHERE owner_id = ? AND name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(name));
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Shop not found: " + name);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to clear trader UUID", e);
        }
    }

    @Nonnull
    public synchronized List<Shop> listShops(@Nonnull String ownerId) {
        List<Shop> shops = new ArrayList<>();
        String sql = "SELECT name, owner_name, trader_uuid FROM shops WHERE owner_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    String ownerName = rs.getString("owner_name");
                    String traderUuid = rs.getString("trader_uuid");
                    
                    List<Trade> trades = loadTrades(ownerId, name);
                    
                    shops.add(new Shop(ownerId, ownerName, name, trades, traderUuid == null ? "" : traderUuid));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list shops", e);
        }
        
        shops.sort(Comparator.comparing(Shop::name, String.CASE_INSENSITIVE_ORDER));
        return shops;
    }

    @Nonnull
    public synchronized List<Shop> listAllShops() {
        List<Shop> shops = new ArrayList<>();
        String sql = "SELECT owner_id, name, owner_name, trader_uuid FROM shops";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String ownerId = rs.getString("owner_id");
                String name = rs.getString("name");
                String ownerName = rs.getString("owner_name");
                String traderUuid = rs.getString("trader_uuid");
                
                List<Trade> trades = loadTrades(ownerId, name);
                
                shops.add(new Shop(ownerId, ownerName, name, trades, traderUuid == null ? "" : traderUuid));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to list all shops", e);
        }
        
        shops.sort(Comparator.comparing(Shop::name, String.CASE_INSENSITIVE_ORDER));
        return shops;
    }

    @Nullable
    public synchronized Shop findShopByTraderUuid(@Nonnull String traderUuid) {
        if (traderUuid.isBlank()) {
            return null;
        }
        
        String sql = "SELECT owner_id, name, owner_name FROM shops WHERE trader_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, traderUuid);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                
                String ownerId = rs.getString("owner_id");
                String name = rs.getString("name");
                String ownerName = rs.getString("owner_name");
                
                List<Trade> trades = loadTrades(ownerId, name);
                
                return new Shop(ownerId, ownerName, name, trades, traderUuid);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find shop by trader UUID", e);
        }
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
        validateItem(inputItemId, "Input item");
        validateItem(outputItemId, "Output item");
        validateQuantity(inputQuantity);
        validateQuantity(outputQuantity);

        String normalizedName = normalizeName(shopName);
        
        // Check trade count
        String countSql = "SELECT COUNT(*) FROM trades WHERE owner_id = ? AND shop_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(countSql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizedName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next() && rs.getInt(1) >= MAX_TRADES) {
                    throw new IllegalArgumentException("Shop already has the maximum of " + MAX_TRADES + " trades.");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check trade count", e);
        }
        
        // Get next trade ID
        int tradeId = getNextTradeId(ownerId, normalizedName);
        
        String sql = "INSERT INTO trades (owner_id, shop_name, trade_id, input_item_id, input_quantity, output_item_id, output_quantity) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizedName);
            pstmt.setInt(3, tradeId);
            pstmt.setString(4, inputItemId);
            pstmt.setInt(5, inputQuantity);
            pstmt.setString(6, outputItemId);
            pstmt.setInt(7, outputQuantity);
            pstmt.executeUpdate();
            
            return new Trade(tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add trade", e);
        }
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
        validateItem(inputItemId, "Input item");
        validateItem(outputItemId, "Output item");
        validateQuantity(inputQuantity);
        validateQuantity(outputQuantity);

        String sql = "UPDATE trades SET input_item_id = ?, input_quantity = ?, output_item_id = ?, output_quantity = ? " +
                     "WHERE owner_id = ? AND shop_name = ? AND trade_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, inputItemId);
            pstmt.setInt(2, inputQuantity);
            pstmt.setString(3, outputItemId);
            pstmt.setInt(4, outputQuantity);
            pstmt.setString(5, ownerId);
            pstmt.setString(6, normalizeName(shopName));
            pstmt.setInt(7, tradeId);
            
            int updated = pstmt.executeUpdate();
            if (updated == 0) {
                throw new IllegalArgumentException("Trade not found: " + tradeId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update trade", e);
        }
    }

    public synchronized void removeTrade(@Nonnull String ownerId, @Nonnull String shopName, int tradeId) {
        String sql = "DELETE FROM trades WHERE owner_id = ? AND shop_name = ? AND trade_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, normalizeName(shopName));
            pstmt.setInt(3, tradeId);
            
            int deleted = pstmt.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("Trade not found: " + tradeId);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to remove trade", e);
        }
    }

    private List<Trade> loadTrades(String ownerId, String shopName) throws SQLException {
        List<Trade> trades = new ArrayList<>();
        String sql = "SELECT trade_id, input_item_id, input_quantity, output_item_id, output_quantity " +
                     "FROM trades WHERE owner_id = ? AND shop_name = ? ORDER BY trade_id";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, shopName);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int tradeId = rs.getInt("trade_id");
                    String inputItemId = rs.getString("input_item_id");
                    int inputQuantity = rs.getInt("input_quantity");
                    String outputItemId = rs.getString("output_item_id");
                    int outputQuantity = rs.getInt("output_quantity");
                    
                    trades.add(new Trade(tradeId, inputItemId, inputQuantity, outputItemId, outputQuantity));
                }
            }
        }
        return trades;
    }

    private int getNextTradeId(String ownerId, String shopName) {
        String sql = "SELECT COALESCE(MAX(trade_id), 0) + 1 FROM trades WHERE owner_id = ? AND shop_name = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, ownerId);
            pstmt.setString(2, shopName);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 1;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to get next trade ID", e);
        }
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
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                System.err.println("Failed to close database connection: " + e.getMessage());
            }
        }
    }
}
