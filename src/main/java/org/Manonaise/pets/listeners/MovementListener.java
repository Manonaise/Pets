package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public class MovementListener implements Listener {
    private final Pets plugin;
    public MovementListener(Pets p){ this.plugin=p; }

    @EventHandler
    public void onMove(PlayerMoveEvent e){
        Location f = e.getFrom(), t = e.getTo();
        if(t==null) return;
        if(f.getBlockX()==t.getBlockX() && f.getBlockY()==t.getBlockY() && f.getBlockZ()==t.getBlockZ()) return;

        plugin.getPetManager().addWalkProgress(e.getPlayer().getUniqueId(), 1);
    }
}
