package com.meranked.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.HashSet;
import java.util.Set;

public final class Arena {

    private final String name;
    private Set<String> allowedGamemodes = new HashSet<>();
    private Set<String> blockedGamemodes = new HashSet<>();
    private Material displayItem = Material.GRASS_BLOCK;
    private Location spawn1;
    private Location spawn2;
    private Location spectatorSpawn;
    private Location intro1;
    private Location intro2;
    private Location introCamera;
    private Location pos1;
    private Location pos2;
    private Location cloneSource;
    private String regenMethod = "SNAPSHOT";
    private boolean enabled = true;
    private boolean broken;
    private String brokenReason;
    private int usageCount;
    private String lastRegenResult = "OK";
    private Set<String> tags = new HashSet<>();
    private String description = "";

    public Arena(String name) {
        this.name = name;
    }

    public Set<String> tags() { return tags; }
    public void setTags(Set<String> tags) { this.tags = tags; }
    public String description() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String name() { return name; }
    public Set<String> allowedGamemodes() { return allowedGamemodes; }
    public void setAllowedGamemodes(Set<String> allowedGamemodes) { this.allowedGamemodes = allowedGamemodes; }
    public Set<String> blockedGamemodes() { return blockedGamemodes; }
    public void setBlockedGamemodes(Set<String> blockedGamemodes) { this.blockedGamemodes = blockedGamemodes; }
    public Material displayItem() { return displayItem; }
    public void setDisplayItem(Material displayItem) { this.displayItem = displayItem; }
    public Location spawn1() { return spawn1; }
    public void setSpawn1(Location spawn1) { this.spawn1 = spawn1; }
    public Location spawn2() { return spawn2; }
    public void setSpawn2(Location spawn2) { this.spawn2 = spawn2; }
    public Location spectatorSpawn() { return spectatorSpawn; }
    public void setSpectatorSpawn(Location spectatorSpawn) { this.spectatorSpawn = spectatorSpawn; }
    public Location intro1() { return intro1; }
    public void setIntro1(Location intro1) { this.intro1 = intro1; }
    public Location intro2() { return intro2; }
    public void setIntro2(Location intro2) { this.intro2 = intro2; }
    public Location introCamera() { return introCamera; }
    public void setIntroCamera(Location introCamera) { this.introCamera = introCamera; }
    public Location pos1() { return pos1; }
    public void setPos1(Location pos1) { this.pos1 = pos1; }
    public Location pos2() { return pos2; }
    public void setPos2(Location pos2) { this.pos2 = pos2; }
    public Location cloneSource() { return cloneSource; }
    public void setCloneSource(Location cloneSource) { this.cloneSource = cloneSource; }
    public String regenMethod() { return regenMethod; }
    public void setRegenMethod(String regenMethod) { this.regenMethod = regenMethod; }
    public boolean enabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean broken() { return broken; }
    public void setBroken(boolean broken) { this.broken = broken; }
    public String brokenReason() { return brokenReason; }
    public void setBrokenReason(String brokenReason) { this.brokenReason = brokenReason; }
    public int usageCount() { return usageCount; }
    public void incrementUsage() { this.usageCount++; }
    public String lastRegenResult() { return lastRegenResult; }
    public void setLastRegenResult(String lastRegenResult) { this.lastRegenResult = lastRegenResult; }

    public boolean supportsGamemode(String gamemode) {
        if (blockedGamemodes.contains(gamemode)) return false;
        if (allowedGamemodes.isEmpty()) return true;
        return allowedGamemodes.contains(gamemode);
    }

    public boolean isValid() {
        return spawn1 != null && spawn2 != null && pos1 != null && pos2 != null;
    }
}
