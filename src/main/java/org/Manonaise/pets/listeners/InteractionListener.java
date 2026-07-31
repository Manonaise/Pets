package org.Manonaise.pets.listeners;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.data.PetManager;
import org.Manonaise.pets.items.PetFoodManager;
import org.Manonaise.pets.menu.PetDetailMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.Locale;

public class InteractionListener implements Listener {

    private final Pets plugin;
    private final PetFoodManager foodManager;

    public InteractionListener(Pets plugin) {
        this.plugin = plugin;
        this.foodManager = new PetFoodManager(plugin);
    }

    @EventHandler
    public void onClickPet(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() == null) return;

        var pdc = e.getRightClicked().getPersistentDataContainer();

        if (!pdc.has(Pets.key("pet-owner"), PersistentDataType.STRING)) return;

        String owner = pdc.get(Pets.key("pet-owner"), PersistentDataType.STRING);
        Integer pid = pdc.get(Pets.key("pet-id"), PersistentDataType.INTEGER);

        if (pid == null) return;

        Player p = e.getPlayer();

        if (!p.getUniqueId().toString().equals(owner)) {
            p.sendMessage(ChatColor.RED + "Dit is niet jouw pet.");
            return;
        }

        e.setCancelled(true);

        PetManager pm = plugin.getPetManager();
        Pet pet = pm.get(p.getUniqueId(), pid);

        if (pet == null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();

        if (isWaterBottle(hand)) {
            if (pm.tryDrink(pet)) {
                consumeWaterBottle(p);
                p.sendMessage("§b" + pet.getName() + " heeft water gedronken (+XP).");
            } else {
                p.sendMessage("§7Nog in cooldown voor water.");
            }

            return;
        }

        PetFoodManager.FoodCategory foodCategory = foodManager.findCategoryByItem(hand);

        if (foodCategory != null) {
            if (!foodManager.canEat(pet.getType(), foodCategory)) {
                p.sendMessage(ChatColor.RED + pet.getName() + " mag geen " + foodCategory.displayName().toLowerCase(Locale.ROOT) + " eten.");
                p.sendMessage(ChatColor.GRAY + "Deze voer-categorie is voor: " + foodManager.allowedPetTypesText(foodCategory));
                return;
            }

            if (pm.tryFeed(pet)) {
                consumeOne(hand, p);
                p.sendMessage("§a" + pet.getName() + " heeft " + foodCategory.displayName().toLowerCase(Locale.ROOT) + " gegeten (+XP).");
            } else {
                p.sendMessage("§7Nog in cooldown voor eten.");
            }

            return;
        }

        new PetDetailMenu(plugin, p, pet).open(p);
    }

    private boolean isWaterBottle(ItemStack item) {
        if (item == null || item.getType() != Material.POTION) return false;

        ItemMeta meta = item.getItemMeta();

        if (!(meta instanceof PotionMeta pm)) return false;

        try {
            return pm.getBasePotionType() == PotionType.WATER;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void consumeWaterBottle(Player p) {
        ItemStack inHand = p.getInventory().getItemInMainHand();

        if (inHand.getAmount() > 1) {
            inHand.setAmount(inHand.getAmount() - 1);
            p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        } else {
            p.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        }
    }

    private void consumeOne(ItemStack stack, Player p) {
        if (stack.getAmount() > 1) {
            stack.setAmount(stack.getAmount() - 1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
    }
}