package me.bear.moreLoomLayers.commands;


import java.util.List;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class BannerLayerViewerCommand implements CommandExecutor {

    private JavaPlugin plugin;
    private final List<Material> bannerTypes = List.of(
            Material.BLACK_BANNER,
            Material.BLUE_BANNER,
            Material.BROWN_BANNER,
            Material.CYAN_BANNER,
            Material.GRAY_BANNER,
            Material.GREEN_BANNER,
            Material.LIGHT_BLUE_BANNER,
            Material.LIGHT_GRAY_BANNER,
            Material.LIME_BANNER,
            Material.MAGENTA_BANNER,
            Material.ORANGE_BANNER,
            Material.PINK_BANNER,
            Material.PURPLE_BANNER,
            Material.RED_BANNER,
            Material.WHITE_BANNER,
            Material.YELLOW_BANNER
    );

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if(!(sender instanceof Player player)) return false;
        player.getInventory().getItemInMainHand();
        if (bannerTypes.contains(player.getInventory().getItemInMainHand().getType())) {
            openBannerInv(player, player.getInventory().getItemInMainHand());
            return false;
        }
        return true;
    }

    private void openBannerInv(Player p, ItemStack item) {
        Inventory inv = Bukkit.createInventory(p, 18, Component.text("Banner Layers"));
        BannerMeta meta = (BannerMeta) item.getItemMeta();
        for (int i = 0; i < meta.getPatterns().size(); i++) {
            Pattern pattern = meta.getPatterns().get(i);
            ItemStack patternItem = new ItemStack(item.getType());
            BannerMeta patternMeta = (BannerMeta) patternItem.getItemMeta();
            patternMeta.addPattern(pattern);
            patternItem.setItemMeta(patternMeta);
            inv.setItem(i, patternItem);
        }
        p.openInventory(inv);
    }
}