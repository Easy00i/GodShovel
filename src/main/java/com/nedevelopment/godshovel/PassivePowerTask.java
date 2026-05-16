package com.nedevelopment.godshovel;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

public class PassivePowerTask extends BukkitRunnable {

    private final Main plugin;
    private double t = 0;

    public PassivePowerTask(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        t += Math.PI / 8; // Snake orbit speed
        
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getInventory().getItemInMainHand() != null && ItemManager.isGodShovel(player.getInventory().getItemInMainHand())) {
                
                // 1. Red Snake Particle (Leg to Head)
                Location loc = player.getLocation();
                double x = 0.6 * Math.cos(t);
                double z = 0.6 * Math.sin(t);
                double y = (t % 15) / 5; // Climbs up and repeats
                
                Particle.DustOptions red = new Particle.DustOptions(Color.RED, 2.0f);
                player.getWorld().spawnParticle(Particle.DUST, loc.clone().add(x, y, z), 1, red);

                // 2. God Effects (Applied continuously so they clear easily)
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 4, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 4, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 2, false, false));

            } else {
                // Remove effects if taken out of hand
                if (player.hasPotionEffect(PotionEffectType.STRENGTH)) {
                    player.removePotionEffect(PotionEffectType.STRENGTH);
                    player.removePotionEffect(PotionEffectType.REGENERATION);
                    player.removePotionEffect(PotionEffectType.RESISTANCE);
                    player.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }
    }
}

