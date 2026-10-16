package br.com.leonardson.taleshop.shop;

import br.com.leonardson.taleshop.config.PluginConfig;
import br.com.leonardson.taleshop.shop.trade.Trade;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

class TradeMetadataStorageTest {
    @TempDir Path directory;
    private static final String INPUT = "{\"Id\":\"Gold\",\"Metadata\":{\"CustomName\":\"Payment\"}}";
    private static final String OUTPUT = "{\"Id\":\"Sword\",\"Durability\":37.0,\"MaxDurability\":100.0,\"Metadata\":{\"SimpleEnchantments\":{\"Sharpness\":5}}}";

    private ShopStorage open(PluginConfig.StorageBackend backend) {
        return backend == PluginConfig.StorageBackend.SQLITE ? new SqliteShopStorage(directory) : new JsonShopStorage(directory);
    }

    @ParameterizedTest
    @EnumSource(PluginConfig.StorageBackend.class)
    void templatesSurviveCreateUpdateAndReopen(PluginConfig.StorageBackend backend) {
        ShopStorage storage = open(backend);
        int id;
        try {
            storage.createShop("owner", "Owner", "Shop", true);
            Trade created = storage.addTrade("owner", "Shop", "Gold", 2, "Sword", 1, INPUT, OUTPUT);
            id = created.id();
            assertEquals(INPUT, created.inputStackData());
            assertEquals(OUTPUT, created.outputStackData());
        } finally { storage.close(); }
        storage = open(backend);
        try {
            Trade loaded = storage.getShop("owner", "Shop").trades().getFirst();
            assertEquals(INPUT, loaded.inputStackData());
            assertEquals(OUTPUT, loaded.outputStackData());
            storage.updateTrade("owner", "Shop", id, "Gold", 4, "Sword", 3, INPUT, OUTPUT);
        } finally { storage.close(); }
        storage = open(backend);
        try {
            assertEquals(new Trade(id, "Gold", 4, "Sword", 3, INPUT, OUTPUT), storage.getShop("owner", "Shop").trades().getFirst());
            assertTrue(storage.getShop("owner", "Shop").isAdmin());
        } finally { storage.close(); }
    }

    @ParameterizedTest
    @EnumSource(PluginConfig.StorageBackend.class)
    void oldOnDiskSchemaLoadsAndCanBeUpdatedWithoutLosingOtherOffers(PluginConfig.StorageBackend backend) throws Exception {
        if (backend == PluginConfig.StorageBackend.SQLITE) {
            Class.forName("org.sqlite.JDBC");
            try (var connection = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("shops.db"));
                 var stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE shops (owner_id TEXT, name TEXT, display_name TEXT, owner_name TEXT, trader_uuid TEXT, is_admin INTEGER, PRIMARY KEY(owner_id, name))");
                stmt.execute("CREATE TABLE trades (id INTEGER PRIMARY KEY, owner_id TEXT, shop_name TEXT, trade_id INTEGER, input_item_id TEXT, input_quantity INTEGER, output_item_id TEXT, output_quantity INTEGER)");
                stmt.execute("INSERT INTO shops VALUES ('owner', 'shop', 'Shop', 'Owner', 'npc-uuid', 0)");
                stmt.execute("INSERT INTO trades VALUES (1, 'owner', 'shop', 7, 'Gold', 2, 'Sword', 1)");
            }
        } else {
            Files.writeString(directory.resolve("shops.json"), """
                {"shops":[{"ownerId":"owner","ownerName":"Owner","name":"Shop","traderUuid":"npc-uuid","trades":[
                {"id":7,"inputItemId":"Gold","inputQuantity":2,"outputItemId":"Sword","outputQuantity":1}]}]}
                """);
        }
        for (int pass = 0; pass < 2; pass++) {
            ShopStorage storage = open(backend);
            try {
                assertEquals(new Trade(7, "Gold", 2, "Sword", 1), storage.getShop("owner", "Shop").trades().getFirst());
                assertEquals("npc-uuid", storage.getTraderUuid("owner", "Shop"));
                if (pass == 0) storage.addTrade("owner", "Shop", "Gold", 1, "Sword", 1, INPUT, OUTPUT);
                else assertEquals(OUTPUT, storage.getShop("owner", "Shop").trades().get(1).outputStackData());
            } finally { storage.close(); }
        }
    }
}
