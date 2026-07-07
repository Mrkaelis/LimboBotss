package ru.mrsteve.limbobots;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Хранит всех активных ботов и отвечает за их "поведение":
 * плавное перемещение, чат, добычу ресурсов, PvP и авто-возрождение.
 */
public class BotManager {

    private final LimboBots plugin;
    private final Map<String, Bot> bots = new LinkedHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private final Random random = new Random();

    public BotManager(LimboBots plugin) {
        this.plugin = plugin;
        startWalkTickTask();
        startWalkDecisionTask();
        startChatTask();
        startActivityTask();
    }

    // ---------------------------------------------------------------
    // Управление ботами
    // ---------------------------------------------------------------

    public int addBots(int amount) {
        Location spawn = Bukkit.getWorlds().get(0).getSpawnLocation();
        int added = 0;
        for (int i = 0; i < amount; i++) {
            int id = idCounter.getAndIncrement();
            String name = plugin.getNicknameGenerator().generate(bots.keySet());

            Bot bot = new Bot(id, name, spawn);
            bot.setMaxHp(plugin.getConfig().getInt("combat.max-hp", 20));
            bot.setMuted(random.nextDouble() < plugin.getConfig().getDouble("vcmute.muted-chance", 0.15));
            bot.spawn();

            bots.put(name, bot);
            plugin.getTabListManager().applyTeam(bot);
            added++;
        }
        return added;
    }

    public int removeBots(int amount) {
        int removed = 0;
        Iterator<Map.Entry<String, Bot>> it = bots.entrySet().iterator();
        while (it.hasNext() && removed < amount) {
            Map.Entry<String, Bot> entry = it.next();
            Bot bot = entry.getValue();
            plugin.getTabListManager().removeTeam(bot);
            bot.remove();
            it.remove();
            removed++;
        }
        return removed;
    }

    public void clearBots() {
        for (Bot bot : bots.values()) {
            plugin.getTabListManager().removeTeam(bot);
            bot.remove();
        }
        bots.clear();
    }

    public int getBotCount() {
        return bots.size();
    }

    public Bot getBot(String name) {
        return bots.get(name);
    }

    public Collection<Bot> getAllBots() {
        return bots.values();
    }

    /**
     * Назначает боту привилегию (LuckPerms-группу) - обновляет его вес
     * (влияет на сортировку в табе) и цветной префикс.
     */
    public boolean assignRank(String botName, String groupName) {
        Bot bot = bots.get(botName);
        if (bot == null) return false;

        Rank rank = plugin.getLuckPermsHook().buildRank(groupName);
        bot.setRank(rank);

        // имя команды зависит от веса, поэтому проще пересоздать команду целиком
        plugin.getTabListManager().removeTeam(bot);
        plugin.getTabListManager().applyTeam(bot);
        return true;
    }

    public boolean toggleMute(String botName, boolean muted) {
        Bot bot = bots.get(botName);
        if (bot == null) return false;
        bot.setMuted(muted);
        plugin.getTabListManager().updateTeam(bot);
        return true;
    }

    // ---------------------------------------------------------------
    // Показ ботов новому игроку
    // ---------------------------------------------------------------

    public void showAllTo(org.bukkit.entity.Player player) {
        for (Bot bot : bots.values()) {
            bot.sendSpawnPackets(player);
            plugin.getTabListManager().sendTeam(bot, player);
        }
    }

    // ---------------------------------------------------------------
    // Задачи планировщика
    // ---------------------------------------------------------------

