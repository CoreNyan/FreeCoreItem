package io.github.freecoreitem.model;

import org.bukkit.inventory.ItemStack;

public final class CustomItem {
    private final String id;
    private ItemStack template;
    private final ItemAction rightClick;
    private final ItemAction leftClick;
    private boolean bindOnGive;

    public CustomItem(String id, ItemStack template, ItemAction rightClick, ItemAction leftClick) {
        this(id, template, rightClick, leftClick, false);
    }

    public CustomItem(String id, ItemStack template, ItemAction rightClick, ItemAction leftClick, boolean bindOnGive) {
        this.id = id;
        this.template = template.clone();
        this.rightClick = rightClick;
        this.leftClick = leftClick;
        this.bindOnGive = bindOnGive;
    }

    public String id() { return id; }
    public boolean bindOnGive() { return bindOnGive; }
    public void bindOnGive(boolean value) { bindOnGive = value; }
    public ItemStack template() { return template.clone(); }
    public void template(ItemStack value) { template = value.clone(); }
    public ItemAction rightClick() { return rightClick; }
    public ItemAction leftClick() { return leftClick; }
}
