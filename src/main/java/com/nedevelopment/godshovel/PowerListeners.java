package com.nedevelopment.godshovel;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Display;
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
            executeMeteorRingPower(player);
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

// =====================================================================
// METEOR RING POWER  —  Shift + Right Click
// Caller update karo: executeHammerPower(player) → executeMeteorRingPower(player)
//
// Extra imports (missing hon to add karo):
//   import org.bukkit.block.Block;
//   import org.bukkit.Color;
//   import org.bukkit.Particle;
// =====================================================================
private void executeMeteorRingPower(Player player) {
    if (!checkCooldown(player, beamCooldowns, "Meteor Ring")) return;

    RayTraceResult ray = player.getWorld().rayTrace(
            player.getEyeLocation(),
            player.getLocation().getDirection(),
            60.0,
            FluidCollisionMode.NEVER,
            true,
            2.0,
            entity -> entity instanceof LivingEntity && entity != player
    );

    if (!(ray != null && ray.getHitEntity() instanceof LivingEntity target)) {
        player.sendActionBar("§cNo target in sight!");
        return;
    }
    if (activeTargets.contains(target.getUniqueId())) {
        player.sendActionBar("§cTarget is already affected by a power!");
        return;
    }

    beamCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    activeTargets.add(target.getUniqueId());

    final World    world      = player.getWorld();
    final Location groundLoc  = target.getLocation().clone();       // enemy ke pair
    final Location ringCenter = groundLoc.clone().add(0, 30.5, 0); // 30 blocks upar

    player.sendActionBar("§b§l✦ Ancient Eye — Summoning...");
    world.playSound(ringCenter, Sound.BLOCK_END_PORTAL_FRAME_FILL, 2.5f, 0.35f);
    world.playSound(ringCenter, Sound.ENTITY_ELDER_GUARDIAN_CURSE,  1.5f, 0.40f);

    new BukkitRunnable() {

        // ── State ──────────────────────────────────────────────────────
        int     ticks      = 0;
        boolean impactDone = false;
        int     impactTick = Integer.MAX_VALUE;

        // ── Spin angles (updated every tick) ──────────────────────────
        double outerSpin = 0.0;   // clockwise:          +0.025 / tick
        double innerSpin = 0.0;   // counter-clockwise:  −0.035 / tick

        // ── Ring materialise scale (0 → 1) ────────────────────────────
        double ringScale = 0.0;

        // ── Particle colours — exact cyan/teal palette from photo ──────
        final Color BRIGHT = Color.fromRGB(  0, 222, 255);
        final Color MID    = Color.fromRGB( 88, 235, 255);
        final Color WHITE  = Color.fromRGB(210, 250, 255);
        final Color DARK   = Color.fromRGB(  0, 148, 200);

        // ── Ring geometry — full-scale radii (blocks) ──────────────────
        // (sab ring center se measured hai)
        final double G_R1  = 15.0;  // outermost ring
        final double G_R2  = 13.6;  // second ring (just inside nodes)
        final double G_RN  = 12.2;  // node orbit radius
        final double G_RM  =  9.6;  // middle ring (inside nodes)
        final double G_RI  =  6.8;  // inner ring
        final double G_RSO =  6.2;  // octagram outer tips
        final double G_RSI =  2.7;  // octagram inner notches
        final double G_RC  =  1.7;  // centre circle
        final double G_RND =  1.9;  // each node circle radius
        final double G_AR  =  4.0;  // asteroid visual radius

        // ═══════════════════════════════════════════════════════════════
        // HELPERS
        // ═══════════════════════════════════════════════════════════════

        /** Ring plane height par ek DUST particle spawn karo. */
        void D(double x, double z, Color c, float sz) {
            world.spawnParticle(Particle.DUST,
                    new Location(world, x, ringCenter.getY(), z),
                    1, 0, 0, 0, 0, new Particle.DustOptions(c, sz));
        }

        /** XZ mein do points ke beech DUST line draw karo. */
        void DL(double x1, double z1, double x2, double z2,
                Color c, float sz, int steps) {
            for (int s = 0; s <= steps; s++) {
                double t = (double) s / steps;
                D(x1 + (x2 - x1) * t, z1 + (z2 - z1) * t, c, sz);
            }
        }

        /** Ring plane par ek pura circle draw karo. */
        void RING(double radius, double offset, Color c, float sz, int pts) {
            double cx = ringCenter.getX(), cz = ringCenter.getZ();
            for (int i = 0; i < pts; i++) {
                double a = 2 * Math.PI * i / pts + offset;
                D(cx + Math.cos(a) * radius, cz + Math.sin(a) * radius, c, sz);
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // DRAW MAGIC RING — photo jaisa exact circle — har tick called
        // ═══════════════════════════════════════════════════════════════
        void drawMagicRing() {
            double sc  = ringScale;
            // Scale apply karo sab radii par
            double r1  = G_R1  * sc,  r2  = G_R2  * sc,  rn  = G_RN  * sc;
            double rm  = G_RM  * sc,  ri  = G_RI  * sc,  rc  = G_RC  * sc;
            double rso = G_RSO * sc,  rsi = G_RSI * sc,  rnd = G_RND * sc;
            double cx  = ringCenter.getX(), cz = ringCenter.getZ();

            // ── 1. Outermost ring — clockwise spin ─────────────────────
            RING(r1, outerSpin, BRIGHT, 1.8f, 80);

            // ── 2. Second ring — clockwise, slightly ahead ─────────────
            RING(r2, outerSpin + 0.20, MID, 1.5f, 72);

            // ── 3. Middle ring — counter-clockwise ─────────────────────
            RING(rm, innerSpin, BRIGHT, 1.5f, 60);

            // ── 4. Inner ring — counter-clockwise, faster ──────────────
            RING(ri, innerSpin * 1.40, MID, 1.3f, 44);

            // ── 5. Centre circle — counter-clockwise (center mein sirf circle, khaali) ─
            RING(rc, innerSpin * 2.10, WHITE, 1.2f, 18);

            // ── 6. Octagram 8-pointed star — counter-clockwise spin ────
            // Photo mein jo inner star geometry hai — exactly wahi
            {
                double sOff = innerSpin * 0.65;

                // 8 spike triangles: har spike ka outer tip → 2 inner notches
                for (int i = 0; i < 8; i++) {
                    double a0 = 2 * Math.PI * i / 8.0 + sOff;
                    double ox = cx + Math.cos(a0) * rso;
                    double oz = cz + Math.sin(a0) * rso;
                    // Left aur right inner notch angles
                    double lA = a0 - Math.PI / 8.0;
                    double rA = a0 + Math.PI / 8.0;
                    DL(ox, oz, cx + Math.cos(lA) * rsi, cz + Math.sin(lA) * rsi, BRIGHT, 1.1f, 7);
                    DL(ox, oz, cx + Math.cos(rA) * rsi, cz + Math.sin(rA) * rsi, BRIGHT, 1.1f, 7);
                }

                // Skip-2 cross-connections → inner web lines (photo mein jo criss-cross lines hain)
                for (int i = 0; i < 8; i++) {
                    double a0 = 2 * Math.PI *  i         / 8.0 + sOff;
                    double a1 = 2 * Math.PI * ((i + 2) % 8) / 8.0 + sOff;
                    DL(cx + Math.cos(a0) * rso, cz + Math.sin(a0) * rso,
                       cx + Math.cos(a1) * rso, cz + Math.sin(a1) * rso,
                       DARK, 1.0f, 10);
                }
            }

            // ── 7. Six rune node circles — clockwise orbit ─────────────
            // Photo mein 6 circles equally spaced hain (hexagonal)
            for (int i = 0; i < 6; i++) {
                double na = 2 * Math.PI * i / 6.0 + outerSpin * 0.38;
                double nx = cx + Math.cos(na) * rn;
                double nz = cz + Math.sin(na) * rn;

                // Node border circle
                for (int j = 0; j < 24; j++) {
                    double ja = 2 * Math.PI * j / 24.0;
                    D(nx + Math.cos(ja) * rnd, nz + Math.sin(ja) * rnd, BRIGHT, 1.3f);
                }
                // Node centre glow
                world.spawnParticle(Particle.END_ROD,
                        new Location(world, nx, ringCenter.getY(), nz),
                        1, 0.04, 0.04, 0.04, 0.0);
                // Rune symbol inside
                drawRune(nx, nz, rnd * 0.50, i);
            }

            // ── 8. 6 radial spokes: middle ring → second ring ──────────
            // Photo mein jo faint lines hain nodes ke through
            for (int i = 0; i < 6; i++) {
                double na  = 2 * Math.PI * i / 6.0 + outerSpin * 0.38;
                double cos = Math.cos(na), sin = Math.sin(na);
                DL(cx + cos * rm, cz + sin * rm,
                   cx + cos * r2, cz + sin * r2,
                   DARK, 1.0f, 9);
            }

            // ── 9. Centre subtle glow ──────────────────────────────────
            world.spawnParticle(Particle.END_ROD, ringCenter, 2, 0.35, 0.08, 0.35, 0.02);
            if (ticks % 4 == 0)
                world.spawnParticle(Particle.ELECTRIC_SPARK, ringCenter,
                        3, rc * 0.7, 0.04, rc * 0.7, 0.03);
        }

        // ═══════════════════════════════════════════════════════════════
        // RUNE SYMBOLS — 6 different runes har node ke andar
        // Photo mein Elder Futhark jaisi symbols hain — inhe approximate kiya
        // ═══════════════════════════════════════════════════════════════
        void drawRune(double nx, double nz, double r, int type) {
            switch (type % 6) {

                case 0 -> { // ᚠ Fehu: vertical stem + 2 right stubs
                    DL(nx, nz - r, nx, nz + r, WHITE, 0.9f, 5);
                    DL(nx, nz - r * 0.35, nx + r * 0.75, nz - r * 0.35, WHITE, 0.9f, 3);
                    DL(nx, nz + r * 0.20, nx + r * 0.75, nz + r * 0.20, WHITE, 0.9f, 3);
                }
                case 1 -> { // ᚱ Raido: vertical + leg diagonal
                    DL(nx - r*0.25, nz - r, nx - r*0.25, nz + r,   WHITE, 0.9f, 5);
                    DL(nx - r*0.25, nz - r, nx + r*0.55, nz - r*0.3, WHITE, 0.9f, 4);
                    DL(nx + r*0.55, nz - r*0.3, nx - r*0.25, nz + r*0.1, WHITE, 0.9f, 4);
                    DL(nx - r*0.25, nz + r*0.1, nx + r*0.75, nz + r,  WHITE, 0.9f, 5);
                }
                case 2 -> { // ᚾ Nauthiz: two verticals + cross diagonal
                    DL(nx - r*0.38, nz - r, nx - r*0.38, nz + r, WHITE, 0.9f, 5);
                    DL(nx + r*0.38, nz - r, nx + r*0.38, nz + r, WHITE, 0.9f, 5);
                    DL(nx - r*0.38, nz - r, nx + r*0.38, nz + r, WHITE, 0.9f, 6);
                }
                case 3 -> { // ᚲ Kenaz: K-shape
                    DL(nx - r*0.30, nz - r, nx - r*0.30, nz + r,  WHITE, 0.9f, 5);
                    DL(nx - r*0.30, nz,     nx + r*0.70, nz - r,   WHITE, 0.9f, 5);
                    DL(nx - r*0.30, nz,     nx + r*0.70, nz + r,   WHITE, 0.9f, 5);
                }
                case 4 -> { // ᚴ Y-rune: three branches
                    DL(nx, nz, nx,           nz + r,       WHITE, 0.9f, 4);
                    DL(nx, nz, nx - r*0.70, nz - r*0.70, WHITE, 0.9f, 4);
                    DL(nx, nz, nx + r*0.70, nz - r*0.70, WHITE, 0.9f, 4);
                }
                case 5 -> { // ᚦ Thurisaz: stem + right-facing triangle
                    DL(nx - r*0.20, nz - r,      nx - r*0.20, nz + r,      WHITE, 0.9f, 5);
                    DL(nx - r*0.20, nz - r*0.30, nx + r*0.80, nz,           WHITE, 0.9f, 4);
                    DL(nx - r*0.20, nz + r*0.30, nx + r*0.80, nz,           WHITE, 0.9f, 4);
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // DRAW ASTEROID — realistic falling space rock with fire trail
        // ═══════════════════════════════════════════════════════════════
        void drawAsteroid(double fallDist) {
            double ay = ringCenter.getY() - fallDist;
            double ax = groundLoc.getX(), az = groundLoc.getZ();
            Location aLoc = new Location(world, ax, ay, az);

            Material[] rocks = {
                Material.STONE, Material.COBBLESTONE, Material.GRAVEL,
                Material.NETHERITE_BLOCK, Material.BASALT, Material.BLACKSTONE,
                Material.ANDESITE, Material.DEEPSLATE
            };

            // Rocky shell — random sphere of block particles (non-uniform = real rock jaisa)
            for (int i = 0; i < 75; i++) {
                double phi   = Math.random() * 2 * Math.PI;
                double theta = Math.acos(2 * Math.random() - 1);
                double r     = G_AR * (0.55 + Math.random() * 0.45);
                world.spawnParticle(Particle.BLOCK,
                        new Location(world,
                                ax + r * Math.sin(theta) * Math.cos(phi),
                                ay + r * Math.sin(theta) * Math.sin(phi) * 0.65, // slightly flattened
                                az + r * Math.cos(theta)),
                        1, 0.06, 0.06, 0.06, 0,
                        rocks[(int)(Math.random() * rocks.length)].createBlockData());
            }

            // Molten netherite core (dense dark center)
            world.spawnParticle(Particle.BLOCK, aLoc,
                    25, G_AR*0.35, G_AR*0.30, G_AR*0.35, 0,
                    Material.NETHERITE_BLOCK.createBlockData());

            // Fire aura
            world.spawnParticle(Particle.FLAME, aLoc,
                    30, G_AR*0.65, G_AR*0.50, G_AR*0.65, 0.04);
            world.spawnParticle(Particle.LAVA, aLoc,
                    10, G_AR*0.45, G_AR*0.35, G_AR*0.45, 0);
            world.spawnParticle(Particle.ELECTRIC_SPARK, aLoc,
                    6,  G_AR*0.55, G_AR*0.45, G_AR*0.55, 0.10);

            // Heat glow (orange-red DUST halo)
            world.spawnParticle(Particle.DUST, aLoc,
                    12, G_AR*0.75, G_AR*0.60, G_AR*0.75, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 100, 0), 3.0f));
            world.spawnParticle(Particle.DUST, aLoc,
                    8,  G_AR*0.55, G_AR*0.45, G_AR*0.55, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 50,  0), 2.5f));

            // Rising fire + smoke trail above asteroid (as it falls, trail goes up)
            double trailLen = Math.min(fallDist * 0.75, 16.0);
            for (double t = 1.5; t <= trailLen; t += 2.0) {
                double spread = t / Math.max(trailLen, 1.0);
                Location tLoc = new Location(world, ax, ay + t, az);
                int fc = (int)(18 * (1 - spread * 0.55));
                world.spawnParticle(Particle.FLAME, tLoc,
                        fc, G_AR * 0.28 * (0.5 + spread), 0.25,
                            G_AR * 0.28 * (0.5 + spread), 0.04);
                if (spread > 0.3)
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, tLoc,
                            (int)(7 * spread),
                            G_AR*0.35*spread, 0.18,
                            G_AR*0.35*spread, 0.05);
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // TRIGGER IMPACT — immediate, no delay, 15 TNT power level
        // ═══════════════════════════════════════════════════════════════
        void triggerImpact() {
            Location impLoc = groundLoc.clone().add(0, 0.5, 0);

            // ── Massive particle burst ──────────────────────────────────
            world.spawnParticle(Particle.EXPLOSION, impLoc, 40,  5.0,  1.0,  5.0, 0.0);
            world.spawnParticle(Particle.FLAME,     impLoc, 350, 7.0, 12.0,  7.0, 0.18);
            world.spawnParticle(Particle.LAVA,      impLoc, 90,  5.0,  4.0,  5.0, 0);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                    impLoc.clone().add(0, 8, 0), 140, 9.0, 6.0, 9.0, 0.14);
            world.spawnParticle(Particle.BLOCK, impLoc, 220, 5.0, 3.5, 5.0, 0.55,
                    Material.COBBLESTONE.createBlockData());
            world.spawnParticle(Particle.BLOCK, impLoc, 100, 4.0, 2.5, 4.0, 0.45,
                    Material.NETHERITE_BLOCK.createBlockData());
            world.spawnParticle(Particle.ELECTRIC_SPARK, impLoc, 80, 6.0, 4.0, 6.0, 0.15);
            // Orange-red heat flash
            world.spawnParticle(Particle.DUST, impLoc, 50, 6.0, 3.0, 6.0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 0), 4.0f));

            // Expanding shockwave rings (3 heights — exact circle pattern)
            for (double h : new double[]{0.4, 2.0, 4.0}) {
                for (double r = 0.5; r <= 16.0; r += 0.75) {
                    int pts = Math.max(8, (int)(r * 6));
                    for (int i = 0; i < pts; i++) {
                        double a = 2 * Math.PI * i / pts;
                        Location p = groundLoc.clone().add(Math.cos(a) * r, h, Math.sin(a) * r);
                        world.spawnParticle(Particle.EXPLOSION,      p, 1, 0,   0,   0,   0);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, p, 2, 0.2, 0.2, 0.2, 0.08);
                    }
                }
            }

            // Block debris — area ke sab solid blocks fly karein
            for (int bx = -7; bx <= 7; bx++) {
                for (int bz = -7; bz <= 7; bz++) {
                    if (bx * bx + bz * bz > 52) continue;
                    Block blk = groundLoc.clone().add(bx, 0, bz).getBlock();
                    if (blk.getType().isSolid())
                        world.spawnParticle(Particle.BLOCK,
                                blk.getLocation().add(0.5, 1, 0.5),
                                20, 0.5, 1.1, 0.5, 0.48, blk.getBlockData());
                }
            }

            // ── Sounds ──────────────────────────────────────────────────
            world.playSound(impLoc, Sound.ENTITY_GENERIC_EXPLODE,         5.0f, 0.35f);
            world.playSound(impLoc, Sound.ENTITY_GENERIC_EXPLODE,         4.5f, 0.58f);
            world.playSound(impLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,  5.0f, 0.28f);
            world.playSound(impLoc, Sound.ENTITY_WITHER_DEATH,            3.5f, 0.42f);
            world.playSound(impLoc, Sound.BLOCK_STONE_BREAK,              4.5f, 0.24f);
            world.playSound(impLoc, Sound.ENTITY_IRON_GOLEM_DEATH,        3.0f, 0.28f);
            world.playSound(impLoc, Sound.ENTITY_BREEZE_WIND_BURST,       3.5f, 0.18f);
            player.sendActionBar("§4§l☄ METEOR IMPACT!");

            // ── Block-breaking explosions — cascade pattern (~15 TNT power total) ──
            // Centre: power 8
            world.createExplosion(impLoc, 8.0f, true, true, player);
            // Inner ring × 8 (power 5 each, 5 blocks out)
            for (int i = 0; i < 8; i++) {
                double a = 2 * Math.PI * i / 8.0;
                world.createExplosion(
                        groundLoc.clone().add(Math.cos(a) * 5, 0, Math.sin(a) * 5),
                        5.0f, true, true, player);
            }
            // Outer ring × 6 (power 4 each, 10 blocks out)
            for (int i = 0; i < 6; i++) {
                double a = 2 * Math.PI * i / 6.0 + Math.PI / 6.0;
                world.createExplosion(
                        groundLoc.clone().add(Math.cos(a) * 10, 0, Math.sin(a) * 10),
                        4.0f, true, true, player);
            }

            // ── Primary target ko direct damage ─────────────────────────
            if (target.isValid() && !target.isDead())
                target.damage(40.0, player);  // 20 hearts

            // ── AoE: nearby entities ko damage + knockback ──────────────
            for (Entity e : world.getNearbyEntities(impLoc, 16, 10, 16)) {
                if (!(e instanceof LivingEntity le) || e == player) continue;
                double dist = e.getLocation().distance(impLoc);
                double f    = Math.max(0.0, 1.0 - dist / 16.0);
                if (f < 0.05) continue;
                le.damage(30.0 * f, player);
                Vector dir = e.getLocation().toVector().subtract(impLoc.toVector()).setY(0);
                if (dir.lengthSquared() < 0.001) dir.setX(1);
                dir.normalize().multiply(3.5 * f).setY(2.8 * f);
                e.setVelocity(dir);
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // MAIN LOOP — 1 tick = 1/20 second, total ~160 ticks = 8 seconds
        // ═══════════════════════════════════════════════════════════════
        @Override
        public void run() {

            // Safety hard cutoff
            if (ticks > 170) {
                activeTargets.remove(target.getUniqueId());
                cancel();
                return;
            }

            // Spin angles advance every tick
            outerSpin += 0.025;   // outer: clockwise
            innerSpin -= 0.035;   // inner: counter-clockwise

            // ══════════════════════════════════════════════════════════
            // PHASE 1 · RING FORMS & HOLDS  (ticks 0 – 100 = 5 seconds)
            // ══════════════════════════════════════════════════════════
            if (ticks <= 100) {

                // Smooth scale-in: fast initial appear, stable at 1.0 from tick 45
                ringScale = ticks < 45 ? Math.sqrt(ticks / 45.0) : 1.0;
                if (ringScale > 0.05) drawMagicRing();

                // Target ko freeze karo ring ke neeche
                if (target.isValid() && !target.isDead())
                    target.teleport(groundLoc);

                // Formation sounds & messages
                switch (ticks) {
                    case 0 -> {
                        world.playSound(ringCenter, Sound.BLOCK_END_PORTAL_FRAME_FILL,  2.5f, 0.38f);
                        world.playSound(ringCenter, Sound.ENTITY_ELDER_GUARDIAN_CURSE,  1.5f, 0.44f);
                    }
                    case 25 -> world.playSound(ringCenter, Sound.BLOCK_BEACON_AMBIENT,  2.0f, 0.30f);
                    case 55 -> world.playSound(ringCenter, Sound.ENTITY_WITHER_SPAWN,   1.2f, 0.54f);
                    case 85 -> {
                        world.playSound(ringCenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.44f);
                        player.sendActionBar("§c§l☄ METEOR RING — IMPACT INCOMING!");
                    }
                    case 98 -> {
                        world.playSound(ringCenter, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 2.0f, 0.38f);
                        world.playSound(ringCenter, Sound.ENTITY_BREEZE_WIND_BURST,     3.0f, 0.20f);
                    }
                }

            // ══════════════════════════════════════════════════════════
            // PHASE 2 · ASTEROID FALLS  (ticks 101 – ~139 = 2 seconds)
            // Ring center se ground tak — quadratic (gravity-like) acceleration
            // ══════════════════════════════════════════════════════════
            } else if (!impactDone) {

                // Ring fully visible during fall
                ringScale = 1.0;
                drawMagicRing();

                // Quadratic fall: 0 → 30.5 blocks in 39 ticks (~2 s)
                int    ft       = ticks - 101;
                double t        = Math.min(ft / 39.0, 1.0);
                double fallDist = 30.5 * t * t;   // accelerates like real gravity

                drawAsteroid(fallDist);

                // Approaching rumble sounds (louder as asteroid nears)
                switch (ft) {
                    case 0 -> {
                        world.playSound(ringCenter, Sound.ENTITY_BREEZE_WIND_BURST,      3.0f, 0.18f);
                        world.playSound(ringCenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.28f);
                    }
                    case 14 -> world.playSound(groundLoc, Sound.ENTITY_BREEZE_WIND_BURST, 2.5f, 0.50f);
                    case 28 -> world.playSound(groundLoc, Sound.ENTITY_BREEZE_WIND_BURST, 3.0f, 0.80f);
                    case 36 -> world.playSound(groundLoc, Sound.ENTITY_GENERIC_EXPLODE,   3.0f, 0.60f);
                }

                // Asteroid zameen ko touch kare → IMMEDIATE impact, koi delay nahin
                if (fallDist >= 29.0) {
                    impactDone = true;
                    impactTick = ticks;
                    triggerImpact();  // ← Instant, same tick par
                }

            // ══════════════════════════════════════════════════════════
            // PHASE 3 · AFTERMATH  (impact ke baad — fade out & cleanup)
            // ══════════════════════════════════════════════════════════
            } else {

                int ft = ticks - impactTick;

                // Ring fades out in 10 ticks
                if (ft <= 10) {
                    ringScale = 1.0 - ft / 10.0;
                    if (ringScale > 0.04) drawMagicRing();
                }

                // Lingering fire + smoke on ground
                if (ft % 4 == 0 && ft <= 20) {
                    world.spawnParticle(Particle.FLAME,
                            groundLoc.clone().add(0, 0.6, 0), 22, 5.5, 0.5, 5.5, 0.06);
                    world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                            groundLoc.clone().add(0, 3.0, 0), 18, 5.0, 1.0, 5.0, 0.05);
                }

                // Cleanup — 20 ticks (~1 second) after impact
                if (ft >= 20) {
                    activeTargets.remove(target.getUniqueId());
                    cancel();
                }
            }

            ticks++;
        }
    }.runTaskTimer(plugin, 0L, 1L);
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
