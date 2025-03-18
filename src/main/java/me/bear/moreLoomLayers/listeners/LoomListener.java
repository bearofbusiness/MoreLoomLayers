package me.bear.moreLoomLayers.listeners;

import me.bear.moreLoomLayers.MoreLoomLayers;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.block.banner.PatternType.*;

import static org.bukkit.block.banner.PatternType.*;

public class LoomListener implements Listener {

    private final MoreLoomLayers plugin;
    private final NamespacedKey patternDataKey;
    private Map<PatternType, String> patterns = new HashMap<>();
    // Loom inventory slots as per current version:
    // Slot 0: Banner input
    // Slot 1: Dye input
    // Slot 2: Pattern input (like a banner pattern item)
    // Slot 3: Output slot

    public LoomListener(MoreLoomLayers plugin, NamespacedKey patternDataKey) {
        this.plugin = plugin;
        this.patternDataKey = patternDataKey;

        patterns.put(BASE, "Base");
        patterns.put(STRIPE_DOWNRIGHT, "Bend");
        patterns.put(STRIPE_DOWNLEFT, "Bend Sinister");
        patterns.put(GRADIENT_UP, "Base Gradient");
        patterns.put(SQUARE_BOTTOM_LEFT, "Base Dexter Canton");
        patterns.put(SQUARE_BOTTOM_RIGHT, "Base Sinister Canton");
        patterns.put(BORDER, "Bordure");
        patterns.put(CURLY_BORDER, "Bordure Indented");
        patterns.put(TRIANGLES_BOTTOM, "Base Indented");
        patterns.put(STRIPE_TOP, "Chief");
        patterns.put(STRAIGHT_CROSS, "Cross");
        patterns.put(SQUARE_TOP_LEFT, "Chief Dexter Canton");
        patterns.put(SQUARE_TOP_RIGHT, "Chief Sinister Canton");
        patterns.put(TRIANGLE_BOTTOM, "Chevron");
        patterns.put(CREEPER, "Creeper Charge");
        patterns.put(BRICKS, "Field Masoned");
        patterns.put(FLOWER, "Flower Charge");
        patterns.put(STRIPE_MIDDLE, "Fess");
        patterns.put(GRADIENT, "Gradient");
        patterns.put(GLOBE, "Globe");
        patterns.put(TRIANGLE_TOP, "Inverted Chevron");
        patterns.put(STRIPE_LEFT, "Pale Dexter");
        patterns.put(STRIPE_RIGHT, "Pale Sinister");
        patterns.put(STRIPE_CENTER, "Pale");
        patterns.put(DIAGONAL_LEFT, "Per Bend Inverted");
        patterns.put(DIAGONAL_RIGHT, "Per Bend Sinister Inverted");
        patterns.put(HALF_VERTICAL, "Per Pale");
        patterns.put(HALF_HORIZONTAL, "Per Fess");
        patterns.put(SKULL, "Skull Charge");
        patterns.put(CROSS, "Saltire");
        patterns.put(PIGLIN, "Snout");
        patterns.put(MOJANG, "Thing");
        patterns.put(TRIANGLES_TOP, "Chief Indented");
        patterns.put(DIAGONAL_UP_RIGHT, "Per Bend Sinister");
        patterns.put(DIAGONAL_UP_LEFT, "Per Bend");
        patterns.put(CIRCLE, "Roundel");
        patterns.put(RHOMBUS, "Lozenge");
        patterns.put(HALF_VERTICAL_RIGHT, "Per Pale Inverted");
        patterns.put(HALF_HORIZONTAL_BOTTOM, "Per Fess Inverted");
    }

