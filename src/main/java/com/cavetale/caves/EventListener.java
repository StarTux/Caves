package com.cavetale.caves;

import com.winthier.decorator.DecoratorEvent;
import lombok.RequiredArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

@RequiredArgsConstructor
public final class EventListener implements Listener {
    private final CavesPlugin plugin;

    @EventHandler
    public void onDecorator(DecoratorEvent event) {
        Chunk chunk = event.getChunk();
        World world = chunk.getWorld();
        if (world.getEnvironment() == World.Environment.NORMAL) {
            plugin.getCaveDecorator(world).onChunkDecorate(chunk);
        }
    }
}
