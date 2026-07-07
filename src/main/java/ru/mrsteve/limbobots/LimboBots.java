package ru.mrsteve.limbobots;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * LimboBots — плагин для Spigot/Paper 1.16.5
 * Автор: MrSteve
 */
public class LimboBots extends JavaPlugin {

    private BotManager botManager;
    private LuckPermsHook luckPermsHook;
    private NicknameGenerator nicknameGenerator;
    private FormatUtil formatUtil;
    private TabListManager tabListManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.luckPermsHook = new LuckPermsHook();
        this.nicknameGenerator = new NicknameGenerator(this);
        this.formatUtil = new FormatUtil(this);
        this.tabListManager = new TabListManager(this);
        this.botManager = new BotManager(this);

        getCommand("limbobots").setExecutor(new LimboBotsCommand(this));

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        getServer().getPluginManager().registerEvents(new ServerPingListener(this), this);

        if (getConfig().getBoolean("tp-override.enabled", true)) {
            overrideTpCommand();
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                new LimboBotsExpansion(this).register();
                getLogger().info("Плейсхолдер %limbobots_online% зарегистрирован в PlaceholderAPI.");
            } catch (Throwable t) {
                getLogger().warning("Не удалось зарегистрировать плейсхолдер PlaceholderAPI: " + t.getMessage());
            }
        }

        if (luckPermsHook.isAvailable()) {
            getLogger().info("Найден LuckPerms — доступна команда /limbobots rank.");
        } else {
            getLogger().info("LuckPerms не найден — %luckperms-prefix% будет пустым, пока LuckPerms не установлен.");
        }

        getLogger().info("========================================");
        getLogger().info(" LimboBots v" + getDescription().getVersion() + " включен!");
        getLogger().info(" Автор: MrSteve");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (botManager != null) {
            botManager.clearBots();
        }
        getLogger().info("LimboBots выключен, все боты удалены.");
    }

    /**
     * Заменяет ванильную /tp и /teleport на версию, понимающую ботов.
     * См. подробности и ограничения в TpOverrideCommand и README.md.
     */
    @SuppressWarnings("unchecked")
    private void overrideTpCommand() {
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommandsField.setAccessible(true);
            Map<String, Command> knownCommands = (Map<String, Command>) knownCommandsField.get(commandMap);

            TpOverrideCommand tpCommand = new TpOverrideCommand(this);
            knownCommands.put("tp", tpCommand);
            knownCommands.put("teleport", tpCommand);
            knownCommands.put("minecraft:tp", tpCommand);
            knownCommands.put("minecraft:teleport", tpCommand);
            knownCommands.put("bukkit:tp", tpCommand);

            getLogger().info("Команда /tp переопределена (поддержка телепортации к ботам).");
        } catch (Exception e) {
            getLogger().warning("Не удалось переопределить команду /tp: " + e.getMessage()
                    + " (используйте /limbobots tp <имя> как запасной вариант)");
        }
    }

    public BotManager getBotManager() {
        return botManager;
    }

    public LuckPermsHook getLuckPermsHook() {
        return luckPermsHook;
    }

    public NicknameGenerator getNicknameGenerator() {
        return nicknameGenerator;
    }

    public FormatUtil getFormatUtil() {
        return formatUtil;
    }

    public TabListManager getTabListManager() {
        return tabListManager;
    }
}
