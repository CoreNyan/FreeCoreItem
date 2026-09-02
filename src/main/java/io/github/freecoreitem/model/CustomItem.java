package io.github.freecoreitem.model;

import org.bukkit.inventory.ItemStack;

public final class CustomItem {
    private final String id;
    private ItemStack template;
    private final ItemAction rightClick;
    private final ItemAction leftClick;

    public CustomItem(String id, ItemStack template, ItemAction rightClick, ItemAction leftClick) {
        this.id = id;
        this.template = template.clone();
        this.rightClick = rightClick;
        this.leftClick = leftClick;
    }

    public String id() { return id; }
    public ItemStack template() { return template.clone(); }
    public void template(ItemStack value) { template = value.clone(); }
    public ItemAction rightClick() { return rightClick; }
    public ItemAction leftClick() { return leftClick; }
}
