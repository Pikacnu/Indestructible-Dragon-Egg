package org.pikacnu.undestoriedDragonEgg;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class EggRegistry {
    private static final EggRegistry INSTANCE = new EggRegistry();

    private final Map<UUID, UUID> playerToEggItemMap = new HashMap<>();
    private final Map<UUID, Location> PlayerUUIDToHolderLocation = new HashMap<>();

    public boolean hasPlayer(UUID playerUUID) {
        return playerToEggItemMap.containsKey(playerUUID);
    }

    public boolean hasEggEntity(UUID eggItemID) {
        return playerToEggItemMap.containsValue(eggItemID);
    }

    public void setPlayerItemKeyPair(UUID holderUUID, UUID eggItemID) {
        playerToEggItemMap.put(holderUUID, eggItemID);
    }

    public void removePairFromPlayerUUID(UUID playerUUID) {
        playerToEggItemMap.remove(playerUUID);
    }

    public void removePairFromItemEntityUUID(UUID itemUUID) {
        if (itemUUID == null) return;
        playerToEggItemMap.values().removeIf(itemUUID::equals);
    }

    public ArrayList<UUID> getAllEggItemIDs() {
        return new ArrayList<>(playerToEggItemMap.values());
    }

    public ArrayList<UUID> getAllHolderIds(){
        return new ArrayList<>(playerToEggItemMap.keySet());
    }

    public Location getHolderLocationByUUID(UUID playerUUID){
        return PlayerUUIDToHolderLocation.get(playerUUID);
    }

    public UUID getItemEntityUUIDByHolderUUID(UUID playerUUID){
        return playerToEggItemMap.get(playerUUID);
    }

    public void setPlayerLocationByPlayerUUID(UUID playerUUID,Location playerLocation){
        PlayerUUIDToHolderLocation.put(playerUUID,playerLocation);
    }

    public static EggRegistry getInstance() {
        return INSTANCE;
    }

    public Map<UUID, Location> getPlayerUUIDToHolderLocation() {
        return PlayerUUIDToHolderLocation;
    }

    public Location getEggTargetLocation(UUID holderUUID) {
        UUID itemUUID = playerToEggItemMap.get(holderUUID);

        if (itemUUID != null) {
            Entity entity = Bukkit.getEntity(itemUUID);
            if (entity != null && entity.isValid()) {
                return entity.getLocation();
            }
        }

        Player holder = Bukkit.getPlayer(holderUUID);
        if (holder != null && holder.isOnline()) {
            return holder.getLocation();
        }

        return null;
    }
}
