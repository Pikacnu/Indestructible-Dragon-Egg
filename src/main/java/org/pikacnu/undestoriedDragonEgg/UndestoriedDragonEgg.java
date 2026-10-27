package org.pikacnu.undestoriedDragonEgg;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.bossbar.BossBar;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    private static final Cooldown playerToggleTrackerCooldown = new Cooldown(2*20); // 2 * 20 ticks
    private static final Indicator playerBossbarIndicator = new Indicator();

    private final NamespacedKey TrackerNSKey = new NamespacedKey(this,"tracker");

    @Override
    public void onEnable() {

        this.getServer().getPluginManager().registerEvents(this,this);

        scheduler = Bukkit.getScheduler();

        task = scheduler.runTaskTimer(this, () -> {
            // process dropped Egg
            // prevent item dropped into void
            // and tp back to player
            for (UUID eggItemUUID : registry.getAllDroppedEggItemIDs()) {
                Entity entity = getServer().getEntity(eggItemUUID);

                if (!(entity instanceof Item item) || !item.isValid()) {
                    registry.removeDroppedEgg(eggItemUUID);
                    continue;
                }

                Location itemLocation = item.getLocation();
                if (itemLocation.getBlockY() < -64) {
                    item.setGravity(false);
                    Player nearestPlayer = null;
                    double minDistance = Double.MAX_VALUE;

                    for (Player onlinePlayer : item.getWorld().getPlayers()) {
                        if (onlinePlayer.getLocation().getBlockY() < -64) continue;
                        double distance = itemLocation.distance(onlinePlayer.getLocation());
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestPlayer = onlinePlayer;
                        }
                    }

                    if (nearestPlayer != null) {
                        item.teleport(nearestPlayer.getLocation());
                        item.setGravity(true);
                    }
                } else {
                    item.setGravity(true);
                }

                UUID throwerUUID = registry.getThrowerByEggItemUUID(eggItemUUID);
                if (throwerUUID != null){
                    Player thrower = getServer().getPlayer(throwerUUID);
                    if(thrower != null && thrower.isOnline()){
                        if(!thrower.getWorld().equals(item.getWorld())){
                            item.teleport(thrower.getLocation());
                        }else if (itemLocation.distance(thrower.getLocation()) > 10){
                            item.teleport((thrower.getLocation()));
                            item.setPickupDelay(10);
                        }
                    }
                }
            }

            // process all Player Online
            getServer().getOnlinePlayers().forEach(player -> {
                // Check Player Inventory And Get Item's First
                boolean isEggInsidePlayer = false;
                ArrayList<ItemStack> trackerCompasses = new ArrayList<>();

                for (ItemStack itemStack : player.getInventory().getContents()) {
                    if (itemStack == null) continue;
                    if (itemStack.getType() == Material.DRAGON_EGG) {
                        isEggInsidePlayer = true;
                        continue;
                    }
                    if (itemStack.getType() == Material.COMPASS){
                        ItemMeta meta = itemStack.getItemMeta();
                        if(meta != null && meta.getPersistentDataContainer().getOrDefault(TrackerNSKey,PersistentDataType.BOOLEAN,false)) {
                            trackerCompasses.add(itemStack);
                        }
                    }
                }

                // if Is Egg Inside Player Inventory
                if (isEggInsidePlayer){
                    registry.addInventoryHolder(player.getUniqueId());
                }else{
                    registry.removeInventoryHolder(player.getUniqueId());
                }

                if(!trackerCompasses.isEmpty()){
                    Location playerLocation = player.getLocation();
                    Location minTargetLocation = null;
                    double minDistance = Double.MAX_VALUE;
                    for (UUID eggUUID : registry.getAllDroppedEggItemIDs()){
                      Entity eggEntity = getServer().getEntity(eggUUID);
                      if (eggEntity != null && eggEntity.getWorld().equals(player.getWorld())){
                          double dist = eggEntity.getLocation().distance(playerLocation);
                          if(dist < minDistance){
                              minDistance = dist;
                              minTargetLocation = eggEntity.getLocation();
                          }
                      }
                    }
                    for (UUID holderUUID : registry.getAllInventoryHolders()){
                        if(holderUUID.equals(player.getUniqueId())) continue;
                        Player holder = getServer().getPlayer(holderUUID);
                        if (holder != null && holder.getWorld().equals(player.getWorld())){
                            double dist = holder.getLocation().distance(playerLocation);
                            if(dist < minDistance){
                                minDistance = dist;
                                minTargetLocation = holder.getLocation();
                            }
                        }
                    }
                    boolean isBossBarOpen = playerBossbarIndicator.IsPlayerInitial(player.getUniqueId());
                    if( minTargetLocation !=null ){
                        if (isBossBarOpen) {
                            BossBar bossbar = playerBossbarIndicator.GetBossBarFromPlayer(player.getUniqueId());
                            bossbar.name(
                                            Component.text("Target Position is : ", NamedTextColor.GREEN)
                                                    .append(
                                                            Component.text(minTargetLocation.getBlockX() + ", " + minTargetLocation.getBlockY() + ", " + minTargetLocation.getBlockZ(), NamedTextColor.YELLOW)
                                                    )
                                    );

                            double distance = player.getLocation().distance(minTargetLocation);
                            bossbar.progress((float) (1 - distance / 500 ));
                            if (distance > 500){
                                bossbar.color(BossBar.Color.PURPLE);
                            } else if (distance > 250) {
                                bossbar.color(BossBar.Color.BLUE);
                            } else if (distance > 100) {
                                bossbar.color(BossBar.Color.PINK);
                            } else if (distance > 50) {
                                bossbar.color(BossBar.Color.YELLOW);
                            } else if (distance > 25) {
                                bossbar.color(BossBar.Color.GREEN);
                            }else {
                                bossbar.color(BossBar.Color.WHITE);
                            }
                        }
                        for (ItemStack itemStack : trackerCompasses){
                            CompassMeta meta = (CompassMeta) itemStack.getItemMeta();
                            meta.setLodestoneTracked(false);
                            meta.setLodestone(minTargetLocation);
                        }
                    }else{
                        if(isBossBarOpen){
                            playerBossbarIndicator.GetBossBarFromPlayer(player.getUniqueId())
                                    .name(Component.text("Target Not Found", NamedTextColor.RED));
                        }
                        for (ItemStack itemStack : trackerCompasses){
                            CompassMeta meta = (CompassMeta) itemStack.getItemMeta();
                            Location SettedLocation = meta.getLodestone();
                            Location SetedToLocation = new Location(player.getWorld(),0,-65,0);
                            if (SettedLocation == null || SetedToLocation.distance(SettedLocation) == 0){
                                meta.setLodestoneTracked(true);
                                meta.setLodestone(SettedLocation);
                            }
                        }
                    }
                }else{
                    if (playerBossbarIndicator.IsPlayerInitial(player.getUniqueId())) {
                        playerBossbarIndicator.RemovePlayer(player.getUniqueId());
                    }
                }


            });
        },0L,5L);

        getServer().getWorlds().forEach((wordData)->{
            for (Entity entity : wordData.getEntities()) {
                // For Track DragonEggEntity
                if (entity instanceof Item item){
                    if (item.getItemStack().getType() == Material.DRAGON_EGG){
                        UUID thrower = item.getThrower();
                        if (thrower != null) {
                            registry.addDroppedEgg(item.getUniqueId(),thrower);
                        }
                    }
                }
            }
        });

        LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("degg")
                .then(Commands.literal("give")
                    .then(Commands.literal("compass")
                            .executes(context ->{
                                if (!context.getSource().getSender().isOp()){
                                    return 0;
                                }
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
                                            Component.text("Tracker of DragonEgg").decorate(TextDecoration.BOLD).color(TextColor.color(255, 252, 77)),
                                            Component.text("Right click can toggle boosbar tracker").color(NamedTextColor.GRAY)
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
                                    leftover.values().forEach(item -> sourceEntity.getWorld().dropItem(dropItemLot,item));
                                    sourceEntity.sendMessage(Component.text("Inventory full! Item dropped on the ground.", NamedTextColor.YELLOW));
                                }else{
                                    sourceEntity.sendMessage(Component.text("Received Dragon Egg Tracker!", NamedTextColor.GREEN));
                                }
                                return 1;
                            })
                    )
                )
                .then(
                        Commands.literal("help")
                                .executes(context ->{
                                    CommandSender sender = context.getSource().getSender();
                                    sender.sendMessage(Component.text("This plugin in design to keep track of dragon eggs and Prevent it disappear.",NamedTextColor.YELLOW));
                                    return 1;
                                })
                );
        LiteralCommandNode<CommandSourceStack> buildCommand = command.build();
        this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> event.registrar().register(buildCommand));
    }

    @Override
    public void onDisable() {
        scheduler.cancelTask(task.getTaskId());
        Component message = Component.text("Plugin is shutting down!").color(TextColor.color(255, 0, 0));
        this.getServer().sendMessage(message);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack playerHandItem = event.getItem();
        Action action = event.getAction();
        if (!action.isRightClick() && !(action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) return;
        if(event.getHand() != EquipmentSlot.HAND) return;
        if (playerHandItem == null) return;
        if(playerHandItem.getType() != Material.COMPASS) return;
        CompassMeta meta = (CompassMeta) playerHandItem.getItemMeta();
        if (!meta.getPersistentDataContainer().getOrDefault(TrackerNSKey,PersistentDataType.BOOLEAN,false)) return;
        boolean isPlayerNotInsideCooldown = playerToggleTrackerCooldown.setPlayerToCooldown(player.getUniqueId());
        if (!isPlayerNotInsideCooldown){
            player.sendMessage(Component.text("In Cooldown, Try again later",NamedTextColor.RED));
            return;
        }
        //getServer().broadcast(Component.text("Player Handle Tracker Compass"));
        if (playerBossbarIndicator.IsPlayerInitial(player.getUniqueId())){
            playerBossbarIndicator.RemovePlayer(player.getUniqueId());
        }else{
            playerBossbarIndicator.AddPlayer(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDropped(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.DRAGON_EGG) return;
        registry.addDroppedEgg(item.getUniqueId(),event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggPickup(EntityPickupItemEvent event) {
        registry.removeDroppedEgg(event.getItem().getUniqueId());
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
        item.setItemStack(stack);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDragonEggDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Item item)) return;
        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.DRAGON_EGG) return;
        EntityDamageEvent.DamageCause cause = event.getCause();

        event.setCancelled(true);

        if (cause == EntityDamageEvent.DamageCause.LAVA) {
            item.setFireTicks(0);
            Vector velocity = item.getVelocity();
            item.setVelocity(new Vector(velocity.getX(), 0.2, velocity.getZ()));
            event.setCancelled(true);
        } else {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEntityEvent event){
        Entity entity = event.getRightClicked();
        if (entity.getType() == EntityType.COW){
            entity.getWorld().createExplosion(entity, 4.0f);
        }
    }
}
