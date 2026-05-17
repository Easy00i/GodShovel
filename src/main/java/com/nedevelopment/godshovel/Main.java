package com.nedevelopment.godshovel;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private DataManager dataManager;

    @Override
    public void onEnable() {
        // 1. Config and Data File Initialization
        saveDefaultConfig();
        this.dataManager = new DataManager(this);

        // 2. Register Events/Listeners
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PowerListeners(this), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);

        // 3. Register Command & Tab Completer
        CommandHandler commandHandler = new CommandHandler(this);
        if (getCommand("giveshovel") != null) {
            getCommand("giveshovel").setExecutor(commandHandler);
            getCommand("giveshovel").setTabCompleter(commandHandler);
        }

        // 4. Start Passive Particle & Aura Task (Runs every 2 ticks)
        new PassivePowerTask(this).runTaskTimer(this, 0L, 2L);

        // 5. Register God Shovel Custom Recipe
        registerGodShovelRecipe();
        
        getLogger().info("§aGodShovel Plugin successfully loaded without bugs!");
    }

    @Override
    public void onDisable() {
        getLogger().info("§cGodShovel Plugin stopped.");
    }

    /**
     * God Shovel ki custom recipe setup:
     * Dragon Egg, Mace, Diamond Block, Netherite Ingot, aur Wooden Shovel.
     */
    private void registerGodShovelRecipe() {
        NamespacedKey key = new NamespacedKey(this, "god_shovel_recipe");
        
        // Custom item result output definition
        ShapedRecipe recipe = new ShapedRecipe(key, ItemManager.getGodShovel());
        
        // Matrix Shape: Top -> Mid -> Bottom
        recipe.shape(
                " E ",
                "NMD",
                " S "
        );

        recipe.setIngredient('E', Material.DRAGON_EGG);
        recipe.setIngredient('M', Material.MACE);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('S', Material.WOODEN_SHOVEL);

        // Duplicate override protection
        if (Bukkit.getRecipe(key) == null) {
            Bukkit.addRecipe(recipe);
        }
    }

    // --- MACE SINGLE-CRAFT TRACKING LOGIC ---
    @EventHandler(priority = EventPriority.HIGH)
    public void onMaceCraft(CraftItemEvent event) {
        if (event.getRecipe().getResult().getType() == Material.MACE) {
            if (dataManager.isMaceCrafted()) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("§cMACE Already Crafted.");
                return;
            }
            
            dataManager.setMaceCrafted(true);
            Player player = (Player) event.getWhoClicked();
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§6§lMACE CRAFTED!", "§e" + player.getName() + " crafted the first Mace!", 10, 70, 20);
            }
        }
    }

        // --- GOD SHOVEL CRAFTING RITUAL TRIGGER (1-TICK DELAY FIXED) ---
    @EventHandler(priority = EventPriority.HIGH)
    public void onGodShovelCraft(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (ItemManager.isGodShovel(result)) {
            event.setCancelled(true); // Inventory click cancel taaki glitch se direct hath me na aaye
            
            Player player = (Player) event.getWhoClicked();
            Location tableLoc = event.getInventory().getLocation();
            
            if (tableLoc != null && tableLoc.getBlock().getType() == Material.CRAFTING_TABLE) {
                // Matrix items ko completely consume/clear karo
                event.getInventory().clear();
                player.closeInventory();
                
                // 1 Ticker Delay taaki advanced math aur rotation bina lag ke execute ho sakein
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    CraftingRitual.startRitual(this, player, tableLoc);
                }, 1L);
            }
        }
    }

    // --- DRAGON EGG FIRST PICKUP LOGIC ---
    @EventHandler
    public void onEggPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getItem().getItemStack().getType() == Material.DRAGON_EGG && !dataManager.isEggFound()) {
                triggerEggAnnouncement(player);
            }
        }
    }

    @EventHandler
    public void onEggInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.DRAGON_EGG && !dataManager.isEggFound()) {
            if (event.getWhoClicked() instanceof Player player) {
                triggerEggAnnouncement(player);
            }
        }
    }

    private void triggerEggAnnouncement(Player player) {
        dataManager.setEggFound(true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§d§lDRAGON EGG OBTAINED!", "§5" + player.getName() + " got the Egg!", 10, 70, 20);
        }
    }

    public DataManager getDataManager() {
        return dataManager;
    }
}
