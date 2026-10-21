package io.github.freecoreitem.listener;

import io.github.freecoreitem.item.ItemService;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.Plugin;

public final class TradeBindingListener implements Listener {
    private final Plugin plugin;
    private final ItemService items;

    public TradeBindingListener(Plugin plugin, ItemService items) {
        this.plugin = plugin;
        this.items = items;
    }

    /**
     * 当玩家在交易界面（Merchant / Shopkeeper）中操作输入槽位时的智能解绑与安全防护：
     * 1. 放入输入槽（Slot 0 或 Slot 1）：
     *    - 如果是自己绑定的 FItem：自动解绑为纯净模板物品，以便 Shopkeeper 能够匹配交易；
     *    - 如果不是自己的绑定 FItem：严密拦截，取消操作，不作任何反应，防止盗窃兑换。
     * 2. 拿出输入槽（回到背包或光标）：
     *    - 重新自动绑定给该玩家本人，防止卡物品洗白。
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMerchantClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.MERCHANT) return;

        int rawSlot = event.getRawSlot();
        boolean isTop = event.getClickedInventory() == top;

        // 1. 玩家点击的是商人的输入槽（Slot 0, 1）
        if (isTop && (rawSlot == 0 || rawSlot == 1)) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir() && items.bindOnGive(cursor)) {
                if (items.isBound(cursor)) {
                    if (!items.ownedBy(cursor, player)) {
                        // 不是自己的绑定物品：直接拦截，没有任何反应
                        event.setCancelled(true);
                        return;
                    }
                    // 是自己的绑定物品：放入交易槽位时自动解绑为纯净模板，供 Shopkeeper 匹配
                    // 将光标物品转为无绑定物品后由事件放入
                    ItemStack unbound = items.unbind(cursor);
                    event.getWhoClicked().setItemOnCursor(unbound);
                }
            }
        }

        // 2. 玩家在背包中 Shift-Click 将物品快速移入商人输入槽
        if (!isTop && event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && !current.getType().isAir() && items.bindOnGive(current)) {
                if (items.isBound(current)) {
                    if (!items.ownedBy(current, player)) {
                        // 不是自己的绑定物品：拦截 Shift 点击
                        event.setCancelled(true);
                        return;
                    }
                    // 是自己的绑定物品：解绑后放入
                    ItemStack unbound = items.unbind(current);
                    event.setCurrentItem(unbound);
                }
            }
        }

        // 3. 在下一个 tick 检查：
        // (a) 输入槽（Slot 0, 1）如果留存了绑定给本人的物品，自动将其转换为解绑纯净物品
        // (b) 玩家光标或背包如果留存了解绑的 FItem，立即重新绑定回给本人
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory().getTopInventory().getType() != InventoryType.MERCHANT) {
                return;
            }
            Inventory currentTop = player.getOpenInventory().getTopInventory();
            for (int slot = 0; slot <= 1; slot++) {
                ItemStack item = currentTop.getItem(slot);
                if (item != null && !item.getType().isAir() && items.bindOnGive(item)) {
                    if (items.isBound(item)) {
                        if (items.ownedBy(item, player)) {
                            currentTop.setItem(slot, items.unbind(item));
                        } else {
                            // 非法占有者的物品，移出输入槽
                            currentTop.setItem(slot, null);
                            player.getInventory().addItem(item);
                        }
                    }
                }
            }

            // 检查玩家光标
            ItemStack cursorNow = player.getItemOnCursor();
            if (cursorNow != null && !cursorNow.getType().isAir() && items.bindOnGive(cursorNow)) {
                if (!items.isBound(cursorNow)) {
                    player.setItemOnCursor(items.bindTo(cursorNow, player.getUniqueId()));
                }
            }

            // 检查玩家背包
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack invItem = player.getInventory().getItem(slot);
                if (invItem != null && !invItem.getType().isAir() && items.bindOnGive(invItem)) {
                    if (!items.isBound(invItem)) {
                        player.getInventory().setItem(slot, items.bindTo(invItem, player.getUniqueId()));
                    }
                }
            }
            player.updateInventory();
        });
    }

    /**
     * 防止拖拽非自己绑定的物品进入商人槽位
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMerchantDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.MERCHANT) return;

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot == 0 || rawSlot == 1) {
                ItemStack old = event.getOldCursor();
                if (old != null && !old.getType().isAir() && items.bindOnGive(old)) {
                    if (items.isBound(old) && !items.ownedBy(old, player)) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 玩家关闭商人界面时：
     * 如果输入槽或背包里还留有任何解绑的 FItem，统统重新签署并绑定回给该玩家本人！
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top.getType() != InventoryType.MERCHANT) return;

        // 如果输入槽里还有未兑换完拿出的材料，返还时确保绑定
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack item = top.getItem(slot);
            if (item != null && !item.getType().isAir() && items.bindOnGive(item)) {
                top.setItem(slot, items.bindTo(item, player.getUniqueId()));
            }
        }

        // 重新检查玩家背包中所有 bindOnGive 物品，未绑定的补签回给该玩家
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                ItemStack item = player.getInventory().getItem(slot);
                if (item != null && !item.getType().isAir() && items.bindOnGive(item) && !items.isBound(item)) {
                    player.getInventory().setItem(slot, items.bindTo(item, player.getUniqueId()));
                }
            }
            ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && !cursor.getType().isAir() && items.bindOnGive(cursor) && !items.isBound(cursor)) {
                player.setItemOnCursor(items.bindTo(cursor, player.getUniqueId()));
            }
            player.updateInventory();
        });
    }

    /**
     * 交易完成产出新物品时，若是 bindOnGive 物品，自动签署并绑定为该玩家
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        Player player = event.getPlayer();
        ItemStack result = event.getTrade().getResult();
        if (!items.bindOnGive(result)) return;
        MerchantRecipe original = event.getTrade();
        MerchantRecipe trade = new MerchantRecipe(
                items.bindTo(result, player.getUniqueId()), original.getMaxUses(), original.getUses(),
                original.hasExperienceReward(), original.getVillagerExperience(), original.getPriceMultiplier(),
                original.getDemand(), original.getSpecialPrice(), original.shouldIgnoreDiscounts());
        trade.setIngredients(original.getIngredients());
        event.setTrade(trade);
    }
}


