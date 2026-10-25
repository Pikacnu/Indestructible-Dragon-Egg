package org.pikacnu.undestoriedDragonEgg;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.key.Namespaced;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.bukkit.inventory.meta.CompassMeta;

import java.util.*;

public final class UndestoriedDragonEgg extends JavaPlugin implements Listener {

    private static final EggRegistry registry = EggRegistry.getInstance();
    private static BukkitScheduler scheduler = Bukkit.getScheduler();
    private static BukkitTask task;
    private static Map<String,Location> DragonEggHolderUUIDStringToLocation = new HashMap<>();
    private static ArrayList<String> DragonEggHolderUUID = new ArrayList<>();

    private final NamespacedKey TrackerNSKey = new NamespacedKey(this,"tracker");

    @Override
    public void onEnable() {
        // Plugin startup logic
        this.getServer().getPluginManager().registerEvents(this,this);
        scheduler = Bukkit.getScheduler();
        task = scheduler.runTaskTimer(this, () -> {
            registry.GetAllEggItemIDs().forEach((id)->{
                Entity eggItemEntity = getServer().getEntity(UUID.fromString(id));
                if (eggItemEntity == null){
                    registry.RemovePairFromEggItemID(id);
                    return;
                }
                // Vector velocity = item.getVelocity();
                //            item.setVelocity(new Vector(velocity.getX(), 0.2, velocity.getZ()));
                if (eggItemEntity instanceof Item item){
                    Location itemLocation = item.getLocation();
                    Location targetLocation = item.getLocation();
                    Location playerLocation = null;
                    targetLocation.setY(-70);
                    if (itemLocation.getBlockY() < -64) {
                        double minDistance = Double.MAX_VALUE;
                        for (Player onlinePlayer : getServer().getOnlinePlayers()) {
                            Location currentPlayer = onlinePlayer.getLocation();
                            if (currentPlayer.getBlockY() < -64) {
                                continue;
                            }
                            double distance = itemLocation.distance(currentPlayer);
                            if (distance < minDistance) {
                                minDistance = distance;
                                playerLocation = currentPlayer;
                            }
                        }
                    }
                    if (playerLocation != null) {
                        item.setGravity(true);
                        targetLocation = playerLocation;
                    }else{
                        item.setGravity(false);
                    }
                    item.teleport(targetLocation);

                }
                return;
            });
        },0L,5L);
        // Init The DragonEgg Entity Status
        getServer().getWorlds().forEach((wordData)->{
            for (Entity entity : wordData.getEntities()) {
                // For Track DragonEggEntity
                if (entity instanceof Item item){
                    if (item.getItemStack().getType() == Material.DRAGON_EGG){
                        UUID thrower = item.getThrower();
                        if (thrower != null) {
                            registry.AddPair(thrower.toString(),item.getUniqueId().toString());
                        }
                    }
                }
                // For Update User Compass Tracking Data
                if (entity instanceof Player player){
                    for (ItemStack itemStack : player.getInventory().getContents()){
                        String PlayerUUIDString = player.getUniqueId().toString();
                        if (itemStack == null) continue;
                        if (itemStack.getType() == Material.COMPASS) {
                            ItemMeta meta = itemStack.getItemMeta();
                            if (meta == null) continue;
                            if (meta.getPersistentDataContainer().getOrDefault(TrackerNSKey,PersistentDataType.BOOLEAN,false)){
                                CompassMeta cmeta = (CompassMeta) itemStack.getItemMeta();
                                if (cmeta == null) continue;
                                double minDistance = Double.MAX_VALUE;
                                Location currentLocation = player.getLocation();
                                Location targetLocation = null;
                                for (String holderID : DragonEggHolderUUIDStringToLocation.keySet()){
                                    Location holderLocation = DragonEggHolderUUIDStringToLocation.get(holderID);
                                    if (PlayerUUIDString.equals(holderID)){
                                        continue;
                                    }
                                    double distance = currentLocation.distance(holderLocation);
                                    if (distance < minDistance){
                                        minDistance = distance;
                                        targetLocation = holderLocation;
                                    }
                                }
                                if (targetLocation != null){
                                    cmeta.setLodestone(targetLocation);
                                }
                                itemStack.setItemMeta(cmeta);
                            }
                        }
                        if (itemStack.getType() == Material.DRAGON_EGG) {
                            ItemMeta meta = itemStack.getItemMeta();
                            if (meta == null) continue;
                            DragonEggHolderUUID.add(PlayerUUIDString);
                            DragonEggHolderUUIDStringToLocation.put(PlayerUUIDString,player.getLocation());
                        }
                    }
                }
            }
        });

        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("degg")
                .then(Commands.literal("give")
                    .then(Commands.literal("compass")
                            .executes(context ->{
                                Entity executor = context.getSource().getExecutor();
                                if (!(executor instanceof Player sourceEntity)){
                                    context.getSource().getSender().sendMessage(Component.text("Can't give item to non-player"));
                                    return 0;
                                }

                                ItemStack compass = new ItemStack(Material.COMPASS,1);
                                CompassMeta meta = (CompassMeta) compass.getItemMeta();
                                if (meta != null){
                                    meta.displayName(Component.text("Dragon Egg Tracker").decorate(TextDecoration.BOLD).decoration(TextDecoration.ITALIC,false));
                                    meta.lore(List.of(
                                            Component.text("Tracker of DragonEgg").decorate(TextDecoration.BOLD).color(TextColor.color(255, 252, 77))
                                    ));
                                    Location lot = new Location(sourceEntity.getWorld(),0.0d,0.0d,0.0d);
                                    meta.setLodestone(lot);
                                    meta.setLodestoneTracked(false);
                                    meta.getPersistentDataContainer().set(TrackerNSKey, PersistentDataType.BOOLEAN,true);

                                    compass.setItemMeta(meta);
                                }
                                Map<Integer,ItemStack> leftover = sourceEntity.getInventory().addItem(compass);
                                Location dropItemLot = sourceEntity.getLocation();
                                if (!leftover.isEmpty()){
                                    leftover.values().forEach(item -> {
                                        sourceEntity.getWorld().dropItem(dropItemLot,item);
                                    });
                                    sourceEntity.sendMessage(Component.text("Inventory full! Item dropped on the ground.", NamedTextColor.YELLOW));
                                }else{
                                    sourceEntity.sendMessage(Component.text("Received Dragon Egg Tracker!", NamedTextColor.GREEN));
                                }
                                return 1;
                            })
                    )
                );
        LiteralCommandNode buildCommand = command.build();
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register(buildCommand);
        });
    }

    @Override
    public void onDisable() {
        scheduler.cancelTask(task.getTaskId());
        Component message = Component.text("Plugin is shutting down!").color(TextColor.color(255, 0, 0));
        this.getServer().sendMessage(message);
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDropped(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        ItemStack stack = item.getItemStack();
        //getServer().broadcast(Component.text("Item Dropped: " + stack.getType()));
        if (stack.getType() != Material.DRAGON_EGG) return;
        registry.AddPair(event.getPlayer().getUniqueId().toString(), item.getUniqueId().toString());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggPickup(EntityPickupItemEvent event) {
        // 檢查被撿起的是不是我們正在追蹤的那顆蛋
        if (registry.HasEgg(event.getItem().getUniqueId())) {
            registry.RemovePairFromEggItemID(event.getItem().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event){
        Item item = event.getEntity();
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (stack.getType() == Material.DRAGON_EGG){
            item.setHealth(2147483647);
            if (meta != null) {
                Registry<@NotNull DamageType> damageTypeRegistry = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE);
                Set<DamageType> allDamageTypes = new HashSet<>();
                for (DamageType type : damageTypeRegistry) {
                    allDamageTypes.add(type);
                }
                RegistryKeySet<DamageType> immuneSet = RegistrySet.keySetFromValues(RegistryKey.DAMAGE_TYPE,allDamageTypes);
                meta.setDamageResistantTypes(immuneSet);
            }
            item.setUnlimitedLifetime(true);
        }
        stack.setItemMeta(meta);

    }

    @EventHandler(ignoreCancelled = true)
    public void onDragonEggDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Item item)) return;
        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.DRAGON_EGG) return;
        EntityDamageEvent.DamageCause cause = event.getCause();

        event.setCancelled(true);

        if (cause == EntityDamageEvent.DamageCause.LAVA) {// 強制關閉視覺著火特效 (設為 0 ticks)
            item.setFireTicks(0);
            Vector velocity = item.getVelocity();
            item.setVelocity(new Vector(velocity.getX(), 0.2, velocity.getZ()));
            event.setCancelled(true);
        } else {
            // 對於其他傷害來源，直接取消傷害
            event.setCancelled(true);
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event){
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        if (!registry.HasPlayer(player.getUniqueId())) {
            return;
        }
        String eggItemID = registry.GetEggItemIDFromThrowerUUID(player.getUniqueId().toString());
        if (eggItemID == null) {
            return;
        }

        UUID ThrowerRelatedEggItemID = UUID.fromString(eggItemID);
        Entity itemEntity = getServer().getEntity(ThrowerRelatedEggItemID);

        if (itemEntity == null) return;
        if (player.getUniqueId() != ThrowerRelatedEggItemID) return;
        if ((itemEntity instanceof Item item)) {
            Location playerLocation = player.getLocation();
            Location itemLocation = item.getLocation();

            double distance = playerLocation.distance(itemLocation);
            if (distance > 10) {
                // 如果玩家與物品的距離大於 10，將物品傳送到玩家位置
                item.teleport(playerLocation);
                return;
            }
            if (player.getWorld() != item.getWorld()) {
                // 如果玩家與物品不在同一個世界，將物品傳送到玩家位置
                item.teleport(playerLocation);
            }
        }
    }


    // 龍蛋虛空會傳送到 ThrownPlayer
    // 龍蛋在遠離 丟出的人 10 格遠時會傳送到使用者身上
    // 持有者進入虛空時會把龍蛋給到最近的玩家身

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event){
        Entity entity = event.getRightClicked();
        if (entity.getType() == EntityType.COW){
            entity.getWorld().createExplosion(entity, 4.0f);
        }
    }
}
