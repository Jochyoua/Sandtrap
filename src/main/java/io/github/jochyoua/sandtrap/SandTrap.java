package io.github.jochyoua.sandtrap;

import io.github.jochyoua.sandtrap.listeners.PlayerMovementListener;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public final class SandTrap extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(this), this);

        this.registerCommand();
        if (getConfig().getBoolean("metrics.enabled")) {
            this.registerMetrics();
        }
        this.validateWhitelist();
    }

    private void validateWhitelist() {
        Set<String> validatedWhitelist = new HashSet<>();
        for (String entry : this.getConfig().getStringList("fallingBlockWhitelist")) {
            try {
                Material mat = Material.valueOf(entry.toUpperCase());
                if (mat.isBlock() && mat.hasGravity()) {
                    validatedWhitelist.add(entry.toUpperCase());
                } else {
                    if (getLogger().isLoggable(Level.WARNING)) {
                        getLogger().warning("Invalid falling block material: " + entry);
                    }
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Unknown material in whitelist: " + entry);
            }
        }
        getConfig().set("fallingBlockWhitelist", validatedWhitelist.toArray());
        saveConfig();
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, 26946);

        metrics.addCustomChart(new SingleLineChart("trap_triggers", () -> getConfig().getInt("metrics.trapTriggers", 0)));

        metrics.addCustomChart(new AdvancedPie("trigger_block_type", () -> {
            ConfigurationSection section = getConfig().getConfigurationSection("metrics.blockTypeStats");
            Map<String, Integer> map = new HashMap<>();
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    map.put(key, section.getInt(key));
                }
            }
            return map;
        }));
    }

    private void registerCommand() {
        PluginCommand reloadCommand = getCommand("SandtrapReload");
        if (reloadCommand != null) {
            reloadCommand.setExecutor((sender, command, label, args) -> {
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "SandTrap config reloaded.");
                return true;
            });
        }
    }
}
