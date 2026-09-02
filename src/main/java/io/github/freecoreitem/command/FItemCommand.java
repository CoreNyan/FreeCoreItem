package io.github.freecoreitem.command;

import io.github.freecoreitem.FreeCoreItemPlugin;
import io.github.freecoreitem.editor.ItemEditorMenu;
import io.github.freecoreitem.item.ItemService;
import io.github.freecoreitem.lang.Lang;
import io.github.freecoreitem.model.CustomItem;
import io.github.freecoreitem.model.ItemAction;
import io.github.freecoreitem.storage.ItemRepository;
import io.github.freecoreitem.text.ItemTextParser;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class FItemCommand implements CommandExecutor, TabCompleter {
    private final FreeCoreItemPlugin plugin;
    private final ItemRepository repository;
    private final ItemService items;
    private final ItemEditorMenu editor;
    private final Lang lang;

    public FItemCommand(FreeCoreItemPlugin plugin, ItemRepository repository, ItemService items,
                        ItemEditorMenu editor, Lang lang) {
        this.plugin = plugin;
        this.repository = repository;
        this.items = items;
        this.editor = editor;
        this.lang = lang;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        List<String> reserved = List.of("help", "editor", "create", "delete", "remove", "give", "get", "list",
                "set", "lore", "enchant", "flag", "attribute", "action", "reload");
        if (!reserved.contains(sub) && repository.find(sub).isPresent()) {
            if (!sender.hasPermission("freecoreitem.edit")) {
                lang.send(sender, "command.no-permission");
                return true;
            }
            Player player = player(sender);
            if (player != null) editor.openEditor(player, sub);
            return true;
        }
        if (!hasPermission(sender, sub)) return true;
        try {
            switch (sub) {
                case "editor" -> editor(sender, args);
                case "create" -> create(sender, args);
                case "delete", "remove" -> delete(sender, args);
                case "give", "get" -> give(sender, args);
                case "list" -> list(sender);
                case "set" -> set(sender, args);
                case "lore" -> lore(sender, args);
                case "enchant" -> enchant(sender, args);
                case "flag" -> flag(sender, args);
                case "attribute" -> attribute(sender, args);
                case "action" -> action(sender, args);
                case "reload" -> reload(sender);
                default -> lang.send(sender, "command.invalid-operation");
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Cannot save custom item: " + exception.getMessage());
            lang.send(sender, "command.save-failed");
        } catch (IllegalArgumentException exception) {
            if (!"Invalid boolean".equals(exception.getMessage())) {
                lang.send(sender, "command.invalid-operation");
            }
        }
        return true;
    }

    private void editor(CommandSender sender, String[] args) {
        Player player = player(sender);
        if (player == null) return;
        if (args.length >= 2) editor.openEditor(player, args[1]);
        else editor.openBrowser(player, 0);
    }

    private void create(CommandSender sender, String[] args) throws IOException {
        if (args.length < 2) { help(sender); return; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!ItemRepository.validId(id)) { lang.send(sender, "command.invalid-id"); return; }
        if (repository.find(id).isPresent()) { lang.send(sender, "command.item-exists", "id", id); return; }
        ItemStack template;
        if (args.length >= 3 && args[2].equalsIgnoreCase("hand")) {
            Player player = player(sender);
            if (player == null) return;
            template = player.getInventory().getItemInMainHand().clone();
            if (template.getType().isAir()) { lang.send(sender, "command.empty-hand"); return; }
        } else {
            String value = args.length >= 3 ? args[2] : "stone";
            Material material = Material.matchMaterial(value);
            if (material == null || material.isAir() || !material.isItem()) {
                lang.send(sender, "command.invalid-material", "value", value); return;
            }
            template = ItemRepository.defaultTemplate(material);
        }
        repository.create(id, template);
        lang.send(sender, args.length >= 3 && args[2].equalsIgnoreCase("hand")
                ? "command.create-from-hand-success" : "command.create-success", "id", id);
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) { help(sender); return; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (repository.find(id).isEmpty()) { lang.send(sender, "command.item-not-found", "id", id); return; }
        if (repository.delete(id)) lang.send(sender, "command.delete-success", "id", id);
        else lang.send(sender, "command.save-failed");
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) { help(sender); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        Player target = sender instanceof Player player ? player : null;
        int amount = 1;
        if (args.length >= 3) {
            Integer directAmount = integer(args[2]);
            if (directAmount != null && target != null) amount = directAmount;
            else {
                target = Bukkit.getPlayerExact(args[2]);
                if (target == null) { lang.send(sender, "command.player-only"); return; }
                if (args.length >= 4) {
                    Integer parsed = integer(args[3]);
                    if (parsed == null || parsed < 1) { lang.send(sender, "command.invalid-number", "value", args[3]); return; }
                    amount = parsed;
                }
            }
        }
        if (target == null) { lang.send(sender, "command.player-only"); return; }
        amount = Math.max(1, Math.min(2304, amount));
        boolean dropped = items.give(target, custom, amount);
        lang.send(sender, "command.give-success", "player", target.getName(), "id", custom.id(), "amount", Integer.toString(amount));
        if (!sender.equals(target)) lang.send(target, "command.received", "id", custom.id(), "amount", Integer.toString(amount));
        if (dropped) lang.send(target, "command.inventory-full");
    }

    private void list(CommandSender sender) {
        Collection<CustomItem> all = repository.all();
        if (all.isEmpty()) { lang.send(sender, "command.list-empty"); return; }
        lang.send(sender, "command.list-header", "count", Integer.toString(all.size()));
        for (CustomItem custom : all) {
            sender.sendMessage(lang.component("command.list-entry", "id", custom.id(),
                    "name", plainName(custom)));
        }
    }

    private void set(CommandSender sender, String[] args) throws IOException {
        if (args.length < 4) { lang.send(sender, "command.set-properties"); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        String property = args[2].toLowerCase(Locale.ROOT);
        String value = join(args, 3);
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        try {
            switch (property) {
                case "material" -> {
                    Material material = Material.matchMaterial(value);
                    if (material == null || material.isAir() || !material.isItem()) {
                        lang.send(sender, "command.invalid-material", "value", value); return;
                    }
                    template = template.withType(material);
                }
                case "name" -> meta.displayName(isNone(value) ? null : ItemTextParser.parse(value));
                case "amount" -> template.setAmount(clamp(Integer.parseInt(value), 1, template.getMaxStackSize()));
                case "model" -> meta.setCustomModelData(isNone(value) ? null : Integer.parseInt(value));
                case "damage" -> {
                    if (!(meta instanceof Damageable damageable)) { invalidProperty(sender, property); return; }
                    damageable.setDamage(Math.max(0, Integer.parseInt(value)));
                }
                case "max-stack" -> meta.setMaxStackSize(isNone(value) ? null : clamp(Integer.parseInt(value), 1, 99));
                case "rarity" -> meta.setRarity(ItemRarity.valueOf(value.toUpperCase(Locale.ROOT)));
                case "unbreakable" -> meta.setUnbreakable(parseBoolean(sender, value));
                case "glint" -> meta.setEnchantmentGlintOverride(parseBoolean(sender, value));
                case "fire-resistant" -> meta.setFireResistant(parseBoolean(sender, value));
                case "hide-tooltip" -> meta.setHideTooltip(parseBoolean(sender, value));
                default -> { invalidProperty(sender, property); return; }
            }
            if (!property.equals("material") && !property.equals("amount")) template.setItemMeta(meta);
            custom.template(template);
            repository.save(custom);
            updated(sender, custom, property);
        } catch (NumberFormatException exception) {
            lang.send(sender, "command.invalid-number", "value", value);
        } catch (IllegalArgumentException exception) {
            if (property.equals("rarity")) lang.send(sender, "command.invalid-rarity", "value", value);
        }
    }

    private void lore(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) { help(sender); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        List<Component> lore = new ArrayList<>(Optional.ofNullable(meta.lore()).orElse(List.of()));
        String operation = args[2].toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "add" -> lore.add(ItemTextParser.parse(join(args, 3)));
                case "set" -> lore.set(Integer.parseInt(args[3]), ItemTextParser.parse(join(args, 4)));
                case "remove" -> lore.remove(Integer.parseInt(args[3]));
                case "clear" -> lore.clear();
                default -> { lang.send(sender, "command.invalid-operation"); return; }
            }
        } catch (IndexOutOfBoundsException | NumberFormatException exception) {
            lang.send(sender, "command.invalid-number", "value", args.length > 3 ? args[3] : ""); return;
        }
        meta.lore(lore.isEmpty() ? null : lore);
        template.setItemMeta(meta);
        custom.template(template);
        repository.save(custom);
        updated(sender, custom, "lore");
    }

    private void enchant(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) { help(sender); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        String operation = args[2].toLowerCase(Locale.ROOT);
        if (operation.equals("clear")) meta.removeEnchantments();
        else {
            if (args.length < 4) { help(sender); return; }
            Enchantment enchantment = enchantment(args[3]);
            if (enchantment == null) { lang.send(sender, "command.invalid-enchantment", "value", args[3]); return; }
            if (operation.equals("remove")) meta.removeEnchant(enchantment);
            else if (operation.equals("add")) {
                int level;
                try { level = args.length >= 5 ? Integer.parseInt(args[4]) : 1; }
                catch (NumberFormatException exception) { lang.send(sender, "command.invalid-number", "value", args[4]); return; }
                meta.addEnchant(enchantment, level, true);
            } else { lang.send(sender, "command.invalid-operation"); return; }
        }
        template.setItemMeta(meta);
        custom.template(template);
        repository.save(custom);
        updated(sender, custom, "enchant");
    }

    private void flag(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) { help(sender); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        String operation = args[2].toLowerCase(Locale.ROOT);
        if (operation.equals("clear")) meta.removeItemFlags(ItemFlag.values());
        else {
            if (args.length < 4) { help(sender); return; }
            ItemFlag flag;
            try { flag = ItemFlag.valueOf(args[3].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { lang.send(sender, "command.invalid-flag", "value", args[3]); return; }
            if (operation.equals("add")) meta.addItemFlags(flag);
            else if (operation.equals("remove")) meta.removeItemFlags(flag);
            else { lang.send(sender, "command.invalid-operation"); return; }
        }
        template.setItemMeta(meta);
        custom.template(template);
        repository.save(custom);
        updated(sender, custom, "flag");
    }

    private void attribute(CommandSender sender, String[] args) throws IOException {
        if (args.length < 3) { help(sender); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        ItemStack template = custom.template();
        ItemMeta meta = template.getItemMeta();
        String operation = args[2].toLowerCase(Locale.ROOT);
        if (operation.equals("clear")) {
            meta.setAttributeModifiers(null);
        } else {
            if (!operation.equals("add") || args.length < 6) { help(sender); return; }
            String attributeKey = args[3].toLowerCase(Locale.ROOT).replace("minecraft:", "");
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attributeKey));
            if (attribute == null) { lang.send(sender, "command.invalid-property", "value", args[3]); return; }
            double amount;
            try { amount = Double.parseDouble(args[4]); }
            catch (NumberFormatException exception) { lang.send(sender, "command.invalid-number", "value", args[4]); return; }
            AttributeModifier.Operation modifierOperation;
            try { modifierOperation = AttributeModifier.Operation.valueOf(args[5].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException exception) { lang.send(sender, "command.invalid-operation"); return; }
            org.bukkit.inventory.EquipmentSlotGroup slot = args.length >= 7
                    ? org.bukkit.inventory.EquipmentSlotGroup.getByName(args[6].toUpperCase(Locale.ROOT))
                    : org.bukkit.inventory.EquipmentSlotGroup.ANY;
            if (slot == null) slot = org.bukkit.inventory.EquipmentSlotGroup.ANY;
            meta.addAttributeModifier(attribute, new AttributeModifier(
                    new NamespacedKey(plugin, "fitem_" + custom.id() + "_" + System.nanoTime()), amount,
                    modifierOperation, slot));
        }
        template.setItemMeta(meta);
        custom.template(template);
        repository.save(custom);
        updated(sender, custom, "attribute");
    }

    private void action(CommandSender sender, String[] args) throws IOException {
        if (args.length < 4) { help(sender); return; }
        CustomItem custom = require(sender, args[1]);
        if (custom == null) return;
        ItemAction action = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "right-click", "right" -> custom.rightClick();
            case "left-click", "left" -> custom.leftClick();
            default -> null;
        };
        if (action == null) { lang.send(sender, "command.invalid-action"); return; }
        String operation = args[3].toLowerCase(Locale.ROOT);
        try {
            switch (operation) {
                case "add" -> action.commands().add(join(args, 4));
                case "remove" -> action.commands().remove(Integer.parseInt(args[4]));
                case "clear" -> action.commands().clear();
                case "cooldown" -> action.cooldownTicks(Integer.parseInt(args[4]));
                case "consume" -> action.consume(parseBoolean(sender, args[4]));
                case "cancel" -> action.cancelEvent(parseBoolean(sender, args[4]));
                default -> { lang.send(sender, "command.invalid-operation"); return; }
            }
        } catch (IndexOutOfBoundsException | NumberFormatException exception) {
            lang.send(sender, "command.invalid-number", "value", args.length > 4 ? args[4] : ""); return;
        }
        repository.save(custom);
        updated(sender, custom, "action");
    }

    private void reload(CommandSender sender) {
        plugin.reloadAll();
        lang.send(sender, "command.reload-success", "count", Integer.toString(repository.size()));
    }

    private boolean hasPermission(CommandSender sender, String sub) {
        String permission = switch (sub) {
            case "create" -> "freecoreitem.create";
            case "delete", "remove" -> "freecoreitem.delete";
            case "give", "get" -> "freecoreitem.give";
            case "reload" -> "freecoreitem.reload";
            case "editor", "set", "lore", "enchant", "flag", "attribute", "action" -> "freecoreitem.edit";
            default -> "freecoreitem.use";
        };
        if (sender.hasPermission(permission)) return true;
        lang.send(sender, "command.no-permission");
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        lang.send(sender, "command.player-only");
        return null;
    }

    private CustomItem require(CommandSender sender, String id) {
        CustomItem custom = repository.find(id).orElse(null);
        if (custom == null) lang.send(sender, "command.item-not-found", "id", id);
        return custom;
    }

    private void updated(CommandSender sender, CustomItem item, String property) {
        lang.send(sender, "command.update-success", "id", item.id(), "property", property);
    }

    private void invalidProperty(CommandSender sender, String property) {
        lang.send(sender, "command.invalid-property", "value", property);
    }

    private boolean parseBoolean(CommandSender sender, String value) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        lang.send(sender, "command.invalid-boolean");
        throw new IllegalArgumentException("Invalid boolean");
    }

    private String plainName(CustomItem custom) {
        Component name = items.displayName(custom);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
    }

    private static Enchantment enchantment(String value) {
        String key = value.toLowerCase(Locale.ROOT);
        if (key.startsWith("minecraft:")) key = key.substring(10);
        return Enchantment.getByKey(NamespacedKey.minecraft(key));
    }

    private static String join(String[] args, int start) {
        return start >= args.length ? "" : String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private static boolean isNone(String value) {
        return value.equalsIgnoreCase("none") || value.equalsIgnoreCase("null");
    }

    private static Integer integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void help(CommandSender sender) {
        for (Component line : lang.components("command.help")) sender.sendMessage(line);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> roots = new ArrayList<>(List.of("help", "editor", "create", "delete", "give", "list",
                    "set", "lore", "enchant", "flag", "attribute", "action", "reload"));
            roots.addAll(ids());
            return filter(roots, args[0]);
        }
        if (args.length == 2 && List.of("editor", "delete", "give", "set", "lore", "enchant", "flag", "attribute", "action")
                .contains(args[0].toLowerCase(Locale.ROOT))) return filter(ids(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) return filter(List.of("hand", "stone", "diamond_sword"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) return filter(List.of("material", "name", "amount", "model",
                "damage", "max-stack", "rarity", "unbreakable", "glint", "fire-resistant", "hide-tooltip"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("lore")) return filter(List.of("add", "set", "remove", "clear"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("enchant")) return filter(List.of("add", "remove", "clear"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("flag")) return filter(List.of("add", "remove", "clear"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("attribute")) return filter(List.of("add", "clear"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("attribute") && args[2].equalsIgnoreCase("add")) {
            List<String> attributes = new ArrayList<>();
            Registry.ATTRIBUTE.forEach(value -> attributes.add(value.getKey().getKey()));
            return filter(attributes, args[3]);
        }
        if (args.length == 6 && args[0].equalsIgnoreCase("attribute") && args[2].equalsIgnoreCase("add"))
            return filter(Arrays.stream(AttributeModifier.Operation.values()).map(Enum::name).toList(), args[5]);
        if (args.length == 7 && args[0].equalsIgnoreCase("attribute") && args[2].equalsIgnoreCase("add"))
            return filter(List.of("ANY", "HAND", "MAINHAND", "OFFHAND", "HEAD", "CHEST", "LEGS", "FEET", "BODY", "SADDLE"), args[6]);
        if (args.length == 3 && args[0].equalsIgnoreCase("action")) return filter(List.of("right-click", "left-click"), args[2]);
        if (args.length == 4 && args[0].equalsIgnoreCase("action")) return filter(List.of("add", "remove", "clear", "cooldown", "consume", "cancel"), args[3]);
        if (args.length == 4 && args[0].equalsIgnoreCase("set") && List.of("unbreakable", "glint", "fire-resistant", "hide-tooltip").contains(args[2].toLowerCase(Locale.ROOT)))
            return filter(List.of("true", "false"), args[3]);
        if (args.length == 4 && args[0].equalsIgnoreCase("set") && args[2].equalsIgnoreCase("rarity"))
            return filter(Arrays.stream(ItemRarity.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList(), args[3]);
        if (args.length == 4 && args[0].equalsIgnoreCase("flag") && !args[2].equalsIgnoreCase("clear"))
            return filter(Arrays.stream(ItemFlag.values()).map(Enum::name).toList(), args[3]);
        if (args.length == 5 && args[0].equalsIgnoreCase("action") && List.of("consume", "cancel").contains(args[3].toLowerCase(Locale.ROOT)))
            return filter(List.of("true", "false"), args[4]);
        return List.of();
    }

    private List<String> ids() {
        return repository.all().stream().map(CustomItem::id).toList();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
