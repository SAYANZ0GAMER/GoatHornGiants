package com.example.goathorngiants;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class TransformationService {

    private final GoatHornGiantsPlugin plugin;
    private final GiantManager giants;

    /** Zombies already scheduled for transformation (prevents double transformation). */
    private final Set<UUID> pending = new HashSet<>();

    public TransformationService(GoatHornGiantsPlugin plugin, GiantManager giants) {
        this.plugin = plugin;
        this.giants = giants;
    }

    /** Selects the zombies in the player's chunk now and schedules their transformation. */
    public void schedule(Player creator) {
        Chunk chunk = creator.getLocation().getChunk();
        List<Zombie> selected = new ArrayList<>();

        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() != EntityType.ZOMBIE) { // plain zombies only (no husk/drowned/villager/piglin)
                continue;
            }
            Zombie zombie = (Zombie) entity;
            if (!isEligible(zombie)) {
                continue;
            }
            Location loc = zombie.getLocation();
            if ((loc.getBlockX() >> 4) != chunk.getX() || (loc.getBlockZ() >> 4) != chunk.getZ()) {
                continue; // strictly inside the player's chunk
            }
            if (!pending.add(zombie.getUniqueId())) {
                continue; // already scheduled by an earlier horn use
            }
            selected.add(zombie);
        }

        if (selected.isEmpty()) {
            return;
        }

        UUID creatorId = creator.getUniqueId();
        long delay = Math.max(1L, plugin.getConfig().getLong("transformation-delay-ticks", 80L));
        Deque<Zombie> queue = new ArrayDeque<>(selected);

        // Runs on the main thread after exactly `delay` ticks.
        Bukkit.getScheduler().runTaskLater(plugin, () -> drain(creatorId, queue), delay);
    }

    private boolean isEligible(Zombie zombie) {
        return zombie.isValid() && !zombie.isDead() && zombie.customName() == null;
    }

    /** Processes up to batch-size zombies; the rest follow on the next tick(s). */
    private void drain(UUID creatorId, Deque<Zombie> queue) {
        int batch = Math.max(1, plugin.getConfig().getInt("batch-size", 8));
        int processed = 0;

        while (!queue.isEmpty() && processed < batch) {
            Zombie zombie = queue.poll();
            processed++;
            try {
                transform(creatorId, zombie);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to transform a zombie", ex);
            } finally {
                pending.remove(zombie.getUniqueId());
            }
        }

        if (!queue.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> drain(creatorId, queue), 1L);
        }
    }

    private void transform(UUID creatorId, Zombie zombie) {
        // Re-check everything: the world may have changed during the 4 seconds.
        if (!zombie.isValid() || zombie.isDead()) {
            return;
        }
        if (zombie.customName() != null) { // name-tagged meanwhile -> never transform
            return;
        }

        Location loc = zombie.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }

        Giant giant;
        try {
            giant = world.spawn(loc, Giant.class, g -> giants.initGiant(g, creatorId, loc));
        } catch (RuntimeException ex) {
            return; // spawn blocked (e.g. by another plugin): leave the zombie untouched
        }
        if (giant == null) {
            return;
        }

        zombie.remove();
        // Visual + sound only: no damage, no fire, no mob conversions.
        world.strikeLightningEffect(loc);
    }
}
