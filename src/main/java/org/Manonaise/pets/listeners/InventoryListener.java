package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.items.ItemsManager;
import org.Manonaise.pets.menu.ItemsMenu;
import org.Manonaise.pets.menu.PetDetailMenu;
import org.Manonaise.pets.menu.PetMenu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class InventoryListener implements Listener {
    private final Pets plugin;
    public InventoryListener(Pets plugin){ this.plugin=plugin; }

    /* ===================== CLICK HANDLER ===================== */

    @EventHandler
    public void onClick(InventoryClickEvent e){
        Inventory inv = e.getInventory();
        InventoryHolder holder = inv.getHolder();
        ItemStack it = e.getCurrentItem();
        Player p = (Player)e.getWhoClicked();

        /* ----- PetMenu: spawn/toggle, shift = detail ----- */
        if(holder instanceof PetMenu){
            e.setCancelled(true);
            if(it==null || !it.hasItemMeta()) return;

            Integer id = it.getItemMeta().getPersistentDataContainer()
                    .get(Pets.key("pet-id"), PersistentDataType.INTEGER);
            if(id==null) return;
            Pet pet = plugin.getPetManager().get(p.getUniqueId(), id);
            if(pet==null) return;

            if(e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT){
                new PetDetailMenu(plugin, p, pet).open(p);
                return;
            }

            if(pet.isSpawned()){
                plugin.getPetManager().despawn(pet);
                p.sendMessage("§cPet gedespawned.");
            } else {
                plugin.getPetManager().spawn(p, pet); // despawnOthers zit al in manager
                p.sendMessage("§aPet gespawned.");
            }
            return;
        }

        /* ----- PetDetailMenu: alleen upgrades ----- */
        if(holder instanceof PetDetailMenu det){
            e.setCancelled(true);
            if(it==null || !it.hasItemMeta()) return;

            String action = it.getItemMeta().getPersistentDataContainer()
                    .get(Pets.key("action"), PersistentDataType.STRING);
            if(action==null) return;

            Pet pet = det.getPet();
            switch (action){
                case "up_water"    -> spend(p, pet, "water");
                case "up_eten"     -> spend(p, pet, "eten");
                case "up_snelheid" -> spend(p, pet, "snelheid");
                case "up_zoeken"   -> spend(p, pet, "zoeken");
                case "up_grinden"  -> spend(p, pet, "grinden");
                case "up_uurloon"  -> spend(p, pet, "uurloon");
            }
            plugin.getPetManager().save();
            new PetDetailMenu(plugin, p, pet).open(p);
            return;
        }

        /* ----- ItemsMenu: loot-configurator ----- */
        if(holder instanceof ItemsMenu menu){
            // We handlen clicks zelf
            e.setCancelled(true);

            int rawSlot = e.getRawSlot();
            ItemStack cursor = e.getCursor();

            // Save & Close knop
            if(it != null && it.hasItemMeta()){
                String action = it.getItemMeta().getPersistentDataContainer()
                        .get(Pets.key("action"), PersistentDataType.STRING);
                if("save_close".equals(action)){
                    plugin.getItemsManager().setLootEntries(menu.readEntries());
                    p.closeInventory();
                    p.sendMessage("§aLoot-config opgeslagen.");
                    return;
                }
            }

            // SHIFT-klik vanuit eigen inventory → voeg zoveel mogelijk toe
            if((e.getClick() == ClickType.SHIFT_LEFT || e.getClick() == ClickType.SHIFT_RIGHT)
                    && e.getClickedInventory() != null
                    && e.getClickedInventory().equals(p.getInventory())) {
                if(it == null || it.getType() == Material.AIR) return;

                int toPlace = it.getAmount();
                List<Integer> empties = emptyLootSlots(menu);
                int placed = 0;
                for (int slot : empties){
                    if(placed >= toPlace) break;
                    ItemStack one = it.clone(); one.setAmount(1);
                    ItemsManager.setChanceOnItem(plugin, one, 10);
                    one = menu.decorate(one);
                    menu.getInventory().setItem(slot, one);
                    placed++;
                }
                if(placed > 0){
                    ItemStack rem = it.clone();
                    rem.setAmount(it.getAmount() - placed);
                    p.getInventory().setItem(e.getSlot(), rem.getAmount() <= 0 ? null : rem);
                }
                return;
            }

            // Klik in loot-slot (bovenste 5 rijen)
            if(ItemsMenu.isLootSlot(rawSlot)){
                // aanpassen kans op bestaand item
                if(it != null && it.getType() != Material.AIR){
                    switch (e.getClick()){
                        case LEFT -> adjustChance(menu, rawSlot, +1);
                        case RIGHT -> adjustChance(menu, rawSlot, -1);
                        case SHIFT_LEFT -> adjustChance(menu, rawSlot, +10);
                        case SHIFT_RIGHT -> adjustChance(menu, rawSlot, -10);
                        case MIDDLE -> setChance(menu, rawSlot, 10);
                        case DROP, CONTROL_DROP -> menu.getInventory().setItem(rawSlot, null); // verwijderen
                        default -> { /* niets */ }
                    }
                    return;
                }

                // leeg slot + cursor item → plaats 1
                if((it == null || it.getType()==Material.AIR) && cursor != null && cursor.getType()!=Material.AIR){
                    ItemStack place = cursor.clone(); place.setAmount(1);
                    ItemsManager.setChanceOnItem(plugin, place, 10);
                    place = menu.decorate(place);
                    menu.getInventory().setItem(rawSlot, place);

                    // cursor verminderen
                    if(cursor.getAmount() > 1){
                        cursor.setAmount(cursor.getAmount()-1);
                        e.getView().setCursor(cursor);
                    } else {
                        e.getView().setCursor(null);
                    }
                    return;
                }
            }
        }
    }

    /* ===================== DRAG HANDLER ===================== */

    @EventHandler
    public void onDrag(InventoryDragEvent e){
        InventoryHolder holder = e.getInventory().getHolder();
        if(!(holder instanceof ItemsMenu menu)) return;

        // We handelen drag zelf af → cancel de vanilla verdeling
        e.setCancelled(true);

        Player p = (Player) e.getWhoClicked();
        ItemStack cursor = e.getOldCursor();
        if(cursor == null || cursor.getType() == Material.AIR) return;

        // Alleen slots in de bovenste inventory en binnen de loot-range (0..44)
        int topSize = menu.getInventory().getSize(); // 54
        List<Integer> targets = new ArrayList<>();
        for (int raw : e.getRawSlots()){
            if(raw < topSize && ItemsMenu.isLootSlot(raw)){
                // alleen lege doelen gebruiken
                ItemStack existing = menu.getInventory().getItem(raw);
                if(existing == null || existing.getType() == Material.AIR){
                    targets.add(raw);
                }
            }
        }
        if(targets.isEmpty()) return;

        int amount = cursor.getAmount();
        int placed = 0;
        for(int slot : targets){
            if(placed >= amount) break;
            ItemStack one = cursor.clone(); one.setAmount(1);
            ItemsManager.setChanceOnItem(plugin, one, 10);
            one = menu.decorate(one);
            menu.getInventory().setItem(slot, one);
            placed++;
        }

        // cursor verminderen
        int left = amount - placed;
        if(left <= 0) {
            p.setItemOnCursor(null);
        } else {
            ItemStack nc = cursor.clone(); nc.setAmount(left);
            p.setItemOnCursor(nc);
        }
    }

    /* ===================== helpers ===================== */

    private void adjustChance(ItemsMenu menu, int slot, int delta){
        ItemStack it = menu.getInventory().getItem(slot);
        if(it == null || it.getType()==Material.AIR) return;
        int cur = ItemsManager.getChanceFromItem(plugin, it);
        int next = Math.max(0, Math.min(100, cur + delta));
        setChance(menu, slot, next);
    }

    private void setChance(ItemsMenu menu, int slot, int val){
        ItemStack it = menu.getInventory().getItem(slot);
        if(it==null) return;
        ItemsManager.setChanceOnItem(plugin, it, val);
        // redraw lore
        menu.getInventory().setItem(slot, menu.decorate(it));
    }

    private List<Integer> emptyLootSlots(ItemsMenu menu){
        List<Integer> slots = new ArrayList<>();
        Inventory inv = menu.getInventory();
        for (int s = 0; s <= 44; s++){
            ItemStack x = inv.getItem(s);
            if(x == null || x.getType() == Material.AIR) slots.add(s);
        }
        return slots;
    }

    private void spend(Player p, Pet pet, String which){
        if(pet.getSkillPoints() <= 0){ p.sendMessage("§7Geen skill points."); return; }
        if(pet.spendPoint(which)) p.sendMessage("§aUpgrade gekocht: §f" + which);
        else p.sendMessage("§7Deze skill staat al op max.");
    }
}
