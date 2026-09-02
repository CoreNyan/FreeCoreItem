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

import java.util.HashMap;
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
        ItemAction action = right ? custom.rightClick() : custom.leftClick();
        if (action.commands().isEmpty() && !action.consume() && !action.cancelEvent()) return;
        if (action.cancelEvent()) event.setCancelled(true);
        if (!ready(event.getPlayer(), custom.id(), right ? "right" : "left", action.cooldownTicks())) return;
        int limit = Math.max(0, plugin.getConfig().getInt("actions.max-commands-per-trigger", 20));
        action.commands().stream().limit(limit).forEach(command -> execute(event.getPlayer(), custom, command));
        if (action.consume()) consume(event.getPlayer(), event.getItem());
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

    private void execute(Player player, CustomItem item, String raw) {
        String value = raw.replace("%player%", player.getName()).replace("%item%", item.id()).trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("[console]")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value.substring(9).trim());
        } else if (lower.startsWith("[player]")) {
            player.performCommand(value.substring(8).trim());
        } else if (lower.startsWith("[message]")) {
            player.sendMessage(miniMessage.deserialize(value.substring(9).trim()));
        } else {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value);
        }
    }

    private void consume(Player player, ItemStack stack) {
        if (stack == null) return;
        if (stack.getAmount() <= 1) player.getInventory().setItemInMainHand(null);
        else stack.setAmount(stack.getAmount() - 1);
    }
}
