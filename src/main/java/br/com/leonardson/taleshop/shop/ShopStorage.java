package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.shop.trade.Trade;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface ShopStorage {
    @Nonnull
    default Shop createShop(@Nonnull String ownerId, @Nonnull String ownerName, @Nonnull String name) {
        return createShop(ownerId, ownerName, name, false);
    }

    @Nonnull
    Shop createShop(@Nonnull String ownerId, @Nonnull String ownerName, @Nonnull String name, boolean isAdmin);

    @Nonnull
    Shop renameShop(@Nonnull String ownerId, @Nonnull String currentName, @Nonnull String newName);

    void deleteShop(@Nonnull String ownerId, @Nonnull String name);

    @Nonnull
    Shop getShop(@Nonnull String ownerId, @Nonnull String name);

    @Nonnull
    String getTraderUuid(@Nonnull String ownerId, @Nonnull String name);

    void setTraderUuid(@Nonnull String ownerId, @Nonnull String name, @Nonnull String traderUuid);

    void clearTraderUuid(@Nonnull String ownerId, @Nonnull String name);

    @Nonnull
    List<Shop> listShops(@Nonnull String ownerId);

    @Nonnull
    List<Shop> listAllShops();

    @Nullable
    Shop findShopByTraderUuid(@Nonnull String traderUuid);

    @Nonnull
    Trade addTrade(
        @Nonnull String ownerId,
        @Nonnull String shopName,
        @Nonnull String inputItemId,
        int inputQuantity,
        @Nonnull String outputItemId,
        int outputQuantity
    );

    void updateTrade(
        @Nonnull String ownerId,
        @Nonnull String shopName,
        int tradeId,
        @Nonnull String inputItemId,
        int inputQuantity,
        @Nonnull String outputItemId,
        int outputQuantity
    );

    void removeTrade(@Nonnull String ownerId, @Nonnull String shopName, int tradeId);

    void close();
}
