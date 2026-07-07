package ru.mrsteve.limbobots;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Генерирует ники ботов, похожие на обычные никнеймы игроков
 * (вместо технических "Bot_1", "Bot_2"), на основе пула базовых
 * имён/слов из config.yml, иногда добавляя цифровой суффикс.
 */
public class NicknameGenerator {

    private final Random random = new Random();
    private final List<String> pool = new ArrayList<>();
    private double numberSuffixChance = 0.6;

    public NicknameGenerator(LimboBots plugin) {
        reload(plugin);
    }

    public void reload(LimboBots plugin) {
        FileConfiguration cfg = plugin.getConfig();
        pool.clear();
        List<String> configured = cfg.getStringList("names.pool");
        if (configured != null && !configured.isEmpty()) {
            pool.addAll(configured);
        } else {
            pool.add("Player");
        }
        numberSuffixChance = cfg.getDouble("names.number-suffix-chance", 0.6);
    }

    /**
     * Генерирует уникальный (относительно уже занятых имён) ник.
     */
    public String generate(Set<String> takenNames) {
        String name;
        int attempts = 0;
        do {
            name = buildRandomName();
            attempts++;
            // подстраховка от бесконечного цикла, если пул очень маленький
            if (attempts > 200) {
                name = name + random.nextInt(9999);
                break;
            }
        } while (takenNames.contains(name));
        return name;
    }

    private String buildRandomName() {
        String base = pool.get(random.nextInt(pool.size()));
        String name = base;

        if (random.nextDouble() < numberSuffixChance) {
            int digits = 2 + random.nextInt(3); // 2-4 цифры
            int max = (int) Math.pow(10, digits);
            int number = random.nextInt(max);
            name = base + number;
        }

        // ограничение Minecraft на длину ника - 16 символов
        if (name.length() > 16) {
            name = name.substring(0, 16);
        }
        return name;
    }
}
