package org.Manonaise.pets.listeners;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.menu.PetMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class MenuGateListener implements Listener {

    private final Pets plugin;

    public MenuGateListener(Pets plugin) {
        this.plugin = plugin;
    }

    /**
     * Klik op geplaatst ItemsAdder custom block.
     * Bijvoorbeeld: topia:dierenmand
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onCustomBlockInteract(CustomBlockInteractEvent e) {
        if (!isIaPresent()) return;

        String targetId = cfgId();
        if (targetId.isEmpty()) return;

        try {
            String id = e.getNamespacedID();

            if (id == null || !id.equalsIgnoreCase(targetId)) {
                return;
            }

            Player player = e.getPlayer();

            allowNow(player);
            openPetMenu(player);

        } catch (Throwable ignored) {
        }
    }

    /**
     * Klik op geplaatste ItemsAdder furniture.
     * Voor als je dierenmand ooit als furniture werkt.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.HIGHEST)
    public void onFurnitureInteract(FurnitureInteractEvent e) {
        if (!isIaPresent()) return;

        String targetId = cfgId();
        if (targetId.isEmpty()) return;

        try {
            String id = null;

            try {
                id = e.getNamespacedID();
            } catch (Throwable ignored) {
            }

            if (id == null && e.getFurniture() != null) {
                try {
                    id = e.getFurniture().getNamespacedID();
                } catch (Throwable ignored) {
                }
            }

            if (id == null || !id.equalsIgnoreCase(targetId)) {
                return;
            }

            Player player = e.getPlayer();

            allowNow(player);
            openPetMenu(player);

        } catch (Throwable ignored) {
        }
    }

    /**
     * Fallback: wanneer speler met het dierenmand-item in de hand klikt.
     * Dit opent NIET het menu, maar zet alleen tijdelijk toestemming.
     * Het echte menu openen gebeurt bij klikken op het geplaatste block.
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onInteractWithItem(PlayerInteractEvent e) {
        if (!isIaPresent()) return;

        ItemStack hand = e.getItem();
        if (hand == null || hand.getType() == Material.AIR) return;

        String targetId = cfgId();
        if (targetId.isEmpty()) return;

        try {
            CustomStack cs = CustomStack.byItemStack(hand);

            if (cs != null && cs.getNamespacedID().equalsIgnoreCase(targetId)) {
                allowNow(e.getPlayer());
            }
        } catch (Throwable ignored) {
        }
    }

    private void openPetMenu(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            new PetMenu(plugin, player).open(player);
        });
    }

    private boolean isIaPresent() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    private String cfgId() {
        String id = plugin.getConfig().getString(
                "menu.itemsadder_id",
                plugin.getConfig().getString("basket.itemsadder_id", "topia:dierenmand")
        );

        return id == null ? "" : id.trim();
    }

    private void allowNow(Player player) {
        long until = System.currentTimeMillis() + 2000L;

        player.getPersistentDataContainer().set(
                Pets.key(Pets.KEY_MENU_ALLOW_UNTIL),
                PersistentDataType.LONG,
                until
        );
    }
}