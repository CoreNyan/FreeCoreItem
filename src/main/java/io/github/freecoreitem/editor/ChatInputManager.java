package io.github.freecoreitem.editor;

import io.github.freecoreitem.lang.Lang;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class ChatInputManager implements Listener {
    private final Plugin plugin;
    private final Lang lang;
    private final Map<UUID, Request> requests = new ConcurrentHashMap<>();

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
        player.closeInventory();
        requests.put(player.getUniqueId(), new Request(consumer, onCancel, new AtomicBoolean()));
        String[] values = new String[replacements.length + 2];
        values[0] = "cancel";
        values[1] = lang.raw("input.cancel-keyword");
        System.arraycopy(replacements, 0, values, 2, replacements.length);
        player.sendMessage(lang.message(promptPath, values));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent event) {
        Request request = requests.get(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        accept(event.getPlayer(), request, PlainTextComponentSerializer.plainText().serialize(event.message()).trim());
    }

    // Some chat bridges still dispatch the legacy Bukkit chat event instead of Paper's event.
    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Request request = requests.get(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        accept(event.getPlayer(), request, event.getMessage().trim());
    }

    private void accept(Player player, Request request, String input) {
        if (!request.accepted().compareAndSet(false, true)) return;
        // Keep the request until the next tick so a second chat event for the
        // same message is also cancelled before the editor is reopened.
        Bukkit.getScheduler().runTask(plugin, () -> requests.remove(player.getUniqueId(), request));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (input.equalsIgnoreCase(lang.raw("input.cancel-keyword"))) {
                lang.send(player, "input.cancelled");
                request.onCancel().accept(player);
                return;
            }
            try {
                request.consumer().accept(player, input);
            } catch (Throwable exception) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "FreeCoreItem editor input failed for " + player.getName(), exception);
                lang.send(player, "command.invalid-operation");
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        requests.remove(event.getPlayer().getUniqueId());
    }

    private record Request(BiConsumer<Player, String> consumer,
                           java.util.function.Consumer<Player> onCancel, AtomicBoolean accepted) { }
}
