package br.com.leonardson.taleshop.shop.trade;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.hypixel.hytale.assetstore.AssetRegistry;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.server.core.asset.HytaleAssetStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TradeMetadataTest {
    @BeforeAll
    static void registerItemAssets() throws Exception {
        com.hypixel.hytale.server.core.Options.parse(new String[0]);
        // Exercise real SDK containers/codecs without starting a server or loading game assets.
        var builder = new HytaleAssetStore.Builder<>(String.class, Item.class,
            new DefaultAssetMap<String, Item>()).setPath("Item/Items").setCodec(Item.CODEC)
            .setKeyFunction(Item::getId);
        AssetRegistry.register(new HytaleAssetStore<>(builder) {
            private final com.hypixel.hytale.event.EventBus bus = new com.hypixel.hytale.event.EventBus(false);
            @Override
            protected com.hypixel.hytale.event.EventBus getEventBus() { return bus; }
        });
    }

    private ItemStack enchanted(String id, int amount, String enchantment) {
        return new ItemStack(id, amount, 37, 100, 3,
            BsonDocument.parse("{\"SimpleEnchantments\":{\"" + enchantment + "\":5},\"CustomName\":\"Test\"}"));
    }

    @Test
    void codecPreservesNestedModDataDurabilityAndQuality() {
        ItemStack original = enchanted("Weapon_Sword_Iron", 2, "Sharpness").withMetadata(
            enchanted("Weapon_Sword_Iron", 2, "Sharpness").getMetadata()
                .append("LongValue", new org.bson.BsonInt64(5))
                .append("DoubleValue", new org.bson.BsonDouble(5.0))
                .append("BinaryValue", new org.bson.BsonBinary(new byte[]{1, 2, 3})));
        ItemStack restored = TradeItems.decode(TradeItems.encode(original), original.getItemId(), 2);
        assertEquals(original, restored);
        assertEquals(original.getMetadata(), restored.getMetadata());
        assertEquals(37, restored.getDurability());
        assertEquals(100, restored.getMaxDurability());
        assertEquals(3, restored.getQualityIndex());
    }

    @Test
    void legacyOfferTransfersActualStockAndPaymentAcrossSlots() {
        SimpleItemContainer buyer = new SimpleItemContainer((short) 3);
        SimpleItemContainer chest = new SimpleItemContainer((short) 4);
        ItemStack payment = enchanted("Ingredient_Gold", 3, "PaymentData");
        ItemStack sword = enchanted("Weapon_Sword_Iron", 2, "Sharpness");
        ItemStack otherSword = enchanted("Weapon_Sword_Iron", 1, "Smite");
        buyer.setItemStackForSlot((short) 0, payment);
        chest.setItemStackForSlot((short) 0, sword);
        chest.setItemStackForSlot((short) 1, otherSword);
        var purchased = TradeExchange.exchange(new Trade(1, payment.getItemId(), 2, sword.getItemId(), 3), buyer, List.of(chest), false);
        assertEquals(List.of(sword, otherSword), purchased);
        assertEquals(payment.withQuantity(1), buyer.getItemStack((short) 0));
        assertTrue(ItemStack.isEmpty(chest.getItemStack((short) 1)));
        assertEquals(2, TradeExchange.count(List.of(chest), s -> !ItemStack.isEmpty(s) && payment.getMetadata().equals(s.getMetadata())));
    }

    @Test
    void templateDoesNotSellOrAcceptOtherVariants() {
        ItemStack sword = enchanted("Weapon_Sword_Iron", 1, "Sharpness");
        ItemStack payment = enchanted("Ingredient_Gold", 1, "PaymentData");
        Trade trade = new Trade(1, payment.getItemId(), 1, sword.getItemId(), 1, TradeItems.encode(payment), TradeItems.encode(sword));
        assertTrue(TradeItems.matchesOutput(trade, sword.withQuantity(8)));
        assertFalse(TradeItems.matchesOutput(trade, enchanted(sword.getItemId(), 1, "Smite")));
        assertFalse(TradeItems.matchesOutput(trade, new ItemStack(sword.getItemId(), 1)));
        assertFalse(TradeItems.matchesInput(trade, new ItemStack(payment.getItemId(), 1)));
        SimpleItemContainer buyer = new SimpleItemContainer((short) 2);
        SimpleItemContainer chest = new SimpleItemContainer((short) 2);
        buyer.setItemStackForSlot((short) 0, payment);
        chest.setItemStackForSlot((short) 0, enchanted(sword.getItemId(), 1, "Smite"));
        assertThrows(IllegalArgumentException.class, () -> TradeExchange.exchange(trade, buyer, List.of(chest), false));
        assertEquals(payment, buyer.getItemStack((short) 0));
        chest.setItemStackForSlot((short) 1, sword);
        assertEquals(List.of(sword), TradeExchange.exchange(trade, buyer, List.of(chest), false));
        assertEquals("Smite", chest.getItemStack((short) 0).getMetadata().getDocument("SimpleEnchantments").getFirstKey());
    }

    @Test
    void failedPartialPaymentDepositRestoresBothInventoriesAndMetadata() {
        SimpleItemContainer buyer = new SimpleItemContainer((short) 2);
        SimpleItemContainer chest = new SimpleItemContainer((short) 2);
        ItemStack first = enchanted("Ingredient_Gold", 1, "First");
        ItemStack second = enchanted("Ingredient_Gold", 1, "Second");
        ItemStack sword = enchanted("Weapon_Sword_Iron", 2, "Sharpness");
        buyer.setItemStackForSlot((short) 0, first);
        buyer.setItemStackForSlot((short) 1, second);
        chest.setItemStackForSlot((short) 0, sword);
        assertThrows(IllegalArgumentException.class, () -> TradeExchange.exchange(
            new Trade(1, first.getItemId(), 2, sword.getItemId(), 1), buyer, List.of(chest), false));
        assertEquals(first, buyer.getItemStack((short) 0));
        assertEquals(second, buyer.getItemStack((short) 1));
        assertEquals(sword, chest.getItemStack((short) 0));
        assertTrue(ItemStack.isEmpty(chest.getItemStack((short) 1)));
    }

    @Test
    void deniedRemovalRestoresStockWithoutChargingOrLosingMetadata() {
        SimpleItemContainer buyer = new SimpleItemContainer((short) 1);
        SimpleItemContainer chest = new SimpleItemContainer((short) 1);
        ItemStack payment = enchanted("Ingredient_Gold", 1, "PaymentData");
        ItemStack sword = enchanted("Weapon_Sword_Iron", 1, "Sharpness");
        buyer.setItemStackForSlot((short) 0, payment);
        chest.setItemStackForSlot((short) 0, sword);
        buyer.setGlobalFilter(com.hypixel.hytale.server.core.inventory.container.filter.FilterType.DENY_ALL);
        assertThrows(IllegalArgumentException.class, () -> TradeExchange.exchange(
            new Trade(1, payment.getItemId(), 1, sword.getItemId(), 1), buyer, List.of(chest), false));
        assertEquals(payment, buyer.getItemStack((short) 0));
        assertEquals(sword, chest.getItemStack((short) 0));
    }

    @Test
    void deliveryRemainderRetainsMetadataWhenBuyerInventoryIsFull() {
        SimpleItemContainer buyer = new SimpleItemContainer((short) 1);
        buyer.setItemStackForSlot((short) 0, new ItemStack("Ingredient_Gold", 2));
        ItemStack sword = enchanted("Weapon_Sword_Iron", 1, "Sharpness");
        Trade trade = new Trade(1, "Ingredient_Gold", 1, sword.getItemId(), 1, null, TradeItems.encode(sword));
        ItemStack purchased = TradeExchange.exchange(trade, buyer, List.of(), true).getFirst();
        assertEquals(sword, buyer.addItemStack(purchased).getRemainder());
    }

    @Test
    void adminUsesStoredTemplateAndDoesNotNeedStock() {
        ItemStack sword = enchanted("Weapon_Sword_Iron", 1, "Sharpness");
        SimpleItemContainer buyer = new SimpleItemContainer((short) 1);
        buyer.setItemStackForSlot((short) 0, new ItemStack("Ingredient_Gold", 2));
        Trade trade = new Trade(1, "Ingredient_Gold", 1, sword.getItemId(), 2, null, TradeItems.encode(sword));
        assertEquals(List.of(sword.withQuantity(2)), TradeExchange.exchange(trade, buyer, List.of(), true));
        assertEquals(1, buyer.getItemStack((short) 0).getQuantity());
    }
}
