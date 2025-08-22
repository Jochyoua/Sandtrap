package io.github.jochyoua.sandtrap.listeners;

import io.github.jochyoua.sandtrap.SandTrap;
import io.github.jochyoua.sandtrap.utilities.ArmorWeightUtil;
import io.github.jochyoua.sandtrap.utilities.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class PlayerMovementListener implements Listener {
    private final SandTrap plugin;
    private final Map<UUID, Long> lastTremorMessage = new HashMap<>();

    public PlayerMovementListener(SandTrap plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!hasPlayerChangedBlock(event) || !shouldTriggerTrap(player)) {
            return;
        }


        int depth = calculateDepth(player);


        for (int i = 1; i <= depth; i++) {
            Block blockTo = event.getTo().clone().subtract(0, i, 0).getBlock();
            Block blockFrom = event.getFrom().clone().subtract(0, i, 0).getBlock();

            boolean toValid = checkAndUpdate(blockTo);
            boolean fromValid = checkAndUpdate(blockFrom);

            if (!toValid && !fromValid) {
                continue;
            }

            triggerEffects(player);
        }

    }

    private boolean shouldTriggerTrap(Player player) {
        FileConfiguration config = plugin.getConfig();
        List<String> allowedGamemodes = config.getStringList("allowedGamemodes");

        if (config.getBoolean("flightGroundCheck", true)) {
            boolean flying   = player.isFlying();
            boolean onGround = player.isOnGround();

            if (flying || !onGround) {
                DebugLogger.log(plugin, () ->
                        String.format(
                                "[FlightCheck] Skipping %s: flying=%b, onGround=%b",
                                player.getName(), flying, onGround
                        )
                );
                return false;
            }
        }

        if (player.hasPermission("sandtrap.immune")) {
            DebugLogger.log(plugin, () -> "Trap skipped for " + player.getName() + " due to immunity permission: sandtrap.immune");
            return false;
        }

        boolean isGamemodeDisallowed = allowedGamemodes.stream().noneMatch(mode -> mode.equalsIgnoreCase(player.getGameMode().name()));
        if (isGamemodeDisallowed) {
            DebugLogger.log(plugin, () -> (String.format("Trap ignored for %s due to game mode '%s'. Allowed modes: %s", player.getName(), player.getGameMode(), String.join(", ", allowedGamemodes))));
            return false;
        }

        if (config.getBoolean("biomeFilter.enabled", false)) {
            String biome = player.getLocation().getBlock().getBiome().name();
            List<String> list = config.getStringList("biomeFilter.list");
            String mode = config.getString("biomeFilter.mode", "whitelist").trim().toLowerCase();

            boolean listed = false;
            for (String b : list) {
                if (b.equalsIgnoreCase(biome)) {
                    listed = true;
                    break;
                }
            }

            boolean allowed = ("whitelist".equals(mode)) ? listed : !"blacklist".equals(mode) || !listed;

            if (!allowed) {
                if (config.getBoolean("debug", false)) {
                    DebugLogger.log(plugin, () -> String.format("Trap ignored for %s in biome '%s'. Mode: %s, List: [%s]", player.getName(), biome, mode, String.join(", ", list)));
                }
                return false;
            }
        }

        return true;
    }

    private int calculateDepth(Player player) {
        FileConfiguration config = plugin.getConfig();
        int minimumDepth = config.getInt("minimumDepth", 1);
        int rawDepth = Math.max(ArmorWeightUtil.getTotalWeight(player, config), minimumDepth);

        String mode = config.getString("sneak-behavior.mode", "negate");
        int negateCount = config.getInt("sneak-behavior.negate-count", 3);

        if (player.isSneaking()) {
            switch (mode) {
                case "negate":
                    rawDepth -= negateCount;
                    break;
                case "disable":
                    rawDepth = minimumDepth;
                    break;
                case "ignore":
                    break;
                default:
                    if (plugin.getLogger().isLoggable(Level.WARNING)) {
                        plugin.getLogger().warning("Unknown sneak-behavior.mode: " + mode);
                    }
            }
        }

        return Math.max(rawDepth, minimumDepth);
    }


    private void triggerEffects(Player player) {
        long now = System.currentTimeMillis();
        String message = plugin.getConfig().getString("message", "&cYou feel the ground tremor beneath your feet.");

        if ((message != null && !message.isEmpty()) && (!lastTremorMessage.containsKey(player.getUniqueId()) || now - lastTremorMessage.get(player.getUniqueId()) > 3000)) {
            lastTremorMessage.put(player.getUniqueId(), now);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        DebugLogger.log(plugin, () -> String.format("Player %s triggered a trap! Total weight was %d.", player.getName(), ArmorWeightUtil.getTotalWeight(player, plugin.getConfig())));


        if (plugin.getConfig().getBoolean("freezePlayer")) {
            String freezeCommand = plugin.getConfig().getString("freezePlayerCommand");
            if (freezeCommand != null && !freezeCommand.isEmpty()) {
                freezeCommand = String.format(freezeCommand, player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), freezeCommand);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getConfig().getBoolean("metrics.enabled")) {
                plugin.getConfig().set("metrics.trapTriggers", plugin.getConfig().getInt("metrics.trapTriggers", 0) + 1);
                plugin.saveConfig();
            }
        });

    }


    private boolean checkAndUpdate(Block blockBelow) {
        if (!blockBelow.isEmpty() && plugin.getConfig().getStringList("fallingBlockWhitelist").contains(blockBelow.getType().name())) {
            Block blockUnderneath = blockBelow.getRelative(BlockFace.DOWN);

            Material underneathType = blockUnderneath.getType();

            boolean isTriggerableSurface = underneathType == Material.AIR || underneathType == Material.WATER;


            if (isTriggerableSurface) {
                BlockData originalData = blockUnderneath.getBlockData();
                blockUnderneath.setType(Material.BARRIER, true);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (blockUnderneath.getType() == Material.BARRIER) {
                        blockUnderneath.setBlockData(originalData, true);
                    }
                }, 1L);

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    if (plugin.getConfig().getBoolean("metrics.enabled")) {
                        String typeName = blockBelow.getType().name();
                        String path = "metrics.blockTypeStats." + typeName;
                        int currentCount = plugin.getConfig().getInt(path, 0);
                        plugin.getConfig().set(path, currentCount + 1);
                        plugin.saveConfig();
                    }
                });


                return true;
            }
        }
        return false;
    }

    private boolean hasPlayerChangedBlock(PlayerMoveEvent event) {
        return event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockY() != event.getTo().getBlockY() || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
    }


}
