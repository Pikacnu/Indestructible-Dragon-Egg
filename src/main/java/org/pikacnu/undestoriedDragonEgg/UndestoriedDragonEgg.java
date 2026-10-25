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
import org.bukkit.event.player.PlayerInteractEvent;
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

    private static Cooldown playerToggleTrackerCooldown = new Cooldown(2*20); // 2 * 20 ticks
    private static Indicator playerBossbarIndicator = new Indicator();

    private final NamespacedKey TrackerNSKey = new NamespacedKey(this,"tracker");
    private final NamespacedKey MarkedUUID = new NamespacedKey(this,"eggEntityUUID");

    @Override
    public void onEnable() {

        this.getServer().getPluginManager().registerEvents(this,this);

        scheduler = Bukkit.getScheduler();

        task = scheduler.runTaskTimer(this, () -> {
            // process dropped Egg
            // prevent item dropped into void
            // and tp back to player
            for (UUID eggItemUUID : registry.getAllEggItemIDs()) {
                if (eggItemUUID == null) continue;
                Entity entity = getServer().getEntity(eggItemUUID);

                if (!(entity instanceof Item item) || !item.isValid()) {
                    registry.removePairFromItemEntityUUID(eggItemUUID);
                    continue;
                }

                Location itemLocation = item.getLocation();

                if (itemLocation.getBlockY() < -64) {
                    // if inside void
                    item.setGravity(false);
                    // try to find nesrest player in same world to tp to
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
                    // if not at void remove floating
                    item.setGravity(true);
                }
            }

            // process all Player Online
            getServer().getOnlinePlayers().forEach(player -> {
                // Check Player Inventory And Get Item's First
                boolean isEggInsidePlayer = false;
                boolean isCompassInsidePlayer = false;
                boolean isPlayerOpenBossbarIndicator = playerBossbarIndicator.IsPlayerInitial(player.getUniqueId());

                ArrayList<ItemStack> CompassItemStacks = new ArrayList<>();

                for (ItemStack itemStack : player.getInventory().getContents()) {
                    if (itemStack == null) continue;
                    if (itemStack.getType() == Material.DRAGON_EGG) {
                        isEggInsidePlayer = true;
                        continue;
                    }
                    if (itemStack.getType() == Material.COMPASS){
                        ItemMeta meta = itemStack.getItemMeta();
                        if(meta != null){
                            if (meta.getPersistentDataContainer().getOrDefault(TrackerNSKey,PersistentDataType.BOOLEAN,false)){
                                isCompassInsidePlayer = true;
                                CompassItemStacks.add(itemStack);
                            }

                        }
                    }
                }
                // if Is Egg Inside Player Inventory
                if (isEggInsidePlayer){
                    // set And Track It
                    if (!registry.hasPlayer(player.getUniqueId())){
                        registry.setPlayerItemKeyPair(player.getUniqueId(),null);
                    }
                }

                // update Compass And BossBar
                if(isCompassInsidePlayer) {
                    ArrayList<UUID> EntitiesRelatedToEgg = new ArrayList<>();
                    for(UUID holderUUID : registry.getAllHolderIds()){
                        UUID ItemEggUUID = registry.getItemEntityUUIDByHolderUUID(holderUUID);
                        if (ItemEggUUID != null){
                            Entity EggItem = getServer().getEntity(ItemEggUUID);
                            if (EggItem != null){
                                if(EggItem.getWorld().equals(player.getWorld())){
                                    EntitiesRelatedToEgg.add(ItemEggUUID);
                                    continue;
                                }
                            }
                        }
                        if (holderUUID.equals(player.getUniqueId())) continue;
                        Player holder = getServer().getPlayer(holderUUID);
                        if (holder == null) continue;
                        if (!Objects.requireNonNull(holder).getWorld().equals(player.getWorld()))continue;
                        EntitiesRelatedToEgg.add(holderUUID);
                    }
                    double playerDistanceToTarget = Double.MAX_VALUE;
                    Location playerLocation = player.getLocation();
                    Location minEggRelatedEntityLocation = null;
                    for (UUID uuid : EntitiesRelatedToEgg){
                        Entity entity = getServer().getEntity(uuid);
                        if(entity == null) continue;
                        Location targetLocation = entity.getLocation();
                        double playerToTargetDisTemp = targetLocation.distance(playerLocation);
                        if (playerToTargetDisTemp < playerDistanceToTarget){
                            minEggRelatedEntityLocation = targetLocation;
                            playerDistanceToTarget = playerToTargetDisTemp;
                        }
                    }
                    if( minEggRelatedEntityLocation == null){
                        // if not found
                        // set compass to someplace which not place loadstone
                        // and set bossbar to not found
                        if(isPlayerOpenBossbarIndicator){
                            BossBar bossbar = playerBossbarIndicator.GetBossBarFromPlayer(player.getUniqueId());
                            bossbar.name(Component.text("Target Not Found",NamedTextColor.RED));
                        }
                        for(ItemStack stack : CompassItemStacks){
                            ItemMeta meta = stack.getItemMeta();
                            if (meta instanceof CompassMeta compassMeta){
                                Location savedLot = compassMeta.getLodestone();
                                Location setToLot = new Location(player.getWorld(), 0,-65,0);
                                if (savedLot != null && savedLot.distance(setToLot) == 0) continue;
                                compassMeta.setLodestoneTracked(true);
                                compassMeta.setLodestone(setToLot);
                                stack.setItemMeta(compassMeta);
                            }
                        }
                    }else{
                        // if found
                        // update compass position and bossbar
                        if(isPlayerOpenBossbarIndicator){
                            BossBar bossbar = playerBossbarIndicator.GetBossBarFromPlayer(player.getUniqueId());
                            bossbar.name(Component.text("Target Position is : ",NamedTextColor.GREEN).append(
                                                Component.text(
                                                        minEggRelatedEntityLocation.getBlockX()+", "+
                                                                minEggRelatedEntityLocation.getBlockY()+", "+
                                                                minEggRelatedEntityLocation.getBlockZ(),
                                                        NamedTextColor.YELLOW)
                                            ));
                        }
                        for(ItemStack stack : CompassItemStacks){
                            ItemMeta meta = stack.getItemMeta();
                            if (meta instanceof CompassMeta compassMeta){
                                compassMeta.setLodestoneTracked(false);
                                compassMeta.setLodestone(minEggRelatedEntityLocation);
                                stack.setItemMeta(compassMeta);
                            }
                        }
                    }

                }else{
                    // Bossbar can't be open when compass is not inside Player Inventory
                    if (isPlayerOpenBossbarIndicator){
                        playerBossbarIndicator.RemovePlayer(player.getUniqueId());
                    }
                }

//                registry.setPlayerLocationByPlayerUUID(player.getUniqueId(),player.getLocation());
//                    }
//                    if (itemStack.getType() == Material.COMPASS) {
//                        ItemMeta meta = itemStack.getItemMeta();
//                        if (meta == null) continue;
//                        if (meta.getPersistentDataContainer().getOrDefault(TrackerNSKey,PersistentDataType.BOOLEAN,false)){
//                            CompassMeta cmeta = (CompassMeta) itemStack.getItemMeta();
//                            if (cmeta == null) continue;
//
//                            double minDistance = Double.MAX_VALUE;
//                            Location currentLocation = player.getLocation();
//                            Location targetLocation = null;
//                            for (UUID holderID : registry.getAllHolderIds()){
//                                Location holderLocation = registry.getHolderLocationByUUID(holderID);
//                                if (holderLocation == null) continue;
//                                if (playerUUID.equals(holderID)){
//                                    continue;
//                                }
//                                if (!currentLocation.getWorld().equals(holderLocation.getWorld())) continue;
//                                double distance = currentLocation.distance(holderLocation);
//                                if (distance < minDistance){
//                                    minDistance = distance;
//                                    targetLocation = holderLocation;
//                                }
//                            }
//                            if (targetLocation != null){
//                                cmeta.setLodestone(targetLocation);
//                            }
//
//                            if (EnalbedBossbarIndicatorPlayerUUIDs.contains(player.getUniqueId())){
//                                BossBar targetBossbar = playerBossbarIndicator.GetBossBarFromPlayer(player.getUniqueId());
//                                if (targetLocation != null){
//                                    targetBossbar.name(Component.text("Target Position is : ",NamedTextColor.GREEN)
//                                            .append(
//                                                Component.text(targetLocation.getBlockX()+", "+targetLocation.getBlockY()+", "+ targetLocation.getBlockZ(),NamedTextColor.YELLOW)
//                                            )
//                                    );
//                                }else{
//                                    targetBossbar.name(Component.text("Target Not Found",NamedTextColor.RED));
//                                }
//                            }
//                            itemStack.setItemMeta(cmeta);
//                        }
//                    }
//                }
            });
        },0L,5L);
        getServer().getWorlds().forEach((wordData)->{
            for (Entity entity : wordData.getEntities()) {
                // For Track DragonEggEntity
                if (entity instanceof Item item){
                    if (item.getItemStack().getType() == Material.DRAGON_EGG){
                        UUID thrower = item.getThrower();
                        if (thrower != null) {
                            registry.setPlayerItemKeyPair(thrower,item.getUniqueId());
                        }
                    }
                }
            }
//            for (Player player : getServer().getOnlinePlayers()){
//                boolean isPlayerHaveDragonEgg = false;
//                for (ItemStack itemstack : player.getInventory().getContents()){
//                    if(itemstack.getType() == Material.DRAGON_EGG){
//                        isPlayerHaveDragonEgg = true;
//                        break;
//                    }
//                }
//                if (isPlayerHaveDragonEgg){
//                    if (!registry.hasPlayer(player.getUniqueId())){
//                        registry.setPlayerItemKeyPair(player.getUniqueId(),null);
//                    }
//                }
//            }
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

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event){
        Player player = event.getPlayer();
        ItemStack playerHandItem = event.getItem();
        if (playerHandItem == null) return;
        if(playerHandItem.getType() != Material.COMPASS) return;
        CompassMeta meta = (CompassMeta) playerHandItem.getItemMeta();
        if (!meta.getPersistentDataContainer().getOrDefault(TrackerNSKey,PersistentDataType.BOOLEAN,false)) return;
        boolean isPlayerNotInsideCooldown = playerToggleTrackerCooldown.setPlayerToCooldown(player.getUniqueId());
        if (!isPlayerNotInsideCooldown){
            player.sendMessage(Component.text("In Cooldown, Try again later",NamedTextColor.RED));
            return;
        }
        getServer().broadcast(Component.text("Player Handle Tracker Compass"));
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
        ItemMeta meta = stack.getItemMeta();
        if (meta != null){
            meta.getPersistentDataContainer().set(MarkedUUID,PersistentDataType.STRING,item.getUniqueId().toString());
            stack.setItemMeta(meta);
            item.setItemStack(stack);
        }
        registry.setPlayerItemKeyPair(event.getPlayer().getUniqueId(), item.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEggPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        ItemStack stack = item.getItemStack();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null){
            String UUIDString = meta.getPersistentDataContainer().get(MarkedUUID,PersistentDataType.STRING);
            UUID ItemEntityUUID = null;
            if (UUIDString != null) {
                ItemEntityUUID = UUID.fromString(UUIDString);
            }
            registry.removePairFromItemEntityUUID(ItemEntityUUID);
        }
        if (registry.hasEggEntity(event.getItem().getUniqueId())) {
            registry.removePairFromItemEntityUUID(event.getItem().getUniqueId());
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

    @EventHandler (ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event){
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY()) {
            return;
        }

        Player player = event.getPlayer();
        if (!registry.hasPlayer(player.getUniqueId())) {
            return;
        }
        UUID eggItemID = registry.getItemEntityUUIDByHolderUUID(player.getUniqueId());
        if (eggItemID == null) {
            return;
        }

        Entity itemEntity = getServer().getEntity(eggItemID);

        if (itemEntity == null) return;
        if ((itemEntity instanceof Item item)) {
            Location playerLocation = player.getLocation();
            Location itemLocation = item.getLocation();

            if (!player.getWorld().equals(item.getWorld())) {
                item.teleport(playerLocation);
                return;
            }
            double distance = playerLocation.distance(itemLocation);
            if (distance > 10) {
                item.teleport(playerLocation);
                item.setPickupDelay(10);
            }
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
