package com.cavetale.caves;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class CavesPlugin extends JavaPlugin {
    private Biomes biomes;
    private final Map<String, CaveDecorator> caves = new HashMap<>();
    private final CavesCommand command = new CavesCommand(this);

    @Override
    public void onEnable() {
        biomes = new Biomes(getLogger());
        biomes.load();
        if (getServer().getPluginManager().isPluginEnabled("Decorator")) {
            final EventListener listener = new EventListener(this);
            getServer().getPluginManager().registerEvents(listener, this);
        } else {
            getLogger().warning("Decorator NOT found!");
        }
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
