package io.github.freecoreitem.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Pattern;

public final class ItemTextParser {
    private static final Pattern LEGACY_CODE = Pattern.compile("(?i)&(?:[0-9a-fk-or]|#[0-9a-f]{6})");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private ItemTextParser() {
    }

    public static Component parse(String input) {
        Component parsed;
        if (LEGACY_CODE.matcher(input).find()) {
            parsed = LEGACY.deserialize(input);
        } else {
            parsed = MINI_MESSAGE.deserialize(input);
        }
        return nonItalic(parsed);
    }

    public static Component nonItalic(Component component) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, TextDecoration.State.FALSE)
                .append(component);
    }
}
