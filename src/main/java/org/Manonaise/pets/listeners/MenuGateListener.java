package org.Manonaise.pets.listeners;

import dev.lone.itemsadder.api.CustomStack;
import dev.lone.itemsadder.api.Events.CustomBlockInteractEvent;
import dev.lone.itemsadder.api.Events.FurnitureInteractEvent;
import org.Manonaise.pets.Pets;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class MenuGateListener implements Listener {
    private final Pets plugin;
    public static final String KEY_ALLOW_UNTIL = "pet-menu-allow-until";

    public MenuGateListener(Pets plugin) {
        this.plugin = plugin;
    }

    /* ---------------- 1) Klik op geplaatst CUSTOM BLOCK ---------------- */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onCustomBlockInteract(CustomBlockInteractEvent e) {
        if (!isIaPresent()) return;
        String targetId = cfgId();
        if (targetId.isEmpty()) return;

        try {
            String id = e.getNamespacedID(); // bv. "master_bedroom_furniture_v1:basket"
            if (id != null && id.equalsIgnoreCase(targetId)) {
                allowNow(e.getPlayer());
                // Open direct (optioneel, dan ben je 100% onafhankelijk van IA-commands):
                // e.getPlayer().performCommand("pet menu");
            }
        } catch (Throwable ignored) {}
    }

    /* ---------------- 2) Klik op geplaatste FURNITURE ------------------ */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onFurnitureInteract(FurnitureInteractEvent e) {
        if (!isIaPresent()) return;
        String targetId = cfgId();
        if (targetId.isEmpty()) return;

        try {
            String id = null;
            try { id = e.getNamespacedID(); } catch (Throwable ignored) {}
            if (id == null && e.getFurniture() != null) {
                try { id = e.getFurniture().getNamespacedID(); } catch (Throwable ignored) {}
            }
            if (id != null && id.equalsIgnoreCase(targetId)) {
                allowNow(e.getPlayer());
                // e.getPlayer().performCommand("pet menu"); // ← optioneel
            }
        } catch (Throwable ignored) {}
    }

    /* --------------- 3) (Optioneel) item-in-hand fallback -------------- */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (!isIaPresent()) return;
        ItemStack hand = e.getItem();
        if (hand == null || hand.getType() == Material.AIR) return;

        String targetId = cfgId();
        if (targetId.isEmpty()) return;

        try {
            CustomStack cs = CustomStack.byItemStack(hand);
            if (cs != null && cs.getNamespacedID().equalsIgnoreCase(targetId)) {
                allowNow(e.getPlayer());
                // e.getPlayer().performCommand("pet menu"); // ← optioneel
            }
        } catch (Throwable ignored) {}
    }

    /* -------------------------------- helpers -------------------------- */
    private boolean isIaPresent() {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    private String cfgId() {
        String id = plugin.getConfig().getString("menu.itemsadder_id", "");
        return id == null ? "" : id.trim();
    }

    /** Zet 2s venster waarin /pet menu toegestaan is. */
    private void allowNow(org.bukkit.entity.Player p) {
        long until = System.currentTimeMillis() + 2000L;
        p.getPersistentDataContainer().set(
                Pets.key(KEY_ALLOW_UNTIL),
                PersistentDataType.LONG,
                until
        );
    }
}
