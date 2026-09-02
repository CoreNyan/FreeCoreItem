package io.github.freecoreitem.lang;

import io.github.freecoreitem.text.ItemTextParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class Lang {
    private static final net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer LEGACY_SERIALIZER = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final java.util.regex.Pattern LEGACY_CODE = java.util.regex.Pattern.compile(
            "(?i)&([0-9a-fk-or]|#[0-9a-f]{6}|x[0-9a-f](?:[0-9a-f]{5}))");

    private static String convertLegacy(String input) {
        if (input == null) return null;
        java.util.regex.Matcher matcher = LEGACY_CODE.matcher(input);
        StringBuilder output = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            if (code.length() == 7 && Character.toLowerCase(code.charAt(0)) == 'x') {
                StringBuilder hex = new StringBuilder("&#");
                for (int index = 1; index < 7; index++) hex.append(code.charAt(index));
                code = hex.toString();
            }
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement("\u00a7" + code));
        }
        matcher.appendTail(output);
        return output.toString();
    }
    private static final String DEFAULT_LOCALE = "zh_CN";
    private final JavaPlugin plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private YamlConfiguration values;


    private static boolean hasLegacy(String input) {
        return input != null && LEGACY_CODE.matcher(input).find();
    }

    private static boolean hasMiniMessageTag(String input) {
        return input != null && input.matches(".*<[a-zA-Z#/][^>]*>.*");
    }

    private static boolean hasLegacyOnlyLegacyTags(String input) {
        return hasLegacy(input) && !hasMiniMessageTag(input);
    }

    private static String replaceLegacyPlaceholders(String input, String... placeholders) {
        String rendered = input;
        for (int index = 0; index + 1 < placeholders.length; index += 2) {
            rendered = rendered.replace("<" + placeholders[index] + ">", placeholders[index + 1]);
        }
        return rendered;
    }
    public Lang(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File directory = new File(plugin.getDataFolder(), "lang");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create language directory: " + directory);
        }
        File defaultsFile = new File(directory, DEFAULT_LOCALE + ".yml");
        if (!defaultsFile.isFile()) {
            plugin.saveResource("lang/" + DEFAULT_LOCALE + ".yml", false);
        }
        String locale = plugin.getConfig().getString("language", DEFAULT_LOCALE);
        File selected = new File(directory, locale + ".yml");
        if (!selected.isFile()) {
            plugin.getLogger().warning("Language file " + selected.getName() + " is missing; using zh_CN.yml");
            selected = defaultsFile;
        }
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(selected);
        try (InputStream input = plugin.getResource("lang/" + DEFAULT_LOCALE + ".yml")) {
            if (input != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(input, StandardCharsets.UTF_8));
                boolean changed = false;
                for (String path : defaults.getKeys(true)) {
                    if (!defaults.isConfigurationSection(path) && !loaded.contains(path)) {
                        loaded.set(path, defaults.get(path));
                        changed = true;
                    }
                }
                loaded.setDefaults(defaults);
                if (changed) loaded.save(selected);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Cannot load bundled language defaults: " + exception.getMessage());
        }
        values = loaded;
    }

    public Component component(String path, String... replacements) {
        return render(values.getString(path, "<red>Missing language key: " + path + "</red>"), replacements);
    }

    public Component message(String path, String... replacements) {
        return component("prefix").append(component(path, replacements));
    }

    public List<Component> components(String path, String... replacements) {
        List<Component> result = new ArrayList<>();
        for (String value : values.getStringList(path)) {
            result.add(render(value, replacements));
        }
        return result;
    }

    public String raw(String path) {
        return values.getString(path, path);
    }

    public String plain(String path, String... replacements) {
        return PlainTextComponentSerializer.plainText().serialize(component(path, replacements));
    }

    public void send(CommandSender sender, String path, String... replacements) {
        sender.sendMessage(message(path, replacements));
    }

    private Component render(String input, String... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Language replacements must be key/value pairs");
        }
        TagResolver.Builder resolver = TagResolver.builder();
        String rendered = input;
        for (int index = 0; index < replacements.length; index += 2) {
            String key = replacements[index];
            String value = replacements[index + 1];
            resolver.resolver(Placeholder.unparsed(key, value));
            rendered = rendered.replace("%" + key + "%", value).replace("{" + key + "}", value);
        }
        return hasLegacyOnlyLegacyTags(rendered) ? ItemTextParser.nonItalic(LEGACY_SERIALIZER.deserialize(rendered)) : ItemTextParser.nonItalic(miniMessage.deserialize(rendered, resolver.build()));
    }
}
