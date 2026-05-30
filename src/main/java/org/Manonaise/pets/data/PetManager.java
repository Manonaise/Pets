package org.Manonaise.pets.data;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.follow.ActivePet;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PetManager {

    private final Pets plugin;
    private final File file;
    private final FileConfiguration cfg;

    private final Map<UUID, Map<Integer, Pet>> pets = new HashMap<>();
    private final Map<PetKey, ActivePet> active = new HashMap<>();
    private final Map<PetKey, Integer> petWalkProgress = new HashMap<>();

    private volatile boolean dirty = false;

    public PetManager(Pets plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "pets.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);

        load();

        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMinute, 20L * 60, 20L * 60);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (dirty) {
                dirty = false;
                save();
            }
        }, 20L * 60, 20L * 60);
    }

    public void markDirty() {
        dirty = true;
    }

    private void load() {
        if (!cfg.isConfigurationSection("players")) return;

        for (String uuidStr : Objects.requireNonNull(cfg.getConfigurationSection("players")).getKeys(false)) {
            UUID uuid;

            try {
                uuid = UUID.fromString(uuidStr);
            } catch (Exception ignored) {
                continue;
            }

            Map<Integer, Pet> map = new HashMap<>();

            var sec = cfg.getConfigurationSection("players." + uuidStr);
            if (sec == null) continue;

            for (String idStr : sec.getKeys(false)) {
                var psec = cfg.getConfigurationSection("players." + uuidStr + "." + idStr);
                if (psec == null) continue;

                try {
                    Map<String, Object> raw = psec.getValues(false);
                    Pet pet = Pet.deserialize(raw);

                    pet.setSpawned(false);

                    map.put(pet.getId(), pet);
                } catch (Throwable t) {
                    plugin.getLogger().warning("Kon pet niet laden voor " + uuidStr + " id " + idStr + ": " + t.getMessage());
                }
            }

            pets.put(uuid, map);
        }

        markDirty();
    }

    public void save() {
        cfg.set("players", null);

        for (var entry : pets.entrySet()) {
            String root = "players." + entry.getKey();

            for (Pet pet : entry.getValue().values()) {
                cfg.createSection(root + "." + pet.getId(), pet.serialize());
            }
        }

        try {
            cfg.save(file);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private int nextId(UUID owner) {
        return pets.computeIfAbsent(owner, k -> new HashMap<>())
                .keySet()
                .stream()
                .mapToInt(i -> i)
                .max()
                .orElse(0) + 1;
    }

    public Pet createPet(UUID owner, PetType type, String name) {
        Pet pet = new Pet(nextId(owner), owner, type, name);

        pets.computeIfAbsent(owner, k -> new HashMap<>()).put(pet.getId(), pet);

        markDirty();

        return pet;
    }

    private void despawnOthers(UUID owner, int keepId) {
        for (Pet other : getPets(owner)) {
            if (other.getId() != keepId && getActive(other) != null) {
                despawn(other);
            }
        }
    }

    public boolean removePet(UUID owner, int id) {
        Map<Integer, Pet> map = pets.get(owner);
        if (map == null) return false;

        Pet pet = map.remove(id);
        if (pet == null) return false;

        despawn(pet);
        markDirty();

        return true;
    }

    public Collection<Pet> getPets(UUID owner) {
        return pets.getOrDefault(owner, Collections.emptyMap()).values();
    }

    public Pet get(UUID owner, int id) {
        return pets.getOrDefault(owner, Collections.emptyMap()).get(id);
    }

    public void spawn(Player player, Pet pet) {
        if (player == null || pet == null) return;

        despawnOthers(player.getUniqueId(), pet.getId());

        despawn(pet);

        ActivePet activePet = new ActivePet(plugin, player, pet);

        active.put(key(pet), activePet);

        pet.setSpawned(true);
        markDirty();
    }

    public void despawn(Pet pet) {
        if (pet == null) return;

        pet.setSpawned(false);

        ActivePet activePet = active.remove(key(pet));

        if (activePet != null) {
            try {
                activePet.remove();
            } catch (Throwable ignored) {
            }
        }

        try {
            Player owner = Bukkit.getPlayer(pet.getOwner());

            if (owner != null && plugin.getAuraSkillsHook() != null) {
                plugin.getAuraSkillsHook().removeMiningSpeedBonus(owner, pet);
            }
        } catch (Throwable ignored) {
        }

        markDirty();
    }

    public void despawnAll() {
        for (Map<Integer, Pet> ownerPets : pets.values()) {
            for (Pet pet : ownerPets.values()) {
                pet.setSpawned(false);
            }
        }

        for (Map.Entry<PetKey, ActivePet> entry : new HashMap<>(active).entrySet()) {
            try {
                entry.getValue().remove();
            } catch (Throwable ignored) {
            }
        }

        active.clear();

        try {
            if (plugin.getAuraSkillsHook() != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    plugin.getAuraSkillsHook().removeAllMiningSpeedBonuses(player);
                }
            }
        } catch (Throwable ignored) {
        }

        markDirty();
    }

    public ActivePet getActive(Pet pet) {
        if (pet == null) return null;
        return active.get(key(pet));
    }

    private PetKey key(Pet pet) {
        return new PetKey(pet.getOwner(), pet.getId());
    }

    public record PetKey(UUID owner, int id) {
    }

    public void addWalkProgress(UUID owner, int blocks) {
        if (blocks <= 0) return;

        for (Pet pet : getPets(owner)) {
            if (getActive(pet) != null) {
                addWalkProgressForPet(pet, blocks);
            }
        }
    }

    public void addWalkProgressForPet(Pet pet, int blocks) {
        if (pet == null || blocks <= 0) return;

        PetKey k = key(pet);
        int total = petWalkProgress.getOrDefault(k, 0) + blocks;

        int xpPerBlock = plugin.getConfig().getInt("walk.xp-per-block", 1);

        if (xpPerBlock > 0) {
            pet.addXp(xpPerBlock * blocks);
        }

        final int step = plugin.getConfig().getInt("walk.quest-step-blocks", 100);
        final int questXp = plugin.getConfig().getInt("walk.quest-xp", 25);
        final long cooldownMs = plugin.getConfig().getInt("walk.quest-cooldown-minutes", 30) * 60_000L;

        long now = System.currentTimeMillis();
        boolean cooldownOver = (now - pet.getLastWalkQuest()) >= cooldownMs;

        if (cooldownOver && total >= step) {
            total -= step;

            pet.addXp(questXp);

            Player player = Bukkit.getPlayer(pet.getOwner());

            if (player != null) {
                player.sendMessage("§a" + pet.getName() + " heeft §f" + step + "§a blokken gelopen (§e+" + questXp + " XP§a).");
            }

            pet.setLastWalkQuest(now);
            pet.setWalkQuestReadyNotified(false);
        }

        petWalkProgress.put(k, total);
        markDirty();
    }

    public void awardWashXp(Pet pet) {
        if (pet == null) return;

        int amount = plugin.getConfig().getInt("wash.xp", 20);

        pet.addXp(amount);

        markDirty();

        Player player = Bukkit.getPlayer(pet.getOwner());

        if (player != null) {
            player.sendMessage("§b" + pet.getName() + " heeft zich gewassen. §7(+" + amount + " XP)");
        }
    }

    public boolean tryFeed(Pet pet) {
        if (pet == null) return false;

        long now = System.currentTimeMillis();
        long cooldown = pet.foodIntervalMinutes() * 60_000L;

        if (now - pet.getLastFed() < cooldown) {
            return false;
        }

        int xp = plugin.getConfig().getInt("feed.xp", 10);

        pet.setLastFed(now);
        pet.addXp(xp);

        markDirty();

        return true;
    }

    public boolean tryDrink(Pet pet) {
        if (pet == null) return false;

        long now = System.currentTimeMillis();
        long cooldown = pet.waterIntervalMinutes() * 60_000L;

        if (now - pet.getLastWater() < cooldown) {
            return false;
        }

        int xp = plugin.getConfig().getInt("drink.xp", 10);

        pet.setLastWater(now);
        pet.addXp(xp);

        markDirty();

        return true;
    }

    private void tickMinute() {
        long now = System.currentTimeMillis();

        final long walkCooldownMs = plugin.getConfig().getInt("walk.quest-cooldown-minutes", 30) * 60_000L;
        final long hourlyCooldownMs = plugin.getConfig().getInt("hourly.cooldown-minutes", 60) * 60_000L;

        for (var entry : pets.entrySet()) {
            UUID ownerUuid = entry.getKey();
            Player player = Bukkit.getPlayer(ownerUuid);

            if (player == null) continue;

            for (Pet pet : entry.getValue().values()) {
                ActivePet activePet = getActive(pet);

                if (activePet == null) {
                    continue;
                }

                if (activePet.elapsedMinutesSinceLoot() >= pet.lootIntervalMinutes()) {
                    plugin.getItemsManager().giveRandomLootImmediate(ownerUuid, pet.getUpZoeken());

                    activePet.resetLootTimer();

                    player.sendMessage("§b" + pet.getName() + " §7heeft iets gevonden! Check je inventory.");

                    markDirty();
                }

                if (now - pet.getLastHourly() >= hourlyCooldownMs) {
                    int base = plugin.getConfig().getInt("hourly.base-amount", 25);
                    int amount = Math.max(1, base * pet.getUpUurloon());

                    String command = plugin.getConfig().getString("hourly.command", "eco give %player% %amount%");

                    command = command
                            .replace("%player%", player.getName())
                            .replace("%amount%", String.valueOf(amount));

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                    pet.setLastHourly(now);

                    player.sendMessage("§6" + pet.getName() + " heeft uurloon ontvangen: §e" + amount);

                    markDirty();
                }

                if ((now - pet.getLastWalkQuest()) >= walkCooldownMs && !pet.isWalkQuestReadyNotified()) {
                    pet.setWalkQuestReadyNotified(true);

                    player.sendMessage("§e" + pet.getName() + " §7moet uitgelaten worden.");

                    markDirty();
                }
            }
        }
    }

    public int whistleTeleportAll(Player player) {
        if (player == null) return 0;

        int count = 0;

        for (Pet pet : getPets(player.getUniqueId())) {
            ActivePet activePet = getActive(pet);

            if (activePet == null) {
                continue;
            }

            activePet.whistleSummon();
            count++;
        }

        return count;
    }
}