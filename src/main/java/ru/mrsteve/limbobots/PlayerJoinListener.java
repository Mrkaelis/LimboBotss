package ru.mrsteve.limbobots;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Когда на сервер заходит настоящий игрок, ему нужно отдельно
 * отправить пакеты появления всех уже существующих ботов и их
 * фейковых scoreboard-команд (для префиксов/сортировки в табе).
 */
public class PlayerJoinListener implements Listener {

    private final LimboBots plugin;

    public PlayerJoinListener(LimboBots plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () ->
                plugin.getBotManager().showAllTo(event.getPlayer()), 10L);
    }
}
