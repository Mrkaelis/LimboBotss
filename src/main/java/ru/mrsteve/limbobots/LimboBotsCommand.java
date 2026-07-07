package ru.mrsteve.limbobots;

import net.minecraft.server.v1_16_R3.EnumItemSlot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LimboBotsCommand implements CommandExecutor, TabCompleter {

    private final LimboBots plugin;

    public LimboBotsCommand(LimboBots plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("limbobots.admin")) {
            sender.sendMessage(ChatColor.RED + "У вас нет прав на использование этой команды.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                handleAdd(sender, args);
                break;
            case "remove":
                handleRemove(sender, args);
                break;
            case "clear":
                plugin.getBotManager().clearBots();
                sender.sendMessage(ChatColor.GREEN + "Все боты удалены.");
                break;
            case "list":
                handleList(sender);
                break;
            case "tp":
                handleTeleport(sender, args);
                break;
            case "equip":
                handleEquip(sender, args);
                break;
            case "rank":
                handleRank(sender, args);
                break;
            case "ranks":
                handleRanks(sender);
                break;
            case "mute":
                handleMute(sender, args, true);
                break;
            case "unmute":
                handleMute(sender, args, false);
                break;
            default:
                sendHelp(sender);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /limbobots add <количество>");
            return;
        }
        int amount = parseInt(sender, args[1]);
        if (amount <= 0) return;

        int added = plugin.getBotManager().addBots(amount);
        sender.sendMessage(ChatColor.GREEN + "Добавлено ботов: " + added
                + ChatColor.GRAY + " (всего сейчас: " + plugin.getBotManager().getBotCount() + ")");
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /limbobots remove <количество>");
            return;
        }
        int amount = parseInt(sender, args[1]);
        if (amount <= 0) return;

        int removed = plugin.getBotManager().removeBots(amount);
        sender.sendMessage(ChatColor.GREEN + "Удалено ботов: " + removed
                + ChatColor.GRAY + " (осталось: " + plugin.getBotManager().getBotCount() + ")");
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Активные боты (" + plugin.getBotManager().getBotCount() + "):");
        for (Bot bot : plugin.getBotManager().getAllBots()) {
            String status = bot.isAlive() ? ChatColor.GREEN + "жив" : ChatColor.RED + "мёртв";
            String rank = bot.getRank() != null ? bot.getRank().getGroupName() : "-";
            sender.sendMessage(ChatColor.YELLOW + " - " + bot.getName()
                    + ChatColor.GRAY + " [" + status + ChatColor.GRAY + ", ранг: " + rank + ", hp: " + bot.getHp() + "]");
        }
    }

    private void handleTeleport(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Эту команду может использовать только игрок.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /limbobots tp <имя_бота>");
            return;
        }
        Bot bot = plugin.getBotManager().getBot(args[1]);
        if (bot == null) {
            sender.sendMessage(ChatColor.RED + "Бот с именем '" + args[1] + "' не найден.");
            return;
        }
        ((Player) sender).teleport(bot.getLocation());
        sender.sendMessage(ChatColor.GREEN + "Вы телепортированы к боту " + bot.getName());
    }

    private void handleEquip(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED + "Использование: /limbobots equip <имя_бота> <слот> <предмет>");
            sender.sendMessage(ChatColor.GRAY + "Слоты: head, chest, legs, feet, hand, offhand");
            return;
        }
        Bot bot = plugin.getBotManager().getBot(args[1]);
        if (bot == null) {
            sender.sendMessage(ChatColor.RED + "Бот с именем '" + args[1] + "' не найден.");
            return;
        }

        Material material;
        try {
            material = Material.valueOf(args[3].toUpperCase());
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + "Неизвестный предмет: " + args[3]);
            return;
        }

        EnumItemSlot slot = parseSlot(args[2]);
        if (slot == null) {
            sender.sendMessage(ChatColor.RED + "Неизвестный слот: " + args[2]
                    + ChatColor.GRAY + " (head, chest, legs, feet, hand, offhand)");
            return;
        }

        bot.equip(slot, new ItemStack(material));
        sender.sendMessage(ChatColor.GREEN + "Предмет " + material + " надет на бота " + bot.getName());
    }

    private void handleRank(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Использование: /limbobots rank <имя_бота> <группа_luckperms>");
            sender.sendMessage(ChatColor.GRAY + "Список доступных групп: /limbobots ranks");
            return;
        }
        String botName = args[1];
        String group = args[2];

        boolean ok = plugin.getBotManager().assignRank(botName, group);
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Бот с именем '" + botName + "' не найден.");
            return;
        }
        if (!plugin.getLuckPermsHook().isAvailable()) {
            sender.sendMessage(ChatColor.YELLOW + "LuckPerms не установлен — префикс будет пустым, "
                    + "но сортировка/вес всё равно применятся (вес по умолчанию 0).");
        }
        sender.sendMessage(ChatColor.GREEN + "Боту " + botName + " назначена привилегия '" + group + "'.");
    }

    private void handleRanks(CommandSender sender) {
        if (!plugin.getLuckPermsHook().isAvailable()) {
            sender.sendMessage(ChatColor.RED + "LuckPerms не установлен на сервере.");
            return;
        }
        List<String> groups = plugin.getLuckPermsHook().getGroupNames();
        sender.sendMessage(ChatColor.GOLD + "Доступные группы LuckPerms:");
        for (String g : groups) {
            sender.sendMessage(ChatColor.YELLOW + " - " + g);
        }
    }

    private void handleMute(CommandSender sender, String[] args, boolean muted) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Использование: /limbobots " + (muted ? "mute" : "unmute") + " <имя_бота>");
            return;
        }
        boolean ok = plugin.getBotManager().toggleMute(args[1], muted);
        if (!ok) {
            sender.sendMessage(ChatColor.RED + "Бот с именем '" + args[1] + "' не найден.");
            return;
        }
        sender.sendMessage(ChatColor.GREEN + "Статус мута бота " + args[1] + ": " + (muted ? "включён" : "выключен"));
    }

    private EnumItemSlot parseSlot(String input) {
        switch (input.toLowerCase()) {
            case "head": return EnumItemSlot.HEAD;
            case "chest": return EnumItemSlot.CHEST;
            case "legs": return EnumItemSlot.LEGS;
            case "feet": return EnumItemSlot.FEET;
            case "hand": return EnumItemSlot.MAINHAND;
            case "offhand": return EnumItemSlot.OFFHAND;
            default: return null;
        }
    }

    private int parseInt(CommandSender sender, String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) {
                sender.sendMessage(ChatColor.RED + "Количество должно быть больше нуля.");
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "'" + raw + "' не является числом.");
            return -1;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== LimboBots (автор: MrSteve) =====");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots add <количество>" + ChatColor.GRAY + " - добавить ботов");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots remove <количество>" + ChatColor.GRAY + " - удалить N ботов");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots clear" + ChatColor.GRAY + " - удалить всех ботов");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots list" + ChatColor.GRAY + " - список ботов");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots tp <имя>" + ChatColor.GRAY + " - телепортироваться к боту");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots equip <имя> <слот> <предмет>" + ChatColor.GRAY + " - надеть предмет");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots rank <имя> <группа>" + ChatColor.GRAY + " - назначить привилегию");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots ranks" + ChatColor.GRAY + " - список групп LuckPerms");
        sender.sendMessage(ChatColor.YELLOW + "/limbobots mute|unmute <имя>" + ChatColor.GRAY + " - переключить статус мута");
        sender.sendMessage(ChatColor.GRAY + "Обычная команда /tp <имя_бота> тоже работает (если tp-override включён).");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.addAll(Arrays.asList("add", "remove", "clear", "list", "tp", "equip", "rank", "ranks", "mute", "unmute"));
        } else if (args.length == 2 && Arrays.asList("tp", "equip", "rank", "mute", "unmute").contains(args[0].toLowerCase())) {
            for (Bot bot : plugin.getBotManager().getAllBots()) {
                completions.add(bot.getName());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("equip")) {
            completions.addAll(Arrays.asList("head", "chest", "legs", "feet", "hand", "offhand"));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            completions.addAll(plugin.getLuckPermsHook().getGroupNames());
        }

        String current = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        List<String> filtered = new ArrayList<>();
        for (String c : completions) {
            if (c.toLowerCase().startsWith(current)) {
                filtered.add(c);
            }
        }
        return filtered;
    }
}
