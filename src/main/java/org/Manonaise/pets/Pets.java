package org.Manonaise.pets;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.Manonaise.pets.commands.PetCommand;
import org.Manonaise.pets.data.PetManager;
import org.Manonaise.pets.integrations.AuraSkillsHook;
import org.Manonaise.pets.integrations.MythicMobsHook;
import org.Manonaise.pets.items.ItemsManager;
import org.Manonaise.pets.listeners.*;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Pets extends JavaPlugin {

    private static Pets instance;

    private PetManager petManager;
    private ItemsManager itemsManager;

    // Hooks
    private AuraSkillsHook auraSkillsHook;
    private MythicMobsHook mythicMobsHook;

    public static Pets getInstance() { return instance; }

    public PetManager getPetManager() { return petManager; }
    public ItemsManager getItemsManager() { return itemsManager; }

    public AuraSkillsHook getAuraSkillsHook() { return auraSkillsHook; }
    public MythicMobsHook getMythicMobsHook() { return mythicMobsHook; }

    public static NamespacedKey key(String k) {
        return new NamespacedKey(getInstance(), k);
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.itemsManager = new ItemsManager(this);
        this.petManager = new PetManager(this);

        // AuraSkills is hard depend bij jou
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
            throw new RuntimeException("AuraSkills is verplicht (depend), maar werd niet gevonden.");
        }
        this.auraSkillsHook = new AuraSkillsHook(this);

        // MythicMobs softdepend
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            this.mythicMobsHook = new MythicMobsHook(this);
            getLogger().info("MythicMobs gevonden -> Mythic pets enabled.");
        } else {
            this.mythicMobsHook = null;
            getLogger().info("MythicMobs niet gevonden -> Mythic pets disabled.");
        }

        registerCommands();

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

        getLogger().info("Enabled Pets v" + getDescription().getVersion());
    }

    private void registerCommands() {
        final PetCommand petCommand = new PetCommand(this);

        // Let op: generics is <Plugin>, NIET <JavaPlugin>
        LifecycleEventManager<Plugin> mgr = this.getLifecycleManager();
        mgr.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    "pet",
                    "Pets hoofdcommand",
                    List.of("pets"),
                    petCommand
            );
        });
    }

    @Override
    public void onDisable() {
        if (petManager != null) {
            petManager.despawnAll();
            petManager.save();
        }
        if (itemsManager != null) itemsManager.save();

        instance = null;
    }
}
