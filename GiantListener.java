package com.example.goathorngiants;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import java.util.UUID;

public final class GiantListener implements Listener {

    private final GoatHornGiantsPlugin plugin;
    private final GiantManager manager;

    public GiantListener(GoatHornGiantsPlugin plugin, GiantManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    // ---- tracking (fires on spawn AND when a chunk with a Giant loads)

    @EventHandler
    public void onAdd(EntityAddToWorldEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Giant giant && manager.isPluginGiant(giant)) {
            manager.track(giant);
        }
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity() instanceof Giant) {
            manager.untrack(event.getEntity().getUniqueId());
        }
    }

    // ---- protection enforcement (defence in depth: our AI already skips protected creators)

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Giant giant
                && event.getTarget() instanceof Player player
                && manager.isPluginGiant(giant)
                && manager.isProtected(giant, player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGiantHitsPlayer(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Giant giant
                && event.getEntity() instanceof Player player
                && manager.isPluginGiant(giant)
                && manager.isProtected(giant, player)) {
            event.setCancelled(true);
        }
    }

    // ---- creator hurts THEIR Giant -> remove protection for that Giant only

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerHitsGiant(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Giant giant) || !manager.isPluginGiant(giant)) {
            return;
        }

        Player attacker = null;
        Entity damager = event.getDamager();
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile projectile
                && plugin.getConfig().getBoolean("protection.projectiles-remove-protection", true)
                && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) {
            return;
        }

        UUID creator = manager.getCreator(giant);
        if (creator == null || !creator.equals(attacker.getUniqueId())) {
            return; // someone else hit it: no effect on protection
        }
        if (event.getFinalDamage() <= 0.0) {
            return; // no actual damage dealt
        }

        if (manager.removeProtection(giant)) {
            giant.setTarget(attacker);
            if (plugin.getConfig().getBoolean("protection.notify-creator", true)) {
                attacker.sendMessage(Component.text(
                        "You attacked your Giant - it no longer protects you!", NamedTextColor.RED));
            }
        }
    }

    // ---- Giants are 12 blocks tall; avoid dying inside caves/buildings

    @EventHandler(ignoreCancelled = true)
    public void onSuffocate(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION
                && plugin.getConfig().getBoolean("giant.suffocation-immune", true)
                && manager.isPluginGiant(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
