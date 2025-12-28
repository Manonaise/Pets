package org.Manonaise.pets.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.Manonaise.pets.data.PetType;
import org.Manonaise.pets.listeners.MenuGateListener;
import org.Manonaise.pets.menu.ItemsMenu;
import org.Manonaise.pets.menu.PetMenu;
import org.Manonaise.pets.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.stream.Collectors;

public class PetCommand implements BasicCommand {
    private final Pets plugin;
    public PetCommand(Pets plugin){ this.plugin = plugin; }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Gebruik: /pet <give|remove|menu|items menu|whistle|givefood|basket>");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give"       -> handleGive(sender, args);
            case "remove"     -> handleRemove(sender, args);
            case "menu"       -> handleMenu(sender, args);
            case "items"      -> handleItems(sender, args);
            case "whistle"    -> handleWhistle(sender);
            case "givefood"   -> handleGiveFood(sender, args);
            case "basket"     -> handleBasket(sender, args);
            default           -> sender.sendMessage(ChatColor.RED + "Onbekend subcommando.");
        }
    }

    /* ============================ /pet give ============================ */

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pet.command.give")) { sender.sendMessage(ChatColor.RED+"Geen permissie."); return; }

        // ✅ Nieuwe syntax voor mythic:
        // /pet give <speler> mythic <MythicMobId> <naam...>
        // /pet give <speler> <type> <naam...> [CAT-variant]
        if (args.length < 4) {
            sender.sendMessage(ChatColor.RED+"/pet give <speler> <type|mythic> <naam...>");
            sender.sendMessage(ChatColor.RED+"/pet give <speler> mythic <MythicMobId> <naam...>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED+"Speler niet online."); return; }

        PetType type;
        String mythicId = null;

        // ✅ 1) Expliciet "mythic" keyword
        if (args[2].equalsIgnoreCase("mythic")) {
            if (args.length < 5) {
                sender.sendMessage(ChatColor.RED+"Gebruik: /pet give <speler> mythic <MythicMobId> <naam...>");
                return;
            }
            type = PetType.MYTHIC;
            mythicId = args[3];

            // naam is alles vanaf args[4]
            String name = ChatColor.translateAlternateColorCodes('&',
                    String.join(" ", Arrays.copyOfRange(args, 4, args.length)));

            givePet(sender, target, type, name, null, mythicId);
            return;
        }

        // ✅ 2) Normale types (wolf/cat/...)
        type = PetType.from(args[2]);

        // ✅ 3) Backwards-compatible: als type onbekend → behandel args[2] als mythicMobId
        // Dan werkt ook: /pet give <speler> Nocsy_Cat-Munchkin <naam...>
        if (type == null) {
            type = PetType.MYTHIC;
            mythicId = args[2];

            String name = ChatColor.translateAlternateColorCodes('&',
                    String.join(" ", Arrays.copyOfRange(args, 3, args.length)));

            givePet(sender, target, type, name, null, mythicId);
            return;
        }

        // Vanaf hier: vanilla pet type
        List<String> tail = new ArrayList<>(Arrays.asList(args).subList(3, args.length));
        if (tail.isEmpty()) { sender.sendMessage(ChatColor.RED + "Je moet een naam opgeven."); return; }

        String variant = null;

        // CAT variant als laatste arg
        if (type == PetType.CAT && !tail.isEmpty()) {
            String last = tail.get(tail.size() - 1);
            if (isCatVariant(last)) {
                variant = normalizeCatVariant(last);
                tail.remove(tail.size() - 1);
            }
        }

        if (tail.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Je moet een naam opgeven (voor variant).");
            return;
        }

        String name = ChatColor.translateAlternateColorCodes('&', String.join(" ", tail));
        givePet(sender, target, type, name, variant, null);
    }

    private void givePet(CommandSender sender, Player target, PetType type, String name, String variant, String mythicId) {
        // MythicMobs check
        if (type == PetType.MYTHIC) {
            if (plugin.getMythicMobsHook() == null || !plugin.getMythicMobsHook().isAvailable()) {
                sender.sendMessage(ChatColor.RED + "MythicMobs is niet aanwezig, dus Mythic pets kunnen niet.");
                return;
            }
            if (mythicId == null || mythicId.isBlank()) {
                sender.sendMessage(ChatColor.RED + "MythicMobId is leeg.");
                return;
            }
        }

        Pet pet = plugin.getPetManager().createPet(target.getUniqueId(), type, name);
        if (variant != null) pet.setVariant(variant);
        if (mythicId != null) pet.setMythicMobId(mythicId);

        plugin.getPetManager().save();

        sender.sendMessage(ChatColor.GREEN + "Pet gegeven: " + name + " (#" + pet.getId() + ") aan " + target.getName()
                + (variant != null ? ChatColor.GRAY + " [CAT variant: " + variant + "]" : "")
                + (mythicId != null ? ChatColor.DARK_AQUA + " [Mythic: " + mythicId + "]" : "")
        );

        // Optioneel: meteen spawnen
        plugin.getPetManager().spawn(target, pet);
    }

    private boolean isCatVariant(String v){
        try { Cat.Type.valueOf(v.toUpperCase(Locale.ROOT)); return true; }
        catch (Exception e){ return false; }
    }
    private String normalizeCatVariant(String v){ return v.toUpperCase(Locale.ROOT); }

    /* ============================ /pet remove ============================ */

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pet.command.remove")) { sender.sendMessage(ChatColor.RED+"Geen permissie."); return; }
        if (args.length < 3) { sender.sendMessage(ChatColor.RED+"/pet remove <speler> <id>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED+"Speler niet online."); return; }
        int id;
        try { id = Integer.parseInt(args[2]); } catch (Exception e) { sender.sendMessage("ID moet een nummer zijn."); return; }
        boolean ok = plugin.getPetManager().removePet(target.getUniqueId(), id);
        sender.sendMessage(ok ? ChatColor.GREEN+"Pet verwijderd." : ChatColor.RED+"Niet gevonden.");
    }

    /* ============================ /pet menu ============================ */

    private void handleMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Alleen spelers."); return; }

        boolean bypass = p.hasPermission("pet.command.menu.bypass");

        boolean allowedByToken = false;
        try {
            Long until = p.getPersistentDataContainer().get(
                    Pets.key(MenuGateListener.KEY_ALLOW_UNTIL),
                    PersistentDataType.LONG
            );
            if (until != null && System.currentTimeMillis() <= until) {
                allowedByToken = true;
                p.getPersistentDataContainer().remove(Pets.key(MenuGateListener.KEY_ALLOW_UNTIL));
            }
        } catch (Throwable ignored) {}

        boolean holdingIaItem = false; // optioneel

        if (!bypass && !allowedByToken && !holdingIaItem) {
            p.sendMessage(ChatColor.RED + "Je kunt dit menu alleen openen via een dierenmand-blok of speciale permissie.");
            return;
        }

        new PetMenu(plugin, p).open(p);
    }

    /* ============================ /pet items menu ============================ */

    private void handleItems(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Alleen spelers."); return; }
            if (!sender.hasPermission("pet.command.items.menu")) { sender.sendMessage(ChatColor.RED+"Geen permissie."); return; }
            new ItemsMenu(plugin).open(p);
        } else sender.sendMessage(ChatColor.YELLOW+"/pet items menu");
    }

    /* ============================ /pet whistle ============================ */

    private void handleWhistle(CommandSender sender) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Alleen spelers."); return; }
        if (!sender.hasPermission("pet.command.whistle")) { sender.sendMessage(ChatColor.RED+"Geen permissie."); return; }
        int count = plugin.getPetManager().whistleTeleportAll(p);
        if (count > 0) p.sendMessage(ChatColor.AQUA + "Fluitje! Je riep " + count + " pet(s) naar je toe.");
        else p.sendMessage(ChatColor.GRAY + "Je hebt geen gespawnede pets.");
    }

    /* ============================ /pet givefood ============================ */

    private void handleGiveFood(CommandSender sender, String[] args){
        if (!sender.hasPermission("pet.command.givefood")) { sender.sendMessage(ChatColor.RED+"Geen permissie."); return; }

        Player target;
        int amount = 1;

        if (args.length == 1) {
            if (!(sender instanceof Player p)) { sender.sendMessage("/pet givefood <speler> [aantal]"); return; }
            target = p;
        } else {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage(ChatColor.RED+"Speler niet online."); return; }
            if (args.length >= 3) {
                try { amount = Math.max(1, Integer.parseInt(args[2])); } catch (Exception ignored) {}
            }
        }

        ItemStack food = new ItemStack(Material.IRON_INGOT, amount);
        ItemMeta meta = food.getItemMeta();
        meta.lore(List.of(Component.text("Dieren eten")));
        food.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = target.getInventory().addItem(food);
        leftover.values().forEach(lf -> target.getWorld().dropItemNaturally(target.getLocation(), lf));

        sender.sendMessage(ChatColor.GREEN + "Dieren-eten gegeven aan " + target.getName() + " x" + amount + ".");
        if (sender != target) target.sendMessage(ChatColor.GREEN + "Je ontving Dieren eten x" + amount + ".");
    }

    /* ============================ /pet basket ============================ */

    private void handleBasket(CommandSender sender, String[] args) {
        if (!sender.hasPermission("pet.command.basket")) {
            sender.sendMessage(ChatColor.RED + "Geen permissie.");
            return;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Speler niet online.");
                return;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(ChatColor.YELLOW + "Gebruik: /pet basket <speler>");
                return;
            }
            target = p;
        }

        ItemStack stack = new ItemBuilder(Material.BARREL)
                .name("&6Dierenmand")
                .lore("§7Plaats dit blok om het dierenmenu te openen.")
                .build();

        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(Pets.key("pet-basket"), PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);

        Map<Integer, ItemStack> leftover = target.getInventory().addItem(stack);
        leftover.values().forEach(lf -> target.getWorld().dropItemNaturally(target.getLocation(), lf));

        sender.sendMessage(ChatColor.GREEN + "Dierenmand gegeven aan " + target.getName() + ".");
        if (sender != target) target.sendMessage(ChatColor.GREEN + "Je hebt een dierenmand ontvangen.");
    }

    /* ============================ Suggesties ============================ */

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length == 0)
            return List.of("give","remove","menu","items","whistle","givefood","basket");
        if (args.length == 1)
            return filter(List.of("give","remove","menu","items","whistle","givefood","basket"), args[0]);

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 3) {
                // ✅ voeg "mythic" toe
                List<String> base = new ArrayList<>();
                base.add("mythic");
                base.addAll(Arrays.stream(PetType.values()).map(PetType::name).toList());
                return base;
            }
        }

        return List.of();
    }

    private Collection<String> filter(Collection<String> base, String last) {
        String n = last.toLowerCase(Locale.ROOT);
        return base.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(n)).toList();
    }
}
