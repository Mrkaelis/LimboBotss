package ru.mrsteve.limbobots;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Немного "накручивает" отображаемый maxPlayers в списке серверов.
 * Полная подмена numPlayers возможна только на Paper через
 * PaperServerListPingEvent#setNumPlayers(int) — см. README.
 */
public class ServerPingListener implements Listener {

    private final LimboBots plugin;

    public ServerPingListener(LimboBots plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(ServerListPingEvent event) {
        int botCount = plugin.getBotManager().getBotCount();
        if (botCount <= 0) return;

        int desiredMax = event.getNumPlayers() + botCount;
        if (event.getMaxPlayers() < desiredMax) {
            event.setMaxPlayers(desiredMax);
        }
    }
}
