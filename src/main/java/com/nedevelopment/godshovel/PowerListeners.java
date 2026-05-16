package com.nedevelopment.godshovel;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class PowerListeners implements Listener {

    private final Main plugin;

    public PowerListeners(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        if (event.getItem() == null || !ItemManager.isGodShovel(event.getItem())) return;

        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // SHIFT + RIGHT CLICK: Death Beam
            executeBeamPower(player);
        } else if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // SHIFT + LEFT CLICK: Control Power
            executeControlPower(player);
        }
    }

    private void executeBeamPower(Player player) {
        RayTraceResult ray = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getLocation().getDirection(), 20.0);
        if (ray != null && ray.getHitEntity() instanceof LivingEntity target && target != player) {
            
            Location lockLoc = target.getLocation().clone(); // Freeze location
            
            new BukkitRunnable() {
                int ticks = 0;

                @Override
                public void run() {
                    if (ticks >= 200 || target.isDead()) { // 10 seconds (200 ticks)
                        player.getWorld().createExplosion(target.getLocation(), 10f, false, true); // Explosion
                        this.cancel();
                        return;
                    }

                    // Freeze target (1 inch bhi nahi hil sakta)
                    target.teleport(lockLoc);

                    // Beam Particles (Mix of Red, Blue, Black)
                    Location start = target.getLocation().add(0, 30, 0);
                    for (double y = 0; y <= 30; y += 0.5) {
                        Location particleLoc = start.clone().subtract(0, y, 0);
                        // Using dust options for mix colors
                        Particle.DustOptions red = new Particle.DustOptions(Color.RED, 2.0f);
                        Particle.DustOptions blue = new Particle.DustOptions(Color.BLUE, 2.0f);
                        Particle.DustOptions black = new Particle.DustOptions(Color.BLACK, 2.0f);
                        
                        player.getWorld().spawnParticle(Particle.DUST, particleLoc.add(0.2, 0, 0), 1, red);
                        player.getWorld().spawnParticle(Particle.DUST, particleLoc.add(-0.2, 0, 0), 1, blue);
                        player.getWorld().spawnParticle(Particle.DUST, particleLoc.add(0, 0, 0.2), 1, black);
                    }

                    // Damage every 1 second (20 ticks) -> 4 hearts (8 damage)
                    if (ticks % 20 == 0) {
                        target.damage(8.0);
                        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
                    }

                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }

    private void executeControlPower(Player player) {
        // Control Power: Forces target to be teleported to where the player looks for 10s
        RayTraceResult ray = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getLocation().getDirection(), 20.0);
        if (ray != null && ray.getHitEntity() instanceof LivingEntity target && target != player) {
            
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 200 || target.isDead() || !player.isOnline()) {
                        this.cancel();
                        return;
                    }
                    // Teleport target 3 blocks in front of the player (Mind Control Puppet)
                    Location puppetLoc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(3));
                    target.teleport(puppetLoc);
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }
    }
}

