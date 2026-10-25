package org.pikacnu.undestoriedDragonEgg;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class Indicator {
    private ArrayList<UUID> enablePlayerUUID = new ArrayList<>();
    private Map<UUID,BossBar> playerToBossBar = new HashMap<>();

    public void AddPlayer(UUID playerUUID){
        if (!enablePlayerUUID.contains(playerUUID)){
            enablePlayerUUID.add(playerUUID);
            this.InitializePlayer(playerUUID);
        }
    }

    public void RemovePlayer(UUID playerUUID){
        if (enablePlayerUUID.contains(playerUUID)) {
            this.enablePlayerUUID.remove(playerUUID);
            DeinitializePlayer(playerUUID);
        };
    }

    public boolean IsPlayerInitial(UUID playerUUID){
        return enablePlayerUUID.contains(playerUUID);
    }

    public ArrayList<UUID> GetAllEnabledPlayer(){
        return enablePlayerUUID;
    }

    public BossBar GetBossBarFromPlayer(UUID playerUUID){
        return playerToBossBar.get(playerUUID);
    }

    private void InitializePlayer(UUID playerUUID){
        Component bossbarName = Component.text("Target Not Found", NamedTextColor.RED);
        BossBar bossbar = BossBar.bossBar(bossbarName,1, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        Objects.requireNonNull(Bukkit.getPlayer(playerUUID)).showBossBar(bossbar);
        playerToBossBar.put(playerUUID,bossbar);
    }

    private void DeinitializePlayer(UUID playerUUID) {
        BossBar bossbar = playerToBossBar.get(playerUUID);
        if (bossbar == null){
            return;
        }
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null && player.isOnline()) {
            player.hideBossBar(bossbar);
        }
    }
}
