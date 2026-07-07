package ru.mrsteve.limbobots;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.v1_16_R3.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.CraftServer;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Обёртка над NMS EntityPlayer — представляет одного "живого" бота.
 * Бот не является реальным подключением, а полностью управляется
 * через пакеты, отправляемые настоящим игрокам.
 */
public class Bot {

    private final int internalId;
    private final String name;
    private final UUID uuid;
    private final Random random = new Random();

    /** Точка, вокруг которой бот гуляет и куда возвращается после смерти. */
    private final Location homeLocation;

    private EntityPlayer npc;
    private Location location;
    private float yaw;

    private Rank rank;
    private boolean muted;

    private int maxHp = 20;
    private int hp = 20;
    private boolean alive = true;

    /** Очередь маленьких шагов для плавной прогулки без рывков/провалов. */
    private final Deque<double[]> walkQueue = new ArrayDeque<>();

    public Bot(int internalId, String name, Location homeLocation) {
        this.internalId = internalId;
        this.name = name;
        this.uuid = UUID.randomUUID();
        this.homeLocation = homeLocation.clone();
        this.location = homeLocation.clone();
        this.yaw = homeLocation.getYaw();
    }

    // ---------------------------------------------------------------
    // Базовые геттеры
    // ---------------------------------------------------------------

    public int getInternalId() {
        return internalId;
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public Location getLocation() {
        return location.clone();
    }

    public Location getHomeLocation() {
        return homeLocation.clone();
    }

    public EntityPlayer getHandle() {
        return npc;
    }

    public Rank getRank() {
        return rank;
    }

    public void setRank(Rank rank) {
        this.rank = rank;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public boolean isAlive() {
        return alive;
    }

    public int getHp() {
        return hp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    // ---------------------------------------------------------------
    // Появление / исчезновение
    // ---------------------------------------------------------------

    /**
     * Создаёт NMS-сущность игрока и показывает её всем онлайн-игрокам.
     */
    public void spawn() {
        MinecraftServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WorldServer worldServer = ((CraftWorld) location.getWorld()).getHandle();

        GameProfile profile = new GameProfile(uuid, name);
        PlayerInteractManager interactManager = new PlayerInteractManager(worldServer);

        npc = new EntityPlayer(server, worldServer, profile, interactManager);
        npc.setLocation(location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch());
        this.alive = true;
        this.hp = maxHp;

        for (Player player : Bukkit.getOnlinePlayers()) {
            sendSpawnPackets(player);
        }
    }

    public void sendSpawnPackets(Player player) {
        if (npc == null) return;
        PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;

        connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.ADD_PLAYER, npc));
        connection.sendPacket(new PacketPlayOutNamedEntitySpawn(npc));
        connection.sendPacket(new PacketPlayOutEntityHeadRotation(npc, (byte) (yaw * 256 / 360)));
        connection.sendPacket(new PacketPlayOutEntityMetadata(npc.getId(), npc.getDataWatcher(), true));
    }

    public void sendRemovePackets(Player player) {
        if (npc == null) return;
        PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
        connection.sendPacket(new PacketPlayOutPlayerInfo(PacketPlayOutPlayerInfo.EnumPlayerInfoAction.REMOVE_PLAYER, npc));
        connection.sendPacket(new PacketPlayOutEntityDestroy(npc.getId()));
    }

    public void remove() {
        if (npc == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            sendRemovePackets(player);
        }
    }

    // ---------------------------------------------------------------
    // Экипировка
    // ---------------------------------------------------------------

    public void equip(EnumItemSlot slot, org.bukkit.inventory.ItemStack item) {
        if (npc == null) return;

        net.minecraft.server.v1_16_R3.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);
        List<Pair<EnumItemSlot, net.minecraft.server.v1_16_R3.ItemStack>> equipmentList = new ArrayList<>();
        equipmentList.add(new Pair<>(slot, nmsItem));

        PacketPlayOutEntityEquipment packet = new PacketPlayOutEntityEquipment(npc.getId(), equipmentList);
        for (Player player : Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        }
    }

    // ---------------------------------------------------------------
    // Движение
    // ---------------------------------------------------------------

    /**
     * Выбирает новую случайную точку в пределах радиуса от "домашней"
     * точки бота (а не от его текущей позиции!) и разбивает путь до неё
     * на маленькие шаги. Привязка к homeLocation - ключевая защита от
     * "расползания" ботов за пределы платформы спавна и провала в пустоту.
     *
     * Возвращает false, если безопасную точку найти не удалось (тогда
     * бот просто постоит на месте до следующей попытки).
     */
    public boolean planRandomWalk(double radius, double stepSize) {
        if (npc == null || !alive || homeLocation.getWorld() == null) return false;

        double angle = random.nextDouble() * Math.PI * 2;
        double dist = random.nextDouble() * radius;
        double targetX = homeLocation.getX() + Math.cos(angle) * dist;
        double targetZ = homeLocation.getZ() + Math.sin(angle) * dist;

        Integer safeY = findSafeY(homeLocation.getWorld(), targetX, targetZ);
        if (safeY == null) {
            // нет безопасной земли в этой точке (обрыв/пустота) - пропускаем
            return false;
        }

        // если точка сильно ниже/выше домашней высоты - тоже считаем её небезопасной
        if (Math.abs(safeY - homeLocation.getBlockY()) > 3) {
            return false;
        }

        double fromX = location.getX();
        double fromY = location.getY();
        double fromZ = location.getZ();
        double toY = safeY + 0.0;

        double dx = targetX - fromX;
        double dy = toY - fromY;
        double dz = targetZ - fromZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.round(distance / Math.max(0.01, stepSize)));

