package org.Manonaise.pets.items;

import org.Manonaise.pets.Pets;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Beheert loot-config (welke items en welke kansen) én offline found items fallback.
 */
public class ItemsManager {
    private final Pets plugin;

    // Oude found items opslag (fallback voor offline spelers)
    private final File foundFile;
    private final FileConfiguration foundCfg;

    // NIEUW: loot-config
    private final File lootFile;
    private final FileConfiguration lootCfg;

    public static final String LOOT_ROOT = "loot-entries"; // lijst met genummerde children

    public ItemsManager(Pets plugin){
        this.plugin = plugin;

        // Offline found items opslag
        this.foundFile = new File(plugin.getDataFolder(),"found.yml");
        this.foundCfg = YamlConfiguration.loadConfiguration(foundFile);

        // Loot config
        this.lootFile = new File(plugin.getDataFolder(),"loot.yml");
        this.lootCfg = YamlConfiguration.loadConfiguration(lootFile);
        // Maak sectie als die er nog niet is
        if(!lootCfg.isConfigurationSection(LOOT_ROOT)){
            lootCfg.createSection(LOOT_ROOT);
            saveLoot();
        }
    }

    public void save() {
        // sla zowel loot-config als offline-found items op
        try { lootCfg.save(lootFile); } catch (Exception ignored) {}
        try { foundCfg.save(foundFile); } catch (Exception ignored) {}
    }


    /* ========== Found items (offline fallback) ========== */

    @SuppressWarnings("unchecked")
    private List<ItemStack> list(UUID owner){
        List<ItemStack> list = (List<ItemStack>) foundCfg.getList(owner.toString());
        if(list==null){ list = new ArrayList<>(); foundCfg.set(owner.toString(), list); }
        return list;
    }

    public List<ItemStack> peek(UUID owner){ return new ArrayList<>(list(owner)); }

    public void add(UUID owner, ItemStack it){ List<ItemStack> l = list(owner); l.add(it); saveFound(); }

    public void clear(UUID owner){ foundCfg.set(owner.toString(), new ArrayList<>()); saveFound(); }

    private void saveFound(){ try{ foundCfg.save(foundFile);}catch(IOException e){ e.printStackTrace(); } }

    /* ========== Loot-config API ========== */

    public static class LootEntry {
        public final ItemStack item; // amount wordt genegeerd; alleen type/meta tellen
        public final int chance;     // 0..100 (als gewicht gebruikt)
        public LootEntry(ItemStack item, int chance){ this.item = item; this.chance = Math.max(0, Math.min(100, chance)); }
    }

    public List<LootEntry> getLootEntries(){
        List<LootEntry> list = new ArrayList<>();
        ConfigurationSection root = lootCfg.getConfigurationSection(LOOT_ROOT);
        if(root==null) return list;

        for(String key : root.getKeys(false)){
            ConfigurationSection sec = root.getConfigurationSection(key);
            if(sec==null) continue;
            ItemStack item = sec.getItemStack("item");
            int chance = sec.getInt("chance", 10);
            if(item==null) continue;
            ItemStack clone = item.clone();
            clone.setAmount(1);
            list.add(new LootEntry(clone, chance));
        }
        return list;
    }

    public void setLootEntries(List<LootEntry> entries){
        lootCfg.set(LOOT_ROOT, null);
        ConfigurationSection root = lootCfg.createSection(LOOT_ROOT);
        int idx = 0;
        for(LootEntry e : entries){
            ConfigurationSection sec = root.createSection(String.valueOf(idx++));
            ItemStack prot = e.item.clone();
            prot.setAmount(1);
            sec.set("item", prot);
            sec.set("chance", Math.max(0, Math.min(100, e.chance)));
        }
        saveLoot();
    }

    private void saveLoot(){ try{ lootCfg.save(lootFile);}catch(IOException e){ e.printStackTrace(); } }

    /* ========== Loot generator (direct naar inventory of offline fallback) ========== */

    public void giveRandomLootImmediate(UUID owner, int upZoeken){
        List<LootEntry> table = getLootEntries();
        // fallback pool als tabel leeg is
        if(table.isEmpty()){
            Material[] pool = {Material.IRON_INGOT, Material.BONE, Material.FEATHER, Material.STRING, Material.COAL, Material.LEATHER};
            Random r = new Random();
            int rolls = 1 + (upZoeken>=6 ? 1 : 0);
            Player p = Bukkit.getPlayer(owner);
            for(int i=0;i<rolls;i++){
                Material m = pool[r.nextInt(pool.length)];
                ItemStack item = new ItemStack(m, 1 + r.nextInt(2));
                if(p != null && p.isOnline()){
                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
                    if(!leftover.isEmpty()){
                        for(ItemStack lf : leftover.values()){
                            p.getWorld().dropItemNaturally(p.getLocation(), lf);
                        }
                    }
                } else {
                    add(owner, item);
                }
            }
            return;
        }

        int rolls = 1 + (upZoeken>=6 ? 1 : 0);
        Player p = Bukkit.getPlayer(owner);
        for(int i=0;i<rolls;i++){
            ItemStack item = rollOne(table);
            if(item == null) continue;
            if(p != null && p.isOnline()){
                Map<Integer, ItemStack> leftover = p.getInventory().addItem(item);
                if(!leftover.isEmpty()){
                    for(ItemStack lf : leftover.values()){
                        p.getWorld().dropItemNaturally(p.getLocation(), lf);
                    }
                }
            } else {
                add(owner, item);
            }
        }
    }

    private ItemStack rollOne(List<LootEntry> table){
        int total = table.stream().mapToInt(e -> Math.max(0, e.chance)).sum();
        if(total <= 0){
            // alle kansen 0 → kies gelijkmatig
            LootEntry pick = table.get(new Random().nextInt(table.size()));
            return safeOne(pick.item);
        }
        int r = new Random().nextInt(total);
        int cum = 0;
        for(LootEntry e : table){
            int w = Math.max(0, e.chance);
            if(r < cum + w){
                return safeOne(e.item);
            }
            cum += w;
        }
        // fallback
        return safeOne(table.get(table.size()-1).item);
    }

    private ItemStack safeOne(ItemStack proto){
        ItemStack it = proto.clone();
        if(it.getAmount() < 1) it.setAmount(1);
        return it;
    }

    /* ========== Helpers voor PDC op ItemStacks in GUI ========== */

    public static final String KEY_LOOT_CHANCE = "loot-chance";

    public static int getChanceFromItem(Pets plugin, ItemStack it){
        if(it==null || !it.hasItemMeta()) return 10;
        Integer n = it.getItemMeta().getPersistentDataContainer()
                .get(Pets.key(KEY_LOOT_CHANCE), PersistentDataType.INTEGER);
        return n==null ? 10 : Math.max(0, Math.min(100, n));
    }

    public static ItemStack setChanceOnItem(Pets plugin, ItemStack it, int chance){
        if(it==null) return null;
        var meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Pets.key(KEY_LOOT_CHANCE),
                PersistentDataType.INTEGER, Math.max(0, Math.min(100, chance)));
        it.setItemMeta(meta);
        return it;
    }
}
