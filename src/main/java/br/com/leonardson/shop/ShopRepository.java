package br.com.leonardson.shop;

import br.com.leonardson.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShopRepository {
    private final DatabaseManager databaseManager;

    public ShopRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public Shop createShop(String playerId, String playerName, String shopName) throws SQLException {
        String insertSql = "INSERT OR IGNORE INTO shops (player_id, player_name, shop_name, created_at) VALUES (?, ?, ?, ?)";
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, playerId);
            stmt.setString(2, playerName);
            stmt.setString(3, shopName);
            stmt.setLong(4, System.currentTimeMillis() / 1000L);

            int updated = stmt.executeUpdate();
            if (updated > 0) {
                try (ResultSet keys = stmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        return new Shop(id, playerId, playerName, shopName);
                    }
                }
            }
        }

        Optional<Shop> existing = getShopByPlayerAndName(playerId, shopName);
        if (existing.isPresent()) {
            return existing.get();
        }

        throw new SQLException("Failed to create or fetch shop");
    }

    public Optional<Shop> getShopById(long id) throws SQLException {
        String query = "SELECT id, player_id, player_name, shop_name FROM shops WHERE id = ?";
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapShop(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<Shop> getShopByPlayerAndName(String playerId, String shopName) throws SQLException {
        String query = "SELECT id, player_id, player_name, shop_name FROM shops WHERE player_id = ? AND shop_name = ?";
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerId);
            stmt.setString(2, shopName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapShop(rs));
                }
            }
        }
        return Optional.empty();
    }

    public List<Shop> getShopsByPlayerId(String playerId) throws SQLException {
        String query = "SELECT id, player_id, player_name, shop_name FROM shops WHERE player_id = ? ORDER BY id ASC";
        List<Shop> shops = new ArrayList<>();
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    shops.add(mapShop(rs));
                }
            }
        }
        return shops;
    }

    public boolean deleteShop(long id) throws SQLException {
        String delete = "DELETE FROM shops WHERE id = ?";
        Connection connection = databaseManager.getConnection();
        try (PreparedStatement stmt = connection.prepareStatement(delete)) {
            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        }
    }

    private Shop mapShop(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String playerId = rs.getString("player_id");
        String playerName = rs.getString("player_name");
        String shopName = rs.getString("shop_name");
        return new Shop(id, playerId, playerName, shopName);
    }
}