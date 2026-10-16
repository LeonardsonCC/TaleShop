package br.com.leonardson.taleshop.shop.ui;

import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.util.MessageUtil;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Converts item-tooltip-only markup into native message spans usable by text tooltips. */
public final class ShopTooltipText {
    private static final Pattern ITEM_TAG = Pattern.compile(
        "<item\\b[^>]*?/\\s*>|<item\\b[^>]*>.*?</item\\s*>|</?item\\b[^>]*>",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ITEM_ID = Pattern.compile("\\bis\\s*=\\s*([\"'])(.*?)\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCAPED_ITEM_TAG = Pattern.compile("&lt;/?item\\b.*?&gt;", Pattern.CASE_INSENSITIVE);

    private ShopTooltipText() {}

    public static Message resolve(Message source, String language) {
        I18nModule i18n = I18nModule.get();
        return rewrite(source, key -> {
            if (i18n == null) return null;
            String translated = translation(i18n, language, key);
            return translated != null ? translated : translation(i18n, I18nModule.DEFAULT_LANGUAGE, key);
        }, id -> {
            Item item = Item.getAssetMap().getAsset(id);
            return item == null ? Message.raw("item") : item.getTranslationMessage();
        });
    }

    private static String translation(I18nModule i18n, String language, String key) {
        if (language == null || language.isBlank()) return null;
        String value = i18n.getMessage(language, key);
        if ((value == null || value.equals(key)) && key.startsWith("server.")) {
            value = i18n.getMessage(language, key.substring(7));
        }
        return value == null || value.equals(key) ? null : value;
    }

    public static Message rewrite(Message source, Function<String, String> translations,
                                  Function<String, Message> itemNames) {
        return new Message(rewrite(source.getFormattedMessage(), translations, itemNames));
    }

    private static FormattedMessage rewrite(FormattedMessage source, Function<String, String> translations,
                                           Function<String, Message> itemNames) {
        FormattedMessage result = new FormattedMessage(source);
        if (source.children != null) {
            result.children = new FormattedMessage[source.children.length];
            for (int i = 0; i < source.children.length; i++) {
                result.children[i] = rewrite(source.children[i], translations, itemNames);
            }
        }
        if (source.messageParams != null) {
            result.messageParams = new HashMap<>();
            source.messageParams.forEach((key, value) -> result.messageParams.put(key, rewrite(value, translations, itemNames)));
        }
        String text = source.rawText != null ? source.rawText
            : source.messageId == null ? null : translations.apply(source.messageId);
        if (text == null) return result;
        // Some providers escape description markup; decode only item-reference tags, not arbitrary text.
        text = ESCAPED_ITEM_TAG.matcher(text).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
            match.group().replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")));
        var tags = ITEM_TAG.matcher(text);
        if (!tags.find()) return result; // Leave normal translations client-side, including mod descriptions.
        String token = "\uE000" + UUID.randomUUID() + ":";
        List<FormattedMessage> references = new ArrayList<>();
        Map<String, FormattedMessage> placeholders = new HashMap<>();
        if (result.messageParams != null) {
            result.messageParams.forEach((key, value) -> {
                placeholders.put(key, Message.raw(marker(token, references.size())).getFormattedMessage());
                references.add(value);
            });
        }
        StringBuilder replaced = new StringBuilder();
        do {
            var id = ITEM_ID.matcher(tags.group());
            String replacement = "";
            if (id.find()) {
                Message name = itemNames.apply(id.group(2));
                if (name == null) name = Message.raw("item");
                replacement = marker(token, references.size());
                references.add(name.getFormattedMessage());
            }
            tags.appendReplacement(replaced, java.util.regex.Matcher.quoteReplacement(replacement));
        } while (tags.find());
        tags.appendTail(replaced);
        // Format primitives/ICU with the SDK, keeping rich message parameters as separate spans.
        // Raw-text messages do not recursively interpolate message parameters in every renderer.
        String formatted = MessageUtil.formatText(replaced.toString(), source.params, placeholders);
        List<FormattedMessage> spans = new ArrayList<>();
        var markers = Pattern.compile(Pattern.quote(token) + "(\\d+)\\uE001").matcher(formatted);
        int end = 0;
        while (markers.find()) {
            if (markers.start() > end) spans.add(Message.raw(formatted.substring(end, markers.start())).getFormattedMessage());
            spans.add(references.get(Integer.parseInt(markers.group(1))));
            end = markers.end();
        }
        if (end < formatted.length()) spans.add(Message.raw(formatted.substring(end)).getFormattedMessage());
        if (result.children != null) spans.addAll(List.of(result.children));
        result.rawText = null;
        result.messageId = null;
        result.params = null;
        result.messageParams = null;
        result.children = spans.toArray(FormattedMessage[]::new);
        return result;
    }

    private static String marker(String token, int index) {
        return token + index + "\uE001";
    }
}
