package io.github.freecoreitem.item;

import io.github.freecoreitem.model.CustomItem;
import io.github.freecoreitem.storage.ItemRepository;
import io.github.freecoreitem.text.ItemTextParser;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemService implements Listener {
    private final Plugin plugin;
    private final ItemRepository repository;
    private final NamespacedKey idKey;

    public ItemService(Plugin plugin, ItemRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.idKey = new NamespacedKey(plugin, "item_id");
    }

    public ItemStack build(CustomItem item, int amount) {
        ItemStack stack = item.template();
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasDisplayName()) {
            meta.displayName(ItemTextParser.nonItalic(meta.displayName()));
        }
        List<Component> lore = meta.lore();
        if (lore != null) {
            meta.lore(lore.stream().map(ItemTextParser::nonItalic).toList());
        }
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, item.id());
        stack.setItemMeta(meta);
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    public Optional<CustomItem> identify(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String id = stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : repository.find(id);
    }

    public boolean give(Player player, CustomItem item, int total) {
        int remaining = Math.max(1, total);
        boolean dropped = false;
        while (remaining > 0) {
            ItemStack stack = build(item, Math.min(remaining, item.template().getMaxStackSize()));
            remaining -= stack.getAmount();
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        return dropped;
    }

    public Component displayName(CustomItem item) {
        ItemMeta meta = item.template().getItemMeta();
        return meta.hasDisplayName() ? meta.displayName() : Component.translatable(item.template().getType());
    }

    public void refreshOnlineInventories() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refreshInventory(player);
        }
    }

    public void refreshOnlineInventories(CustomItem item) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refreshInventory(player, item);
        }
    }

    public void refreshInventory(Player player) {
        List<CustomItem> knownItems = new ArrayList<>(repository.all());
        for (CustomItem item : knownItems) {
            refreshInventory(player, item);
        }
    }

    private void refreshInventory(Player player, CustomItem item) {
        Inventory inventory = player.getInventory();
        List<ItemStack> excess = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (!hasId(existing, item.id())) continue;
            int amount = existing.getAmount();
            ItemStack replacement = build(item, amount);
            inventory.setItem(slot, replacement);
            addExcess(item, amount - replacement.getAmount(), excess);
        }

        ItemStack cursor = player.getItemOnCursor();
        if (hasId(cursor, item.id())) {
            int amount = cursor.getAmount();
            ItemStack replacement = build(item, amount);
            player.setItemOnCursor(replacement);
            addExcess(item, amount - replacement.getAmount(), excess);
        }

        for (ItemStack stack : excess) {
            for (ItemStack leftover : inventory.addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }

    private boolean hasId(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) return false;
        String stored = stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id.equals(stored);
    }

    private void addExcess(CustomItem item, int amount, List<ItemStack> excess) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = build(item, remaining);
            excess.add(stack);
            remaining -= stack.getAmount();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshInventory(event.getPlayer()));
    }
}
