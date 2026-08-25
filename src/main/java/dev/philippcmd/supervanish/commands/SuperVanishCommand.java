package dev.philippcmd.supervanish.commands;

import dev.philippcmd.supervanish.library.VanishService;
import dev.philippcmd.supervanish.library.VanishTier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SuperVanishCommand implements CommandExecutor {

    private final VanishService vanishService;

    public SuperVanishCommand(VanishService vanishService) {
        this.vanishService = vanishService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        boolean vanished = this.vanishService.toggle(player, VanishTier.SILENT);
        player.sendMessage(vanished ? "You are now in SuperVanish mode." : "You have left SuperVanish mode.");
        return true;
    }
}
