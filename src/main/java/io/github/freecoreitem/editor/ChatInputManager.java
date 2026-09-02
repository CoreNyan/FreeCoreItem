package io.github.freecoreitem.editor;

import io.github.freecoreitem.lang.Lang;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class ChatInputManager implements Listener {
    private final Plugin plugin;
    private final Lang lang;
    private final Map<UUID, Request> requests = new HashMap<>();

    public ChatInputManager(Plugin plugin, Lang lang) {
        this.plugin = plugin;
        this.lang = lang;
    }

    public void request(Player player, String promptPath, BiConsumer<Player, String> consumer) {
        request(player, promptPath, consumer, ignored -> { });
    }

    public void request(Player player, String promptPath, BiConsumer<Player, String> consumer,
                        java.util.function.Consumer<Player> onCancel) {
        request(player, promptPath, consumer, onCancel, new String[0]);
    }

    public void request(Player player, String promptPath, BiConsumer<Player, String> consumer,
                        java.util.function.Consumer<Player> onCancel, String... replacements) {
        requests.put(player.getUniqueId(), new Request(consumer, onCancel));
        player.closeInventory();
        String[] values = new String[replacements.length + 2];
        values[0] = "cancel";
        values[1] = lang.raw("input.cancel-keyword");
        System.arraycopy(replacements, 0, values, 2, replacements.length);
        player.sendMessage(lang.message(promptPath, values));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Request request = requests.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.equalsIgnoreCase(lang.raw("input.cancel-keyword"))) {
                lang.send(event.getPlayer(), "input.cancelled");
                request.onCancel().accept(event.getPlayer());
                return;
            }
            request.consumer().accept(event.getPlayer(), input);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
    }

    private record Request(BiConsumer<Player, String> consumer,
                           java.util.function.Consumer<Player> onCancel) { }
}
