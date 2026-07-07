package ru.mrsteve.limbobots;

/**
 * Простое представление "привилегии" бота: имя LuckPerms-группы,
 * её вес (используется для сортировки ботов между собой в табе)
 * и её цветной префикс (легаси-формат с & кодами).
 */
public class Rank {

    private final String groupName;
    private final int weight;
    private final String prefix;

    public Rank(String groupName, int weight, String prefix) {
        this.groupName = groupName;
        this.weight = weight;
        this.prefix = prefix == null ? "" : prefix;
    }

    public String getGroupName() {
        return groupName;
    }

    public int getWeight() {
        return weight;
    }

    public String getPrefix() {
        return prefix;
    }
}
