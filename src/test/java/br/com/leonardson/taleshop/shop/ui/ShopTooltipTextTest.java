package br.com.leonardson.taleshop.shop.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.util.MessageUtil;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ShopTooltipTextTest {
    private static final String DESCRIPTION = "Can be smelted into a <item is=\"Ingredient_Bar_Gold\"/> at a <item is=\"Bench_Furnace\"/>.";

    private String plain(Message message) {
        return MessageUtil.formatMessageToPlainString(message.getFormattedMessage());
    }

    @Test
    void nativeDescriptionItemReferencesBecomeReadableNamesWithoutEnchantmentsPlugin() {
        var names = Map.of("Ingredient_Bar_Gold", "Gold Ingot", "Bench_Furnace", "Furnace");
        Message converted = ShopTooltipText.rewrite(Message.translation("server.items.Ore_Gold.description"),
            key -> DESCRIPTION, id -> Message.raw(names.get(id)));
        assertEquals("Can be smelted into a Gold Ingot at a Furnace.", plain(converted));
        assertNull(converted.getMessageId());
        assertFalse(plain(converted).contains("<item"));
    }

    @Test
    void resolvesDescriptionForRequestedLocaleAndKeepsReferencedItemTranslationsNative() {
        Message translated = ShopTooltipText.rewrite(Message.translation("server.items.Ore_Gold.description"),
            key -> "Pode ser fundido em <item is='Ingredient_Bar_Gold'/>.",
            id -> Message.raw("Barra de Ouro"));
        assertEquals("Pode ser fundido em Barra de Ouro.", plain(translated));
        Message nativeName = ShopTooltipText.rewrite(Message.raw(DESCRIPTION), key -> null,
            id -> Message.translation("server.items." + id + ".name"));
        assertEquals("server.items.Ingredient_Bar_Gold.name",
            nativeName.getFormattedMessage().children[1].messageId);
        assertEquals("server.items.Bench_Furnace.name",
            nativeName.getFormattedMessage().children[3].messageId);
    }

    @Test
    void preservesEnchantmentFormattingParametersAndOriginalMessageWhileRewritingNestedDescriptions() {
        Message description = Message.translation("description").param("amount", 60).color("#aa88ff").italic(true);
        Message enchantment = Message.translation("enchantment.efficiency.name").bold(true).color("#aa00ff");
        Message source = Message.empty().insert(description).insert(enchantment);
        var original = Message.CODEC.encode(source, new com.hypixel.hytale.codec.ExtraInfo());
        Message converted = ShopTooltipText.rewrite(source,
            key -> key.equals("description") ? "{amount}% at <item is=\"Bench_Furnace\"/>" : "Efficiency III",
            id -> Message.raw("Furnace"));
        var child = converted.getFormattedMessage().children[0];
        assertEquals("60% at Furnace", plain(new Message(child)));
        assertEquals("#aa88ff", child.color);
        assertEquals(Boolean.TRUE, child.italic);
        assertEquals("enchantment.efficiency.name", converted.getFormattedMessage().children[1].messageId);
        assertEquals(Boolean.TRUE, converted.getFormattedMessage().children[1].bold);
        assertEquals(original, Message.CODEC.encode(source, new com.hypixel.hytale.codec.ExtraInfo()));
    }

    @Test
    void handlesEscapedPairedAndMissingItemReferencesWithoutLeakingTags() {
        Message converted = ShopTooltipText.rewrite(Message.raw(
            "Use &lt;item is=&quot;Bench_Furnace&quot;/&gt; and <item is='Removed_Item'>old name</item><item/>."),
            key -> null, id -> id.equals("Bench_Furnace") ? Message.raw("Furnace") : null);
        assertEquals("Use Furnace and item.", plain(converted));
        assertFalse(plain(converted).contains("<item"));
        assertFalse(plain(converted).contains("&lt;item"));
    }

    @Test
    void keepsOrdinaryAndUnresolvedTranslationsClientSide() {
        Message source = Message.translation("enchantment.efficiency.bonus").param("amount", 60).color("#aa00ff");
        var expected = Message.CODEC.encode(source, new com.hypixel.hytale.codec.ExtraInfo());
        assertEquals(expected, Message.CODEC.encode(ShopTooltipText.rewrite(source,
            key -> "Mining speed increased by {amount}%", id -> fail("No item lookup expected")),
            new com.hypixel.hytale.codec.ExtraInfo()));
        assertEquals(expected, Message.CODEC.encode(ShopTooltipText.rewrite(source,
            key -> null, id -> fail("No item lookup expected")), new com.hypixel.hytale.codec.ExtraInfo()));
    }

    @Test
    void rewritesMessageParametersAndAvoidsOverwritingExistingParameterNames() {
        Message source = Message.raw("{taleshopItem0}: <item is='Bench_Furnace'/>")
            .param("taleshopItem0", Message.raw("<item is='Ingredient_Bar_Gold'/>"));
        Message converted = ShopTooltipText.rewrite(source, key -> null,
            id -> Message.raw(id.equals("Bench_Furnace") ? "Furnace" : "Gold Ingot"));
        assertEquals("Gold Ingot: Furnace", plain(converted));
        assertNull(converted.getFormattedMessage().messageParams);
    }
}
