package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

public class PetDamageListener implements Listener {

    private final Pets plugin;

    public PetDamageListener(Pets plugin) {
        this.plugin = plugin;
    }

    private boolean isOurPet(Entity e) {
        try {
            return e.getPersistentDataContainer().has(Pets.key("pet-owner"), PersistentDataType.STRING)
                    && e.getPersistentDataContainer().has(Pets.key("pet-id"), PersistentDataType.INTEGER);
        } catch (Throwable t) {
            return false;
        }
    }

    // ✅ Alle damage types blokkeren (fall, fire, drowning, cactus, etc)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!isOurPet(e.getEntity())) return;

        e.setCancelled(true);
        try { e.getEntity().setFireTicks(0); } catch (Throwable ignored) {}
    }

    // ✅ Ook entity-vs-entity damage (soms handig bij plugins)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (!isOurPet(e.getEntity())) return;
        e.setCancelled(true);
    }
}
