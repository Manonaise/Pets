package org.Manonaise.pets.menu;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.data.PetManager;
import org.Manonaise.pets.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PetMenu implements InventoryHolder {
    private final Pets plugin; private final Player viewer; private final Inventory inv;

    public PetMenu(Pets plugin, Player viewer){
        this.plugin=plugin; this.viewer=viewer;
        int size = 9 * Math.max(1, (int)Math.ceil(plugin.getPetManager().getPets(viewer.getUniqueId()).size() / 7.0));
        size = Math.min(54, Math.max(9, size));
        inv = Bukkit.createInventory(this, size, ChatColor.GOLD + "Jouw Dieren");
        draw();
    }

    private void draw(){
        PetManager pm = plugin.getPetManager();
        int slot = 0;
        for(Pet p : pm.getPets(viewer.getUniqueId())){
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY+"Type: "+ChatColor.WHITE+p.getType());
            lore.add(ChatColor.GRAY+"Level: "+ChatColor.GOLD+p.getLevel()
                    + (p.isBaby()? ChatColor.LIGHT_PURPLE+" (Baby)" : ChatColor.GREEN+" (Volwassen)"));
            lore.add(ChatColor.GRAY+"XP: " + ChatColor.YELLOW + p.getXp() + ChatColor.GRAY + "/" + p.getXpToNext());
            lore.add(ChatColor.GRAY+"Skill Points: " + ChatColor.AQUA + p.getSkillPoints());
            lore.add("");
            lore.add(p.isSpawned()? ChatColor.RED+"Klik: DESPAWN" : ChatColor.YELLOW+"Klik: SPAWN");
            lore.add(ChatColor.GRAY+"Shift-klik: Details/Upgrades");
            ItemStack it = new ItemBuilder(Material.NAME_TAG)
                    .name(ChatColor.GOLD+p.getName())
                    .lore(lore)
                    .nbt("pet-id", p.getId())
                    .build();
            inv.setItem(slot++, it);
        }
    }

    public void open(Player p){ p.openInventory(inv); }

    @Override public Inventory getInventory(){ return inv; }
}
