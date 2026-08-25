package dev.philippcmd.supervanish;

import dev.philippcmd.supervanish.commands.SuperVanishCommand;
import dev.philippcmd.supervanish.commands.VanishCommand;
import dev.philippcmd.supervanish.commands.VanishListCommand;
import dev.philippcmd.supervanish.commands.VanishShowCommand;
import dev.philippcmd.supervanish.library.VanishAudience;
import dev.philippcmd.supervanish.library.VanishService;
import dev.philippcmd.supervanish.library.VanishVisibilityListener;
import dev.philippcmd.supervanish.storage.YamlVanishStateStore;
import dev.philippcmd.supervanish.tabcompleters.VanishShowTabCompleter;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * The SuperVanish plugin: a thin lifecycle shim over {@link VanishService}.
 *
 * <p>All of the behaviour now lives in the {@code library} module, which knows
 * nothing about this class. That is what lets the same visibility engine be driven
 * by a host that already has a plugin of its own.
 */
public class Main extends JavaPlugin {

    private VanishService vanishService;

    @Override
    public void onEnable() {
        this.vanishService = new VanishService(
                this,
                new YamlVanishStateStore(this),
                VanishAudience.permission("vanish.show"),
                true);

        getCommand("vanish").setExecutor(new VanishCommand(this.vanishService));
        getCommand("supervanish").setExecutor(new SuperVanishCommand(this.vanishService));
        getCommand("vanish-show").setExecutor(new VanishShowCommand(this.vanishService));
        getCommand("vanish-list").setExecutor(new VanishListCommand(this.vanishService));

        getCommand("vanish-show").setTabCompleter(new VanishShowTabCompleter());

        getServer().getPluginManager().registerEvents(
                new VanishVisibilityListener(this.vanishService, true), this);

        // Players who were already online across a /reload must be reconciled too.
        this.vanishService.refreshAll();

        getLogger().info("SuperVanish Plugin enabled!");
    }

    @Override
    public void onDisable() {
        if (this.vanishService != null) {
            // Leaving players hidden after the plugin unloads would strand them
            // invisible with nothing left to restore them.
            this.vanishService.releaseAll();
        }
        getLogger().info("SuperVanish Plugin disabled!");
    }

    public VanishService getVanishService() {
        return this.vanishService;
    }
}
