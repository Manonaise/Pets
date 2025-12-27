package org.Manonaise.pets.util;

import org.Manonaise.pets.Pets;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material mat) {
        this.item = new ItemStack(mat);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder name(String name) {
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder nbt(String key, int value) {
        meta.getPersistentDataContainer().set(Pets.key(key), PersistentDataType.INTEGER, value);
        return this;
    }

    public ItemBuilder nbt(String key, String value) {
        meta.getPersistentDataContainer().set(Pets.key(key), PersistentDataType.STRING, value);
        return this;
    }

    public ItemStack build() {
        if (!lore.isEmpty()) meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        item.setItemMeta(meta);
        return item;
    }

    public ItemBuilder lore(java.util.List<String> lines) {
        this.lore.addAll(lines);
        return this;
    }

}
