package br.com.leonardson.taleshop.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PackagedSqliteTest {
    @TempDir Path directory;

    @Test
    void distributedJarCanOpenSqliteWithoutGradleDependencies() throws Exception {
        URL jar = Path.of(System.getProperty("taleshop.testJar")).toUri().toURL();
        // Model the plugin's isolated loader: Gradle's runtime dependencies are invisible.
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar}, ClassLoader.getPlatformClassLoader())) {
            Class<?> storageType = Class.forName("br.com.leonardson.taleshop.shop.SqliteShopStorage", true, loader);
            Object storage = storageType.getConstructor(Path.class, int.class).newInstance(directory, 50);
            try {
                storageType.getMethod("createShop", String.class, String.class, String.class, boolean.class)
                    .invoke(storage, "owner", "Owner", "Shop", false);
                Object trade = storageType.getMethod("addTrade", String.class, String.class,
                        String.class, int.class, String.class, int.class)
                    .invoke(storage, "owner", "Shop", "Ingredient_Gold", 1, "Ingredient_Diamond", 1);
                assertEquals(1, trade.getClass().getMethod("inputQuantity").invoke(trade));
            } finally {
                storageType.getMethod("close").invoke(storage);
            }
        }
    }
}
