package org.Manonaise.pets.menu;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class PetDetailMenu implements InventoryHolder {
    private final Pets plugin; private final Player viewer; private final Pet pet; private final Inventory inv;
    public Pet getPet(){ return pet; }

    public PetDetailMenu(Pets plugin, Player viewer, Pet pet){
        this.plugin=plugin; this.viewer=viewer; this.pet=pet;
        inv = Bukkit.createInventory(this, 27, ChatColor.GOLD + pet.getName() +
                ChatColor.GRAY + " • Lv." + pet.getLevel() +
                ChatColor.DARK_GRAY + " • SP " + pet.getSkillPoints());
        draw();
    }

    private void draw(){
        // Header
        inv.setItem(4, new ItemBuilder(Material.NETHER_STAR).name("§6" + pet.getName())
                .lore("§7Level: §e"+pet.getLevel(), "§7Skill Points: §b"+pet.getSkillPoints())
                .build());

        // Bovenste rij = info (geen actie)
        inv.setItem(10, new ItemBuilder(Material.WATER_BUCKET).name("§bWater-interval §7(" + pet.getUpWater()+"/10)")
                .lore("§7Minder vaak water nodig").build());
        inv.setItem(11, new ItemBuilder(Material.BREAD).name("§fEten-interval §7(" + pet.getUpEten()+"/10)")
                .lore("§7Minder vaak voeren nodig").build());
        inv.setItem(12, new ItemBuilder(Material.SUGAR).name("§fSnelheid §7(" + pet.getUpSnelheid()+"/10)")
                .lore("§7Sneller volgen").build());
        inv.setItem(13, new ItemBuilder(Material.CHEST).name("§6Zoeken §7(" + pet.getUpZoeken()+"/10)")
                .lore("§7Vaker/betere loot").build());
        inv.setItem(14, new ItemBuilder(Material.IRON_PICKAXE).name("§aGrinden §7(" + pet.getUpGrinden()+"/10)")
                .lore("§7Meer XP uit acties").build());
        inv.setItem(15, new ItemBuilder(Material.GOLD_INGOT).name("§eUurloon §7(" + pet.getUpUurloon()+"/10)")
                .lore("§7Beloning via eco").build());

        // Onderste rij = upgrade-knoppen (één onder elke info-tegel)
        inv.setItem(19, upgradeButton("up_water",   pet.getUpWater()   < 10));
        inv.setItem(20, upgradeButton("up_eten",    pet.getUpEten()    < 10));
        inv.setItem(21, upgradeButton("up_snelheid",pet.getUpSnelheid()< 10));
        inv.setItem(22, upgradeButton("up_zoeken",  pet.getUpZoeken()  < 10));
        inv.setItem(23, upgradeButton("up_grinden", pet.getUpGrinden() < 10));
        inv.setItem(24, upgradeButton("up_uurloon", pet.getUpUurloon() < 10));

        // Geen despawn-knop meer (verwijderd)
    }

    private org.bukkit.inventory.ItemStack upgradeButton(String action, boolean can){
        String nice =
                switch (action){
                    case "up_water"    -> "Water";
                    case "up_eten"     -> "Eten";
                    case "up_snelheid" -> "Snelheid";
                    case "up_zoeken"   -> "Zoeken";
                    case "up_grinden"  -> "Grinden";
                    case "up_uurloon"  -> "Uurloon";
                    default -> "Upgrade";
                };

        if (can) {
            return new ItemBuilder(Material.LIME_DYE).name("§aUpgrade §f" + nice + " §7(+1)")
                    .lore("§7Besteed 1 skill point")
                    .nbt("action", action).build();
        } else {
            return new ItemBuilder(Material.GRAY_DYE).name("§7" + nice + " §8(MAX)")
                    .lore("§8Geen verdere upgrades")
                    .build();
        }
    }

    public void open(Player p){ p.openInventory(inv); }
    @Override public Inventory getInventory(){ return inv; }
}
