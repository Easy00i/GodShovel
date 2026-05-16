package com.nedevelopment.godshovel;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PowerListeners implements Listener {

    private final Main plugin;
    
    // Cooldown trackers
    private final HashMap<UUID, Long> beamCooldowns = new HashMap<>();
    private final HashMap<UUID, Long> controlCooldowns = new HashMap<>();
    private final int COOLDOWN_SECONDS = 15; // 15 seconds cooldown

    // Prevent stacking powers on the same entity (No glitches)
    private final Set<UUID> activeTargets = new HashSet<>();

    public PowerListeners(Main plugin) {
        this.plugin = plugin;
    }

    // 1. Handle Long-Range Aiming (Air/Block Clicks)
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (player.getInventory().getItemInMainHand() == null || !ItemManager.isGodShovel(player.getInventory().getItemInMainHand())) return;

        Action action = event.getAction();
        
        if (action.isRightClick()) {
            // SHIFT + RIGHT CLICK: Death Beam (RayTrace)
            executeBeamPower(player);
        } else if (action.isLeftClick()) {
            // SHIFT + LEFT CLICK: Mind Control (RayTrace)
            executeControlPowerRayTrace(player);
        }
    }

    // 2. Handle Close-Range Hits (Cancel normal damage & trigger Mind Control)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            // Agar God Shovel haath mein hai aur SHIFT press hai
            if (player.isSneaking() && player.getInventory().getItemInMainHand() != null && ItemManager.isGodShovel(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true); // Normal hit CANCEL
                
                if (event.getEntity() instanceof LivingEntity target) {
                    startMindControl(player, target);
                }
            }
        }
    }

    // --- COOLDOWN LOGIC ---
    private boolean checkCooldown(Player player, HashMap<UUID, Long> cooldowns, String powerName) {
        if (cooldowns.containsKey(player.getUniqueId())) {
            long timePassed = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
            if (timePassed < (COOLDOWN_SECONDS * 1000L)) {
                double remaining = (COOLDOWN_SECONDS * 1000L - timePassed) / 1000.0;
                player.sendActionBar("§c§l" + powerName + " on Cooldown: §e" + String.format("%.1f", remaining) + "s");
                return false; // Not ready
            }
        }
        return true; // Ready
    }

    // --- DEATH BEAM POWER (Shift + Right Click) ---
    private void executeBeamPower(Player player) {
        if (!checkCooldown(player, beamCooldowns, "Death Beam")) return;

        // Advanced RayTrace with 1.5 block radius (Aim assist so you don't miss)
        RayTraceResult ray = player.getWorld().rayTrace(
                player.getEyeLocation(), 
                player.getLocation().getDirection(), 
                20.0, 
                FluidCollisionMode.NEVER, 
                true, 
                1.5, // Hitbox expansion
                entity -> entity instanceof LivingEntity && entity != player
        );

        if (ray != null && ray.getHitEntity() instanceof LivingEntity target) {
            if (activeTargets.contains(target.getUniqueId())) {
                player.sendActionBar("§cTarget is already affected by a power!");
                return;
            }

            beamCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            activeTargets.add(target.getUniqueId());
            Location lockLoc = target.getLocation().clone(); // Lock their exact position
            
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 200 || target.isDead() || !target.isValid()) { // 10s complete
                        if (!target.isDead()) {
                            player.getWorld().createExplosion(target.getLocation(), 10f, false, false);
                        }
                        activeTargets.remove(target.getUniqueId());
                        this.cancel();
                        return;
                    }

                    // Freeze target completely
                    target.teleport(lockLoc);

                    // Thick Mixed Beam Particles
                    Location start = target.getLocation().add(0, 30, 0);
                    for (double y = 0; y <= 30; y += 1.0) {
                        Location pLoc = start.clone().subtract(0, y, 0);
                        player.getWorld().spawnParticle(Particle.DUST, pLoc.clone().add(0.3, 0, 0), 2, new Particle.DustOptions(Color.RED, 2.5f));
                        player.getWorld().spawnParticle(Particle.DUST, pLoc.clone().add(-0.3, 0, 0), 2, new Particle.DustOptions(Color.BLUE, 2.5f));
                        player.getWorld().spawnParticle(Particle.DUST, pLoc.clone().add(0, 0, 0.3), 2, new Particle.DustOptions(Color.BLACK, 2.5f));
                    }

                    // Damage and Sound every 1 second (20 ticks)
                    if (ticks % 20 == 0) {
                        target.damage(8.0); // 4 hearts
                        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 0.8f);
                    }

                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        } else {
            player.sendActionBar("§cNo target found in sight!");
        }
    }

    // --- MIND CONTROL POWER (RayTrace Check) ---
    private void executeControlPowerRayTrace(Player player) {
        RayTraceResult ray = player.getWorld().rayTrace(
                player.getEyeLocation(), 
                player.getLocation().getDirection(), 
                20.0, 
                FluidCollisionMode.NEVER, 
                true, 
                1.5, 
                entity -> entity instanceof LivingEntity && entity != player
        );

        if (ray != null && ray.getHitEntity() instanceof LivingEntity target) {
            startMindControl(player, target);
        } else {
            player.sendActionBar("§cNo target found in sight to control!");
        }
    }

    // --- MIND CONTROL LOGIC EXECUTION ---
    private void startMindControl(Player player, LivingEntity target) {
        if (!checkCooldown(player, controlCooldowns, "Mind Control")) return;
        
        if (activeTargets.contains(target.getUniqueId())) {
            player.sendActionBar("§cTarget is already being controlled!");
            return;
        }

        controlCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        activeTargets.add(target.getUniqueId());
        
        player.sendActionBar("§a§lMind Control Activated!");

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 200 || target.isDead() || !player.isOnline()) { // 10s complete
                    activeTargets.remove(target.getUniqueId());
                    player.sendActionBar("§c§lMind Control Ended.");
                    this.cancel();
                    return;
                }
                
                // Keep target floating 3 blocks directly in front of the player
                Location puppetLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(3));
                target.teleport(puppetLoc);
                
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
