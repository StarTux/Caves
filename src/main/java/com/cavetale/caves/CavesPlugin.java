package com.cavetale.caves;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

public final class CavesPlugin extends JavaPlugin {
    final EventListener listener = new EventListener(this);
    final Biomes biomes = new Biomes(this);
    final Map<String, CaveDecorator> caves = new HashMap<>();
    final CavesCommand command = new CavesCommand(this);

    @Override
    public void onEnable() {
        biomes.load();
        getServer().getPluginManager().registerEvents(listener, this);
        getCommand("caves").setExecutor(command);
    }

    CaveDecorator getCaveDecorator(World world) {
        CaveDecorator result = caves.get(world.getName());
        if (result == null) {
            result = new CaveDecorator(this, world);
            caves.put(world.getName(), result);
        }
        return result;
    }
}
