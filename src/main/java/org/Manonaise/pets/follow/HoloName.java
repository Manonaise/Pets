package org.Manonaise.pets.follow;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;

public class HoloName {
    private final Plugin plugin;
    private final Entity target;
    private ArmorStand tag;

    public HoloName(Plugin plugin, Entity target, Component text){
        this.plugin = plugin; this.target = target;
        spawn(text);
    }

    private void spawn(Component text){
        Location loc = above(target);
        tag = (ArmorStand) target.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        tag.setInvisible(true);
        tag.setMarker(true);
        tag.setGravity(false);
        tag.setSmall(true);
        tag.setArms(false);
        tag.setBasePlate(false);
        tag.setInvulnerable(true);
        tag.setPersistent(false);
        tag.setCollidable(false);
        tag.customName(text);
        tag.setCustomNameVisible(true);
    }

    private Location above(Entity e){
        return e.getLocation().add(0, e.getHeight() + 0.35, 0);
    }

    /** Verplaats de hologram boven de target (1x per tick aanroepen). */
    public void followTick(){
        if(tag == null || tag.isDead() || !target.isValid()) return;
        tag.teleport(above(target));
    }

    /** Update de getoonde tekst. */
    public void setText(Component text){
        if(tag != null && !tag.isDead()) tag.customName(text);
    }

    public void remove(){
        if(tag != null){ tag.remove(); tag = null; }
    }
}
