package com.nedevelopment.godshovel;

import org.bukkit.Location;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CraftingRitual {

    public static void startRitual(Main plugin, Player crafter, Location tableLocation) {
        // Crafting table hatao
        tableLocation.getBlock().setType(Material.AIR);
        
        Location spawnLoc = tableLocation.clone().add(0.5, -2, 0.5); // Starts underground
        
        // BlockDisplay for Sculk Shrieker
        BlockDisplay shrieker = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class);
        shrieker.setBlock(Bukkit.createBlockData(Material.SCULK_SHRIEKER));
        
        new BukkitRunnable() {
            int ticks = 0;
            ItemDisplay glowingShovel = null;

            @Override
            public void run() {
                // If player leaves, pause or handle (Basic handling: cancel and drop directly)
                if (!crafter.isOnline()) {
                    dropShovel(spawnLoc.clone().add(0, 3, 0));
                    shrieker.remove();
                    if(glowingShovel != null) glowingShovel.remove();
                    this.cancel();
                    return;
                }

                if (ticks < 80) { // First 4 seconds: Sculk Shrieker comes up and orbits
                    Location current = shrieker.getLocation();
                    current.add(0, 0.025, 0); // Rise slowly
                    current.setYaw(current.getYaw() + 5); // Orbit/spin
                    shrieker.teleport(current);
                    
                } else if (ticks == 80) { // Shrieker has risen, spawn Shovel in center
                    Location shovelLoc = shrieker.getLocation().clone().add(0, 1, 0);
                    glowingShovel = spawnLoc.getWorld().spawn(shovelLoc, ItemDisplay.class);
                    glowingShovel.setItemStack(ItemManager.getGodShovel());
                    glowingShovel.setGlowing(true); // Glowing aura
                    
                } else if (ticks > 80 && ticks < 160) { // Next 4 seconds: Shovel rises 3 blocks
                    if (glowingShovel != null) {
                        Location shovelLoc = glowingShovel.getLocation();
                        shovelLoc.add(0, 0.0375, 0); // Rise up 3 blocks total
                        shovelLoc.setYaw(shovelLoc.getYaw() + 10); // Floating spin
                        glowingShovel.teleport(shovelLoc);
                    }
                    
                } else if (ticks == 160) { // 2 seconds lightning & sounds
                    spawnLoc.getWorld().strikeLightningEffect(spawnLoc.clone().add(2, 0, 0));
                    spawnLoc.getWorld().strikeLightningEffect(spawnLoc.clone().add(-2, 0, 0));
                    spawnLoc.getWorld().strikeLightningEffect(spawnLoc.clone().add(0, 0, 2));
                    spawnLoc.getWorld().strikeLightningEffect(spawnLoc.clone().add(0, 0, -2));
                    
                } else if (ticks >= 200) { // 10 seconds total: Finish
                    shrieker.remove();
                    if (glowingShovel != null) glowingShovel.remove();
                    
                    dropShovel(glowingShovel.getLocation());
                    
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§4§lGOD SHOVEL CRAFTED!", "§cCrafted by " + crafter.getName() + " at " + tableLocation.getBlockX() + " " + tableLocation.getBlockY() + " " + tableLocation.getBlockZ(), 10, 70, 20);
                    }
                    this.cancel();
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private static void dropShovel(Location loc) {
        loc.getWorld().dropItem(loc, ItemManager.getGodShovel());
    }
}

