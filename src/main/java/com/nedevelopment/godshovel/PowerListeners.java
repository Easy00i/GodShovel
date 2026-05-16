package com.nedevelopment.godshovel;

import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
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
            executeHammerPower(player);
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
// HAMMER STRIKE POWER  —  Shift + Right Click
// Replace executeBeamPower() with this method.
// Caller update karo: executeBeamPower(player) → executeHammerPower(player)
//
// Required imports (add if missing):
//   import org.joml.Quaternionf;
//   import org.joml.Vector3f;
//   import org.bukkit.util.Transformation;
//   import org.bukkit.entity.ItemDisplay;
//   import org.bukkit.entity.Display;
// =====================================================================
private void executeHammerPower(Player player) {
    if (!checkCooldown(player, beamCooldowns, "Hammer Strike")) return;

    RayTraceResult ray = player.getWorld().rayTrace(
            player.getEyeLocation(),
            player.getLocation().getDirection(),
            25.0,
            FluidCollisionMode.NEVER,
            true,
            1.5,
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

    final World    world     = player.getWorld();
    final Location targetLoc = target.getLocation().clone();

    // ── Side vector: random left / right of target ────────────────────────
    Vector fwd = targetLoc.toVector()
            .subtract(player.getLocation().toVector()).setY(0);
    if (fwd.lengthSquared() < 0.0001) fwd.setX(1);
    fwd.normalize();

    Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
    if (Math.random() < 0.5) side.multiply(-1);
    // 'side' = unit vector FROM target TOWARD hammer spawn

    // ── Constants ─────────────────────────────────────────────────────────
    final float  SCALE     = 5.0f;
    final double SIDE_DIST = SCALE; // at 90° swing, head-tip reaches target exactly

    // Pivot = handle bottom of mace.  Placed SIDE_DIST blocks from target, just above ground.
    final Location pivotLoc = targetLoc.clone()
            .add(side.clone().multiply(SIDE_DIST))
            .add(0, 0.3, 0);

    // ── Swing rotation axis ────────────────────────────────────────────────
    // axis = side × UP  →  rotating +angle swings head from UP toward -side (toward target)
    // = (-side.z,  0,  side.x)  — already unit length since side.y = 0 and |side|=1
    final float rax = (float) -side.getZ();
    final float raz = (float)  side.getX();

    // Extracted for lambda capture
    final float sxF = (float) side.getX();
    final float szF = (float) side.getZ();

    // ── Spawn ItemDisplay ──────────────────────────────────────────────────
    final ItemDisplay hammer =
            (ItemDisplay) world.spawnEntity(pivotLoc, EntityType.ITEM_DISPLAY);
    hammer.setItemStack(new ItemStack(Material.MACE));
    hammer.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
    hammer.setBillboard(Display.Billboard.FIXED); // never face camera
    hammer.setShadowRadius(0f);
    hammer.setInterpolationDuration(0);
    hammer.setInterpolationDelay(0);

    // Start invisible (scale ≈ 0).
    // ty = SCALE*0.5 offsets the mace model UP so handle-end sits at entity origin.
    hammer.setTransformation(new Transformation(
            new Vector3f(0, SCALE * 0.5f, 0),
            new Quaternionf(),
            new Vector3f(0.01f, 0.01f, 0.01f),
            new Quaternionf()
    ));

    player.sendActionBar("§6§l⚒ Hammer Strike §7— Charging...");
    world.playSound(pivotLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.4f);

    new BukkitRunnable() {

        int     ticks      = 0;
        boolean impactDone = false;

        // ── Pivot-corrected transformation ─────────────────────────────────
        // Keeps handle-end (model-y = -0.5) fixed at entity origin during rotation.
        //
        // Derivation: after rotating handle-end (0, -SCALE*0.5, 0) by angle around axis:
        //   rotated = (-raz·sinA,  -cosA,  rax·sinA) · SCALE·0.5
        //   translation = -rotated = (raz·sinA, cosA, -rax·sinA) · SCALE·0.5
        //
        // NOTE: If mace appears UPSIDE DOWN (head at bottom), flip the Y sign:
        //   change  ty = +cosA * SCALE*0.5  →  ty = -cosA * SCALE*0.5
        //   and     swingDir model → change rotation to Math.PI - angle
        void applyTransform(float scale, double angle) {
            double cosA = Math.cos(angle);
            double sinA = Math.sin(angle);
            float tx = (float)( raz * sinA  * SCALE * 0.5);
            float ty = (float)( cosA         * SCALE * 0.5);
            float tz = (float)(-rax * sinA  * SCALE * 0.5);
            Quaternionf rot = (angle < 0.0001)
                    ? new Quaternionf()
                    : new Quaternionf().rotationAxis((float) angle, rax, 0f, raz);
            hammer.setTransformation(new Transformation(
                    new Vector3f(tx, ty, tz),
                    rot,
                    new Vector3f(scale, scale, scale),
                    new Quaternionf()
            ));
        }

        double smoothstep(double t) { return t * t * (3 - 2 * t); }

        @Override
        public void run() {
            // ── Safety cleanup ─────────────────────────────────────────
            if (ticks > 215 || !hammer.isValid()) {
                if (hammer.isValid()) hammer.remove();
                activeTargets.remove(target.getUniqueId());
                cancel();
                return;
            }

            // ── Freeze target until impact ──────────────────────────────
            if (!impactDone && target.isValid() && !target.isDead()) {
                target.teleport(targetLoc);
            }

            // ══════════════════════════════════════════════════════════════
            // PHASE 1 · MATERIALISE  (ticks 0 – 140 = 7 s)
            // ══════════════════════════════════════════════════════════════
            if (ticks <= 140) {

                double ep = smoothstep(ticks / 140.0);
                float  s  = Math.max(0.05f, (float)(SCALE * ep));

                hammer.setInterpolationDelay(0);
                hammer.setInterpolationDuration(4);
                applyTransform(s, 0);

                // Charge sparks — head-tip is at pivotLoc + (0, s, 0)
                if (ticks % 3 == 0 && s > 0.5f) {
                    Location hdTip = pivotLoc.clone().add(0, s, 0);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, hdTip,
                            5, s * 0.28, s * 0.30, s * 0.28, 0.10);
                    if (ticks % 20 == 0)
                        world.spawnParticle(Particle.END_ROD, hdTip,
                                3, s * 0.38, s * 0.42, s * 0.38, 0.05);
                }

                if (ticks ==   0) world.playSound(pivotLoc, Sound.BLOCK_BEACON_POWER_SELECT,    1.5f, 0.4f);
                if (ticks ==  50) world.playSound(pivotLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.6f);
                if (ticks == 100) world.playSound(pivotLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.3f);
                if (ticks == 130) world.playSound(pivotLoc, Sound.ENTITY_EVOKER_PREPARE_ATTACK,  1.0f, 0.7f);

            // ══════════════════════════════════════════════════════════════
            // PHASE 2 · HOLD & SHAKE  (ticks 141 – 158 ≈ 0.9 s)
            // ══════════════════════════════════════════════════════════════
            } else if (ticks <= 158) {

                // Micro-angular shake so handle stays planted
                double shakeAngle = Math.sin((ticks - 141) * 1.8) * 0.025;
                hammer.setInterpolationDelay(0);
                hammer.setInterpolationDuration(2);
                applyTransform(SCALE, shakeAngle);

                // Intense electricity at hammer head
                if (ticks % 2 == 0) {
                    Location hdTip = pivotLoc.clone().add(0, SCALE, 0);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, hdTip,
                            10, SCALE * 0.32, SCALE * 0.35, SCALE * 0.32, 0.18);
                    world.spawnParticle(Particle.DUST, hdTip, 5,
                            SCALE * 0.22, SCALE * 0.25, SCALE * 0.22, 0,
                            new Particle.DustOptions(Color.fromRGB(100, 180, 255), 2.5f));
                }

                if (ticks == 142) world.playSound(pivotLoc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.8f, 0.5f);
                if (ticks == 153) {
                    world.playSound(pivotLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.5f, 0.9f);
                    player.sendActionBar("§c§l⚒ IMPACT INCOMING!");
                }

            // ══════════════════════════════════════════════════════════════
            // PHASE 3 · SWING  (ticks 159 – 177 ≈ 0.9 s)
            // ══════════════════════════════════════════════════════════════
            } else if (ticks <= 177) {

                int    st    = ticks - 159;
                double t     = st / 18.0;
                double et    = t * t * t;             // cubic ease-in: accelerates toward impact
                double angle = et * (Math.PI / 2.0); // 0° → 90°

                hammer.setInterpolationDelay(0);
                hammer.setInterpolationDuration(2);
                applyTransform(SCALE, angle);

                // Head world position along arc (for trail particles)
                double cosA = Math.cos(angle), sinA = Math.sin(angle);
                double hx = pivotLoc.getX() + (-sxF) * SCALE * sinA;
                double hy = pivotLoc.getY() + SCALE * cosA;
                double hz = pivotLoc.getZ() + (-szF) * SCALE * sinA;
                Location headPos = new Location(world, hx, hy, hz);

                if (ticks % 2 == 0) {
                    world.spawnParticle(Particle.SWEEP_ATTACK,   headPos, 5, 0.6, 0.3, 0.6, 0);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, headPos, 8, 0.8, 0.5, 0.8, 0.28);
                    world.spawnParticle(Particle.DUST, headPos, 4, 0.4, 0.3, 0.4, 0,
                            new Particle.DustOptions(Color.fromRGB(150, 220, 255), 2.0f));
                }

                if (st ==  1) world.playSound(pivotLoc, Sound.ENTITY_BREEZE_WIND_BURST, 3.0f, 0.3f);
                if (st == 10) world.playSound(pivotLoc, Sound.ENTITY_BREEZE_WIND_BURST, 2.0f, 0.6f);

            // ══════════════════════════════════════════════════════════════
            // PHASE 4 · IMPACT  (tick 178)
            // ══════════════════════════════════════════════════════════════
            } else if (ticks == 178 && !impactDone) {

                impactDone = true;
                final Location impLoc = targetLoc.clone().add(0, 1.0, 0);

                // Lock hammer at 90° (head at target position)
                hammer.setInterpolationDuration(0);
                applyTransform(SCALE, Math.PI / 2.0);

                // ── Sounds ──────────────────────────────────────────────
                world.playSound(impLoc, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 4.0f, 0.5f);
                world.playSound(impLoc, Sound.ENTITY_GENERIC_EXPLODE,        2.0f, 0.4f);
                world.playSound(impLoc, Sound.BLOCK_STONE_BREAK,             3.5f, 0.3f);
                world.playSound(impLoc, Sound.ENTITY_IRON_GOLEM_DEATH,       2.0f, 0.3f);
                player.sendActionBar("§4§l⚒ HAMMER STRIKE!");

                // ── 10 hearts damage ────────────────────────────────────
                if (target.isValid() && !target.isDead())
                    target.damage(20.0, player);

                // ── Expanding shockwave rings ────────────────────────────
                for (double r = 0.4; r <= 6.0; r += 0.45) {
                    int pts = Math.max(6, (int)(r * 7));
                    for (int i = 0; i < pts; i++) {
                        double a = (2 * Math.PI * i) / pts;
                        Location p = impLoc.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                        world.spawnParticle(Particle.EXPLOSION,      p, 1, 0,   0,   0,   0);
                        world.spawnParticle(Particle.ELECTRIC_SPARK, p, 3, 0.2, 0.2, 0.2, 0.15);
                    }
                }

                // ── Block debris flying up ──────────────────────────────
                for (int bx = -3; bx <= 3; bx++) {
                    for (int bz = -3; bz <= 3; bz++) {
                        if (bx * bx + bz * bz > 10) continue;
                        Block blk = targetLoc.clone().add(bx, 0, bz).getBlock();
                        if (blk.getType().isSolid()) {
                            world.spawnParticle(Particle.BLOCK,
                                    blk.getLocation().add(0.5, 1, 0.5),
                                    12, 0.4, 0.5, 0.4, 0.35, blk.getBlockData());
                        }
                    }
                }
                // Heavy netherite-coloured crumble for impact crater feel
                world.spawnParticle(Particle.BLOCK_CRUMBLE, impLoc,
                        80, 2.5, 0.5, 2.5, 0.40,
                        Material.NETHERITE_BLOCK.createBlockData());

                // ── Sink target 4 blocks underground (like hammering a nail) ─
                if (target.isValid() && !target.isDead()) {
                    target.teleport(targetLoc.clone().subtract(0, 4, 0));
                    target.setVelocity(new Vector(0, -1.0, 0));
                }

                // ── AoE knockback for nearby entities ───────────────────
                for (Entity e : world.getNearbyEntities(impLoc, 5, 4, 5)) {
                    if (!(e instanceof LivingEntity le) || e == player || e == target) continue;
                    Vector dir = e.getLocation().toVector().subtract(impLoc.toVector()).setY(0);
                    if (dir.lengthSquared() < 0.0001) dir.setX(1);
                    dir.normalize().multiply(2.0).setY(0.9);
                    le.damage(8.0, player);
                    e.setVelocity(dir);
                }

            // ══════════════════════════════════════════════════════════════
            // PHASE 5 · DISSIPATE  (ticks 179 – 215 ≈ 1.8 s)
            // ══════════════════════════════════════════════════════════════
            } else if (ticks > 178) {

                int   ft = ticks - 178;
                float fs = SCALE * Math.max(0f, 1f - ft / 37f);

                if (fs > 0.05f) {
                    hammer.setInterpolationDelay(0);
                    hammer.setInterpolationDuration(4);
                    applyTransform(fs, Math.PI / 2.0);
                }
                if (ticks % 7 == 0)
                    world.spawnParticle(Particle.ELECTRIC_SPARK,
                            targetLoc.clone().add(0, 0.5, 0), 6, 1.5, 1.0, 1.5, 0.10);
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
