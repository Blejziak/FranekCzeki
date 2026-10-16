package dev.franek.czeki;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class ChequeGuiHolder implements InventoryHolder {

    private final boolean adminMode;
    private final int page;

    public ChequeGuiHolder(boolean adminMode, int page) {
        this.adminMode = adminMode;
        this.page = page;
    }

    public boolean isAdminMode() {
        return adminMode;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
