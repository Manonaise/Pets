package org.Manonaise.pets.follow;

import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.util.Vector;

public class ActivePet {
    private final Pets plugin;
    private final Player owner;
    private final Pet pet;

    private Entity entity;

    // ✅ “Vaste” nametag die aan het dier hangt
    private ArmorStand nameTag;
    private boolean tagIsPassenger = false;

    private int task = -1;
    private long lootStartMs = System.currentTimeMillis();
    private boolean gaveUp = false;
    private boolean following = false;

    // Walk tracking
    private Location lastLoc;
    private double walkAccum = 0.0;

    // Cache voor tekst updates
    private String lastShownName = null;
    private int lastShownLevel = -1;

    public ActivePet(Pets plugin, Player owner, Pet pet){
        this.plugin = plugin;
        this.owner = owner;
        this.pet = pet;
        spawn();
    }

    private void spawn(){
        Location base = findSafeSpawnNear(owner);
        entity = pet.getType().spawn(base, pet, owner);

        // ✅ Maak nametag die als passenger vast hangt
        ensureNameTag(true);

        // PDC markeringen
        entity.getPersistentDataContainer().set(Pets.key("pet-owner"),
                org.bukkit.persistence.PersistentDataType.STRING, owner.getUniqueId().toString());
        entity.getPersistentDataContainer().set(Pets.key("pet-id"),
                org.bukkit.persistence.PersistentDataType.INTEGER, pet.getId());

        lastLoc = entity.getLocation();

        // 10 ticks loop (bestaat al voor follow/walk)
        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entity == null || entity.isDead() || !entity.isValid()) {
                remove();
                return;
            }

            // ✅ Alleen tekst updaten als naam/level veranderd is
            ensureNameTag(false);

            // Als passenger niet gelukt is (heel zeldzaam) -> 1x per tickloop netjes meeteleporteren
            if (nameTag != null && !tagIsPassenger) {
                Location above = above(entity);
                nameTag.teleport(above);
            }

            // Wassen-quest in water
            if (isInWater(entity.getLocation())) {
                long now = System.currentTimeMillis();
                long cd = plugin.getConfig().getInt("wash.cooldown-seconds", 60) * 1000L;
                if (now - pet.getLastWashed() >= cd) {
                    pet.setLastWashed(now);
                    plugin.getPetManager().awardWashXp(pet);
                }
            }

            // Volgen
            followTick();

            // Wandel-quest op basis van afstand
            Location cur = entity.getLocation();
            double moved = cur.distance(lastLoc);

