package com.example.goathorngiants;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.List;

public final class GoatHornGiantsPlugin extends JavaPlugin {

    private GiantManager giantManager;

    @Override
    public void onEnable() {
        // Pathfinder API and EntityAddToWorldEvent are Paper-only.
        try {
            Class.forName("com.destroystokyo.paper.entity.Pathfinder");
            Class.forName("com.destroystokyo.paper.event.entity.EntityAddToWorldEvent");
        } catch (ClassNotFoundException ex) {
            getLogger().severe("GoatHornGiants requires Paper 26.3 or newer. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        giantManager = new GiantManager(this);
        TransformationService service = new TransformationService(this, giantManager);

        getServer().getPluginManager().registerEvents(new HornListener(service), this);
        getServer().getPluginManager().registerEvents(new GiantListener(this, giantManager), this);

        giantManager.trackLoadedGiants();
        giantManager.start();
    }

    @Override
    public void onDisable() {
        if (giantManager != null) {
            giantManager.stop();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("goathorngiants.admin")) {
                sender.sendMessage("You do not have permission.");
                return true;
            }
            reloadConfig();
            giantManager.start(); // re-reads the AI interval
            sender.sendMessage("GoatHornGiants config reloaded.");
            return true;
        }
        sender.sendMessage("Usage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
