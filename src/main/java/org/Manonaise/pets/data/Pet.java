package org.Manonaise.pets.data;

import org.Manonaise.pets.Pets;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class Pet implements ConfigurationSerializable {
    private final int id;
    private final UUID owner;
    private PetType type;
    private String name;

    // Variant (bijv. Cat.Type), optioneel
    private String variant;

    // MythicMobs type-id (bv. "MyCatModel1"), optioneel
    private String mythicMobId;

    // Levels & XP
    private int level = 1;          // 1..100
    private int xp = 0;
    private int skillPoints = 0;

    // Skills (1..10)
    private int upZoeken   = 1;
    private int upGrinden  = 1;
    private int upWater    = 1;
    private int upEten     = 1;
    private int upUurloon  = 1;
    private int upSnelheid = 1;

    // Timers
    private long lastFed = 0L;
    private long lastWater = 0L;
    private long lastWashed = 0L;
    private long lastHourly = 0L;

    // Wandel-quest cooldown + “beschikbaar”-notificatie
    private long lastWalkQuest = 0L;
    private boolean walkQuestReadyNotified = false;

    private boolean spawned = false;

    public Pet(int id, UUID owner, PetType type, String name){
        this.id=id; this.owner=owner; this.type=type; this.name=name;
    }

    public int getId(){ return id; }
    public UUID getOwner(){ return owner; }
    public PetType getType(){ return type; }
    public String getName(){ return name; }
    public void setName(String name){ this.name = name; }

    public String getVariant(){ return variant; }
    public void setVariant(String variant){ this.variant = variant; }

    public String getMythicMobId() { return mythicMobId; }
    public void setMythicMobId(String mythicMobId) { this.mythicMobId = mythicMobId; }

    public int getLevel(){ return level; }
    public int getXp(){ return xp; }
    public int getSkillPoints(){ return skillPoints; }

    public int getXpToNext(){
        return Math.max(50, (int)Math.round(200 + level*15 * (1.0 + (level/25.0))));
    }

    public void addXp(int amount){
        if(level >= 100) return;
        int bonus = (int)Math.round(amount * (1.0 + (upGrinden-1)*0.05));
        xp += Math.max(0, bonus);
        while(level < 100 && xp >= getXpToNext()){
            xp -= getXpToNext();
            level++;
            skillPoints++;
        }
    }

    public boolean spendPoint(String which){
        if(skillPoints <= 0) return false;
        switch (which.toLowerCase()){
            case "zoeken"   -> { if(upZoeken   < 10){ upZoeken++;   skillPoints--; return true; } }
            case "grinden"  -> { if(upGrinden  < 10){ upGrinden++;  skillPoints--; return true; } }
            case "water"    -> { if(upWater    < 10){ upWater++;    skillPoints--; return true; } }
            case "eten"     -> { if(upEten     < 10){ upEten++;     skillPoints--; return true; } }
            case "uurloon"  -> { if(upUurloon  < 10){ upUurloon++;  skillPoints--; return true; } }
            case "snelheid" -> { if(upSnelheid < 10){ upSnelheid++; skillPoints--; return true; } }
        }
        return false;
    }

    public boolean isBaby(){ return level <= 25; }

    public int getUpZoeken(){ return upZoeken; }
    public int getUpGrinden(){ return upGrinden; }
    public int getUpWater(){ return upWater; }
    public int getUpEten(){ return upEten; }
    public int getUpUurloon(){ return upUurloon; }
    public int getUpSnelheid(){ return upSnelheid; }

    public long getLastFed(){ return lastFed; }
    public void setLastFed(long t){ lastFed=t; }
    public long getLastWater(){ return lastWater; }
    public void setLastWater(long t){ lastWater=t; }
    public long getLastWashed(){ return lastWashed; }
    public void setLastWashed(long t){ lastWashed=t; }
    public long getLastHourly(){ return lastHourly; }
    public void setLastHourly(long t){ lastHourly=t; }

    public long getLastWalkQuest(){ return lastWalkQuest; }
    public void setLastWalkQuest(long t){ lastWalkQuest = t; }

    public boolean isWalkQuestReadyNotified(){ return walkQuestReadyNotified; }
    public void setWalkQuestReadyNotified(boolean v){ walkQuestReadyNotified = v; }

    public boolean isSpawned(){ return spawned; }
    public void setSpawned(boolean s){ spawned=s; }

    // Intervallen
    public int foodIntervalMinutes(){ return 10 + (upEten-1); }
    public int waterIntervalMinutes(){ return 10 + (upWater-1); }

    /**
     * ✅ Zoeken interval:
     * basis komt uit config.yml: loot.base-minutes (default 30)
     * upZoeken verlaagt het interval (min 5 minuten).
     */
    public int lootIntervalMinutes(){
        int base = 30;
        try {
            if (Pets.getInstance() != null) {
                base = Pets.getInstance().getConfig().getInt("loot.base-minutes", 30);
            }
        } catch (Throwable ignored) {}

        int reduced = base - (upZoeken - 1) * 2; // elke level zoeken -2 minuten
        return Math.max(5, reduced);
    }

    public boolean isHungry(){ return System.currentTimeMillis() - lastFed >= foodIntervalMinutes() * 60_000L; }
    public boolean isThirsty(){ return System.currentTimeMillis() - lastWater >= waterIntervalMinutes() * 60_000L; }

    public double penaltyMultiplier(){
        double m = 1.0;
        if(isHungry()) m *= 0.6;
        if(isThirsty()) m *= 0.6;
        return m;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("owner", owner.toString());
        m.put("type", type.name());
        m.put("name", name);
        m.put("variant", variant);
        m.put("mythicMobId", mythicMobId);
        m.put("level", level);
        m.put("xp", xp);
        m.put("skillPoints", skillPoints);
        m.put("upZoeken", upZoeken);
        m.put("upGrinden", upGrinden);
        m.put("upWater", upWater);
        m.put("upEten", upEten);
        m.put("upUurloon", upUurloon);
        m.put("upSnelheid", upSnelheid);
        m.put("lastFed", lastFed);
        m.put("lastWater", lastWater);
        m.put("lastWashed", lastWashed);
        m.put("lastHourly", lastHourly);
        m.put("lastWalkQuest", lastWalkQuest);
        m.put("walkQuestReadyNotified", walkQuestReadyNotified);
        m.put("spawned", spawned);
        return m;
    }

    // Number-safe helpers (YAML kan Integer/Long/String geven)
    private static int i(Object o, int def){
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o)); } catch (Exception ignored) { return def; }
    }
    private static long l(Object o, long def){
        if (o == null) return def;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(o)); } catch (Exception ignored) { return def; }
    }
    private static boolean b(Object o, boolean def){
        if (o == null) return def;
        if (o instanceof Boolean bb) return bb;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    public static Pet deserialize(Map<String,Object> m){
        int id = i(m.get("id"), 0);
        UUID owner = UUID.fromString(String.valueOf(m.get("owner")));
        Pet p = new Pet(id, owner, PetType.valueOf(String.valueOf(m.get("type"))), String.valueOf(m.get("name")));

        p.variant     = (String)m.getOrDefault("variant", null);
        p.mythicMobId = (String)m.getOrDefault("mythicMobId", null);

        p.level       = i(m.get("level"), 1);
        p.xp          = i(m.get("xp"), 0);
        p.skillPoints = i(m.get("skillPoints"), 0);

        p.upZoeken    = i(m.get("upZoeken"), 1);
        p.upGrinden   = i(m.get("upGrinden"), 1);
        p.upWater     = i(m.get("upWater"), 1);
        p.upEten      = i(m.get("upEten"), 1);
        p.upUurloon   = i(m.get("upUurloon"), 1);
        p.upSnelheid  = i(m.get("upSnelheid"), 1);

        p.lastFed     = l(m.get("lastFed"), 0L);
        p.lastWater   = l(m.get("lastWater"), 0L);
        p.lastWashed  = l(m.get("lastWashed"), 0L);
        p.lastHourly  = l(m.get("lastHourly"), 0L);

        p.lastWalkQuest = l(m.get("lastWalkQuest"), 0L);
        p.walkQuestReadyNotified = b(m.get("walkQuestReadyNotified"), false);
        p.spawned     = b(m.get("spawned"), false);

        return p;
    }
}
