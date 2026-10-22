package com.example.goathorngiants;

import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;

/** Detects the exact goat horn instrument variant. */
public final class HornUtil {

    private static final String CALL_NAMESPACE = NamespacedKey.MINECRAFT;
    private static final String CALL_KEY = "call_goat_horn";

    private HornUtil() {}

    /**
     * @return true only if the item is a goat horn whose instrument is minecraft:call_goat_horn.
     *         Every other variant (ponder, sing, seek, feel, admire, yearn, dream, custom) and
     *         horns without an instrument return false.
     */
    @SuppressWarnings("deprecation")
    public static boolean isCallGoatHorn(ItemStack item) {
        if (item == null || item.getType() != Material.GOAT_HORN) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof MusicInstrumentMeta instrumentMeta)) {
            return false;
        }
        MusicInstrument instrument = instrumentMeta.getInstrument();
        if (instrument == null) {
            return false;
        }
        NamespacedKey key = instrument.getKey();
        return key != null
                && CALL_NAMESPACE.equals(key.getNamespace())
                && CALL_KEY.equals(key.getKey());
    }
}
