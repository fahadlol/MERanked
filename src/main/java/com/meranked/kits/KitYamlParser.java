package com.meranked.kits;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

/** Parses kits.yml default kit entries (material strings or small maps). */
public final class KitYamlParser {

    private KitYamlParser() {}

    @SuppressWarnings("unchecked")
    public static ItemStack[] parse(Object obj, int size) {
        ItemStack[] arr = new ItemStack[size];
        if (!(obj instanceof List<?> list)) return arr;
        for (int i = 0; i < list.size() && i < size; i++) {
            ItemStack stack = parseEntry(list.get(i));
            if (stack != null) arr[i] = stack;
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack parseEntry(Object entry) {
        if (entry instanceof ItemStack is) return is.clone();
        if (entry instanceof String materialName) {
            Material mat = Material.matchMaterial(materialName);
            return mat == null || mat == Material.AIR ? null : new ItemStack(mat);
        }
        if (entry instanceof Map<?, ?> map) {
            Object matObj = map.get("material");
            if (matObj == null) matObj = map.get("type");
            if (matObj == null) return null;
            Material mat = Material.matchMaterial(String.valueOf(matObj));
            if (mat == null || mat == Material.AIR) return null;
            int amount = 1;
            Object amt = map.get("amount");
            if (amt instanceof Number n) amount = Math.max(1, Math.min(64, n.intValue()));
            return new ItemStack(mat, amount);
        }
        return null;
    }
}
