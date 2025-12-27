package org.Manonaise.pets.commands;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.Manonaise.pets.Pets;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * /petmenutoken
 * Wordt door ItemsAdder uitgevoerd (as_console: false).
 * Zet een 2s token zodat /pet menu meteen erna toegestaan is.
 */
public class PetMenuTokenCommand implements BasicCommand {
    private final Pets plugin;
    public static final String KEY_ALLOW_UNTIL = "pet-menu-allow-until";

    public PetMenuTokenCommand(Pets plugin) { this.plugin = plugin; }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (!(sender instanceof Player p)) return; // alleen logisch voor spelers

        long until = System.currentTimeMillis() + 2000L;
        p.getPersistentDataContainer().set(
                Pets.key(KEY_ALLOW_UNTIL),
                PersistentDataType.LONG,
                until
        );
        // Optioneel: geen chatspam
        // p.sendMessage("§7Pet menu token gezet.");
    }
}
