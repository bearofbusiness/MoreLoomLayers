package me.bear.moreLoomLayers;

import lombok.Getter;
import me.bear.moreLoomLayers.listeners.LoomListener;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;


public final class MoreLoomLayers extends JavaPlugin {

    @Getter
    private static MoreLoomLayers instance;
    @Getter
    private NamespacedKey patternDataKey;

    @Override
    public void onEnable() {
        instance = this;
        patternDataKey = new NamespacedKey(this, "extended_patterns");
        getServer().getPluginManager().registerEvents(new LoomListener(this, patternDataKey), this);
        getLogger().info("BannerExtendedPatternsPlugin enabled. Allowing up to 16 banner patterns.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BannerExtendedPatternsPlugin disabled.");
    }

}