        walkQueue.clear();
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            walkQueue.add(new double[]{fromX + dx * t, fromY + dy * t, fromZ + dz * t});
        }

        this.yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        return true;
    }

    /**
     * Обрабатывает один маленький шаг из очереди перемещения (вызывается
     * часто, например каждый тик), рассылая относительный пакет движения -
     * это выглядит как обычная ходьба, а не рывками/телепортом.
     */
    public void tickWalk() {
        if (npc == null || !alive || walkQueue.isEmpty()) return;

        double[] next = walkQueue.poll();
        double dx = next[0] - location.getX();
        double dy = next[1] - location.getY();
        double dz = next[2] - location.getZ();

        short relX = (short) (dx * 4096);
        short relY = (short) (dy * 4096);
        short relZ = (short) (dz * 4096);

        location = new Location(location.getWorld(), next[0], next[1], next[2], yaw, 0);
        npc.setLocation(next[0], next[1], next[2], yaw, 0);

        PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook movePacket =
                new PacketPlayOutEntity.PacketPlayOutRelEntityMoveLook(
                        npc.getId(), relX, relY, relZ, (byte) (yaw * 256 / 360), (byte) 0, true);
        PacketPlayOutEntityHeadRotation headPacket =
                new PacketPlayOutEntityHeadRotation(npc, (byte) (yaw * 256 / 360));

        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerConnection connection = ((CraftPlayer) player).getHandle().playerConnection;
            connection.sendPacket(movePacket);
            connection.sendPacket(headPacket);
        }
    }

    public boolean isWalking() {
        return !walkQueue.isEmpty();
    }

    /**
     * Ищет безопасную высоту (верхний твёрдый блок + 1) в указанной
     * колонне мира. Возвращает null, если колонна пустая (пустота/край карты).
     */
    private Integer findSafeY(org.bukkit.World world, double x, double z) {
        int blockX = (int) Math.floor(x);
        int blockZ = (int) Math.floor(z);
        int highest = world.getHighestBlockYAt(blockX, blockZ);
        Block ground = world.getBlockAt(blockX, highest, blockZ);
        if (ground.getType() == Material.AIR || ground.getType().isAir()) {
            return null;
        }
        return highest + 1;
    }

    // ---------------------------------------------------------------
    // "Выживание": добыча ресурсов
    // ---------------------------------------------------------------

    /**
     * Пытается найти и сломать один подходящий блок рядом с ботом.
     * Использует стандартный Bukkit API (Block#breakNaturally), поэтому
     * с выпадением предметов всё работает как при обычной добыче.
     */
    public boolean tryMineNearbyBlock(List<Material> allowedBlocks, int radius) {
        if (npc == null || !alive || location.getWorld() == null) return false;

        org.bukkit.World world = location.getWorld();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        for (int attempt = 0; attempt < 6; attempt++) {
            int dx = random.nextInt(radius * 2 + 1) - radius;
            int dz = random.nextInt(radius * 2 + 1) - radius;
            int dy = random.nextInt(3) - 1;

            Block block = world.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
            if (allowedBlocks.contains(block.getType())) {
                playSwingAnimation();
                block.breakNaturally();
                return true;
            }
        }
        return false;
    }

    public void playSwingAnimation() {
        if (npc == null) return;
        PacketPlayOutAnimation packet = new PacketPlayOutAnimation(npc, 0); // 0 = взмах основной рукой
        for (Player player : Bukkit.getOnlinePlayers()) {
            ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
        }
    }

    // ---------------------------------------------------------------
    // "Выживание": PvP, смерть, возрождение
    // ---------------------------------------------------------------

    public double distanceTo(Bot other) {
        if (!location.getWorld().equals(other.location.getWorld())) return Double.MAX_VALUE;
        return location.distance(other.location);
    }

    /**
     * Атакует другого бота: анимация взмаха + случайный урон.
     */
    public void attack(Bot target) {
        if (!alive || !target.alive) return;
        playSwingAnimation();
        int damage = 1 + random.nextInt(4);
        target.damage(damage);
    }

    public void damage(int amount) {
        if (!alive) return;
        hp -= amount;

        // короткая "тряска"/индикатор урона
        if (npc != null) {
            PacketPlayOutEntityStatus hurtStatus = new PacketPlayOutEntityStatus(npc, (byte) 2); // 2 = получен урон
            for (Player player : Bukkit.getOnlinePlayers()) {
                ((CraftPlayer) player).getHandle().playerConnection.sendPacket(hurtStatus);
            }
        }

        if (hp <= 0) {
            die();
        }
    }

    private void die() {
        alive = false;
        walkQueue.clear();

        if (npc != null) {
            PacketPlayOutEntityStatus deathStatus = new PacketPlayOutEntityStatus(npc, (byte) 3); // 3 = смерть
            for (Player player : Bukkit.getOnlinePlayers()) {
                ((CraftPlayer) player).getHandle().playerConnection.sendPacket(deathStatus);
            }
        }
    }

    /**
     * Полностью убирает старую модель и создаёт бота заново на домашней точке
     * с полным HP (используется после задержки возрождения).
     */
    public void respawn() {
        remove();
        this.location = homeLocation.clone();
        this.yaw = homeLocation.getYaw();
        spawn();
    }
}
