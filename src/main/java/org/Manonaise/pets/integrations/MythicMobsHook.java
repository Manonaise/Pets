package org.Manonaise.pets.integrations;

import org.Manonaise.pets.Pets;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

public class MythicMobsHook {

    private final Pets plugin;
    private final Plugin mythic;
    private final ClassLoader cl;

    public MythicMobsHook(Pets plugin) {
        this.plugin = plugin;
        this.mythic = Bukkit.getPluginManager().getPlugin("MythicMobs");
        this.cl = (mythic != null ? mythic.getClass().getClassLoader() : null);
    }

    public boolean isAvailable() {
        return mythic != null && mythic.isEnabled();
    }

    public Entity spawnMythicMobOrThrow(String mobId, Location loc) {
        if (!isAvailable()) {
            throw new RuntimeException("MythicMobs is niet aanwezig maar je probeert een Mythic pet te spawnen.");
        }
        if (mobId == null || mobId.isBlank()) {
            throw new RuntimeException("MythicMobId is leeg/null.");
        }

        try {
            // MythicBukkit.inst()
            Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, cl);
            Object mythicBukkit = mythicBukkitClass.getMethod("inst").invoke(null);

            // mobManager
            Object mobManager = mythicBukkitClass.getMethod("getMobManager").invoke(mythicBukkit);

            // mobManager.getMythicMob(String) -> Optional
            Object optObj = mobManager.getClass().getMethod("getMythicMob", String.class).invoke(mobManager, mobId);
            if (!(optObj instanceof Optional<?> opt) || opt.isEmpty()) {
                throw new RuntimeException("MythicMob niet gevonden: " + mobId);
            }
            Object mythicMob = opt.get();

            // BukkitAdapter.adapt(Location) -> AbstractLocation
            Class<?> bukkitAdapterClass = Class.forName("io.lumine.mythic.bukkit.BukkitAdapter", true, cl);
            Object mythicLoc = bukkitAdapterClass.getMethod("adapt", Location.class).invoke(null, loc);

            // mythicMob.spawn(mythicLoc, 1.0d)  (method signature verschilt per versie -> zoek robust)
            Method spawnMethod = findSpawnMethod(mythicMob.getClass(), mythicLoc);
            if (spawnMethod == null) {
                throw new RuntimeException("Kon geen geschikte spawn(...) methode vinden voor MythicMob: " + mobId);
            }

            Object activeMob = spawnMethod.invoke(mythicMob, mythicLoc, 1.0d);
            if (activeMob == null) {
                throw new RuntimeException("Mythic spawn gaf null terug voor: " + mobId);
            }

            // ActiveMob#getEntity().getBukkitEntity()
            Object mmEntity = activeMob.getClass().getMethod("getEntity").invoke(activeMob);
            Object bukkitEntity = mmEntity.getClass().getMethod("getBukkitEntity").invoke(mmEntity);

            return (Entity) bukkitEntity;

        } catch (Throwable t) {
            throw new RuntimeException("Mythic spawn faalde voor '" + mobId + "'. Bestaat de mob id?", t);
        }
    }

    private Method findSpawnMethod(Class<?> mythicMobClass, Object mythicLoc) {
        // Zoek: spawn(<AbstractLocation subtype>, double)
        for (Method m : mythicMobClass.getMethods()) {
            if (!m.getName().equals("spawn")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[1] == double.class && p[0].isInstance(mythicLoc)) {
                return m;
            }
        }
        // fallback: soms is de eerste param een supertype; probeer assignable check
        for (Method m : mythicMobClass.getMethods()) {
            if (!m.getName().equals("spawn")) continue;
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 2 && p[1] == double.class && p[0].isAssignableFrom(mythicLoc.getClass())) {
                return m;
            }
        }
        return null;
    }
}
