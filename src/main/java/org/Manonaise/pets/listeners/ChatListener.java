package org.Manonaise.pets.listeners;

import io.papermc.paper.event.player.AsyncChatEvent; // Paper chat-event
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.data.PetManager;
import org.Manonaise.pets.follow.ActivePet;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.Locale;

public class ChatListener implements Listener {
    private final Pets plugin;
    public ChatListener(Pets plugin){ this.plugin = plugin; }

    // PAPER (Adventure) chat event
    @EventHandler
    public void onPaperChat(AsyncChatEvent e){
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message())
                .trim().toLowerCase(Locale.ROOT);
        if (handleSitStand(e.getPlayer(), msg)) {
            e.setCancelled(true);
        }
    }

    // Legacy/compat event (als je server dit nog gebruikt)
    @EventHandler
    public void onLegacyChat(AsyncPlayerChatEvent e){
        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (handleSitStand(e.getPlayer(), msg)) {
            e.setCancelled(true);
        }
    }

    private boolean handleSitStand(Player p, String msg){
        PetManager pm = plugin.getPetManager();

        for (Pet pet : pm.getPets(p.getUniqueId())) {
            String cleanName = ChatColor.stripColor(pet.getName())
                    .trim().toLowerCase(Locale.ROOT);

            if (msg.equals(cleanName + " zit")) {
                // Switch naar main thread voor entity-mutaties
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ActivePet ap = pm.getActive(pet);
                    if (ap != null) ap.setSitting(true);
                    p.sendMessage("§7" + pet.getName() + " gaat zitten.");
                });
                return true;
            }
            if (msg.equals(cleanName + " sta") || msg.equals(cleanName + " staan")) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ActivePet ap = pm.getActive(pet);
                    if (ap != null) ap.setSitting(false);
                    p.sendMessage("§7" + pet.getName() + " staat op.");
                });
                return true;
            }
        }
        return false;
    }
}
