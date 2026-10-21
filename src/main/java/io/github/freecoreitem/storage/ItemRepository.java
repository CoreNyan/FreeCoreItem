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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".yml"))
                    .forEach(path -> loadFile(path.toFile()));
        } catch (IOException exception) {
            plugin.getLogger().warning("Cannot scan item directory: " + exception.getMessage());
        }
    }

    private void loadFile(File file) {
            String relative = directory.toPath().relativize(file.toPath()).toString();
            String id = relative.substring(0, relative.length() - 4)
                    .replace(File.separatorChar, '/')
                    .toLowerCase(Locale.ROOT);
            if (!validPath(id)) {
                plugin.getLogger().warning("Ignoring item file with invalid ID: " + file.getName());
                return;
            }
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                ItemStack template = yaml.getItemStack("item");
                if (template == null || template.getType().isAir()) {
                    plugin.getLogger().warning("Ignoring item without a valid template: " + file.getName());
                    return;
                }
                items.put(id, new CustomItem(id, template,
                        ItemAction.load(yaml.getConfigurationSection("actions.right-click")),
                        ItemAction.load(yaml.getConfigurationSection("actions.left-click")),
                        yaml.getBoolean("bind-on-give", false)));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Cannot load item " + file.getName() + ": " + exception.getMessage());
            }
    }

    public static boolean validId(String id) {
        return id != null && VALID_ID.matcher(id.toLowerCase(Locale.ROOT)).matches();
    }

    private static boolean validPath(String id) {
        String[] parts = id.split("/");
        if (parts.length == 0) return false;
        for (String part : parts) if (!validId(part)) return false;
        return true;
    }

    public Optional<CustomItem> find(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        // 1. Direct match with full ID (e.g. "tag_mining/core_mining_lv1")
        CustomItem exact = items.get(normalized);
        if (exact != null) return Optional.of(exact);

        // 2. Exact match on short name (filename without folder, e.g. "core_mining_lv1")
        List<CustomItem> shortMatches = new ArrayList<>();
        for (CustomItem item : items.values()) {
            String shortName = shortNameOf(item.id());
            if (shortName.equalsIgnoreCase(normalized)) {
                shortMatches.add(item);
            }
        }
        if (shortMatches.size() == 1) {
            return Optional.of(shortMatches.get(0));
        }

        // 3. Match by suffix (e.g. "mining/core_mining_lv1")
        List<CustomItem> suffixMatches = new ArrayList<>();
        for (CustomItem item : items.values()) {
            if (item.id().endsWith("/" + normalized)) {
                suffixMatches.add(item);
            }
        }
        if (suffixMatches.size() == 1) {
            return Optional.of(suffixMatches.get(0));
        }

        return Optional.empty();
    }

    public static String shortNameOf(String fullId) {
        if (fullId == null) return "";
        int slash = fullId.lastIndexOf('/');
        return slash >= 0 ? fullId.substring(slash + 1) : fullId;
    }

    public List<String> matchAmbiguities(String query) {
        if (query == null || query.isBlank()) return List.of();
        String normalized = query.toLowerCase(Locale.ROOT).trim();
        List<String> list = new ArrayList<>();
        for (CustomItem item : items.values()) {
            String shortName = shortNameOf(item.id());
            if (shortName.equalsIgnoreCase(normalized) || item.id().endsWith("/" + normalized)) {
                list.add(item.id());
            }
        }
        return list;
    }

    public List<String> completionSuggestions() {
        Map<String, Integer> shortCount = new LinkedHashMap<>();
        for (CustomItem item : items.values()) {
            String shortName = shortNameOf(item.id());
            shortCount.put(shortName, shortCount.getOrDefault(shortName, 0) + 1);
        }
        List<String> result = new ArrayList<>();
        for (CustomItem item : items.values()) {
            String shortName = shortNameOf(item.id());
            if (shortCount.getOrDefault(shortName, 0) == 1) {
                result.add(shortName);
            } else {
                result.add(item.id());
            }
        }
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
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
        yaml.set("bind-on-give", item.bindOnGive());
        ConfigurationSection actions = yaml.createSection("actions");
        item.rightClick().save(actions.createSection("right-click"));
        item.leftClick().save(actions.createSection("left-click"));
        File target = file(item.id());
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create item directory: " + parent);
        }
        yaml.save(target);
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
        return new File(directory, id.replace('/', File.separatorChar) + ".yml");
    }

    public static ItemStack defaultTemplate(Material material) {
        return new ItemStack(material == null || material.isAir() ? Material.STONE : material);
    }
}
