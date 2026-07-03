package com.meranked.kits;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class KitValidationService {

    private final KitService kitService;
    private final com.meranked.config.ConfigService configService;

    public KitValidationService(KitService kitService, com.meranked.config.ConfigService configService) {
        this.kitService = kitService;
        this.configService = configService;
    }

    public ValidationResult validate(UUID uuid, String gamemode) {
        KitService.StoredKit kit = kitService.getKit(uuid, gamemode);
        FileConfiguration config = configService.get("kit-editor.yml");
        List<String> illegal = config.getStringList("illegal-items");
        List<String> issues = new ArrayList<>();

        checkItems(kit.inventory(), illegal, issues, "inventory");
        checkItems(kit.armor(), illegal, issues, "armor");
        checkItems(kit.enderChest(), illegal, issues, "ender chest");

        int max = config.getInt("max-items-per-slot", 64);
        for (ItemStack item : kit.inventory()) {
            if (item != null && item.getAmount() > max) issues.add("Stack too large: " + item.getType());
        }
        return new ValidationResult(issues.isEmpty(), issues);
    }

    private void checkItems(ItemStack[] items, List<String> illegal, List<String> issues, String section) {
        if (items == null) return;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (illegal.contains(item.getType().name())) issues.add("Illegal item in " + section + ": " + item.getType());
        }
    }

    public record ValidationResult(boolean valid, List<String> issues) {
        public String summary() {
            return valid ? "§aValid" : "§c" + String.join(", ", issues);
        }
    }
}
