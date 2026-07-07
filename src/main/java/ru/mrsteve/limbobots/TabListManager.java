package ru.mrsteve.limbobots;

import net.minecraft.server.v1_16_R3.PacketPlayOutScoreboardTeam;
import net.minecraft.server.v1_16_R3.PlayerConnection;
import net.minecraft.server.v1_16_R3.Scoreboard;
import net.minecraft.server.v1_16_R3.ScoreboardTeam;
import net.minecraft.server.v1_16_R3.ScoreboardTeamBase;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

/**
 * Управляет "фейковыми" scoreboard-командами, которые существуют только
 * на уровне пакетов (не регистрируются в реальном скорборде сервера и не
 * конфликтуют с другими плагинами, использующими Bukkit Scoreboard API).
 *
 * Для чего это нужно:
 *  - Ванильный клиент сортирует таб-лист сначала по имени команды игрока
 *    (игроки без команды идут первыми, т.к. их "имя команды" эквивалентно
 *    пустой строке), а затем по имени игрока внутри команды.
 *  - Команда даёт префикс/суффикс, отображаемый прямо в табе рядом с ником,
 *    без необходимости менять сам GameProfile.
 *
 * Поэтому: реальные игроки не получают никакой команды от этого плагина
 * (значит их "ключ сортировки" - пустая строка, и они всегда выше ботов),
 * а каждый бот получает команду с именем вида "~<вес>_<id>", которая всегда
 * лексикографически больше пустой строки (см. teamNameFor) и сортируется
 * между ботами по весу привилегии.
 */
public class TabListManager {

    // Общий "контейнер" для создания объектов команд. Не привязан к
    // реальному скорборду сервера/игроков - используется только как
    // структура данных для генерации пакетов.
    private final Scoreboard fakeScoreboard = new Scoreboard();

    private final LimboBots plugin;

    public TabListManager(LimboBots plugin) {
        this.plugin = plugin;
    }

    private String teamNameFor(Bot bot) {
        int weight = bot.getRank() != null ? bot.getRank().getWeight() : 0;
        int clamped = Math.max(0, Math.min(9999, weight));
        int sortValue = 9999 - clamped; // больший вес -> меньшее число -> раньше среди ботов
        String idPart = Integer.toString(Math.abs(bot.getInternalId()) % 100000);
        // "~" гарантированно сортируется позже любых обычных букв/цифр,
        // поэтому все боты всегда окажутся ниже реальных игроков.
        String name = "~" + String.format("%04d", sortValue) + "_" + idPart;
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return name;
    }

    private ScoreboardTeam buildTeam(Bot bot) {
        FormatUtil formatUtil = plugin.getFormatUtil();
        String[] prefixSuffix = formatUtil.buildTeamPrefixSuffix(bot);

        ScoreboardTeam team = new ScoreboardTeam(fakeScoreboard, teamNameFor(bot));
        team.setPrefix(CraftChatMessage.fromString(prefixSuffix[0], true)[0]);
        team.setSuffix(CraftChatMessage.fromString(prefixSuffix[1], true)[0]);
        team.setNameTagVisibility(ScoreboardTeamBase.EnumNameTagVisibility.ALWAYS);
        return team;
    }

    /**
     * Создаёт команду бота и добавляет в неё бота у всех онлайн-игроков.
     * Вызывается при спавне бота и при обновлении его ранга/статуса мута.
     */
    public void applyTeam(Bot bot) {
        ScoreboardTeam team = buildTeam(bot);
        List<String> members = Collections.singletonList(bot.getName());

        PacketPlayOutScoreboardTeam createPacket = new PacketPlayOutScoreboardTeam(team, 0);
        PacketPlayOutScoreboardTeam addPlayersPacket = new PacketPlayOutScoreboardTeam(team, members, 3);

        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerConnection connection = ((CraftPlayer) p).getHandle().playerConnection;
            connection.sendPacket(createPacket);
            connection.sendPacket(addPlayersPacket);
        }
    }

    /**
     * Отправляет уже существующую команду бота конкретному (например,
     * только что зашедшему) игроку.
     */
    public void sendTeam(Bot bot, Player to) {
        ScoreboardTeam team = buildTeam(bot);
        List<String> members = Collections.singletonList(bot.getName());

        PacketPlayOutScoreboardTeam createPacket = new PacketPlayOutScoreboardTeam(team, 0);
        PacketPlayOutScoreboardTeam addPlayersPacket = new PacketPlayOutScoreboardTeam(team, members, 3);

        PlayerConnection connection = ((CraftPlayer) to).getHandle().playerConnection;
        connection.sendPacket(createPacket);
        connection.sendPacket(addPlayersPacket);
    }

    /**
     * Обновляет префикс/суффикс команды бота (например, после смены ранга
     * или переключения статуса мута) у всех игроков без пересоздания команды.
     */
    public void updateTeam(Bot bot) {
        ScoreboardTeam team = buildTeam(bot);
        PacketPlayOutScoreboardTeam updatePacket = new PacketPlayOutScoreboardTeam(team, 2);

        for (Player p : Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) p).getHandle().playerConnection.sendPacket(updatePacket);
        }
    }

    /**
     * Убирает команду бота у всех игроков (при удалении бота).
     */
    public void removeTeam(Bot bot) {
        ScoreboardTeam team = new ScoreboardTeam(fakeScoreboard, teamNameFor(bot));
        PacketPlayOutScoreboardTeam removePacket = new PacketPlayOutScoreboardTeam(team, 1);

        for (Player p : Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) p).getHandle().playerConnection.sendPacket(removePacket);
        }
    }
}
