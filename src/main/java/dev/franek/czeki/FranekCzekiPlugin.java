package dev.franek.czeki;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class FranekCzekiPlugin extends JavaPlugin {

    private Economy economy;
    private ChequeManager chequeManager;
    private ChequeGuiManager guiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!setupEconomy()) {
            getLogger().severe("Nie znaleziono pluginu Vault lub dostawcy ekonomii! Wylaczanie FranekCzeki...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.chequeManager = new ChequeManager(this);
        this.guiManager = new ChequeGuiManager(this, chequeManager);

        ChequeCommand commandHandler = new ChequeCommand(this, chequeManager, guiManager);
        registerCommand("czek", commandHandler);
        registerCommand("wyplac", commandHandler);
        registerCommand("historiaczeki", commandHandler);
        registerCommand("aczek", commandHandler);
        registerCommand("historiaczek-clear", commandHandler);

        getServer().getPluginManager().registerEvents(new ChequeListener(this, chequeManager, guiManager), this);
        getLogger().info("FranekCzeki v1.0 zostal pomyslnie wlaczony!");
    }

    @Override
    public void onDisable() {
        if (chequeManager != null) {
            chequeManager.saveData();
        }
    }

    private void registerCommand(String name, ChequeCommand executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() {
        return economy;
    }
}
