package org.pikacnu.undestoriedDragonEgg;

import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Cooldown {
    private final Map<UUID,Integer> PlayerToLastUse = new HashMap<>();
    private Integer cooldown = null;

    public Cooldown(Integer cooldown){
        this.cooldown = cooldown;
    }

    public boolean isInCooldown(UUID playerUUID){
        Integer lastUse = this.PlayerToLastUse.get(playerUUID);
        if (lastUse == null){
            lastUse = -1;
        }
        return !(Bukkit.getCurrentTick() > lastUse + this.cooldown);
    }

    public boolean setPlayerToCooldown(UUID playerUUID){
        if (isInCooldown(playerUUID)) return false;
        this.PlayerToLastUse.put(playerUUID,Bukkit.getCurrentTick());
        return true;
    }
}
