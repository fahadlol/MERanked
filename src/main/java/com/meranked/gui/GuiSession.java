package com.meranked.gui;

import org.bukkit.inventory.Inventory;

public record GuiSession(GuiType type, String context, String subContext) {
    public static GuiSession of(GuiType type) {
        return new GuiSession(type, "", "");
    }
    public static GuiSession of(GuiType type, String context) {
        return new GuiSession(type, context, "");
    }
    public GuiSession withSub(String sub) {
        return new GuiSession(type, context, sub);
    }
}
