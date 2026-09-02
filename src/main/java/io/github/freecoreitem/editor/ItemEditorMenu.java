package io.github.freecoreitem.editor;

import io.github.freecoreitem.item.ItemService;
import io.github.freecoreitem.lang.Lang;
import io.github.freecoreitem.model.CustomItem;
import io.github.freecoreitem.storage.ItemRepository;
import io.github.freecoreitem.text.ItemTextParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class ItemEditorMenu implements Listener {
    private static final int[] CONTENT_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44};
    private final Plugin plugin;
    private final ItemRepository repository;
    private final ItemService items;
    private final Lang lang;
    private final ChatInputManager input;
    private final NamespacedKey actionKey;

    public ItemEditorMenu(Plugin plugin, ItemRepository repository, ItemService items, Lang lang,
                          ChatInputManager input) {
        this.plugin = plugin;
        this.repository = repository;
        this.items = items;
        this.lang = lang;
        this.input = input;
        this.actionKey = new NamespacedKey(plugin, "editor_action");
    }

    public void openBrowser(Player player, int requestedPage) {
        List<CustomItem> all = new ArrayList<>(repository.all());
        int pageSize = Math.max(1, Math.min(45, plugin.getConfig().getInt("editor.items-per-page", 45)));
        int pages = Math.max(1, (all.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        EditorHolder holder = new EditorHolder(EditorHolder.View.BROWSER, null, page);
        Inventory inventory = Bukkit.createInventory(holder, 54, lang.component("gui.browser.title",
                "page", Integer.toString(page + 1), "pages", Integer.toString(pages)));
        holder.attach(inventory);
        fill(inventory);
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < all.size(); index++) {
            CustomItem custom = all.get(start + index);
            ItemStack icon = items.build(custom, 1);
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
            lore.addAll(lang.components("gui.browser.item-lore", "id", custom.id()));
            meta.lore(lore);
            tag(meta, "item:" + custom.id());
            icon.setItemMeta(meta);
            inventory.setItem(CONTENT_SLOTS[index], icon);
        }
        if (page > 0) inventory.setItem(45, button(Material.ARROW, "gui.browser.previous", "previous",
                "page", Integer.toString(page)));
        inventory.setItem(49, button(Material.ANVIL, "gui.browser.create", "create"));
        inventory.setItem(50, button(Material.BARRIER, "gui.browser.close", "close"));
        if (page + 1 < pages) inventory.setItem(53, button(Material.ARROW, "gui.browser.next", "next",
                "page", Integer.toString(page + 2)));
        player.openInventory(inventory);
    }

    public void openEditor(Player player, String id) {
        CustomItem custom = repository.find(id).orElse(null);
        if (custom == null) {
            lang.send(player, "command.item-not-found", "id", id);
            return;
        }
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        EditorHolder holder = new EditorHolder(EditorHolder.View.EDITOR, custom.id(), 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, lang.component("gui.editor.title", "id", custom.id()));
        holder.attach(inventory);
        fill(inventory);
        ItemStack preview = items.build(custom, 1);
        ItemMeta previewMeta = preview.getItemMeta();
        List<Component> previewLore = new ArrayList<>(Optional.ofNullable(previewMeta.lore()).orElse(List.of()));
        previewLore.addAll(lang.components("gui.editor.preview-lore", "id", custom.id()));
        previewMeta.lore(previewLore);
        tag(previewMeta, "preview");
        preview.setItemMeta(previewMeta);
        inventory.setItem(4, preview);
        inventory.setItem(10, editorButton(Material.GRASS_BLOCK, "material", template.getType().getKey().getKey()));
        inventory.setItem(11, nameButton(meta));
        inventory.setItem(12, loreButton(meta));
        inventory.setItem(13, editorButton(Material.CHEST, "amount", Integer.toString(template.getAmount())));
        inventory.setItem(14, editorButton(Material.ITEM_FRAME, "model",
                meta.hasCustomModelData() ? Integer.toString(meta.getCustomModelData()) : lang.plain("gui.common.none")));
        inventory.setItem(15, editorButton(Material.ANVIL, "damage",
                meta instanceof Damageable damageable ? Integer.toString(damageable.getDamage()) : "0"));
        inventory.setItem(16, editorButton(Material.BUNDLE, "max-stack",
                meta.hasMaxStackSize() ? Integer.toString(meta.getMaxStackSize()) : lang.plain("gui.common.none")));
        inventory.setItem(19, editorButton(Material.NETHER_STAR, "rarity",
                meta.hasRarity() ? meta.getRarity().name() : ItemRarity.COMMON.name()));
        inventory.setItem(20, editorButton(Material.OBSIDIAN, "unbreakable", state(meta.isUnbreakable())));
        boolean glint = meta.hasEnchantmentGlintOverride() && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
        inventory.setItem(21, editorButton(Material.ENCHANTED_BOOK, "glint", state(glint)));
        inventory.setItem(22, editorButton(Material.FIRE_CHARGE, "fire-resistant", state(meta.isFireResistant())));
        inventory.setItem(23, editorButton(Material.GRAY_DYE, "hide-tooltip", state(meta.isHideTooltip())));
        inventory.setItem(28, editorButton(Material.ENCHANTING_TABLE, "enchant", Integer.toString(meta.getEnchants().size())));
        inventory.setItem(29, editorButton(Material.PAPER, "flag", Integer.toString(meta.getItemFlags().size())));
        inventory.setItem(31, editorButton(Material.WARPED_FUNGUS_ON_A_STICK, "right-action",
                Integer.toString(custom.rightClick().commands().size())));
        inventory.setItem(32, editorButton(Material.BLAZE_ROD, "left-action",
                Integer.toString(custom.leftClick().commands().size())));
        inventory.setItem(45, button(Material.ARROW, "gui.editor.back", "back"));
        inventory.setItem(53, button(Material.TNT, "gui.editor.delete", "delete"));
        player.openInventory(inventory);
    }

    public void openLoreEditor(Player player, String id) {
        CustomItem custom = repository.find(id).orElse(null);
        if (custom == null) {
            lang.send(player, "command.item-not-found", "id", id);
            return;
        }
        List<Component> lore = new ArrayList<>(Optional.ofNullable(custom.template().getItemMeta().lore()).orElse(List.of()));
        EditorHolder holder = new EditorHolder(EditorHolder.View.LORE, id, 0);
        Inventory inventory = Bukkit.createInventory(holder, 54, lang.component("gui.lore.title", "id", id));
        holder.attach(inventory);
        fill(inventory);
        if (lore.isEmpty()) {
            inventory.setItem(22, button(Material.PAPER, "gui.lore.empty", "lore-add"));
        } else {
            int count = Math.min(45, lore.size());
            for (int index = 0; index < count; index++) {
                inventory.setItem(index, loreLine(index, lore.get(index)));
            }
        }
        inventory.setItem(45, button(Material.ARROW, "gui.lore.back", "back"));
        inventory.setItem(49, button(Material.LIME_DYE, "gui.lore.add", "lore-add"));
        inventory.setItem(53, button(Material.RED_DYE, "gui.lore.clear", "lore-clear"));
        player.openInventory(inventory);
    }

    public void openDelete(Player player, String id) {
        if (repository.find(id).isEmpty()) return;
        EditorHolder holder = new EditorHolder(EditorHolder.View.DELETE, id, 0);
        Inventory inventory = Bukkit.createInventory(holder, 27, lang.component("gui.delete.title", "id", id));
        holder.attach(inventory);
        fill(inventory);
        inventory.setItem(11, button(Material.TNT, "gui.delete.confirm", "confirm"));
        inventory.setItem(15, button(Material.LIME_CONCRETE, "gui.delete.cancel", "cancel"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EditorHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        String action = action(clicked);
        if (action == null) return;
        switch (holder.view()) {
            case BROWSER -> handleBrowser(player, holder, action, event.getClick());
            case EDITOR -> handleEditor(player, holder.itemId(), action, event.getClick());
            case LORE -> handleLore(player, holder.itemId(), action, event.getClick());
            case DELETE -> handleDelete(player, holder.itemId(), action);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof EditorHolder) event.setCancelled(true);
    }

    private void handleBrowser(Player player, EditorHolder holder, String action, ClickType click) {
        if (action.startsWith("item:")) {
            String id = action.substring(5);
            if (click.isRightClick()) give(player, id);
            else openEditor(player, id);
            return;
        }
        switch (action) {
            case "previous" -> openBrowser(player, holder.page() - 1);
            case "next" -> openBrowser(player, holder.page() + 1);
            case "close" -> player.closeInventory();
            case "create" -> requestCreate(player);
            default -> { }
        }
    }

    private void handleEditor(Player player, String id, String action, ClickType click) {
        CustomItem custom = repository.find(id).orElse(null);
        if (custom == null) return;
        if (action.equals("preview")) { give(player, id); return; }
        if (action.equals("back")) { openBrowser(player, 0); return; }
        if (action.equals("delete")) { openDelete(player, id); return; }
        if (action.equals("lore")) { openLoreEditor(player, id); return; }
        if (action.equals("amount")) {
            ItemStack template = custom.template();
            int step = click.isShiftClick() ? 16 : 1;
            int value = template.getAmount() + (click.isRightClick() ? -step : step);
            template.setAmount(Math.max(1, Math.min(value, template.getMaxStackSize())));
            saveAndOpen(player, custom, template);
            return;
        }
        if (List.of("unbreakable", "glint", "fire-resistant", "hide-tooltip").contains(action)) {
            ItemStack template = custom.template();
            ItemMeta meta = template.getItemMeta();
            switch (action) {
                case "unbreakable" -> meta.setUnbreakable(!meta.isUnbreakable());
                case "glint" -> {
                    boolean current = meta.hasEnchantmentGlintOverride()
                            && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
                    meta.setEnchantmentGlintOverride(!current);
                }
                case "fire-resistant" -> meta.setFireResistant(!meta.isFireResistant());
                case "hide-tooltip" -> meta.setHideTooltip(!meta.isHideTooltip());
            }
            template.setItemMeta(meta);
            saveAndOpen(player, custom, template);
            return;
        }
        if (action.equals("rarity")) {
            ItemStack template = custom.template();
            ItemMeta meta = template.getItemMeta();
            ItemRarity[] values = ItemRarity.values();
            int current = Arrays.asList(values).indexOf(meta.hasRarity() ? meta.getRarity() : ItemRarity.COMMON);
            int next = Math.floorMod(current + (click.isRightClick() ? -1 : 1), values.length);
            meta.setRarity(values[next]);
            template.setItemMeta(meta);
            saveAndOpen(player, custom, template);
            return;
        }
        if ((action.equals("enchant") || action.equals("flag")
                || action.endsWith("-action")) && click.isRightClick()) {
            clearCollection(custom, action);
            saveAndOpen(player, custom, custom.template());
            return;
        }
        requestProperty(player, custom, action);
    }

    private void handleLore(Player player, String id, String action, ClickType click) {
        CustomItem custom = repository.find(id).orElse(null);
        if (custom == null) return;
        if (action.equals("back")) { openEditor(player, id); return; }
        if (action.equals("lore-add")) {
            requestLoreAdd(player, id);
            return;
        }
        if (action.equals("lore-clear")) {
            ItemStack template = custom.template();
            ItemMeta meta = template.getItemMeta();
            meta.lore(null);
            template.setItemMeta(meta);
            saveAndOpenLore(player, custom, template);
            return;
        }
        if (!action.startsWith("lore-line:")) return;
        int index;
        try { index = Integer.parseInt(action.substring("lore-line:".length())); }
        catch (NumberFormatException ignored) { return; }
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
        if (index < 0 || index >= lore.size()) return;
        if (click.isShiftClick()) {
            int target = click.isRightClick() ? index + 1 : index - 1;
            if (target >= 0 && target < lore.size()) {
                Component line = lore.remove(index);
                lore.add(target, line);
                meta.lore(lore);
                template.setItemMeta(meta);
                saveAndOpenLore(player, custom, template);
            }
        } else if (click.isRightClick()) {
            lore.remove(index);
            meta.lore(lore.isEmpty() ? null : lore);
            template.setItemMeta(meta);
            saveAndOpenLore(player, custom, template);
        } else {
            requestLoreEdit(player, id, index);
        }
    }

    private void handleDelete(Player player, String id, String action) {
        if (action.equals("cancel")) { openEditor(player, id); return; }
        if (action.equals("confirm") && repository.delete(id)) {
            lang.send(player, "command.delete-success", "id", id);
            openBrowser(player, 0);
        }
    }

    private void requestCreate(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { lang.send(player, "command.empty-hand"); return; }
        ItemStack template = hand.clone();
        input.request(player, "input.prompt.create-id", (target, id) -> {
            String normalized = id.toLowerCase(Locale.ROOT);
            if (!ItemRepository.validId(normalized)) { lang.send(target, "command.invalid-id"); return; }
            if (repository.find(normalized).isPresent()) { lang.send(target, "command.item-exists", "id", normalized); return; }
            try {
                repository.create(normalized, template);
                lang.send(target, "command.create-from-hand-success", "id", normalized);
                openEditor(target, normalized);
            } catch (IOException exception) { saveFailed(target, exception); }
        });
    }

    private void requestProperty(Player player, CustomItem custom, String property) {
        String prompt = switch (property) {
            case "material" -> "input.prompt.material";
            case "name" -> "input.prompt.name";
            case "lore" -> "input.prompt.lore-add";
            case "model" -> "input.prompt.model";
            case "damage" -> "input.prompt.damage";
            case "max-stack" -> "input.prompt.max-stack";
            case "enchant" -> "input.prompt.enchant";
            case "flag" -> "input.prompt.flag";
            case "right-action" -> "input.prompt.right-action";
            case "left-action" -> "input.prompt.left-action";
            default -> null;
        };
        if (prompt == null) return;
        input.request(player, prompt,
                (target, value) -> applyInput(target, custom.id(), property, value),
                target -> openEditor(target, custom.id()));
    }

    private void requestLoreAdd(Player player, String id) {
        input.request(player, "input.prompt.lore-add", (target, value) -> {
            CustomItem custom = repository.find(id).orElse(null);
            if (custom == null) return;
            ItemStack template = custom.template();
            ItemMeta meta = template.getItemMeta();
            List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
            try {
                lore.add(ItemTextParser.parse(value));
                meta.lore(lore);
                template.setItemMeta(meta);
                custom.template(template);
                repository.save(custom);
                openLoreEditor(target, id);
            } catch (IllegalArgumentException exception) {
                lang.send(target, "command.invalid-operation");
                openLoreEditor(target, id);
            } catch (IOException exception) {
                saveFailed(target, exception);
                openLoreEditor(target, id);
            }
        }, target -> openLoreEditor(target, id));
    }

    private void requestLoreEdit(Player player, String id, int index) {
        input.request(player, "input.prompt.lore-edit", (target, value) -> {
            CustomItem custom = repository.find(id).orElse(null);
            if (custom == null) return;
            ItemStack template = custom.template();
            ItemMeta meta = template.getItemMeta();
            List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
            if (index < 0 || index >= lore.size()) {
                openLoreEditor(target, id);
                return;
            }
            try {
                lore.set(index, ItemTextParser.parse(value));
                meta.lore(lore);
                template.setItemMeta(meta);
                custom.template(template);
                repository.save(custom);
                openLoreEditor(target, id);
            } catch (IllegalArgumentException exception) {
                lang.send(target, "command.invalid-operation");
                openLoreEditor(target, id);
            } catch (IOException exception) {
                saveFailed(target, exception);
                openLoreEditor(target, id);
            }
        }, target -> openLoreEditor(target, id), "line", Integer.toString(index + 1));
    }

    private void applyInput(Player player, String id, String property, String value) {
        CustomItem custom = repository.find(id).orElse(null);
        if (custom == null) return;
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        try {
            switch (property) {
                case "material" -> {
                    Material material = Material.matchMaterial(value);
                    if (material == null || material.isAir() || !material.isItem()) {
                        lang.send(player, "command.invalid-material", "value", value); return;
                    }
                    template = template.withType(material);
                }
                case "name" -> meta.displayName(value.equalsIgnoreCase("none") ? null : ItemTextParser.parse(value));
                case "lore" -> {
                    List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
                    lore.add(ItemTextParser.parse(value));
                    meta.lore(lore);
                }
                case "model" -> meta.setCustomModelData(value.equalsIgnoreCase("none") ? null : Integer.parseInt(value));
                case "damage" -> {
                    if (!(meta instanceof Damageable damageable)) { lang.send(player, "command.invalid-property", "value", property); return; }
                    damageable.setDamage(Math.max(0, Integer.parseInt(value)));
                }
                case "max-stack" -> meta.setMaxStackSize(value.equalsIgnoreCase("none") ? null : Math.max(1, Math.min(99, Integer.parseInt(value))));
                case "enchant" -> {
                    if (!applyEnchant(player, meta, value)) return;
                }
                case "flag" -> {
                    if (!applyFlag(player, meta, value)) return;
                }
                case "right-action" -> {
                    if (value.equalsIgnoreCase("clear")) custom.rightClick().commands().clear();
                    else custom.rightClick().commands().add(value);
                }
                case "left-action" -> {
                    if (value.equalsIgnoreCase("clear")) custom.leftClick().commands().clear();
                    else custom.leftClick().commands().add(value);
                }
                default -> { return; }
            }
            if (!property.equals("material")) template.setItemMeta(meta);
            custom.template(template);
            repository.save(custom);
            lang.send(player, "command.update-success", "id", id, "property", property);
            openEditor(player, id);
        } catch (NumberFormatException exception) {
            lang.send(player, "command.invalid-number", "value", value);
            openEditor(player, id);
        } catch (IllegalArgumentException exception) {
            lang.send(player, "command.invalid-operation");
            openEditor(player, id);
        } catch (IOException exception) {
            saveFailed(player, exception);
            openEditor(player, id);
        }
    }

    private boolean applyEnchant(Player player, ItemMeta meta, String value) {
        if (value.equalsIgnoreCase("clear")) { meta.removeEnchantments(); return true; }
        String[] parts = value.split("\\s+");
        Enchantment enchantment = parts.length > 0 ? Enchantment.getByKey(NamespacedKey.minecraft(parts[0].toLowerCase(Locale.ROOT))) : null;
        if (enchantment == null) { lang.send(player, "command.invalid-enchantment", "value", value); return false; }
        int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        meta.addEnchant(enchantment, level, true);
        return true;
    }

    private boolean applyFlag(Player player, ItemMeta meta, String value) {
        if (value.equalsIgnoreCase("clear")) { meta.removeItemFlags(ItemFlag.values()); return true; }
        try {
            meta.addItemFlags(ItemFlag.valueOf(value.toUpperCase(Locale.ROOT)));
            return true;
        } catch (IllegalArgumentException exception) {
            lang.send(player, "command.invalid-flag", "value", value);
            return false;
        }
    }

    private void clearCollection(CustomItem custom, String action) {
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        switch (action) {
            case "lore" -> meta.lore(null);
            case "enchant" -> meta.removeEnchantments();
            case "flag" -> meta.removeItemFlags(ItemFlag.values());
            case "right-action" -> custom.rightClick().commands().clear();
            case "left-action" -> custom.leftClick().commands().clear();
        }
        template.setItemMeta(meta);
        custom.template(template);
    }

    private void saveAndOpen(Player player, CustomItem custom, ItemStack template) {
        try {
            custom.template(template);
            repository.save(custom);
            openEditor(player, custom.id());
        } catch (IOException exception) { saveFailed(player, exception); }
    }

    private void saveAndOpenLore(Player player, CustomItem custom, ItemStack template) {
        try {
            custom.template(template);
            repository.save(custom);
            openLoreEditor(player, custom.id());
        } catch (IOException exception) {
            saveFailed(player, exception);
            openLoreEditor(player, custom.id());
        }
    }

    private void give(Player player, String id) {
        repository.find(id).ifPresent(custom -> {
            boolean dropped = items.give(player, custom, 1);
            lang.send(player, "command.received", "id", id, "amount", "1");
            if (dropped) lang.send(player, "command.inventory-full");
        });
    }

    private ItemStack editorButton(Material material, String key, String value) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.component("gui.editor." + key + ".name"));
        String countKey = key.equals("lore") || key.equals("enchant") || key.equals("flag") || key.endsWith("action") ? "count" : "value";
        meta.lore(lang.components("gui.editor." + key + ".lore", countKey, value));
        tag(meta, key);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack nameButton(ItemMeta itemMeta) {
        ItemStack stack = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.component("gui.editor.name.name"));
        List<Component> configured = lang.components("gui.editor.name.lore");
        List<Component> lore = new ArrayList<>();
        if (!configured.isEmpty()) lore.add(configured.getFirst());
        lore.add(itemMeta.hasDisplayName() ? ItemTextParser.nonItalic(itemMeta.displayName()) : lang.component("gui.common.none"));
        if (configured.size() > 1) lore.addAll(configured.subList(1, configured.size()));
        meta.lore(lore);
        tag(meta, "name");
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack loreButton(ItemMeta itemMeta) {
        ItemStack stack = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = stack.getItemMeta();
        List<Component> source = new ArrayList<>(Optional.ofNullable(itemMeta.lore()).orElse(List.of()));
        meta.displayName(lang.component("gui.editor.lore.name"));
        List<Component> lore = new ArrayList<>(lang.components("gui.editor.lore.lore",
                "count", Integer.toString(source.size())));
        if (!source.isEmpty()) {
            lore.add(Component.empty());
            int visible = Math.min(5, source.size());
            for (int index = 0; index < visible; index++) lore.add(ItemTextParser.nonItalic(source.get(index)));
            if (source.size() > visible) lore.add(ItemTextParser.nonItalic(Component.text("...")));
        }
        meta.lore(lore);
        tag(meta, "lore");
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack loreLine(int index, Component value) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.component("gui.lore.line.name", "line", Integer.toString(index + 1)));
        List<Component> lore = new ArrayList<>();
        lore.add(ItemTextParser.nonItalic(value));
        lore.addAll(lang.components("gui.lore.line.controls"));
        meta.lore(lore);
        tag(meta, "lore-line:" + index);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack button(Material material, String path, String action, String... replacements) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(lang.component(path + ".name", replacements));
        meta.lore(lang.components(path + ".lore", replacements));
        tag(meta, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(lang.component("gui.common.filler-name"));
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private String state(boolean enabled) {
        return lang.plain(enabled ? "gui.common.enabled" : "gui.common.disabled");
    }

    private void tag(ItemMeta meta, String action) {
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
    }

    private String action(ItemStack stack) {
        return stack == null || !stack.hasItemMeta() ? null
                : stack.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
    }

    private void saveFailed(Player player, Exception exception) {
        plugin.getLogger().severe("Cannot save custom item: " + exception.getMessage());
        lang.send(player, "command.save-failed");
    }
}
