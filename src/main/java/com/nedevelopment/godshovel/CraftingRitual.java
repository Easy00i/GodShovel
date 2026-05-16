package com.nedevelopment.godshovel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Color;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class CraftingRitual {

    public static void startRitual(Main plugin, Player crafter, Location tableLocation) {
        // 1. Crafting table hatao aur initial locations set karo
        tableLocation.getBlock().setType(Material.AIR);
        
        // Exact center of the block
        Location centerLoc = tableLocation.clone().add(0.5, 0, 0.5); 
        Location spawnLoc = centerLoc.clone().subtract(0, 2, 0); // Starts 2 blocks underground
        
        // 2. Spawn Advanced BlockDisplay (Sculk Shrieker)
        BlockDisplay shrieker = spawnLoc.getWorld().spawn(spawnLoc, BlockDisplay.class);
        shrieker.setBlock(Bukkit.createBlockData(Material.SCULK_SHRIEKER));
        shrieker.setTeleportDuration(1); // Ultra-smooth tick-by-tick interpolation
        
        // 3. Make it HUGE (3x Size) and perfectly center it for stable spinning
        Transformation transform = shrieker.getTransformation();
        transform.getScale().set(3f, 3f, 3f);
        // Translation shifts the block backward so its absolute center becomes the rotation pivot
        transform.getTranslation().set(-1.5f, 0f, -1.5f);
        shrieker.setTransformation(transform);
        
        new BukkitRunnable() {
            int ticks = 0;
            ItemDisplay glowingShovel = null;
            float shriekerYaw = 0f;
            Location currentShriekerLoc = spawnLoc.clone();

            @Override
            public void run() {
                // Safe check: Player disconnects during animation
                if (!crafter.isOnline()) {
                    if (glowingShovel != null) {
                        dropShovel(glowingShovel.getLocation());
                        glowingShovel.remove();
                    } else {
                        dropShovel(centerLoc.clone().add(0, 1, 0));
                    }
                    shrieker.remove();
                    this.cancel();
                    return;
                }

                // -> ROTATION ENGINE (Stable Orbit)
                shriekerYaw += 3f; // Spin speed
                currentShriekerLoc.setYaw(shriekerYaw);

                // --- PHASE 1: 0 to 60 Ticks (0-3s) - Shrieker slowly rises ---
                if (ticks < 60) {
                    currentShriekerLoc.add(0, 2.0 / 60.0, 0); // Rises exactly 2 blocks up
                    if (ticks == 1) {
                        centerLoc.getWorld().playSound(centerLoc, Sound.ENTITY_WARDEN_EMERGE, 1.5f, 0.5f);
                        centerLoc.getWorld().playSound(centerLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.5f);
                    }
                }
                
                // --- PHASE 2: 60 Ticks (3s) - Spawn Shovel deep inside the Shrieker ---
                if (ticks == 60) {
                    Location shovelStart = currentShriekerLoc.clone().add(0, 0.5, 0); // Inside center
                    glowingShovel = centerLoc.getWorld().spawn(shovelStart, ItemDisplay.class);
                    glowingShovel.setItemStack(ItemManager.getGodShovel());
                    glowingShovel.setGlowing(true);
                    glowingShovel.setTeleportDuration(1); // Smooth item movement
                    
                    // Make shovel 1.5x bigger
                    Transformation st = glowingShovel.getTransformation();
                    st.getScale().set(1.5f, 1.5f, 1.5f);
                    glowingShovel.setTransformation(st);
                    
                    centerLoc.getWorld().playSound(centerLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 2.0f);
                }

                // --- PHASE 3: 60 to 120 Ticks (3-6s) - Shovel slowly rises out of the center ---
                if (ticks >= 60 && ticks < 120 && glowingShovel != null) {
                    Location sLoc = glowingShovel.getLocation();
                    sLoc.add(0, 2.5 / 60.0, 0); // Rises 2.5 blocks out smoothly
                    sLoc.setYaw(sLoc.getYaw() + 6f); // Shovel spins faster
                    glowingShovel.teleport(sLoc);
                }

                // --- PHASE 4: 120 to 200 Ticks (6-10s) - Water-like Floating & Bobbing ---
                if (ticks >= 120 && ticks < 200 && glowingShovel != null) {
                    Location sLoc = glowingShovel.getLocation();
                    
                    // Sine Wave Algorithm for smooth water-like floating
                    double bobbing = Math.sin((ticks - 120) * 0.15) * 0.03; 
                    sLoc.add(0, bobbing, 0);
                    sLoc.setYaw(sLoc.getYaw() + 6f);
                    
                    glowingShovel.teleport(sLoc);
                }

                // --- PHASE 5: 160 to 199 Ticks (8-10s) - Epic Lightning on Rotating Pillars ---
                if (ticks >= 160 && ticks < 200 && ticks % 8 == 0) {
                    // Advanced Trigonometry to track the rotating pillars exactly!
                    double angleRad = Math.toRadians(shriekerYaw);
                    double offset = 1.2; // 3x Scale offsets
                    
                    double[] xOffsets = {offset, offset, -offset, -offset};
                    double[] zOffsets = {offset, -offset, offset, -offset};
                    
                    for (int i = 0; i < 4; i++) {
                        double rotX = xOffsets[i] * Math.cos(angleRad) - zOffsets[i] * Math.sin(angleRad);
                        double rotZ = xOffsets[i] * Math.sin(angleRad) + zOffsets[i] * Math.cos(angleRad);
                        
                        Location strikeLoc = currentShriekerLoc.clone().add(rotX, 3.5, rotZ);
                        centerLoc.getWorld().strikeLightningEffect(strikeLoc);
                    }
                    centerLoc.getWorld().playSound(centerLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.8f);
                }

                                // --- EXTRA PHASE: Perfect Centered Red Beam (60 to 200 Ticks) ---
                if (ticks >= 60 && ticks < 200) {
                    Particle.DustOptions redBeam = new Particle.DustOptions(Color.RED, 2.5f);
                    
                    // X aur Z ko ekdum absolute center par lock kar diya hai taaki beam hile na
                    double exactX = centerLoc.getX();
                    double exactZ = centerLoc.getZ();
                    
                    // Y axis shovel ke sath upar aayega
                    double exactY = (glowingShovel != null) ? glowingShovel.getLocation().getY() : currentShriekerLoc.getY() + 1.0;
                    
                    Location beamStart = new Location(centerLoc.getWorld(), exactX, exactY, exactZ);
                    
                    // 30 block upar tak straight red beam
                    for (double y = 0; y <= 30; y += 0.5) {
                        centerLoc.getWorld().spawnParticle(Particle.DUST, beamStart.clone().add(0, y, 0), 1, redBeam);
                    }
                }
                

                // Keep updating Shrieker's location and rotation till the very end
                if (ticks < 200) {
                    shrieker.teleport(currentShriekerLoc);
                }

                // --- PHASE 6: EXACTLY 200 Ticks (10s) - Finish & Cleanup ---
                if (ticks == 200) {
                    // 10s pura hone ke baad hi sab remove hoga
                    shrieker.remove();
                    if (glowingShovel != null) {
                        dropShovel(glowingShovel.getLocation());
                        glowingShovel.remove();
                    }
                    
                    // Final Explosion & Success Sound
                    centerLoc.getWorld().createExplosion(centerLoc, 0f, false, false); // Fake explosion visual
                    centerLoc.getWorld().playSound(centerLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
                    centerLoc.getWorld().playSound(centerLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);

                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendTitle("§4§lGOD SHOVEL CRAFTED!", "§cCrafted by " + crafter.getName(), 10, 70, 20);
                    }
                    
                    this.cancel(); // Stop loop
                }

                ticks++; // Increment time
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    
    private static void dropShovel(Location loc) {
        loc.getWorld().dropItem(loc, ItemManager.getGodShovel());
    }
}
