package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

public class TeleportListener implements Listener {
    private final Pets plugin;
    public TeleportListener(Pets plugin){ this.plugin = plugin; }

    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent e){
        // Alleen ingrijpen bij onze pets
        var pdc = e.getEntity().getPersistentDataContainer();
        if(!pdc.has(Pets.key("pet-owner"), PersistentDataType.STRING)) return;

        // Toegestaan? Alleen direct na een whistle
        Long allowUntil = pdc.get(Pets.key("pet-whistle-allow-until"), PersistentDataType.LONG);
        long now = System.currentTimeMillis();
        if(allowUntil != null && now <= allowUntil){
            // Laat één keer toe en wis de vlag
            pdc.remove(Pets.key("pet-whistle-allow-until"));
            return;
        }

        // Alle andere teleports (bv. vanilla tamed TP) blokkeren
        e.setCancelled(true);
    }
}
