package org.pikacnu.undestoriedDragonEgg;

import java.util.*;

public class EggRegistry {
    private static final EggRegistry INSTANCE = new EggRegistry();

    private Map<String,String> EggItemIDToThrowerUUIDMap = new HashMap<>();
    private Map<String,String> ThrowerUUIDToEggItemIDMap = new HashMap<>();
    private Set<String> ThrowerUUIDSet = new HashSet<>();

    public boolean HasPlayer(String throwerUUID) {
        return ThrowerUUIDSet.contains(throwerUUID);
    }

    public boolean HasPlayer(UUID throwerUUID) {
        return ThrowerUUIDSet.contains(throwerUUID.toString());
    }

    public boolean HasEgg(String eggItemID) {
        return EggItemIDToThrowerUUIDMap.containsKey(eggItemID);
    }

    public boolean HasEgg(UUID eggItemID) {
        return EggItemIDToThrowerUUIDMap.containsKey(eggItemID.toString());
    }

    public void AddPair(String throwerUUID, String eggItemID) {
        EggItemIDToThrowerUUIDMap.put(eggItemID, throwerUUID);
        ThrowerUUIDToEggItemIDMap.put(throwerUUID, eggItemID);
        ThrowerUUIDSet.add(throwerUUID);
    }

    public void RemovePairFromEggItemID(String eggItemID) {
        String throwerUUID = EggItemIDToThrowerUUIDMap.get(eggItemID);
        EggItemIDToThrowerUUIDMap.remove(eggItemID);
        ThrowerUUIDToEggItemIDMap.remove(throwerUUID);
        ThrowerUUIDSet.remove(throwerUUID);
    }

    public void RemovePairFromEggItemID(UUID rawEggItemID) {
        String eggItemID = rawEggItemID.toString();
        String throwerUUID = EggItemIDToThrowerUUIDMap.get(eggItemID);
        EggItemIDToThrowerUUIDMap.remove(eggItemID);
        ThrowerUUIDToEggItemIDMap.remove(throwerUUID);
        ThrowerUUIDSet.remove(throwerUUID);
    }



    public void RemovePairFromThrowerUUID(String throwerUUID) {
        String eggItemID = ThrowerUUIDToEggItemIDMap.get(throwerUUID);
        EggItemIDToThrowerUUIDMap.remove(eggItemID);
        ThrowerUUIDToEggItemIDMap.remove(throwerUUID);
        ThrowerUUIDSet.remove(throwerUUID);
    }

    public String GetThrowerUUIDFromEggItemID(String eggItemID) {
        return EggItemIDToThrowerUUIDMap.get(eggItemID);
    }

    public String GetEggItemIDFromThrowerUUID(String throwerUUID) {
        return ThrowerUUIDToEggItemIDMap.get(throwerUUID);
    }

    public ArrayList<String> GetAllEggItemIDs() {
        return new ArrayList<>(EggItemIDToThrowerUUIDMap.keySet());
    }

    public static EggRegistry getInstance() {
        return INSTANCE;
    }
}
