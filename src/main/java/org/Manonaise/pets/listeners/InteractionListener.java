package org.Manonaise.pets.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.data.PetManager;
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

import java.util.List;

public class InteractionListener implements Listener {
    private final Pets plugin;
    public InteractionListener(Pets plugin){ this.plugin = plugin; }

    @EventHandler
    public void onClickPet(PlayerInteractEntityEvent e){
        if(e.getRightClicked()==null) return;
        var pdc = e.getRightClicked().getPersistentDataContainer();
        if(!pdc.has(Pets.key("pet-owner"), PersistentDataType.STRING)) return;

        String owner = pdc.get(Pets.key("pet-owner"), PersistentDataType.STRING);
        Integer pid = pdc.get(Pets.key("pet-id"), PersistentDataType.INTEGER);
        if(pid==null) return;

        Player p = e.getPlayer();
        if(!p.getUniqueId().toString().equals(owner)){
            p.sendMessage(ChatColor.RED+"Dit is niet jouw pet.");
            return;
        }

        e.setCancelled(true);

        PetManager pm = plugin.getPetManager();
        Pet pet = pm.get(p.getUniqueId(), pid);
        if(pet==null) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (isWaterBottle(hand)) {
            if (pm.tryDrink(pet)) {
                consumeWaterBottle(p);
                p.sendMessage("§b"+pet.getName()+" heeft water gedronken (+XP).");
            } else {
                p.sendMessage("§7Nog in cooldown voor water.");
            }
            return;
        }

        if (isDierenEten(hand)) {
            if (pm.tryFeed(pet)) {
                consumeOne(hand, p);
                p.sendMessage("§a"+pet.getName()+" heeft gegeten (+XP).");
            } else {
                p.sendMessage("§7Nog in cooldown voor eten.");
            }
            return;
        }

        new PetDetailMenu(plugin, p, pet).open(p);
    }

    private boolean isWaterBottle(ItemStack item){
        if(item==null || item.getType()!= Material.POTION) return false;
        ItemMeta meta = item.getItemMeta();
        if(!(meta instanceof PotionMeta pm)) return false;
        try {
            return pm.getBasePotionType() == PotionType.WATER;
        } catch (Throwable ignored){
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isDierenEten(ItemStack item){
        if(item==null || item.getType()!=Material.IRON_INGOT) return false;
        if(!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();

        try {
            List<Component> compLore = meta.lore();
            if(compLore != null) {
                for (Component c : compLore) {
                    String plain = PlainTextComponentSerializer.plainText().serialize(c).trim();
                    if(plain.equalsIgnoreCase("Dieren eten")) return true;
                }
            }
        } catch (Throwable ignored){}

        try {
            List<String> legacy = meta.getLore();
            if(legacy != null) {
                for (String s : legacy) {
                    if(ChatColor.stripColor(s).trim().equalsIgnoreCase("Dieren eten")) return true;
                }
            }
        } catch (Throwable ignored){}

        return false;
    }

    private void consumeWaterBottle(Player p){
        ItemStack inHand = p.getInventory().getItemInMainHand();
        if(inHand.getAmount() > 1){
            inHand.setAmount(inHand.getAmount()-1);
            p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
        } else {
            p.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
        }
    }

    private void consumeOne(ItemStack stack, Player p){
        if(stack.getAmount() > 1){
            stack.setAmount(stack.getAmount()-1);
        } else {
            p.getInventory().setItemInMainHand(null);
        }
    }
}
