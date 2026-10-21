package io.github.freecoreitem.action;

import io.github.freecoreitem.item.ItemService;
import io.github.freecoreitem.lang.Lang;
import io.github.freecoreitem.model.CustomItem;
import io.github.freecoreitem.model.ItemAction;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ItemActionListener implements Listener {
    private final Plugin plugin;
    private final ItemService items;
    private final Lang lang;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public ItemActionListener(Plugin plugin, ItemService items, Lang lang) {
        this.plugin = plugin;
        this.items = items;
        this.lang = lang;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("actions.enabled", true) || event.getHand() != EquipmentSlot.HAND) return;
        boolean right = event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!right && !left) return;
        CustomItem custom = items.identify(event.getItem()).orElse(null);
        if (custom == null) return;
        plugin.getLogger().info("[FreeCoreItem Debug] " + event.getPlayer().getName() + " interacted with " + custom.id() + " (action: " + event.getAction() + ")");
        if (custom.bindOnGive() && items.isBound(event.getItem()) && !items.ownedBy(event.getItem(), event.getPlayer())) {
            event.setCancelled(true);
            lang.send(event.getPlayer(), "action.not-owner");
            return;
        }
        ItemAction action = right ? custom.rightClick() : custom.leftClick();
        if (action.commands().isEmpty() && !action.consume() && !action.cancelEvent()) return;
        if (!conditionsPass(event.getPlayer(), action)) return;
        if (!ready(event.getPlayer(), custom.id(), right ? "right" : "left", action.cooldownTicks())) {
            if (action.cancelEvent()) event.setCancelled(true);
            return;
        }
        if (action.cancelEvent()) event.setCancelled(true);
        int limit = Math.max(0, plugin.getConfig().getInt("actions.max-commands-per-trigger", 20));
        action.commands().stream().limit(limit).forEach(command -> executeIfEligible(event.getPlayer(), custom, event.getItem(), command));
        if (action.consume()) consume(event.getPlayer(), event.getItem());
    }

    private boolean conditionsPass(Player player, ItemAction action) {
        for (String raw : action.conditions()) {
            if (raw == null || raw.isBlank()) continue;
            if (!conditionPass(player, raw.trim())) return false;
        }
        return true;
    }

    private boolean conditionPass(Player player, String condition) {
        String trimmed = condition.trim();
        boolean negate = false;
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("not ") || lower.startsWith("!")) {
            negate = true;
            trimmed = lower.startsWith("not ") ? trimmed.substring(4).trim() : trimmed.substring(1).trim();
            lower = trimmed.toLowerCase(Locale.ROOT);
        }

        boolean pass = evaluateCondition(player, trimmed, lower);
        return negate ? !pass : pass;
    }

    private boolean evaluateCondition(Player player, String condition, String lower) {
        if (lower.startsWith("permission:")) {
            String permission = condition.substring("permission:".length()).trim();
            return !permission.isEmpty() && player.hasPermission(permission);
        }
        if (lower.startsWith("server:")) {
            String expected = condition.substring("server:".length()).trim();
            String serverId = plugin.getConfig().getString("server-id", "").trim();
            // Missing or unset IDs never match, including an empty condition.
            return !expected.isEmpty() && !serverId.isEmpty() && serverId.equalsIgnoreCase(expected);
        }
        if (lower.startsWith("title:")) {
            String expected = condition.substring("title:".length()).trim();
            return hasTitleOrTag(player, expected);
        }
        if (lower.startsWith("tag:")) {
            String expected = condition.substring("tag:".length()).trim();
            return hasTitleOrTag(player, expected);
        }
        if (lower.startsWith("placeholder:")) {
            String expression = condition.substring("placeholder:".length()).trim();
            return checkPlaceholderExpression(player, expression);
        }
        plugin.getLogger().warning("Ignoring unknown FreeCoreItem condition: " + condition);
        return false;
    }

    private boolean hasTitleOrTag(Player player, String expected) {
        if (expected.isEmpty()) return false;
        String expLower = expected.toLowerCase(Locale.ROOT);

        // 1. Check FotiaTags ownership via reflection if present
        try {
            org.bukkit.plugin.Plugin fotia = Bukkit.getPluginManager().getPlugin("FotiaTags");
            if (fotia != null && fotia.isEnabled()) {
                Object tagManager = fotia.getClass().getMethod("getTagManager").invoke(fotia);
                if (tagManager != null) {
                    Object playerData = tagManager.getClass().getMethod("getPlayerData", UUID.class)
                            .invoke(tagManager, player.getUniqueId());
                    if (playerData != null) {
                        Boolean has = (Boolean) playerData.getClass().getMethod("hasTag", String.class)
                                .invoke(playerData, expected);
                        if (Boolean.TRUE.equals(has)) return true;
                    }

                    java.util.List<?> owned = (java.util.List<?>) tagManager.getClass()
                            .getMethod("getOwnedTags", UUID.class)
                            .invoke(tagManager, player.getUniqueId());
                    if (owned != null) {
                        for (Object o : owned) {
                            if (o != null && expLower.equalsIgnoreCase(o.toString().trim())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[FreeCoreItem Debug] FotiaTags check failed: " + t.getMessage());
        }

        // 2. Check current equipped title text via %freecore_title%
        String title = resolvePlaceholder(player, "%freecore_title%");
        if (title != null && !title.equals("%freecore_title%")) {
            String stripped = org.bukkit.ChatColor.stripColor(title.replace('&', '§')).toLowerCase(Locale.ROOT);
            if (stripped.contains(expLower)) return true;
        }

        // 3. Check current equipped tag name via %fotiatags_tag_name% & %fotiatags_tag%
        String currentTagId = resolvePlaceholder(player, "%fotiatags_tag%");
        if (currentTagId != null && currentTagId.equalsIgnoreCase(expected)) return true;

        String currentTagName = resolvePlaceholder(player, "%fotiatags_tag_name%");
        if (currentTagName != null) {
            String stripped = org.bukkit.ChatColor.stripColor(currentTagName.replace('&', '§')).toLowerCase(Locale.ROOT);
            if (stripped.contains(expLower)) return true;
        }

        return false;
    }

    private boolean checkPlaceholderExpression(Player player, String expression) {
        String[] operators = {">=", "<=", "!=", "==", ">", "<", "="};
        for (String op : operators) {
            int idx = expression.indexOf(op);
            if (idx > 0) {
                String key = expression.substring(0, idx).trim();
                String expected = expression.substring(idx + op.length()).trim();
                String actual = resolvePlaceholder(player, key);
                if (actual == null) return false;

                switch (op) {
                    case "!=":
                        return !actual.equalsIgnoreCase(expected);
                    case "==":
                    case "=":
                        return actual.equalsIgnoreCase(expected);
                    case ">":
                    case ">=":
                    case "<":
                    case "<=":
                        try {
                            double actualNum = Double.parseDouble(actual.replace(",", ""));
                            double expectedNum = Double.parseDouble(expected.replace(",", ""));
                            return switch (op) {
                                case ">" -> actualNum > expectedNum;
                                case ">=" -> actualNum >= expectedNum;
                                case "<" -> actualNum < expectedNum;
                                case "<=" -> actualNum <= expectedNum;
                                default -> false;
                            };
                        } catch (NumberFormatException e) {
                            return false;
                        }
                }
            }
        }
        return false;
    }

    private String resolvePlaceholder(Player player, String expression) {
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            Object value = api.getMethod("setPlaceholders", Player.class, String.class)
                    .invoke(null, player, expression);
            return value == null ? null : value.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private boolean ready(Player player, String itemId, String trigger, int cooldownTicks) {
        if (cooldownTicks <= 0 || player.hasPermission("freecoreitem.cooldown.bypass")) return true;
        String key = player.getUniqueId() + ":" + itemId + ":" + trigger;
        long now = System.currentTimeMillis();
        long expires = cooldowns.getOrDefault(key, 0L);
        if (expires > now) {
            long seconds = Math.max(1, (expires - now + 999) / 1000);
            lang.send(player, "action.cooldown", "seconds", Long.toString(seconds));
            return false;
        }
        cooldowns.put(key, now + cooldownTicks * 50L);
        return true;
    }

    private void executeIfEligible(Player player, CustomItem item, ItemStack stack, String raw) {
        if (raw == null) return;
        String line = raw.trim();
        List<String> conditions = new ArrayList<>();

        while (line.regionMatches(true, 0, "[if", 0, 3)) {
            // A malformed conditional must not fall through to console execution.
            if (line.length() <= 3 || !Character.isWhitespace(line.charAt(3))) {
                plugin.getLogger().warning("Invalid FreeCoreItem conditional command: " + line);
                return;
            }
            int end = line.indexOf(']');
            if (end < 0) {
                plugin.getLogger().warning("Unclosed [if] condition in: " + line);
                return;
            }
            conditions.add(line.substring(3, end).trim());
            line = line.substring(end + 1).trim();
        }

        for (String condStr : conditions) {
            boolean passed = conditionPass(player, condStr);
            plugin.getLogger().info("[FreeCoreItem Debug] " + player.getName() + " check condition: '" + condStr + "' -> " + passed);
            if (!passed) return;
        }

        if (line.isEmpty()) return;

        // Support chained commands split by ;; or \n or ; (while not inside brackets)
        List<String> subCommands = splitCommands(line);
        for (String subCmd : subCommands) {
            String trimmed = subCmd.trim();
            if (trimmed.isEmpty()) continue;
            plugin.getLogger().info("[FreeCoreItem Debug] " + player.getName() + " executing command: " + trimmed);
            execute(player, item, stack, trimmed);
        }
    }

    private List<String> splitCommands(String line) {
        List<String> result = new ArrayList<>();
        if (line.contains(";;")) {
            for (String s : line.split(";;")) {
                if (!s.isBlank()) result.add(s.trim());
            }
            return result;
        }
        if (line.contains("\n")) {
            for (String s : line.split("\n")) {
                if (!s.isBlank()) result.add(s.trim());
            }
            return result;
        }
        result.add(line);
        return result;
    }

    private void execute(Player player, CustomItem item, ItemStack stack, String raw) {
        String value = raw.replace("%player%", player.getName()).replace("%item%", item.id()).trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("[consume]") || lower.equals("[remove]")) {
            consume(player, stack);
            return;
        }
        if (lower.startsWith("[consume]") || lower.startsWith("[remove]")) {
            consume(player, stack);
            String remaining = lower.startsWith("[consume]") ? value.substring(9).trim() : value.substring(8).trim();
            if (!remaining.isEmpty()) {
                execute(player, item, stack, remaining);
            }
            return;
        }
        if (lower.startsWith("[console]")) {
            dispatchConsole(value.substring(9));
        } else if (lower.startsWith("[player]")) {
            dispatchPlayer(player, value.substring(8));
        } else if (lower.startsWith("[op]")) {
            String command = normalizeCommand(value.substring(4));
            if (command.isEmpty()) return;
            boolean wasOp = player.isOp();
            try {
                if (!wasOp) player.setOp(true);
                player.performCommand(command);
            } finally {
                if (player.isOp() != wasOp) player.setOp(wasOp);
            }
        } else if (lower.startsWith("[message]")) {
            player.sendMessage(miniMessage.deserialize(value.substring(9).trim()));
        } else if (!lower.startsWith("[")) {
            dispatchConsole(value);
        } else {
            plugin.getLogger().warning("Unknown FreeCoreItem command prefix: " + value);
        }
    }

    private void dispatchConsole(String value) {
        String command = normalizeCommand(value);
        if (!command.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void dispatchPlayer(Player player, String value) {
        String command = normalizeCommand(value);
        if (!command.isEmpty()) player.performCommand(command);
    }

    private String normalizeCommand(String value) {
        String command = value.trim();
        return command.startsWith("/") ? command.substring(1).trim() : command;
    }

    private void consume(Player player, ItemStack stack) {
        if (stack == null) return;
        if (stack.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else stack.setAmount(stack.getAmount() - 1);
    }
}
