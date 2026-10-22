package com.example.goathorngiants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns all per-Giant state and drives the Giant AI.
 *
 * A vanilla Giant has no AI goals, so one central repeating task (not one per Giant)
 * picks the nearest eligible player, paths towards it and performs melee attacks.
 *
 * Persistent data on every Giant:
 *   creator      (STRING) UUID of the summoner
 *   unprotected  (BYTE)   1 once the creator has damaged THIS Giant
 */
public final class GiantManager {

    private static final double STOP_DISTANCE = 2.6; // horizontal, stop pushing into the target

    private final GoatHornGiantsPlugin plugin;
    private final NamespacedKey creatorKey;
    private final NamespacedKey unprotectedKey;

    private final Map<UUID, Giant> tracked = new HashMap<>();
    private final Map<UUID, Integer> attackCooldowns = new HashMap<>();
    private BukkitTask task;

    public GiantManager(GoatHornGiantsPlugin plugin) {
        this.plugin = plugin;
        this.creatorKey = new NamespacedKey(plugin, "creator");
        this.unprotectedKey = new NamespacedKey(plugin, "unprotected");
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        stop();
        int interval = Math.max(1, plugin.getConfig().getInt("giant.ai-interval-ticks", 5));
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(interval), interval, interval);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void trackLoadedGiants() {
        for (World world : Bukkit.getWorlds()) {
            for (Giant giant : world.getEntitiesByClass(Giant.class)) {
                if (isPluginGiant(giant)) {
                    track(giant);
                }
            }
        }
    }

    // ------------------------------------------------------------------ giant data

    /** Called while the Giant is being spawned (before it is added to the world). */
    public void initGiant(Giant giant, UUID creator, Location origin) {
        PersistentDataContainer pdc = giant.getPersistentDataContainer();
        pdc.set(creatorKey, PersistentDataType.STRING, creator.toString());
        pdc.set(unprotectedKey, PersistentDataType.BYTE, (byte) 0);

        FileConfiguration cfg = plugin.getConfig();
        boolean persistent = cfg.getBoolean("giant.persistent", true);
        giant.setPersistent(persistent);
        giant.setRemoveWhenFarAway(!persistent);
        giant.setCanPickupItems(false);
        giant.setRotation(origin.getYaw(), origin.getPitch());
    }

    public boolean isPluginGiant(Entity entity) {
        return entity instanceof Giant
                && entity.getPersistentDataContainer().has(creatorKey, PersistentDataType.STRING);
    }

    public UUID getCreator(Entity giant) {
        String raw = giant.getPersistentDataContainer().get(creatorKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** True while this specific Giant must not target/attack this player. */
    public boolean isProtected(Giant giant, Player player) {
        UUID creator = getCreator(giant);
        if (creator == null || !creator.equals(player.getUniqueId())) {
            return false;
        }
        Byte flag = giant.getPersistentDataContainer().get(unprotectedKey, PersistentDataType.BYTE);
        return flag == null || flag == 0;
    }

    /** Permanently removes the creator's protection for this one Giant. @return true if it changed. */
    public boolean removeProtection(Giant giant) {
        PersistentDataContainer pdc = giant.getPersistentDataContainer();
        Byte flag = pdc.get(unprotectedKey, PersistentDataType.BYTE);
        if (flag != null && flag == 1) {
            return false;
        }
        pdc.set(unprotectedKey, PersistentDataType.BYTE, (byte) 1);
        return true;
    }

    public void track(Giant giant) {
        tracked.put(giant.getUniqueId(), giant);
    }

    public void untrack(UUID id) {
        tracked.remove(id);
        attackCooldowns.remove(id);
    }

    // ------------------------------------------------------------------ AI

    private void tick(int interval) {
        for (Giant giant : new ArrayList<>(tracked.values())) {
            if (!giant.isValid() || giant.isDead()) {
                untrack(giant.getUniqueId());
                continue;
            }
            try {
                tickGiant(giant, interval);
            } catch (Exception ex) {
                plugin.getLogger().warning("Giant AI error: " + ex);
            }
        }
    }

    private void tickGiant(Giant giant, int interval) {
        FileConfiguration cfg = plugin.getConfig();
        UUID id = giant.getUniqueId();

        int cooldown = attackCooldowns.getOrDefault(id, 0);
        if (cooldown > 0) {
            cooldown = Math.max(0, cooldown - interval);
            attackCooldowns.put(id, cooldown);
        }

        Player target = findNearestTarget(giant);
        if (target == null) {
            if (giant.getTarget() != null) {
                giant.setTarget(null);
            }
            giant.getPathfinder().stopPathfinding();
            return;
        }
        if (!target.equals(giant.getTarget())) {
            giant.setTarget(target);
        }

        Location from = giant.getLocation();
        Location to = target.getLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dy = to.getY() - from.getY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // ---- melee attack
        double reach = cfg.getDouble("attack.reach", 6.0);
        boolean verticalOk = dy > -2.0 && dy < giant.getHeight();
        if (cooldown <= 0 && horizontal <= reach && verticalOk) {
            giant.setRotation((float) Math.toDegrees(Math.atan2(-dx, dz)), 0.0f);
            giant.swingMainHand();
            target.damage(cfg.getDouble("attack.damage", 12.0), giant);
            attackCooldowns.put(id, Math.max(1, cfg.getInt("attack.cooldown-ticks", 20)));
        }

        // ---- chase
        if (horizontal <= STOP_DISTANCE) {
            giant.getPathfinder().stopPathfinding();
            return;
        }
        double speed = cfg.getDouble("giant.movement-speed-multiplier", 1.0);
        boolean hasPath = giant.getPathfinder().moveTo(to, speed);
        if (!hasPath) {
            // Fallback when no navigation path exists (very wide mob, odd terrain): walk straight at the target.
            Vector push = new Vector(dx, 0, dz);
            if (push.lengthSquared() > 1.0E-4) {
                push.normalize().multiply(0.3);
                push.setY(giant.getVelocity().getY());
                if (dy > 0.6 && giant.isOnGround()) {
                    push.setY(0.5);
                }
                giant.setVelocity(push);
            }
        }
    }

    private Player findNearestTarget(Giant giant) {
        FileConfiguration cfg = plugin.getConfig();
        double range = cfg.getDouble("giant.detection-range", 32.0);
        double best = range * range;
        boolean includeNonSurvival = cfg.getBoolean("giant.target-creative-and-spectator", false);
        Location loc = giant.getLocation();

        Player result = null;
        for (Player player : giant.getWorld().getPlayers()) {
            if (!player.isOnline() || player.isDead()) {
                continue;
            }
            GameMode mode = player.getGameMode();
            if (!includeNonSurvival && (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR)) {
                continue;
            }
            if (isProtected(giant, player)) {
                continue; // creator, until they hurt THIS giant
            }
            double distSq = player.getLocation().distanceSquared(loc);
            if (distSq < best) {
                best = distSq;
                result = player;
            }
        }
        return result;
    }
}
