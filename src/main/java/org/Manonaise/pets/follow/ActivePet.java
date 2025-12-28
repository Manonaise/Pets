package org.Manonaise.pets.follow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Manonaise.pets.Pets;
import org.Manonaise.pets.data.Pet;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class ActivePet {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final Pets plugin;
    private final Player owner;
    private final Pet pet;

    private Entity entity;

    // ✅ Mythic-proof nametag: los ArmorStand die we elke tick teleporteren
    private ArmorStand nameTag;

    // ✅ 2 tasks: 1 tick voor nametag, 10 ticks voor de rest
    private int nameTask = -1;
    private int mainTask = -1;

    private long lootStartMs = System.currentTimeMillis();
    private boolean gaveUp = false;
    private boolean following = false;

    private Location lastLoc;
    private double walkAccum = 0.0;

    private int lastShownLevel = -1;
    private String lastShownName = null;

    // AuraSkills mining boost tracking (optioneel)
    private int lastGrindLevel = -1;
    private double lastAppliedAmount = Double.NaN;

    // ✅ reuse locatie object om GC-stotters te vermijden
    private final Location tagLocCache = new Location(null, 0, 0, 0);

    public ActivePet(Pets plugin, Player owner, Pet pet) {
        this.plugin = plugin;
        this.owner = owner;
        this.pet = pet;
        spawn();
    }

    private void spawn() {
        Location base = computeBaseSpawn(owner.getLocation());
        Location safe = findSafeSpawnNear(base);

        entity = pet.getType().spawn(safe, pet, owner);

        entity.getPersistentDataContainer().set(
                Pets.key("pet-owner"),
                PersistentDataType.STRING,
                owner.getUniqueId().toString()
        );
        entity.getPersistentDataContainer().set(
                Pets.key("pet-id"),
                PersistentDataType.INTEGER,
                pet.getId()
        );

        // Mythic kan custom-name overschrijven, dus wij tonen onze naam via ArmorStand
        try {
            entity.customName(Component.empty());
            entity.setCustomNameVisible(false);
        } catch (Throwable ignored) {}

        ensureNameTag(true);

        lastLoc = entity.getLocation();
        walkAccum = 0;

        // direct mining boost (optioneel)
        updateMiningBoost(true);

        // ✅ 1-tick nametag task (vloeiend)
        nameTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entity == null || !entity.isValid() || entity.isDead() || !owner.isOnline()) {
                remove();
                return;
            }

            ensureNameTag(false);

            if (nameTag != null && nameTag.isValid() && !nameTag.isDead()) {
                nameTag.teleport(computeNameTagLocation(entity));
            }

        }, 1L, 1L);

        // ✅ 10-tick main task (logica)
        mainTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (entity == null || !entity.isValid() || entity.isDead() || !owner.isOnline()) {
                remove();
                return;
            }

            // Alleen text updaten als nodig
            if (pet.getLevel() != lastShownLevel || !safeEq(lastShownName, pet.getName())) {
                updateNameTagText();
            }

            updateMiningBoost(false);

            if (isInWater(entity.getLocation())) {
                long now = System.currentTimeMillis();
                long cd = plugin.getConfig().getInt("wash.cooldown-seconds", 60) * 1000L;
                if (now - pet.getLastWashed() >= cd) {
                    pet.setLastWashed(now);
                    plugin.getPetManager().awardWashXp(pet);
                }
            }

            followTick();

            Location cur = entity.getLocation();
            double moved = cur.distance(lastLoc);
            if (moved > 0 && moved < 6.0) {
                walkAccum += moved;
                if (walkAccum >= 1.0) {
                    int blocks = (int) Math.floor(walkAccum);
                    walkAccum -= blocks;
                    plugin.getPetManager().addWalkProgressForPet(pet, blocks);
                }
            }
            lastLoc = cur;

        }, 10L, 10L);
    }

    /* ===================== NAME TAG ===================== */

    private void ensureNameTag(boolean forceText) {
        if (entity == null) return;

        if (nameTag == null || !nameTag.isValid() || nameTag.isDead()) {
            spawnNameTag();
            forceText = true;
        }

        if (forceText) {
            updateNameTagText();
        }
    }

    private void spawnNameTag() {
        try {
            Location at = computeNameTagLocation(entity);

            nameTag = entity.getWorld().spawn(at, ArmorStand.class, as -> {
                as.setInvisible(true);
                as.setMarker(true);
                as.setGravity(false);
                as.setSmall(true);
                as.setArms(false);
                as.setBasePlate(false);
                as.setInvulnerable(true);
                as.setPersistent(false);
                as.setCollidable(false);

                as.customName(petNameComponent());
                as.setCustomNameVisible(true);
            });

        } catch (Throwable t) {
            nameTag = null;
        }
    }

    private void updateNameTagText() {
        if (nameTag == null || nameTag.isDead() || !nameTag.isValid()) {
            ensureNameTag(true);
            return;
        }

        nameTag.customName(petNameComponent());
        nameTag.setCustomNameVisible(true);

        lastShownLevel = pet.getLevel();
        lastShownName = pet.getName();
    }

    private Component petNameComponent() {
        Component nm = LEGACY.deserialize(pet.getName());
        return nm.append(Component.text(" §7(Lv." + pet.getLevel() + ")"));
    }

    /**
     * ✅ Automatische hoogte: bovenkant boundingbox + marge.
     * Reused Location object om micro-stutters te voorkomen.
     */
    private Location computeNameTagLocation(Entity e) {
        Location l = e.getLocation();
        double topY;
        try {
            topY = e.getBoundingBox().getMaxY();
        } catch (Throwable t) {
            topY = l.getY() + Math.max(1.0, e.getHeight());
        }

        double extra = plugin.getConfig().getDouble("nametag.extra-y", 0.08);

        tagLocCache.setWorld(l.getWorld());
        tagLocCache.setX(l.getX());
        tagLocCache.setY(topY + extra);
        tagLocCache.setZ(l.getZ());
        tagLocCache.setYaw(0f);
        tagLocCache.setPitch(0f);
        return tagLocCache;
    }

    private boolean safeEq(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    /* ===================== AURASKILLS (OPTIONEEL) ===================== */

    private void updateMiningBoost(boolean force) {
        int g = pet.getUpGrinden();

        double perLevel = plugin.getConfig().getDouble("grinden.auraskills-per-level", 0.01);
        double cap = plugin.getConfig().getDouble("grinden.auraskills-cap", 0.10);

        double amount = 0.0;
        if (g > 1) {
            amount = (g - 1) * perLevel;
            if (amount > cap) amount = cap;
        }

        if (!force && g == lastGrindLevel && Double.compare(amount, lastAppliedAmount) == 0) return;

        lastGrindLevel = g;
        lastAppliedAmount = amount;

        var hook = plugin.getAuraSkillsHook();
        if (hook != null) {
            hook.setMiningSpeedBonus(owner, pet, amount);
        }
    }

    /* ===================== LOOT TIMER ===================== */

    public int elapsedMinutesSinceLoot() {
        return (int) ((System.currentTimeMillis() - lootStartMs) / 60000L);
    }

    public void resetLootTimer() {
        lootStartMs = System.currentTimeMillis();
    }

    /* ===================== SPAWN HELPERS ===================== */

    private Location computeBaseSpawn(Location playerLoc) {
        Location l = playerLoc.clone();
        Vector dir = l.getDirection().setY(0).normalize();
        l.add(dir.multiply(1.5));
        return l;
    }

    private Location findSafeSpawnNear(Location base) {
        Location start = base.clone();
        start.setY(base.getY());

        Location up = start.clone();
        for (int dy = 0; dy <= 8; dy++) {
            Location c = start.clone().add(0, dy, 0);
            if (isPassable(c.getBlock()) && isPassable(c.clone().add(0, 1, 0).getBlock())) {
                up = c;
                break;
            }
        }

        Location grounded = up.clone();
        for (int dy = 0; dy <= 8; dy++) {
            Block below = grounded.clone().add(0, -1, 0).getBlock();
            if (!isPassable(below)) return grounded;
            grounded.subtract(0, 1, 0);
        }
        return up;
    }

    private boolean isPassable(Block b) {
        Material t = b.getType();
        try { return b.isPassable(); }
        catch (Throwable ignored) { return !t.isSolid(); }
    }

    /* ===================== FOLLOW ===================== */

    private void followTick() {
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
        final int maxRange = plugin.getConfig().getInt("follow.max-range", 40);
        final int startRange = plugin.getConfig().getInt("follow.teleport-range", 15);

        final double keepSq = keepDistance * keepDistance;
        final double startSq = startRange * startRange;
        final double stopSq = maxRange * maxRange;

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

        double speed = Math.max(0.1, (1.0 + (pet.getUpSnelheid() - 1) * 0.10) * pet.penaltyMultiplier());

        if (entity instanceof Mob mob) {
            try {
                mob.getPathfinder().moveTo(owner, speed);
            } catch (Throwable ignored) {
                velocityFallback(pl, el);
            }
        } else {
            velocityFallback(pl, el);
        }
    }

    private void velocityFallback(Location pl, Location el) {
        Vector dir = pl.toVector().subtract(el.toVector())
                .normalize()
                .multiply(0.25 + (pet.getUpSnelheid() - 1) * 0.05)
                .multiply(pet.penaltyMultiplier());
        entity.setVelocity(dir);
        if (entity instanceof Creature c) c.setTarget(owner);
    }

    private void stopPathfindingIfMob() {
        if (entity instanceof Mob mob) {
            try { mob.getPathfinder().stopPathfinding(); } catch (Throwable ignored) {}
        }
    }

    private boolean isInWater(Location loc) {
        Block b = loc.getBlock();
        Material t = b.getType();
        return t == Material.WATER
                || (Tag.ICE.isTagged(t) && b.getRelative(0, -1, 0).getType() == Material.WATER)
                || t == Material.BUBBLE_COLUMN;
    }

    /* ===================== WHISTLE ===================== */

    public void whistleSummon() {
        Location rnd = owner.getLocation().clone().add(-1 + Math.random() * 2, 0, -1 + Math.random() * 2);
        Location safe = findSafeSpawnNear(rnd);

        stopPathfindingIfMob();

        entity.getPersistentDataContainer().set(
                Pets.key("pet-whistle-allow-until"),
                PersistentDataType.LONG,
                System.currentTimeMillis() + 2000L
        );

        entity.teleport(safe);

        // nametag meteen mee
        ensureNameTag(false);
        if (nameTag != null && nameTag.isValid() && !nameTag.isDead()) {
            nameTag.teleport(computeNameTagLocation(entity));
        }

        gaveUp = false;
        following = false;
        lastLoc = entity.getLocation();
        walkAccum = 0;
    }

    /* ===================== SITTING ===================== */

    public void setSitting(boolean sit) {
        if (entity instanceof Sittable s) s.setSitting(sit);
    }

    /* ===================== REMOVE ===================== */

    public void remove() {
        if (nameTask != -1) {
            Bukkit.getScheduler().cancelTask(nameTask);
            nameTask = -1;
        }
        if (mainTask != -1) {
            Bukkit.getScheduler().cancelTask(mainTask);
            mainTask = -1;
        }

        // AuraSkills bonus weg (optioneel)
        try {
            var hook = plugin.getAuraSkillsHook();
            if (hook != null) hook.removeMiningSpeedBonus(owner, pet);
        } catch (Throwable ignored) {}

        if (nameTag != null) {
            try { nameTag.remove(); } catch (Throwable ignored) {}
            nameTag = null;
        }

        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }

    public Entity getEntity() {
        return entity;
    }
}
