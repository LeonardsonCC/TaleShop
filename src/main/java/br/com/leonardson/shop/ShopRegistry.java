package br.com.leonardson.shop;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ShopRegistry {
    private final ShopRepository repository;

    public ShopRegistry(ShopRepository repository) {
        this.repository = repository;
    }

    public Shop registerShop(PlayerRef playerRef, String shopName) throws SQLException {
        return registerShop(playerRef.getUuid().toString(), playerRef.getUsername(), shopName);
    }

    public Shop registerShop(String playerId, String playerName, String shopName) throws SQLException {
        return repository.createShop(playerId, playerName, shopName);
    }

    public List<Shop> getShopsForPlayer(PlayerRef playerRef) throws SQLException {
        return getShopsForPlayerId(playerRef.getUuid().toString());
    }

    public List<Shop> getShopsForPlayerId(String playerId) throws SQLException {
        return repository.getShopsByPlayerId(playerId);
    }

    public Optional<Shop> getShopById(long id) throws SQLException {
        return repository.getShopById(id);
    }

    public boolean unregisterShop(long id) throws SQLException {
        return repository.deleteShop(id);
    }
}