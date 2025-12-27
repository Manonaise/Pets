package org.Manonaise.pets.menu;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.items.ItemsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ItemsMenu implements InventoryHolder {
    private final Pets plugin;
    private final Inventory inv;

    // Slots 0..44 = loot slots
    private static final int[] LOOT_SLOTS = new int[45];
    static {
        for (int i = 0; i < 45; i++) LOOT_SLOTS[i] = i;
    }

    // Teksten die we als overlay gebruiken in de GUI (strippable bij opslaan)
    private static final String L_CHANCE_PREFIX = "§7Kans: §e"; // gevolgd door "NN%"
    private static final String L_HINT = "§8(L/R ±1, Shift ±10, Middle reset, Q verwijder)";

    public ItemsMenu(Pets plugin){
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 54, ChatColor.GOLD + "Dieren Loot Config");
        draw();
    }

    private void draw(){
        // Laad bestaande entries
        List<ItemsManager.LootEntry> entries = plugin.getItemsManager().getLootEntries();
        int idx = 0;
        for (ItemsManager.LootEntry e : entries){
            if (idx >= LOOT_SLOTS.length) break;
            ItemStack base = e.item.clone();
            base.setAmount(1);
            // sla kans in PDC (alleen voor GUI) en toon overlay-lore
            ItemsManager.setChanceOnItem(plugin, base, e.chance);
            inv.setItem(LOOT_SLOTS[idx++], decorate(base));
        }

        // Knoppen onderin
        inv.setItem(49, guiButton(Material.LIME_CONCRETE, "§aOpslaan & Sluiten", "Slaat de lootlijst op en sluit dit menu.", "save_close"));
        inv.setItem(45, infoBook());
    }

    private ItemStack guiButton(Material type, String name, String loreLine, String action){
        ItemStack it = new ItemStack(type);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add("§7" + loreLine);
        m.setLore(lore);
        m.getPersistentDataContainer().set(Pets.key("action"), PersistentDataType.STRING, action);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack infoBook(){
        ItemStack it = new ItemStack(Material.BOOK);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName("§eUitleg");
        List<String> lore = new ArrayList<>();
        lore.add("§7Plaats items in de bovenste 5 rijen.");
        lore.add("§7Links/Rechts: §f±1%§7  •  Shift+Klik: §f±10%");
        lore.add("§7Middelklik: §freset naar 10%");
        lore.add("§7Q / Ctrl+Q: §cverwijder item");
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    public void open(Player p){ p.openInventory(inv); }

    @Override public Inventory getInventory(){ return inv; }

    public static boolean isLootSlot(int slot){
        return slot >= 0 && slot <= 44;
    }

    /**
     * Voor GUI-weergave: voeg alleen overlay-lore toe (Kans + hint) en laat
     * alle originele meta 1:1 intact. (DisplayName wordt NIET aangepast.)
     */
    public ItemStack decorate(ItemStack base){
        int chance = ItemsManager.getChanceFromItem(plugin, base);

        ItemStack copy = base.clone();
        copy.setAmount(1);

        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            // Start met bestaande lore
            List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

            // Verwijder oude overlay-lijnen (als je vaker decoreert)
            lore.removeIf(this::isOverlayLine);

            // Voeg overlay toe
            lore.add(L_CHANCE_PREFIX + chance + "%");
            lore.add(L_HINT);
            meta.setLore(lore);

            // PDC (kans) blijft op het item voor live-aanpassing in de GUI
            meta.getPersistentDataContainer().set(
                    Pets.key(ItemsManager.KEY_LOOT_CHANCE),
                    PersistentDataType.INTEGER,
                    chance
            );

            copy.setItemMeta(meta);
        }
        return copy;
    }

    private boolean isOverlayLine(String s){
        if (s == null) return false;
        if (s.equals(L_HINT)) return true;
        if (s.startsWith(L_CHANCE_PREFIX)) return true;
        return false;
    }

    /**
     * Maak een "schoon" item voor opslag: verwijder GUI-lore en GUI-PDC,
     * zodat wat we in loot.yml bewaren exact is wat de speler er in stopte.
     */
    private ItemStack stripForSave(ItemStack decorated){
        ItemStack clean = decorated.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta != null) {
            // lore: haal overlay eruit, laat originele lore intact
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()){
                lore.removeIf(this::isOverlayLine);
                meta.setLore(lore.isEmpty() ? null : lore);
            }
            // verwijder onze GUI-PDC
            meta.getPersistentDataContainer().remove(Pets.key(ItemsManager.KEY_LOOT_CHANCE));
            clean.setItemMeta(meta);
        }
        clean.setAmount(1);
        return clean;
    }

    private String prettify(ItemStack it){
        return it.getType().name().toLowerCase().replace('_',' ');
    }

    /** Leest alle loot-slots uit en levert de loot entries (SCHONE items!). */
    public List<ItemsManager.LootEntry> readEntries(){
        List<ItemsManager.LootEntry> list = new ArrayList<>();
        for (int s : LOOT_SLOTS){
            ItemStack it = inv.getItem(s);
            if (it==null || it.getType()==Material.AIR) continue;

            // Lees kans UIT PDC (GUI), maar strip daarna alle GUI-sporen
            int chance = ItemsManager.getChanceFromItem(plugin, it);
            ItemStack proto = stripForSave(it); // ← geen overlay-lore en geen GUI-PDC

            list.add(new ItemsManager.LootEntry(proto, chance));
        }
        return list;
    }
}
