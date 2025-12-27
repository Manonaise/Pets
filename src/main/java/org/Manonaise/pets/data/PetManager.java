package org.Manonaise.pets.data;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.follow.ActivePet;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PetManager {
    private final Pets plugin;
    private final File file;
    private final FileConfiguration cfg;

    private final Map<UUID, Map<Integer, Pet>> pets = new HashMap<>();

    // ✅ key-object i.p.v. Objects.hash
    private final Map<PetKey, ActivePet> active = new HashMap<>();
    private final Map<PetKey, Integer> petWalkProgress = new HashMap<>();

    // ✅ autosave/dirty
    private volatile boolean dirty = false;

    @Deprecated
    private final Map<UUID, Integer> walkProgress = new HashMap<>(); // niet meer gebruikt

    public PetManager(Pets plugin){
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pets.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        load();

        // Minute tick: loot & uurloon & quest-ready meldingen
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMinute, 20L*60, 20L*60);

        // ✅ Autosave (1x per minuut)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                dirty = false;
                save();
            }
        }, 20L*60, 20L*60);
    }

    public void markDirty() { dirty = true; }

    private void load(){
        if(!cfg.isConfigurationSection("players")) return;
        for(String uuidStr : Objects.requireNonNull(cfg.getConfigurationSection("players")).getKeys(false)){
            UUID u = UUID.fromString(uuidStr);
            Map<Integer, Pet> map = new HashMap<>();
            for(String idStr : Objects.requireNonNull(cfg.getConfigurationSection("players."+uuidStr)).getKeys(false)){
                Map<String,Object> raw =
                        Objects.requireNonNull(cfg.getConfigurationSection("players."+uuidStr+"."+idStr)).getValues(false);
                Pet p = Pet.deserialize(raw);
                map.put(p.getId(), p);
            }
            pets.put(u, map);
        }
    }

    public void save(){
        cfg.set("players", null);
        for(var e : pets.entrySet()){
            String root = "players."+e.getKey();
            for(Pet p : e.getValue().values()){
                cfg.createSection(root+"."+p.getId(), p.serialize());
            }
        }
        try { cfg.save(file); } catch (IOException ex){ ex.printStackTrace(); }
    }

    private int nextId(UUID owner){
        return pets.computeIfAbsent(owner, k->new HashMap<>())
                .keySet().stream().mapToInt(i->i).max().orElse(0) + 1;
    }

    public Pet createPet(UUID owner, PetType type, String name){
        Pet p = new Pet(nextId(owner), owner, type, name);
        pets.computeIfAbsent(owner, k->new HashMap<>()).put(p.getId(), p);
        markDirty();
        return p;
    }

    private void despawnOthers(UUID owner, int keepId){
        for (Pet other : getPets(owner)) {
            if (other.getId() != keepId && other.isSpawned()) {
                despawn(other);
            }
        }
    }

    public boolean removePet(UUID owner, int id){
        Map<Integer, Pet> map = pets.get(owner);
        if(map==null) return false;
        Pet p = map.remove(id);
        if(p==null) return false;
        despawn(p);
        markDirty();
        return true;
    }

    public Collection<Pet> getPets(UUID owner){
        return pets.getOrDefault(owner, Collections.emptyMap()).values();
    }

    public Pet get(UUID owner, int id){
        return pets.getOrDefault(owner, Collections.emptyMap()).get(id);
    }

    public void spawn(Player player, Pet pet){
        despawnOthers(player.getUniqueId(), pet.getId());

        despawn(pet); // safe
        ActivePet ap = new ActivePet(plugin, player, pet);
        active.put(key(pet), ap);
        pet.setSpawned(true);
        markDirty();
    }

    public void despawn(Pet pet){
        ActivePet ap = active.remove(key(pet));
        if(ap != null) ap.remove();
        pet.setSpawned(false);
        markDirty();
    }

    public void despawnAll(){
        active.values().forEach(ActivePet::remove);
        active.clear();
    }

    public ActivePet getActive(Pet p){
        return active.get(key(p));
    }

    private PetKey key(Pet p){
        return new PetKey(p.getOwner(), p.getId());
    }

    public record PetKey(UUID owner, int id) {}

    /* ====== (Legacy) speler-wandel XP – UIT ====== */
    @Deprecated
    public void addWalkProgress(UUID player, int blocks){
        // NIETS meer doen
    }

    /* ====== Wandel-quest per PET ====== */
    public void addWalkProgressForPet(Pet pet, int blocks){
        PetKey k = key(pet);
        int total = petWalkProgress.getOrDefault(k, 0) + blocks;

        int xpPerBlock = plugin.getConfig().getInt("walk.xp-per-block", 1);
        if (xpPerBlock > 0) pet.addXp(xpPerBlock * blocks);

        final int step     = plugin.getConfig().getInt("walk.quest-step-blocks", 100);
        final int questXp  = plugin.getConfig().getInt("walk.quest-xp", 25);
        final long cdMs    = plugin.getConfig().getInt("walk.quest-cooldown-minutes", 30) * 60_000L;

        long now = System.currentTimeMillis();
        boolean cooldownOver = (now - pet.getLastWalkQuest()) >= cdMs;

        if (cooldownOver && total >= step) {
            total -= step;

            pet.addXp(questXp);
            Player p = Bukkit.getPlayer(pet.getOwner());
            if (p != null) {
                p.sendMessage("§a" + pet.getName() + " heeft §f" + step + "§a blokken gelopen (§e+" + questXp + " XP§a).");
            }

            pet.setLastWalkQuest(now);
            pet.setWalkQuestReadyNotified(false);
        }

        petWalkProgress.put(k, total);
        markDirty();
    }

    public void awardWashXp(Pet pet){
        int amt = plugin.getConfig().getInt("wash.xp", 20);
        pet.addXp(amt);
        markDirty();
        Player p = Bukkit.getPlayer(pet.getOwner());
        if(p!=null) p.sendMessage("§b" + pet.getName() + " Heeft zich gewassen");
    }

    public boolean tryFeed(Pet pet){
        long now = System.currentTimeMillis();
        if(now - pet.getLastFed() < pet.foodIntervalMinutes()*60_000L) return false;
        pet.setLastFed(now);
        pet.addXp(plugin.getConfig().getInt("feed.xp", 10));
        markDirty();
        return true;
    }

    public boolean tryDrink(Pet pet){
        long now = System.currentTimeMillis();
        if(now - pet.getLastWater() < pet.waterIntervalMinutes()*60_000L) return false;
        pet.setLastWater(now);
        pet.addXp(plugin.getConfig().getInt("drink.xp", 10));
        markDirty();
        return true;
    }

    /* ====== Minute tick: ZOEKEN loot, uurloon, quest-ready ====== */
    private void tickMinute(){
        long now = System.currentTimeMillis();
        final long cdMs = plugin.getConfig().getInt("walk.quest-cooldown-minutes", 30) * 60_000L;

        for (var e : pets.entrySet()){
            UUID owner = e.getKey();
            Player player = Bukkit.getPlayer(owner);
            if (player == null) continue;

            for (Pet p : e.getValue().values()){
                if (!p.isSpawned()) continue;

                ActivePet ap = active.get(key(p));
                if (ap == null) continue;

                // ✅ ZOEKEN: geeft loot uit loot.yml (Dieren Loot Config) om de zoveel tijd
                if (ap.elapsedMinutesSinceLoot() >= p.lootIntervalMinutes()){
                    plugin.getItemsManager().giveRandomLootImmediate(owner, p.getUpZoeken());
                    ap.resetLootTimer();
                    player.sendMessage("§b" + p.getName() + " §7heeft iets gevonden! Check je inventory.");
                    markDirty();
                }

                // Uurloon
                if (now - p.getLastHourly() >= 60L*60_000L){
                    int base = plugin.getConfig().getInt("hourly.base-amount", 25);
                    int amount = Math.max(1, base * p.getUpUurloon());
                    String cmd = plugin.getConfig().getString("hourly.command", "eco give %player% %amount%");
                    cmd = cmd.replace("%player%", player.getName()).replace("%amount%", String.valueOf(amount));
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                    p.setLastHourly(now);
                    player.sendMessage("§6" + p.getName() + " heeft uurloon ontvangen: §e" + amount);
                    markDirty();
                }

                // “Uitlaten” melding
                if ((now - p.getLastWalkQuest()) >= cdMs && !p.isWalkQuestReadyNotified()){
                    p.setWalkQuestReadyNotified(true);
                    player.sendMessage("§e" + p.getName() + " §7moet uitgelaten worden.");
                    markDirty();
                }
            }
        }
    }

    public int whistleTeleportAll(Player p){
        int count = 0;
        for(Pet pet : getPets(p.getUniqueId())){
            ActivePet ap = getActive(pet);
            if(ap == null) continue;
            ap.whistleSummon();
            count++;
        }
        return count;
    }
}