    /**
     * Каждый тик "проигрывает" один маленький шаг из очереди перемещения
     * у ботов, которые сейчас куда-то идут - это даёт плавную ходьбу.
     */
    private void startWalkTickTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Bot bot : bots.values()) {
                if (bot.isAlive() && bot.isWalking()) {
                    bot.tickWalk();
                }
            }
        }, 5L, 1L);
    }

    /**
     * Периодически даёт стоящим на месте ботам новую случайную цель для
     * прогулки в пределах радиуса от их домашней точки спавна.
     */
    private void startWalkDecisionTask() {
        long interval = Math.max(20L, plugin.getConfig().getLong("movement.decide-interval-ticks", 60L));
        double radius = plugin.getConfig().getDouble("movement.radius", 12.0);
        double stepSize = plugin.getConfig().getDouble("movement.step-size", 0.18);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Bot bot : bots.values()) {
                if (!bot.isAlive() || bot.isWalking()) continue;
                if (random.nextBoolean()) {
                    bot.planRandomWalk(radius, stepSize);
                }
            }
        }, interval, interval);
    }

    /**
     * Периодически заставляет случайного бота написать сообщение в чат
     * сервера, используя формат префикса из config.yml.
     */
    private void startChatTask() {
        if (!plugin.getConfig().getBoolean("chat.enabled", true)) return;

        long interval = Math.max(20L, plugin.getConfig().getLong("chat.interval-ticks", 200L));
        List<String> messages = plugin.getConfig().getStringList("chat.messages");

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (bots.isEmpty() || messages.isEmpty()) return;
            if (random.nextInt(10) != 0) return;

            List<Bot> aliveBots = new ArrayList<>();
            for (Bot bot : bots.values()) {
                if (bot.isAlive()) aliveBots.add(bot);
            }
            if (aliveBots.isEmpty()) return;

            Bot bot = aliveBots.get(random.nextInt(aliveBots.size()));
            String message = messages.get(random.nextInt(messages.size()));
            String prefix = plugin.getFormatUtil().buildFullDisplay(bot);

            Bukkit.broadcastMessage(prefix + "§r§7: §f" + message);
        }, interval, interval);
    }

    /**
     * Периодически заставляет ботов либо копать ресурсы, либо пробовать
     * подраться с другим ботом поблизости - имитация "жизни" на сервере.
     */
    private void startActivityTask() {
        boolean combatEnabled = plugin.getConfig().getBoolean("combat.enabled", true);
        boolean miningEnabled = plugin.getConfig().getBoolean("mining.enabled", true);
        long interval = Math.max(20L, plugin.getConfig().getLong("combat.action-interval-ticks", 100L));

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Bot> aliveBots = new ArrayList<>();
            for (Bot bot : bots.values()) {
                if (bot.isAlive()) aliveBots.add(bot);
            }
            if (aliveBots.isEmpty()) return;

            double pvpRadius = plugin.getConfig().getDouble("combat.pvp-radius", 6.0);
            double pvpChance = plugin.getConfig().getDouble("combat.pvp-chance", 0.3);
            int miningRadius = plugin.getConfig().getInt("mining.radius", 6);
            List<Material> mineableBlocks = new ArrayList<>();
            for (String matName : plugin.getConfig().getStringList("mining.blocks")) {
                try {
                    mineableBlocks.add(Material.valueOf(matName.toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Неизвестный материал в mining.blocks: " + matName);
                }
            }

            for (Bot bot : aliveBots) {
                if (bot.isWalking()) continue; // не отвлекаем бота, который сейчас идёт

                Bot nearestEnemy = combatEnabled ? findNearestAliveBot(bot, aliveBots, pvpRadius) : null;

                if (nearestEnemy != null && random.nextDouble() < pvpChance) {
                    bot.attack(nearestEnemy);
                } else if (miningEnabled && !mineableBlocks.isEmpty()) {
                    bot.tryMineNearbyBlock(mineableBlocks, miningRadius);
                }
            }

            // обработка возрождения умерших ботов
            handleRespawns();
        }, interval, interval);
    }

    private Bot findNearestAliveBot(Bot self, List<Bot> aliveBots, double radius) {
        Bot nearest = null;
        double bestDist = radius;
        for (Bot other : aliveBots) {
            if (other == self) continue;
            double dist = self.distanceTo(other);
            if (dist <= bestDist) {
                bestDist = dist;
                nearest = other;
            }
        }
        return nearest;
    }

    private final Map<String, Long> respawnQueue = new HashMap<>();

    private void handleRespawns() {
        long respawnDelayTicks = plugin.getConfig().getLong("combat.respawn-delay-seconds", 8) * 20L;
        long now = System.currentTimeMillis();

        for (Bot bot : bots.values()) {
            if (bot.isAlive()) {
                respawnQueue.remove(bot.getName());
                continue;
            }
            Long deadSince = respawnQueue.get(bot.getName());
            if (deadSince == null) {
                respawnQueue.put(bot.getName(), now);
                continue;
            }
            if (now - deadSince >= respawnDelayTicks * 50L) {
                bot.respawn();
                plugin.getTabListManager().applyTeam(bot);
                respawnQueue.remove(bot.getName());
            }
        }
    }
}
