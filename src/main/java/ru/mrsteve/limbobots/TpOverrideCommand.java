package ru.mrsteve.limbobots;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

/**
 * Заменяет ванильную команду /tp (и /teleport), чтобы она понимала
 * имена ботов LimboBots как обычную цель телепортации.
 *
 * Поддерживаемые формы:
 *   /tp <игрок_или_бот>                - телепортирует ОТПРАВИТЕЛЯ к цели
 *   /tp <игрок> <игрок_или_бот>         - телепортирует первого ко второму
 *   /tp <x> <y> <z>                    - телепортация на координаты (можно ~)
 *   /tp <игрок> <x> <y> <z>            - телепортация игрока на координаты
 *
 * ВАЖНО: это упрощённая замена. Она не поддерживает селекторы целей
 * (@a, @e, @p и т.д.) и телепортацию с учётом направления взгляда,
 * которые есть в ванильной команде. Если это критично, отключите
 * замену в config.yml (tp-override.enabled: false).
 */
public class TpOverrideCommand extends Command {

    private final LimboBots plugin;

    public TpOverrideCommand(LimboBots plugin) {
        super("tp");
        this.plugin = plugin;
        setDescription("Телепортация (с поддержкой ботов LimboBots)");
        setUsage("/tp <игрок/бот> | /tp <игрок> <игрок/бот> | /tp <x> <y> <z>");
        setPermission("minecraft.command.tp");
        setAliases(Arrays.asList("teleport"));
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!testPermission(sender)) {
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Эту форму команды может использовать только игрок.");
                return true;
            }
            teleportToTarget((Player) sender, sender, args[0]);
            return true;
        }

        if (args.length == 2) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок '" + args[0] + "' не найден.");
                return true;
            }
            teleportToTarget(target, sender, args[1]);
            return true;
        }

        if (args.length == 3) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Укажите игрока явно: /tp <игрок> <x> <y> <z>");
                return true;
            }
            teleportToCoords((Player) sender, sender, (Player) sender, args, 0);
            return true;
        }

        if (args.length == 4) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Игрок '" + args[0] + "' не найден.");
                return true;
            }
            teleportToCoords(target, sender, target, args, 1);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Использование: " + getUsage());
        return true;
    }

    private void teleportToTarget(Player who, CommandSender sender, String targetName) {
        Bot bot = plugin.getBotManager().getBot(targetName);
        if (bot != null) {
            who.teleport(bot.getLocation());
            sender.sendMessage(ChatColor.GREEN + who.getName() + " телепортирован(а) к боту " + bot.getName());
            return;
        }

        Player realTarget = Bukkit.getPlayerExact(targetName);
        if (realTarget != null) {
            who.teleport(realTarget.getLocation());
            sender.sendMessage(ChatColor.GREEN + who.getName() + " телепортирован(а) к игроку " + realTarget.getName());
            return;
        }

        sender.sendMessage(ChatColor.RED + "Цель '" + targetName + "' не найдена (ни игрок, ни бот).");
    }

    private void teleportToCoords(Player who, CommandSender sender, Player relativeBase, String[] args, int offset) {
        try {
            World world = relativeBase.getWorld();
            Location base = relativeBase.getLocation();
            double x = parseCoord(args[offset], base.getX());
            double y = parseCoord(args[offset + 1], base.getY());
            double z = parseCoord(args[offset + 2], base.getZ());
            who.teleport(new Location(world, x, y, z, base.getYaw(), base.getPitch()));
            sender.sendMessage(ChatColor.GREEN + who.getName() + " телепортирован(а) на координаты "
                    + (int) x + " " + (int) y + " " + (int) z);
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "Некорректные координаты.");
        }
    }

    private double parseCoord(String raw, double base) {
        if (raw.startsWith("~")) {
            String rest = raw.substring(1);
            return rest.isEmpty() ? base : base + Double.parseDouble(rest);
        }
        return Double.parseDouble(raw);
    }
}
