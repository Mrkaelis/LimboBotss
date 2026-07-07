package ru.mrsteve.limbobots;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.query.QueryOptions;

import java.util.ArrayList;
import java.util.List;

/**
 * Мягкая (soft-depend) интеграция с LuckPerms.
 *
 * Важно: этот класс НЕ создаёт ботам реальных "пользователей" в LuckPerms
 * (это замусорило бы базу данных прав случайными UUID-ами). Вместо этого
 * он просто читает вес и префикс уже существующей на сервере группы —
 * этого достаточно, чтобы визуально показать боту "привилегию" в табе/чате
 * и использовать вес группы для сортировки ботов между собой.
 *
 * Если LuckPerms не установлен на сервере, все методы просто возвращают
 * пустые/нейтральные значения, ничего не ломая.
 */
public class LuckPermsHook {

    private LuckPerms api;
    private boolean available;

    public LuckPermsHook() {
        try {
            this.api = LuckPermsProvider.get();
            this.available = true;
        } catch (Throwable t) {
            // LuckPerms не установлен либо ещё не проинициализировался
            this.api = null;
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public List<String> getGroupNames() {
        List<String> names = new ArrayList<>();
        if (!available) return names;
        try {
            for (Group g : api.getGroupManager().getLoadedGroups()) {
                names.add(g.getName());
            }
        } catch (Throwable ignored) {
        }
        return names;
    }

    /**
     * Строит объект Rank на основе имени группы LuckPerms.
     * Если LuckPerms недоступен или группа не найдена — возвращает
     * "пустой" ранг без префикса (не ломает работу плагина).
     */
    public Rank buildRank(String groupName) {
        if (!available || groupName == null) {
            return new Rank(groupName, 0, "");
        }
        try {
            Group group = api.getGroupManager().getGroup(groupName.toLowerCase());
            if (group == null) {
                return new Rank(groupName, 0, "");
            }
            CachedMetaData metaData = group.getCachedData().getMetaData(QueryOptions.nonContextual());
            String prefix = metaData.getPrefix();
            int weight = group.getWeight().orElse(0);
            return new Rank(group.getName(), weight, prefix == null ? "" : prefix);
        } catch (Throwable t) {
            return new Rank(groupName, 0, "");
        }
    }
}
