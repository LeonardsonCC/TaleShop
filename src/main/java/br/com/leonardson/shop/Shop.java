package br.com.leonardson.shop;

import java.util.Objects;

public final class Shop {
    private final long id;
    private final String playerId;
    private final String playerName;
    private final String shopName;

    public Shop(long id, String playerId, String playerName, String shopName) {
        this.id = id;
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.playerName = Objects.requireNonNull(playerName, "playerName");
        this.shopName = Objects.requireNonNull(shopName, "shopName");
    }

    public long getId() {
        return id;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getShopName() {
        return shopName;
    }
}