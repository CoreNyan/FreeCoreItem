package io.github.freecoreitem.storage;

import io.github.freecoreitem.model.CustomItem;
import io.github.freecoreitem.model.ItemAction;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.function.Consumer;

public final class ItemRepository {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private final JavaPlugin plugin;
    private final File directory;
    private final Map<String, CustomItem> items = new LinkedHashMap<>();
    private Consumer<CustomItem> updateListener = item -> { };

    public ItemRepository(JavaPlugin plugin) {
        this.plugin = plugin;
        this.directory = new File(plugin.getDataFolder(), "items");
    }

    public void reload() {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create item directory: " + directory);
        }
        items.clear();
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            if (!validId(id)) {
                plugin.getLogger().warning("Ignoring item file with invalid ID: " + file.getName());
                continue;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ItemStack template = yaml.getItemStack("item");
                if (template == null || template.getType().isAir()) {
                    plugin.getLogger().warning("Ignoring item without a valid template: " + file.getName());
                    continue;
                }
                items.put(id, new CustomItem(id, template,
                        ItemAction.load(yaml.getConfigurationSection("actions.right-click")),
                        ItemAction.load(yaml.getConfigurationSection("actions.left-click"))));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Cannot load item " + file.getName() + ": " + exception.getMessage());
            }
        }
    }

    public static boolean validId(String id) {
        return id != null && VALID_ID.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }

    public Optional<CustomItem> find(String id) {
        return Optional.ofNullable(items.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<CustomItem> all() {
        List<CustomItem> sorted = new ArrayList<>(items.values());
        sorted.sort(Comparator.comparing(CustomItem::id));
        return sorted;
    }

    public CustomItem create(String id, ItemStack template) throws IOException {
        String normalized = id.toLowerCase(Locale.ROOT);
        CustomItem item = new CustomItem(normalized, template, new ItemAction(), new ItemAction());
        save(item);
        return item;
    }

    public void save(CustomItem item) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item", item.template());
        ConfigurationSection actions = yaml.createSection("actions");
        item.rightClick().save(actions.createSection("right-click"));
        item.leftClick().save(actions.createSection("left-click"));
        yaml.save(file(item.id()));
        items.put(item.id(), item);
        updateListener.accept(item);
    }

    public void setUpdateListener(Consumer<CustomItem> updateListener) {
        this.updateListener = updateListener == null ? item -> { } : updateListener;
    }

    public boolean delete(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        CustomItem existing = items.get(normalized);
        File file = file(normalized);
        if (existing == null || (file.exists() && !file.delete())) return false;
        items.remove(normalized);
        return true;
    }

    public int size() { return items.size(); }

    private File file(String id) {
        return new File(directory, id + ".yml");
    }

    public static ItemStack defaultTemplate(Material material) {
        return new ItemStack(material == null || material.isAir() ? Material.STONE : material);
    }
}
