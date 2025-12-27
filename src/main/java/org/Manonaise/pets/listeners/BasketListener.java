package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.menu.PetMenu;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BasketListener implements Listener {
    private final Pets plugin;

    public BasketListener(Pets plugin) {
        this.plugin = plugin;
    }

    /**
     * Wanneer de speler de speciale mand plaatst:
     * - herkennen via PDC op de ItemStack (pet-basket)
     * - blok zelf ook markeren met PDC (als TileState, bv. BARREL)
     * - PetMenu openen
     */
    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        if (hand == null || !hand.hasItemMeta()) return;

        ItemMeta meta = hand.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(Pets.key("pet-basket"), PersistentDataType.BYTE)) {
            return;
        }

        Player p = e.getPlayer();
        Block placed = e.getBlockPlaced();
        BlockState state = placed.getState();

        // Alleen blokken die TileState zijn (kisten, barrels, etc.) hebben PDC
        if (state instanceof TileState tileState) {
            PersistentDataContainer blockPdc = tileState.getPersistentDataContainer();

            // Markeer dit blok als "dierenmand"
            blockPdc.set(Pets.key("pet-basket"), PersistentDataType.BYTE, (byte) 1);
            tileState.update(true, false);
        }

        // Open PetMenu 1 tick later
        Bukkit.getScheduler().runTask(plugin, () -> {
            new PetMenu(plugin, p).open(p);
        });
    }

    /**
     * Wanneer de speler rechtsklikt op een blok:
     * - als het een gemarkeerde "dierenmand" is → PetMenu openen i.p.v. default interact (barrel etc.)
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        // Alleen main hand rechtsklik op blok
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Block block = e.getClickedBlock();
        BlockState state = block.getState();

        if (!(state instanceof TileState tileState)) {
            return; // geen tile entity, dus ook geen PDC
        }

        PersistentDataContainer pdc = tileState.getPersistentDataContainer();
        if (!pdc.has(Pets.key("pet-basket"), PersistentDataType.BYTE)) {
            return;
        }

        Player p = e.getPlayer();

        // Normale interact (bv. barrel GUI) cancelen
        e.setCancelled(true);

        // Open PetMenu 1 tick later (veilig i.v.m. inventaris)
        Bukkit.getScheduler().runTask(plugin, () -> {
            new PetMenu(plugin, p).open(p);
        });
    }
}
