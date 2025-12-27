package org.Manonaise.pets;

import org.Manonaise.pets.commands.PetCommand;
import org.Manonaise.pets.data.PetManager;
import org.Manonaise.pets.items.ItemsManager;
import org.Manonaise.pets.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class Pets extends JavaPlugin {
    private static Pets instance;

    private PetManager petManager;
    private ItemsManager itemsManager;

    // ✅ Team naam moet kort zijn (veilig voor scoreboard limits)
    private static final String PET_TEAM_NAME = "petsTag";

    public static Pets getInstance(){ return instance; }
    public PetManager getPetManager(){ return petManager; }
    public ItemsManager getItemsManager(){ return itemsManager; }

    public static NamespacedKey key(String k){ return new NamespacedKey(getInstance(), k); }

    /** ✅ Scoreboard team dat nametags ALTIJD toont. */
    public Team getPetNametagTeam() {
        try {
            var mgr = Bukkit.getScoreboardManager();
            if (mgr == null) return null;

            Scoreboard sb = mgr.getMainScoreboard();
            Team team = sb.getTeam(PET_TEAM_NAME);
            if (team == null) team = sb.registerNewTeam(PET_TEAM_NAME);

            // Forceer nametag always zichtbaar
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);

            // Optioneel: collision uit zodat pets niet duwen
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

            return team;
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public void onEnable() {
        instance = this;

        if (getResource("config.yml") != null) saveDefaultConfig();

        this.petManager   = new PetManager(this);
        this.itemsManager = new ItemsManager(this);

        // ✅ Zorg dat team bestaat bij startup
        getPetNametagTeam();

        // Commands
        this.registerCommand("pet", new PetCommand(this));
        this.registerCommand("petmenutoken", new org.Manonaise.pets.commands.PetMenuTokenCommand(this));

        // Listeners
        Bukkit.getPluginManager().registerEvents(new InteractionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);
        Bukkit.getPluginManager().registerEvents(new QuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new TeleportListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BasketListener(this), this);

        if (Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
            Bukkit.getPluginManager().registerEvents(new MenuGateListener(this), this);
        }

        getLogger().info("Pets enabled");
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.despawnAll();
            petManager.save();
        }
        if (itemsManager != null) itemsManager.save();

        // (Team laten we bestaan; unregister kan andere plugins beïnvloeden als ze hergebruiken)
        getLogger().info("Pets disabled");
    }
}
