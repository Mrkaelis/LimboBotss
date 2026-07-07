package ru.mrsteve.limbobots;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/**
 * Регистрирует плейсхолдеры LimboBots в PlaceholderAPI.
 *
 * %limbobots_online%      - реальный онлайн + количество ботов
 * %limbobots_real_online% - только реальный онлайн (без ботов)
 * %limbobots_bots%        - только количество ботов
 */
public class LimboBotsExpansion extends PlaceholderExpansion {

    private final LimboBots plugin;

    public LimboBotsExpansion(LimboBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "limbobots";
    }

    @Override
    public String getAuthor() {
        return "MrSteve";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params.equalsIgnoreCase("online")) {
            int real = Bukkit.getOnlinePlayers().size();
            int bots = plugin.getBotManager().getBotCount();
            return String.valueOf(real + bots);
        }
        if (params.equalsIgnoreCase("real_online")) {
            return String.valueOf(Bukkit.getOnlinePlayers().size());
        }
        if (params.equalsIgnoreCase("bots")) {
            return String.valueOf(plugin.getBotManager().getBotCount());
        }
        return null;
    }
}
