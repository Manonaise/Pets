package org.Manonaise.pets.items;

import dev.lone.itemsadder.api.CustomStack;
import net.kyori.adventure.text.Component;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.PetType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PetFoodManager {

    public static final String KEY_PET_FOOD_CATEGORY = "pet-food-category";

    private final Pets plugin;

    public PetFoodManager(Pets plugin) {
        this.plugin = plugin;
    }

    public Map<String, FoodCategory> getCategories() {
        Map<String, FoodCategory> categories = new LinkedHashMap<>();

        ConfigurationSection root = plugin.getConfig().getConfigurationSection("food-categories");

        if (root == null) {
            categories.put("katten", new FoodCategory(
                    "katten",
                    "Kattenvoer",
                    "topia:kattenvoer",
                    List.of(PetType.CAT)
            ));

            categories.put("honden", new FoodCategory(
                    "honden",
                    "Hondenvoer",
                    "topia:hondenvoer",
                    List.of(PetType.WOLF)
            ));

            categories.put("konijn", new FoodCategory(
                    "konijn",
                    "Konijnenvoer",
                    "topia:wortel",
                    List.of(PetType.RABBIT)
            ));

            return categories;
        }

        for (String rawKey : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawKey);

            if (section == null) continue;

            String key = normalizeKey(rawKey);
            String displayName = section.getString("display-name", rawKey);
            String itemsAdderId = section.getString("itemsadder-id", "");

            List<String> rawPetTypes = section.getStringList("pet-types");
            List<PetType> petTypes = new ArrayList<>();

            for (String rawType : rawPetTypes) {
                PetType type = PetType.from(rawType);

                if (type != null) {
                    petTypes.add(type);
                }
            }

            if (key.isBlank()) continue;
            if (itemsAdderId == null || itemsAdderId.isBlank()) continue;
            if (petTypes.isEmpty()) continue;

            categories.put(key, new FoodCategory(
                    key,
                    displayName,
                    itemsAdderId,
                    petTypes
            ));
        }

        return categories;
    }

    public FoodCategory getCategory(String rawKey) {
        if (rawKey == null) return null;

        String key = normalizeKey(rawKey);

        return getCategories().get(key);
    }

    public Collection<String> getCategoryKeys() {
        return getCategories().keySet();
    }

    public ItemStack createFood(CommandSender sender, FoodCategory category, int amount) {
        if (category == null) {
            sender.sendMessage(ChatColor.RED + "Onbekende voer-categorie.");
            return null;
        }

        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            sender.sendMessage(ChatColor.RED + "ItemsAdder is niet aanwezig. Het voer kan niet gegeven worden.");
            return null;
        }

        CustomStack customStack;

        try {
            customStack = CustomStack.getInstance(category.itemsAdderId());
        } catch (Throwable t) {
            customStack = null;
        }

        if (customStack == null) {
            sender.sendMessage(ChatColor.RED + "ItemsAdder item niet gevonden: " + category.itemsAdderId());
            sender.sendMessage(ChatColor.GRAY + "Controleer je ItemsAdder ID en doe daarna /iareload.");
            return null;
        }

        ItemStack item = customStack.getItemStack();

        if (item == null || item.getType() == Material.AIR) {
            sender.sendMessage(ChatColor.RED + "ItemsAdder item gaf een leeg item terug: " + category.itemsAdderId());
            return null;
        }

        item.setAmount(Math.max(1, amount));

        ItemMeta meta = item.getItemMeta();

        meta.lore(List.of(
                Component.text(category.displayName()),
                Component.text("Voer-categorie: " + category.key())
        ));

        meta.getPersistentDataContainer().set(
                Pets.key(KEY_PET_FOOD_CATEGORY),
                PersistentDataType.STRING,
                category.key()
        );

        item.setItemMeta(meta);

        return item;
    }

    public FoodCategory findCategoryByItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (!item.hasItemMeta()) return null;

        ItemMeta meta = item.getItemMeta();

        String storedCategory = meta.getPersistentDataContainer().get(
                Pets.key(KEY_PET_FOOD_CATEGORY),
                PersistentDataType.STRING
        );

        FoodCategory byPdc = getCategory(storedCategory);

        if (byPdc != null) {
            return byPdc;
        }

        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
            return null;
        }

        CustomStack customStack;

        try {
            customStack = CustomStack.byItemStack(item);
        } catch (Throwable t) {
            customStack = null;
        }

        if (customStack == null) {
            return null;
        }

        String namespacedId;

        try {
            namespacedId = customStack.getNamespacedID();
        } catch (Throwable t) {
            return null;
        }

        if (namespacedId == null || namespacedId.isBlank()) {
            return null;
        }

        for (FoodCategory category : getCategories().values()) {
            if (category.itemsAdderId().equalsIgnoreCase(namespacedId)) {
                return category;
            }
        }

        return null;
    }

    public boolean canEat(PetType petType, FoodCategory category) {
        if (petType == null || category == null) return false;

        return category.allowedPetTypes().contains(petType);
    }

    public String allowedPetTypesText(FoodCategory category) {
        if (category == null) return "";

        return category.allowedPetTypes().stream()
                .map(PetType::name)
                .toList()
                .toString();
    }

    public static String normalizeKey(String raw) {
        if (raw == null) return "";

        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "_");
    }

    public record FoodCategory(
            String key,
            String displayName,
            String itemsAdderId,
            List<PetType> allowedPetTypes
    ) {
    }
}