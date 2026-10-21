package io.github.freecoreitem;

import io.github.freecoreitem.action.ItemActionListener;
import io.github.freecoreitem.command.FItemCommand;
import io.github.freecoreitem.editor.ChatInputManager;
import io.github.freecoreitem.editor.ItemEditorMenu;
import io.github.freecoreitem.item.ItemService;
import io.github.freecoreitem.listener.ItemProtectionListener;
import io.github.freecoreitem.listener.TradeBindingListener;
import io.github.freecoreitem.lang.Lang;
import io.github.freecoreitem.storage.ItemRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class FreeCoreItemPlugin extends JavaPlugin {
    private Lang lang;
    private ItemRepository repository;
    private ItemService items;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lang = new Lang(this);
        repository = new ItemRepository(this);
        reloadAll();

        items = new ItemService(this, repository);
        repository.setUpdateListener(items::refreshOnlineInventories);
        ChatInputManager chatInput = new ChatInputManager(this, lang);
        ItemEditorMenu editor = new ItemEditorMenu(this, repository, items, lang, chatInput);
        FItemCommand command = new FItemCommand(this, repository, items, editor, lang);
        PluginCommand fitem = getCommand("fitem");
        if (fitem == null) throw new IllegalStateException("Command fitem is missing from plugin.yml");
        fitem.setExecutor(command);
        fitem.setTabCompleter(command);

        getServer().getPluginManager().registerEvents(chatInput, this);
        getServer().getPluginManager().registerEvents(editor, this);
        getServer().getPluginManager().registerEvents(items, this);
        getServer().getPluginManager().registerEvents(new ItemProtectionListener(this, items), this);
        getServer().getPluginManager().registerEvents(new TradeBindingListener(this, items), this);
        getServer().getPluginManager().registerEvents(new ItemActionListener(this, items, lang), this);
        getLogger().info("Loaded " + repository.size() + " custom items.");
    }

    public void reloadAll() {
        reloadConfig();
        lang.reload();
        repository.reload();
        if (items != null) items.refreshOnlineInventories();
    }
}
