package io.github.jochyoua.sandtrap.utilities;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ArmorWeightUtil {

    private ArmorWeightUtil() {
        throw new UnsupportedOperationException("ArmorWeightUtil is a utility class and cannot be instantiated.");
    }

    public static int getTotalWeight(Player player, FileConfiguration config) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        double totalWeight = 0.0;
        boolean hasArmor = false;

        for (ItemStack armor : armorContents) {
            if (isValidArmor(armor)) {
                hasArmor = true;
                totalWeight += getWeightForItem(armor.getType(), config);
            }
        }

        if (!hasArmor) {
            totalWeight = getDefaultWeight(config);
        }

        double roundedWeight = Math.round(totalWeight * 2.0) / 2.0;

        return (int) Math.floor(roundedWeight);
    }


    private static boolean isValidArmor(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }


    private static double getWeightForItem(Material material, FileConfiguration config) {
        String typeName = material.name();

        if (typeName.contains("LEATHER")) {
            return config.getDouble("armorWeights.LEATHER", 0.0);
        } else if (typeName.contains("CHAINMAIL")) {
            return config.getDouble("armorWeights.CHAINMAIL", 0.0);
        } else if (typeName.contains("GOLDEN")) {
            return config.getDouble("armorWeights.GOLD", 0.0);
        } else if (typeName.contains("IRON")) {
            return config.getDouble("armorWeights.IRON", 0.0);
        } else if (typeName.contains("DIAMOND")) {
            return config.getDouble("armorWeights.DIAMOND", 0.0);
        } else if (typeName.contains("NETHERITE")) {
            return config.getDouble("armorWeights.NETHERITE", 0.0);
        }

        return 0.0;
    }

    private static double getDefaultWeight(FileConfiguration config) {
        return config.getDouble("armorWeights.NONE", 0.2);
    }
}
