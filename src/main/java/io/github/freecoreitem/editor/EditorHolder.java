package io.github.freecoreitem.editor;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class EditorHolder implements InventoryHolder {
    public enum View { BROWSER, EDITOR, LORE, DELETE }

    private final View view;
    private final String itemId;
    private final int page;
    private Inventory inventory;

    public EditorHolder(View view, String itemId, int page) {
        this.view = view;
        this.itemId = itemId;
        this.page = page;
    }

    public void attach(Inventory inventory) { this.inventory = inventory; }
    public View view() { return view; }
    public String itemId() { return itemId; }
    public int page() { return page; }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}
