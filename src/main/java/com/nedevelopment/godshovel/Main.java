package com.nedevelopment.godshovel;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    private boolean maceCrafted = false;
    private boolean eggFound = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        maceCrafted = getConfig().getBoolean("mace_crafted", false);
        eggFound = getConfig().getBoolean("egg_found", false);
        
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PowerListeners(this), this);
        getCommand("giveshovel").setExecutor(new CommandHandler(this));
        
        // Start the passive aura and effect task
        new PassivePowerTask(this).runTaskTimer(this, 0L, 2L);
    }

    // --- MACE CRAFTING LOGIC ---
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe().getResult().getType() == Material.MACE) {
            if (maceCrafted) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage("§cServer mein Mace pehle hi craft ho chuka hai! Ab dubara nahi ho sakta.");
                return;
            }
            // First time craft
            maceCrafted = true;
            getConfig().set("mace_crafted", true);
            saveConfig();
            
            Player player = (Player) event.getWhoClicked();
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendTitle("§6§lMACE CRAFTED!", "§e" + player.getName() + " crafted the first Mace!", 10, 70, 20);
            }
        }
    }

    // --- DRAGON EGG LOGIC ---
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getItem().getItemStack().getType() == Material.DRAGON_EGG && !eggFound) {
                triggerEggAnnouncement(player);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.DRAGON_EGG && !eggFound) {
            triggerEggAnnouncement((Player) event.getWhoClicked());
        }
    }

    private void triggerEggAnnouncement(Player player) {
        eggFound = true;
        getConfig().set("egg_found", true);
        saveConfig();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle("§d§lDRAGON EGG OBTAINED!", "§5" + player.getName() + " got the Egg!", 10, 70, 20);
        }
    }
}

