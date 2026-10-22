package com.example.goathorngiants;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

public final class HornListener implements Listener {

    private final TransformationService service;

    public HornListener(TransformationService service) {
        this.service = service;
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Another plugin denied the item use -> the horn will not be played.
        if (event.useItemInHand() == Event.Result.DENY) {
            return;
        }

        ItemStack item = event.getItem(); // the item of the hand this event is for
        if (!HornUtil.isCallGoatHorn(item)) {
            return; // not a goat horn, or not the "Call" variant: ignore completely
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("goathorngiants.use")) {
            return;
        }

        // Right-clicking e.g. a chest/door opens it instead of playing the horn.
        if (action == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
                return;
            }
        }

        // The horn cannot be played while on cooldown.
        if (player.getCooldown(Material.GOAT_HORN) > 0) {
            return;
        }

        service.schedule(player);
    }
}
