package com.nedevelopment.godshovel;

public class DataManager {

    private final Main plugin;

    public DataManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean isMaceCrafted() {
        return plugin.getConfig().getBoolean("server_data.mace_crafted");
    }

    public void setMaceCrafted(boolean status) {
        plugin.getConfig().set("server_data.mace_crafted", status);
        plugin.saveConfig();
    }

    public boolean isEggFound() {
        return plugin.getConfig().getBoolean("server_data.egg_found");
    }

    public void setEggFound(boolean status) {
        plugin.getConfig().set("server_data.egg_found", status);
        plugin.saveConfig();
    }
}

