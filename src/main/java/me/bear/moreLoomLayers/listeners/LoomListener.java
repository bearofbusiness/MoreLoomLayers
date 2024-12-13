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
import java.util.List;

public class LoomListener implements Listener {

    private final MoreLoomLayers plugin;
    private final NamespacedKey patternDataKey;

    // Loom inventory slots as per current version:
    // Slot 0: Banner input
    // Slot 1: Dye input
    // Slot 2: Pattern input (like a banner pattern item)
    // Slot 3: Output slot

    public LoomListener(MoreLoomLayers plugin, NamespacedKey patternDataKey) {
        this.plugin = plugin;
        this.patternDataKey = patternDataKey;
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

//        if(rawSlot > 3) {
//            return;
//        }

//        plugin.getLogger().info("\n\nLoom click event: " + event.getAction());
//        plugin.getLogger().info("Raw slot: " + event.getRawSlot());
//        plugin.getLogger().info("Slot type: " + event.getSlotType());
//        plugin.getLogger().info("Slot: " + event.getSlot());
//        plugin.getLogger().info("Current item: " + event.getCurrentItem());
//        plugin.getLogger().info("Cursor item: " + event.getCursor());
//        plugin.getLogger().info("Clicked item: " + event.getClickedInventory()+ "\n\n");

        // Output slot in the loom is typically slot 3 for 1.20
        // Check if the click is on the output slot
        if (rawSlot == 3 && event.getAction() == InventoryAction.PICKUP_ALL) {
            // The player is attempting to take the final patterned banner out.
            ItemStack output = loomInv.getItem(3);
            if (output == null || output.getType() == Material.AIR) {
                return;
            }

            // If this banner was manipulated, let's restore its patterns.
            restoreExtraPatternsIfAny(output);
        } else {
            // Before the player takes the item, we might need to trick the loom.
            // The loom updates patterns internally when items in slot 0,1,2 change.
            // There's no direct "pattern add" event, so we must rely on a trick:
            // Whenever a pattern is generated in slot 3, we quickly ensure that the underlying banner
            // does not exceed the allowed patterns visually, but we store the full pattern list hidden.
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Check if we have an output banner
                ItemStack out = loomInv.getItem(0);
//                plugin.getLogger().info("Output: " + out);
//                if(out != null)
//                    plugin.getLogger().info("Output type: " + out.getType());
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

            // If this banner was manipulated, let's restore its patterns.
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
        // If the loom is closed and player left the manipulated banner inside (which shouldn’t happen normally),
        // we don’t need to do anything. The final taking of banner is what triggers restoration.
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
            // We only show up to 5 patterns, and store the rest secretly.

            // Store all patterns in PDC
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

        // Combine stored patterns + current patterns. The current patterns are presumably the first 5 patterns + last added pattern.
        // The "current" should now have the actual last pattern the loom tried to apply.
        // Our stored patterns are the full set that we previously had.

        // Strategy: The first 5 patterns in "current" are the same as in stored. The last added pattern is the new one we want to add.
        // So we:
        // 1. Take the stored patterns (which represent the original full set).
        // 2. Replace the first 5 with the current first 5 (just to be safe, though they should match).
        // 3. Append the current patterns beyond the 5th as new ones.

        // However, if current has more than 5 patterns (which it might if the loom succeeded in adding one more),
        // we append everything beyond 5th from current.

        // Maximum of 16 patterns total:
        int MAX_PATTERNS = 16;
        List<Pattern> finalPatterns = new ArrayList<>(storedPatterns);

        // Ensure storedPatterns at least 5 patterns if we had previously truncated them:
        // Actually, we truncated the displayed list to 5 previously, so storedPatterns should have been the full original set (more than 5).
        // We can trust that storedPatterns is the full original set.

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
        // Clear the PDC as we're done
        pdc.remove(patternDataKey);
        banner.setItemMeta(meta);
    }


    private void addLoreForExtraPatterns(BannerMeta meta) {
        List<Pattern> patterns = meta.getPatterns();
        if (patterns.size() > 6) {
            // We have more than 6 patterns. Add a lore to show the other patterns.
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
     * since we have limited datatype for persistentdata, we use a byte array.
     * we'll store patterns as:
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


    private String formatPatternName(String string) {
        String[] words = string.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1).toLowerCase()).append(" ");
        }
        return sb.toString().trim();
    }
    private static String getPatternRealName(PatternType type) {
        switch (type) {
            case BASE:
                return "Base"; // Bottom Stripe
            case STRIPE_DOWNRIGHT:
                return "Bend"; // Down Right Stripe
            case STRIPE_DOWNLEFT:
                return "Bend Sinister"; // Down Left Stripe
            case GRADIENT_UP:
                return "Base Gradient"; // Gradient upside-down
            case SQUARE_BOTTOM_LEFT:
                return "Base Dexter Canton"; // Bottom Left Corner
            case SQUARE_BOTTOM_RIGHT:
                return "Base Sinister Canton"; // Bottom Right Corner
            case BORDER:
                return "Bordure"; // Border
            case CURLY_BORDER:
                return "Bordure Indented"; // Curly Border
            case TRIANGLES_BOTTOM:
                return "Base Indented"; // Bottom Triangle Sawtooth
            case STRIPE_TOP:
                return "Chief"; // Top Stripe
            case STRAIGHT_CROSS:
                return "Cross"; // Square Cross
            case SQUARE_TOP_LEFT:
                return "Chief Dexter Canton"; // Top Left Corner
            case SQUARE_TOP_RIGHT:
                return "Chief Sinister Canton"; // Top Right Corner
            case TRIANGLE_BOTTOM:
                return "Chevron"; // Bottom Triangle
            case CREEPER:
                return "Creeper Charge"; // Creeper
            case BRICKS:
                return "Field Masoned"; // Brick
            case FLOWER:
                return "Flower Charge"; // Flower
            case STRIPE_MIDDLE:
                return "Fess"; // Horizontal Middle Stripe
            case GRADIENT:
                return "Gradient"; // Gradient
            case GLOBE:
                return "Globe"; // Globe
            case TRIANGLE_TOP:
                return "Inverted Chevron"; // Top Triangle
            case STRIPE_LEFT:
                return "Pale Dexter"; // Left Stripe
            case STRIPE_RIGHT:
                return "Pale Sinister"; // Right Stripe
            case STRIPE_CENTER:
                return "Pale"; // Vertical Center Stripe
            case STRIPE_SMALL:
                return "Paly"; // Vertical Small Stripes
            case DIAGONAL_LEFT_MIRROR:
                return "Per Bend Sinister"; // Left of Diagonal
            case DIAGONAL_RIGHT_MIRROR:
                return "Per Bend"; // Right of upside-down Diagonal
            case DIAGONAL_LEFT:
                return "Per Bend Inverted"; // Left of upside-down Diagonal
            case DIAGONAL_RIGHT:
                return "Per Bend Sinister Inverted"; // Right of Diagonal
            case HALF_VERTICAL:
                return "Per Pale"; // Left Vertical Half
            case HALF_VERTICAL_MIRROR:
                return "Per Pale Inverted"; // Right Vertical Half
            case HALF_HORIZONTAL:
                return "Per Fess"; // Top Horizontal Half
            case HALF_HORIZONTAL_MIRROR:
                return "Per Fess Inverted"; // Bottom Horizontal Half
            case CIRCLE_MIDDLE:
                return "Roundel"; // Middle Circle
            case RHOMBUS_MIDDLE:
                return "Lozenge"; // Middle Rhombus
            case SKULL:
                return "Skull Charge"; // Skull
            case CROSS:
                return "Saltire"; // Diagonal Cross
            case PIGLIN:
                return "Snout"; // Piglin
            case MOJANG:
                return "Thing"; // Mojang
        }
        return "Unknown Contact Developer";
    }
}