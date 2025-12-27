package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class QuitListener implements Listener {
    private final Pets plugin;

    public QuitListener(Pets plugin) { this.plugin = plugin; }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) { despawnPlayerPets(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onKick(PlayerKickEvent event) { despawnPlayerPets(event.getPlayer().getUniqueId()); }

    private void despawnPlayerPets(UUID owner) {
        for (Pet pet : plugin.getPetManager().getPets(owner)) {
            if (pet.isSpawned()) plugin.getPetManager().despawn(pet);
        }
        plugin.getPetManager().save();
    }
}
