package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.config.PluginConfig;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TradeLimitTest {
    @TempDir Path directory;

    private ShopRegistry open(PluginConfig.StorageBackend backend, int limit) {
        PluginConfig config = new PluginConfig();
        config.setStorageBackend(backend);
        config.setMaxTradesPerShop(limit);
        return new ShopRegistry(directory, config);
    }

    private void add(ShopRegistry registry) {
        registry.addTrade("owner", "Shop", "Ingredient_Gold", 1, "Ingredient_Diamond", 1);
    }

    @ParameterizedTest
    @EnumSource(PluginConfig.StorageBackend.class)
    void defaultLimitStillRejectsTheTwentyFirstTrade(PluginConfig.StorageBackend backend) {
        PluginConfig config = new PluginConfig();
        config.setStorageBackend(backend);
        ShopRegistry registry = new ShopRegistry(directory, config);
        try {
            registry.createShop("owner", "Owner", "Shop");
            for (int i = 0; i < 20; i++) add(registry);
            assertThrows(IllegalArgumentException.class, () -> add(registry));
            assertEquals(20, registry.getShop("owner", "Shop").trades().size());
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @EnumSource(PluginConfig.StorageBackend.class)
    void permitsMoreThanTwentyTradesAndRejectsTheNext(PluginConfig.StorageBackend backend) {
        ShopRegistry registry = open(backend, 25);
        try {
            registry.createShop("owner", "Owner", "Shop");
            for (int i = 0; i < 25; i++) add(registry);
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> add(registry));
            assertTrue(error.getMessage().contains("25 trades"));
            assertEquals(25, registry.getShop("owner", "Shop").trades().size());
            registry.createShop("owner", "Owner", "Other");
            registry.addTrade("owner", "Other", "Ingredient_Gold", 1, "Ingredient_Diamond", 1);
            assertEquals(1, registry.getShop("owner", "Other").trades().size());
        } finally {
            registry.close();
        }
    }

    @ParameterizedTest
    @EnumSource(PluginConfig.StorageBackend.class)
    void loweringLimitPreservesExistingTradesAndAllowsEditing(PluginConfig.StorageBackend backend) {
        ShopRegistry registry = open(backend, 3);
        try {
            registry.createShop("owner", "Owner", "Shop", true);
            for (int i = 0; i < 3; i++) add(registry);
        } finally {
            registry.close();
        }

        ShopRegistry reopened = open(backend, 2);
        try {
            assertEquals(3, reopened.getShop("owner", "Shop").trades().size());
            assertThrows(IllegalArgumentException.class, () -> add(reopened));
            int id = reopened.getShop("owner", "Shop").trades().getFirst().id();
            reopened.updateTrade("owner", "Shop", id, "Ingredient_Gold", 2, "Ingredient_Diamond", 3);
            assertEquals(3, reopened.getShop("owner", "Shop").trades().size());
            assertEquals(2, reopened.getShop("owner", "Shop").trades().getFirst().inputQuantity());
            reopened.removeTrade("owner", "Shop", id);
            assertThrows(IllegalArgumentException.class, () -> add(reopened));
            int secondId = reopened.getShop("owner", "Shop").trades().getFirst().id();
            reopened.removeTrade("owner", "Shop", secondId);
            add(reopened);
            assertEquals(2, reopened.getShop("owner", "Shop").trades().size());
        } finally {
            reopened.close();
        }
    }

    @ParameterizedTest
    @EnumSource(PluginConfig.StorageBackend.class)
    void invalidLimitStillAllowsOneTrade(PluginConfig.StorageBackend backend) {
        ShopRegistry registry = open(backend, -10);
        try {
            assertEquals(1, registry.getMaxTradesPerShop());
            registry.createShop("owner", "Owner", "Shop");
            add(registry);
            assertThrows(IllegalArgumentException.class, () -> add(registry));
        } finally {
            registry.close();
        }
    }
}
