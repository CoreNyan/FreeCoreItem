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
import java.util.UUID;

public final class ItemService implements Listener {
    private final Plugin plugin;
    private final ItemRepository repository;
    private final NamespacedKey idKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey ownerNameKey;

    public ItemService(Plugin plugin, ItemRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
        this.idKey = new NamespacedKey(plugin, "item_id");
        this.ownerKey = new NamespacedKey(plugin, "item_owner");
        this.ownerNameKey = new NamespacedKey(plugin, "item_owner_name");
    }

    public ItemStack build(CustomItem item, int amount) {
        return build(item, amount, null);
    }

    private ItemStack build(CustomItem item, int amount, UUID owner) {
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
        if (item.bindOnGive() && owner != null) {
            meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
            org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(owner);
            String ownerName = op.getName() != null ? op.getName() : owner.toString().substring(0, 8);
            meta.getPersistentDataContainer().set(ownerNameKey, PersistentDataType.STRING, ownerName);
            appendOwnerLore(meta, ownerName);
        } else {
            meta.getPersistentDataContainer().remove(ownerKey);
            meta.getPersistentDataContainer().remove(ownerNameKey);
        }
        stack.setItemMeta(meta);
        stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        return stack;
    }

    public Optional<CustomItem> identify(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return Optional.empty();
        String id = stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : repository.find(id);
    }

    public boolean ownedBy(ItemStack stack, Player player) {
        if (stack == null || !stack.hasItemMeta()) return false;
        String owner = stack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return owner != null && owner.equalsIgnoreCase(player.getUniqueId().toString());
    }

    public boolean isBound(ItemStack stack) {
        return stack != null && stack.hasItemMeta()
                && stack.getItemMeta().getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    public boolean bindOnGive(ItemStack stack) {
        return identify(stack).map(CustomItem::bindOnGive).orElse(false);
    }

    public ItemStack bindTo(ItemStack stack, UUID owner) {
        if (stack == null || stack.getType().isAir() || owner == null) return stack;
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());
        org.bukkit.OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(owner);
        String ownerName = op.getName() != null ? op.getName() : owner.toString().substring(0, 8);
        meta.getPersistentDataContainer().set(ownerNameKey, PersistentDataType.STRING, ownerName);
        appendOwnerLore(meta, ownerName);
        result.setItemMeta(meta);
        return result;
    }

    public ItemStack unbind(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return stack;
        ItemStack result = stack.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return result;
        meta.getPersistentDataContainer().remove(ownerKey);
        meta.getPersistentDataContainer().remove(ownerNameKey);
        removeOwnerLore(meta);
        result.setItemMeta(meta);
        return result;
    }

    public UUID getOwner(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String s = stack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void appendOwnerLore(ItemMeta meta, String ownerName) {
        List<Component> currentLore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        currentLore.removeIf(comp -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
            return plain.contains("该物品的主人是");
        });
        currentLore.add(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                .deserialize("<!italic><gray>该物品的主人是: <yellow>" + ownerName + "</yellow></gray>"));
        meta.lore(currentLore);
    }

    public static void removeOwnerLore(ItemMeta meta) {
        if (!meta.hasLore() || meta.lore() == null) return;
        List<Component> currentLore = new ArrayList<>(meta.lore());
        boolean removed = currentLore.removeIf(comp -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(comp);
            return plain.contains("该物品的主人是");
        });
        if (removed) {
            meta.lore(currentLore);
        }
    }

    public boolean give(Player player, CustomItem item, int total) {
        int remaining = Math.max(1, total);
        boolean dropped = false;
        while (remaining > 0) {
            ItemStack stack = build(item, Math.min(remaining, item.template().getMaxStackSize()),
                    item.bindOnGive() ? player.getUniqueId() : null);
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
            ItemStack replacement = build(item, amount, ownerOf(existing));
            inventory.setItem(slot, replacement);
            addExcess(item, amount - replacement.getAmount(), excess, ownerOf(existing));
        }

        ItemStack cursor = player.getItemOnCursor();
        if (hasId(cursor, item.id())) {
            int amount = cursor.getAmount();
            ItemStack replacement = build(item, amount, ownerOf(cursor));
            player.setItemOnCursor(replacement);
            addExcess(item, amount - replacement.getAmount(), excess, ownerOf(cursor));
        }

        for (ItemStack stack : excess) {
            for (ItemStack leftover : inventory.addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        player.updateInventory();
    }

    private UUID ownerOf(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String value = stack.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) return null;
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private boolean hasId(ItemStack stack, String id) {
        if (stack == null || !stack.hasItemMeta()) return false;
        String stored = stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        return id.equals(stored);
    }

    private void addExcess(CustomItem item, int amount, List<ItemStack> excess, UUID owner) {
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = build(item, remaining, owner);
            excess.add(stack);
            remaining -= stack.getAmount();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshInventory(event.getPlayer()));
    }
}
