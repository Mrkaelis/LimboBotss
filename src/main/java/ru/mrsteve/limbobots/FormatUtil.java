package ru.mrsteve.limbobots;

import org.bukkit.ChatColor;

/**
 * Собирает итоговый текст отображения бота (таб/чат) на основе
 * настраиваемого формата из config.yml (tab-format), подставляя
 * плейсхолдеры %vcmute_status%, %luckperms-prefix%, %bot_name%.
 */
public class FormatUtil {

    private static final String TOKEN_NAME = "%bot_name%";

    private final LimboBots plugin;

    public FormatUtil(LimboBots plugin) {
        this.plugin = plugin;
    }

    private String rawFormat() {
        return plugin.getConfig().getString("tab-format", " %vcmute_status% &f%luckperms-prefix%&7 %bot_name%");
    }

    private String vcStatus(Bot bot) {
        String key = bot.isMuted() ? "vcmute.muted-symbol" : "vcmute.unmuted-symbol";
        return plugin.getConfig().getString(key, "");
    }

    private String luckPermsPrefix(Bot bot) {
        Rank rank = bot.getRank();
        return rank != null ? rank.getPrefix() : "";
    }

    /**
     * Полная строка для чата: "<префикс+ранг> BotName".
     */
    public String buildFullDisplay(Bot bot) {
        String result = rawFormat()
                .replace("%vcmute_status%", vcStatus(bot))
                .replace("%luckperms-prefix%", luckPermsPrefix(bot))
                .replace(TOKEN_NAME, bot.getName());
        return ChatColor.translateAlternateColorCodes('&', result);
    }

    /**
     * Разбивает формат на "префикс команды" (всё до %bot_name%) и
     * "суффикс команды" (всё после) — используется для скорборд-команды,
     * т.к. само имя бота в табе рисуется отдельно клиентом.
     */
    public String[] buildTeamPrefixSuffix(Bot bot) {
        String withoutName = rawFormat()
                .replace("%vcmute_status%", vcStatus(bot))
                .replace("%luckperms-prefix%", luckPermsPrefix(bot));

        int idx = withoutName.indexOf(TOKEN_NAME);
        String prefixPart = idx >= 0 ? withoutName.substring(0, idx) : withoutName;
        String suffixPart = idx >= 0 ? withoutName.substring(idx + TOKEN_NAME.length()) : "";

        prefixPart = ChatColor.translateAlternateColorCodes('&', prefixPart);
        suffixPart = ChatColor.translateAlternateColorCodes('&', suffixPart);

        return new String[]{prefixPart, suffixPart};
    }
}
