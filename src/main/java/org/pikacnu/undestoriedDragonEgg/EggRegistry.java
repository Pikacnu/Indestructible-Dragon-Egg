package org.pikacnu.undestoriedDragonEgg;

import java.util.*;
public class EggRegistry {
    private static final EggRegistry INSTANCE = new EggRegistry();

    private final Map<UUID, UUID> droppedEggToThrowerMap = new HashMap<>();

    private final Set<UUID> inventoryHolders = new HashSet<>();

    public static EggRegistry getInstance() {
        return INSTANCE;
    }

    public void addDroppedEgg(UUID eggItemUUID, UUID throwerUUID) {
        droppedEggToThrowerMap.put(eggItemUUID, throwerUUID);
    }

    public void removeDroppedEgg(UUID eggItemUUID) {
        if (eggItemUUID != null) {
            droppedEggToThrowerMap.remove(eggItemUUID);
        }
    }

    public Set<UUID> getAllDroppedEggItemIDs() {
        return new HashSet<>(droppedEggToThrowerMap.keySet());
    }

    public boolean isDroppedEgg(UUID eggItemUUID) {
        return droppedEggToThrowerMap.containsKey(eggItemUUID);
    }

    public UUID getThrowerByEggItemUUID(UUID eggItemUUID){
        return droppedEggToThrowerMap.get(eggItemUUID);
    }

    public void addInventoryHolder(UUID playerUUID) {
        inventoryHolders.add(playerUUID);
    }

    public boolean hasPlayer(UUID playerUUID){
        return inventoryHolders.contains(playerUUID);
    }

    public void removeInventoryHolder(UUID playerUUID) {
        inventoryHolders.remove(playerUUID);
    }

    public Set<UUID> getAllInventoryHolders() {
        return new HashSet<>(inventoryHolders);
    }
}