            if (moved > 0 && moved < 6.0) {
                walkAccum += moved;
                if (walkAccum >= 1.0) {
                    int blocks = (int)Math.floor(walkAccum);
                    walkAccum -= blocks;
                    plugin.getPetManager().addWalkProgressForPet(pet, blocks);
                }
            }
            lastLoc = cur;

        }, 10L, 10L);
    }

    /**
     * ✅ Zorgt dat er een nametag bestaat en dat de tekst klopt.
     * force=true bij spawn/teleport.
     */
    private void ensureNameTag(boolean force){
        if (entity == null) return;

        // 1) Spawn tag als die weg is
        if (nameTag == null || nameTag.isDead() || !nameTag.isValid()) {
            spawnNameTag();
            force = true;
        }

        // 2) Update text alleen als nodig
        String nowName = pet.getName();
        int nowLevel = pet.getLevel();
        if (!force
                && nowLevel == lastShownLevel
                && ((nowName == null && lastShownName == null) || (nowName != null && nowName.equals(lastShownName)))) {
            return;
        }
        lastShownName = nowName;
        lastShownLevel = nowLevel;

        if (nameTag != null) {
            nameTag.setCustomName(buildLegacyName());
            nameTag.setCustomNameVisible(true);
        }
    }

    private void spawnNameTag(){
        try {
            // Spawn boven het dier
            Location loc = above(entity);

            nameTag = entity.getWorld().spawn(loc, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);          // ✅ geen hitbox, super licht
                as.setGravity(false);
                as.setSmall(true);
                as.setArms(false);
                as.setBasePlate(false);
                as.setInvulnerable(true);
                as.setPersistent(false);
                as.setCollidable(false);

                as.setCustomName(buildLegacyName());
                as.setCustomNameVisible(true);
            });

            // ✅ Hang de tag vast aan het dier (passenger)
            tagIsPassenger = false;
            try {
                tagIsPassenger = entity.addPassenger(nameTag);
            } catch (Throwable ignored) {
                tagIsPassenger = false;
            }

            // Als passenger faalt: laten we hem meebewegen in de tickloop (teleport 10 ticks)
            if (!tagIsPassenger) {
                nameTag.teleport(above(entity));
            }

        } catch (Throwable t) {
            // Als het écht fout gaat, geen crash: gewoon geen tag.
            nameTag = null;
            tagIsPassenger = false;
        }
    }

    private Location above(Entity e){
        // vergelijkbaar met jouw HoloName offset, maar zonder tick-follow nodig
        return e.getLocation().add(0, e.getHeight() + 0.35, 0);
    }

    private String buildLegacyName(){
        return ChatColor.GOLD + pet.getName()
                + ChatColor.GRAY + " (Lv."
                + ChatColor.YELLOW + pet.getLevel()
                + ChatColor.GRAY + ")";
    }

    private Location findSafeSpawnNear(Player p) {
        Location pl = p.getLocation();
        World w = pl.getWorld();
        if (w == null) return pl.clone().add(0, 1, 0);

        int baseX = pl.getBlockX();
        int baseZ = pl.getBlockZ();
        int baseY = pl.getBlockY();

        int[][] offsets = {
                { 1, 0}, {-1, 0}, { 0, 1}, { 0,-1},
                { 1, 1}, { 1,-1}, {-1, 1}, {-1,-1},
                { 2, 0}, {-2, 0}, { 0, 2}, { 0,-2},
                { 3, 0}, {-3, 0}, { 0, 3}, { 0,-3}
        };

        for (int[] off : offsets) {
            int x = baseX + off[0];
            int z = baseZ + off[1];

            if (!w.isChunkLoaded(x >> 4, z >> 4)) w.getChunkAt(x >> 4, z >> 4);

            for (int y = baseY + 3; y >= baseY - 6; y--) {
                Block ground = w.getBlockAt(x, y - 1, z);
                Block feet   = w.getBlockAt(x, y, z);
                Block head   = w.getBlockAt(x, y + 1, z);

                if (!ground.getType().isSolid()) continue;
                if (!feet.isPassable() || !head.isPassable()) continue;

                Material ft = feet.getType();
                if (ft == Material.WATER || ft == Material.LAVA) continue;

                return new Location(w, x + 0.5, y, z + 0.5, pl.getYaw(), pl.getPitch());
            }
        }

        return pl.clone().add(0, 1, 0);
    }

    private void followTick(){
        if (entity instanceof Sittable s && s.isSitting()) {
            stopPathfindingIfMob();
            entity.setVelocity(new Vector());
            following = false;
            return;
        }

        Location pl = owner.getLocation();
        Location el = entity.getLocation();
        double d2 = pl.distanceSquared(el);

        final double keepDistance = 2.0;
        final int maxRange   = plugin.getConfig().getInt("follow.max-range", 40);
        final int startRange = plugin.getConfig().getInt("follow.teleport-range", 15);

        final double keepSq  = keepDistance * keepDistance;
        final double startSq = startRange * startRange;
        final double stopSq  = maxRange * maxRange;

        if (d2 > stopSq) {
            if (!gaveUp) {
                gaveUp = true;
                owner.sendMessage("§7" + pet.getName() + " is te ver weg en geeft het op. Gebruik §b/pet whistle§7.");
            }
            stopPathfindingIfMob();
            entity.setVelocity(new Vector());
            following = false;
            return;
        }

        if (!following) {
            if (d2 <= startSq) following = true;
            else return;
        }

        if (d2 <= keepSq) {
            stopPathfindingIfMob();
            entity.setVelocity(new Vector());
            return;
        }

        gaveUp = false;

        double speed = Math.max(0.1, (1.0 + (pet.getUpSnelheid()-1) * 0.10) * pet.penaltyMultiplier());

        if (entity instanceof Mob mob) {
            try { mob.getPathfinder().moveTo(owner, speed); }
            catch (Throwable ignored) { velocityFallback(pl, el); }
        } else {
            velocityFallback(pl, el);
        }
    }

    private void velocityFallback(Location pl, Location el){
        Vector dir = pl.toVector().subtract(el.toVector())
                .normalize()
                .multiply(0.25 + (pet.getUpSnelheid()-1)*0.05)
                .multiply(pet.penaltyMultiplier());
        entity.setVelocity(dir);
        if(entity instanceof Creature c){ c.setTarget(owner); }
    }

    private void stopPathfindingIfMob() {
        if (entity instanceof Mob mob) {
            try { mob.getPathfinder().stopPathfinding(); } catch (Throwable ignored) {}
        }
    }

    private boolean isInWater(Location loc){
        Block b = loc.getBlock();
        Material t = b.getType();
        return t == Material.WATER
                || (Tag.ICE.isTagged(t) && b.getRelative(0,-1,0).getType()==Material.WATER)
                || t == Material.BUBBLE_COLUMN;
    }

    public int elapsedMinutesSinceLoot(){
        return (int)((System.currentTimeMillis()-lootStartMs)/60000L);
    }
    public void resetLootTimer(){ lootStartMs = System.currentTimeMillis(); }

    public void whistleSummon(){
        Location target = findSafeSpawnNear(owner);
        stopPathfindingIfMob();

        entity.getPersistentDataContainer().set(
                Pets.key("pet-whistle-allow-until"),
                org.bukkit.persistence.PersistentDataType.LONG,
                System.currentTimeMillis() + 2000L
        );

        entity.teleport(target);
        gaveUp = false;
        following = false;
        lastLoc = entity.getLocation();
        walkAccum = 0;

        // passenger tag volgt automatisch; anders teleporten we hem 1x
        if (nameTag != null && !tagIsPassenger) nameTag.teleport(above(entity));
        ensureNameTag(true);
    }

    public void setSitting(boolean sit){
        if(entity instanceof Sittable s) s.setSitting(sit);
    }

    public void remove(){
        if(task!=-1){ Bukkit.getScheduler().cancelTask(task); task=-1; }

        if (nameTag != null) {
            try {
                if (tagIsPassenger && entity != null) entity.removePassenger(nameTag);
            } catch (Throwable ignored) {}
            nameTag.remove();
            nameTag = null;
        }

        if(entity!=null){ entity.remove(); entity=null; }
    }

    public Entity getEntity(){ return entity; }
}
