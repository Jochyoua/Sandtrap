package io.github.jochyoua.sandtrap.listeners;

import io.github.jochyoua.sandtrap.SandTrap;
import io.github.jochyoua.sandtrap.utilities.ArmorWeightUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlayerMovementListener implements Listener {
    private final SandTrap plugin;
    private final Map<UUID, Long> lastTremorMessage = new HashMap<>();

    public PlayerMovementListener(SandTrap plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!hasPlayerChangedBlock(event)) {
            return;
        }
        Player player = event.getPlayer();


        int depth = Math.max(ArmorWeightUtil.getTotalWeight(player, plugin.getConfig()), plugin.getConfig().getInt("minimumDepth", 1));


        for (int i = 1; i <= depth; i++) {
            Block blockTo = event.getTo().clone().subtract(0, i, 0).getBlock();
            Block blockFrom = event.getFrom().clone().subtract(0, i, 0).getBlock();

            boolean toValid = checkAndUpdate(blockTo);
            boolean fromValid = checkAndUpdate(blockFrom);

            if (!toValid && !fromValid) {
                break;
            }

            triggerEffects(player);
        }

    }

    private void triggerEffects(Player player) {
        long now = System.currentTimeMillis();
        String message = plugin.getConfig().getString("message", "&cYou feel the ground tremor beneath your feet.");

        if ((message != null && !message.isEmpty()) && (!lastTremorMessage.containsKey(player.getUniqueId()) || now - lastTremorMessage.get(player.getUniqueId()) > 3000)) {
            lastTremorMessage.put(player.getUniqueId(), now);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }

        if (plugin.getConfig().getBoolean("debug", false)) {
            long totalWeight = ArmorWeightUtil.getTotalWeight(player, plugin.getConfig());
            plugin.getLogger().info(String.format("Player %s triggered a trap! Total weight was %d.", player.getName(), totalWeight));
        }


        if (plugin.getConfig().getBoolean("freezePlayer")) {
            String freezeCommand = plugin.getConfig().getString("freezePlayerCommand");
            if (freezeCommand != null && !freezeCommand.isEmpty()) {
                freezeCommand = String.format(freezeCommand, player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), freezeCommand);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if(plugin.getConfig().getBoolean("metrics")) {
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
                    if(plugin.getConfig().getBoolean("metrics")) {
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