    @EventHandler
    public void onLoomClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getType() == InventoryType.LOOM)) {
            return; // Not a loom
        }

        Inventory loomInv = event.getView().getTopInventory();
        if (loomInv.getType() != InventoryType.LOOM) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot == 3 && event.getAction() == InventoryAction.PICKUP_ALL) {
            ItemStack output = loomInv.getItem(3);
            if (output == null || output.getType() == Material.AIR) {
                return;
            }
            restoreExtraPatternsIfAny(output);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                ItemStack out = loomInv.getItem(0);
                if (out != null && out.getType().toString().endsWith("BANNER")) {
                    // Attempt to increase patterns if possible
                    handleBannerDuringLoom(out);
                }
            }, 1L);
        }
        if (rawSlot == 0 && isRemovingBannerFromSlot(event)) {
            // The player is attempting to take the banner out.
            ItemStack banner = loomInv.getItem(0);
            if (banner == null || banner.getType() == Material.AIR) {
                return;
            }
            restoreExtraPatternsIfAny(banner);
        }
    }

    private static boolean isRemovingBannerFromSlot(InventoryClickEvent event) {
        return switch (event.getAction()) {
            case PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, SWAP_WITH_CURSOR, DROP_ALL_SLOT, DROP_ONE_SLOT, MOVE_TO_OTHER_INVENTORY ->
                    true;
            default -> false;
        };
    }

    @EventHandler
    public void onLoomClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getType() == InventoryType.LOOM)) {
            return; // Not a loom
        }

        Inventory loomInv = event.getView().getTopInventory();

        if (loomInv.getType() != InventoryType.LOOM) {
            return;
        }

        if(loomInv.getItem(0) == null) {
            return;
        }

        ItemStack banner = loomInv.getItem(0);
        if (banner == null || banner.getType() == Material.AIR) {
            return;
        }

        restoreExtraPatternsIfAny(banner);
    }

    /**
     * This method checks the banner in the output slot and tries to apply our pattern-extension trick:
     * If the banner has more than 6 patterns, we store them and revert the displayed patterns to 5,
     * waiting for the next pattern to be added. This way, the loom still thinks it can add more patterns.
     */
    private void handleBannerDuringLoom(ItemStack banner) {
        if (!Tag.BANNERS.isTagged(banner.getType())) return;

        BannerMeta meta = (BannerMeta) banner.getItemMeta();
        List<Pattern> patterns = new ArrayList<>(meta.getPatterns());
        if (patterns.size() > 5) {
            storePatternsInPDC(meta, patterns);

            // Show only first 5 patterns to the player/loom
            List<Pattern> displayed = patterns.subList(0, 5);
            meta.setPatterns(displayed);
            banner.setItemMeta(meta);
        }
    }

    /**
     * When the player takes the output banner, we restore all the stored patterns and add the new one,
     * up to a max of 16 patterns.
     */
    private void restoreExtraPatternsIfAny(ItemStack banner) {
        if (!Tag.BANNERS.isTagged(banner.getType())) return;
        ItemMeta im = banner.getItemMeta();
        if (!(im instanceof BannerMeta meta)) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        byte[] data = pdc.get(patternDataKey, PersistentDataType.BYTE_ARRAY);
        if (data == null) return;

        List<Pattern> storedPatterns = decodePatterns(data);
        // The banner currently has up to 5 visible patterns
        List<Pattern> current = meta.getPatterns();

        int MAX_PATTERNS = 16;
        List<Pattern> finalPatterns = new ArrayList<>(storedPatterns);
        // Now, append any additional patterns that loom might have added:
        if (current.size() > 5) {
            for (int i = 5; i < current.size(); i++) {
                finalPatterns.add(current.get(i));
            }
        }

        // If finalPatterns > MAX_PATTERNS, just truncate to MAX_PATTERNS:
        if (finalPatterns.size() > MAX_PATTERNS) {
            finalPatterns = finalPatterns.subList(0, MAX_PATTERNS);
        }

        meta.setPatterns(finalPatterns);
        addLoreForExtraPatterns(meta);
        // clear pdc
        pdc.remove(patternDataKey);
        banner.setItemMeta(meta);
    }

    /**
     * Adds lore to the banner if there are more than 6 patterns.
     * @param meta
     */
    private void addLoreForExtraPatterns(BannerMeta meta) {
        List<Pattern> patterns = meta.getPatterns();
        if (patterns.size() > 6) {
            List<Component> lore = new ArrayList<>();
            for (int i = 6; i < patterns.size(); i++) {
                Pattern p = patterns.get(i);
                //selection section symbol

                lore.add(Component.text("§7" + formatPatternName(p.getColor().name()) + " " + getPatternRealName(p.getPattern()) ));
            }
            meta.lore(lore);
        }
    }

    /**
     * store patterns as a custom byte array in the banner's pdc for retrieval later.
     * we have to encode the patterns somehow. a simple encoding:
     * [int length][for each pattern: byte color, short pattern type id]
     * <p>
     * since there is a limited datatype for persistentdata
     * using a byte array stored patterns as:
     * - first 4 bytes: int count
     * - then for each pattern:
     *   1 byte: dye color ordinal
     *   2 bytes: pattern type ordinal (short)
     */
    private void storePatternsInPDC(BannerMeta meta, List<Pattern> patterns) {
        byte[] data = encodePatterns(patterns);
        meta.getPersistentDataContainer().set(patternDataKey, PersistentDataType.BYTE_ARRAY, data);
    }

    private byte[] encodePatterns(List<Pattern> patterns) {
        // Patterns can be up to 16 or so, an int count and for each pattern 3 bytes (1 for color, 2 for pattern ID)
        int count = patterns.size();
        byte[] data = new byte[4 + (3 * count)];
        // int count
        data[0] = (byte)(count >> 24);
        data[1] = (byte)(count >> 16);
        data[2] = (byte)(count >> 8);
        data[3] = (byte)(count);

        int index = 4;
        for (Pattern p : patterns) {
            // DyeColor ordinal fits in a byte since there aren't many DyeColors:
            byte colorOrdinal = (byte)p.getColor().ordinal();
            // PatternType: We can store as a short since pattern types are enumerations with small ordinals.
            short patternOrdinal = (short)p.getPattern().ordinal();

            data[index++] = colorOrdinal;
            data[index++] = (byte)(0);
            data[index++] = (byte)patternOrdinal;
        }

        return data;
    }
    /**
     * Decode the byte array back into a list of patterns.
     * @param data
     * @return
     */
    private List<Pattern> decodePatterns(byte[] data) {
        if (data.length < 4) return new ArrayList<>();
        int count = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);

        List<Pattern> patterns = new ArrayList<>(count);
        int index = 4;
        for (int i = 0; i < count; i++) {
            if (index + 3 > data.length) break;
            byte colorOrdinal = data[index++];
            short patternOrdinal = (short)(((data[index++] & 0xFF) << 8) | (data[index++] & 0xFF));

            DyeColor color = DyeColor.values()[colorOrdinal];
            PatternType patternType = PatternType.values()[patternOrdinal];
            patterns.add(new Pattern(color, patternType));
        }
        return patterns;
    }

    /**
     * Format the pattern name to be more readable.
     * @param string
     * @return
     */
    private String formatPatternName(String string) {
        String[] words = string.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
    /**
     * Get the real name of the pattern.
     * @param type
     * @return
     */
    private String getPatternRealName(PatternType type) {
        if (type == null) {
            return "Unknown Contact Developer";
        }
        return patterns.getOrDefault(type, "Unknown Contact Developer");
    }
}