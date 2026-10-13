package br.com.leonardson.taleshop.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PluginConfigManagerTest {
    @TempDir Path directory;

    @Test
    void oldConfigurationKeepsDefaultLimit() throws IOException {
        Files.writeString(directory.resolve("TaleShopConfig.json"), "{\"StorageBackend\":\"JSON\"}");
        assertEquals(20, new PluginConfigManager(directory).getConfig().getMaxTradesPerShop());
    }

    @Test
    void customLimitSurvivesSavingAndReloading() {
        PluginConfigManager manager = new PluginConfigManager(directory);
        assertEquals(20, manager.getConfig().getMaxTradesPerShop());
        manager.getConfig().setMaxTradesPerShop(50);
        manager.save();
        assertEquals(50, new PluginConfigManager(directory).getConfig().getMaxTradesPerShop());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -50})
    void invalidConfiguredLimitsAreClamped(int limit) throws IOException {
        Files.writeString(directory.resolve("TaleShopConfig.json"), "{\"MaxTradesPerShop\":" + limit + "}");
        assertEquals(1, new PluginConfigManager(directory).getConfig().getMaxTradesPerShop());
    }
}
