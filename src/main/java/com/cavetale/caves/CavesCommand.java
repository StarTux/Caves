package com.cavetale.caves;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class CavesCommand implements CommandExecutor {
    private final CavesPlugin plugin;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        return onCommand(sender, args[0], Arrays.copyOfRange(args, 1, args.length));
    }

    boolean onCommand(CommandSender sender, String cmd, String[] args) {
        switch (cmd) {
        case "test": {
            if (args.length > 2) return false;
            Player player = (Player) sender;
            Chunk chunk = player.getLocation().getChunk();
            World world = chunk.getWorld();
            Biomes.Type biome = args.length >= 1
                ? Biomes.Type.valueOf(args[0].toUpperCase())
                : null;
            int r = 1;
            plugin.getCaveDecorator(world).setBiome(biome);
            for (int z = chunk.getZ() - r; z <= chunk.getZ() + r; z += 1) {
                for (int x = chunk.getX() - r; x <= chunk.getX() + r; x += 1) {
                    plugin.getCaveDecorator(world)
                        .transformChunk(world.getChunkAt(x, z));
                }
            }
            plugin.getCaveDecorator(world).setBiome(null);
            int count = 1 + 2 * r;
            count *= count;
            player.sendMessage(count + " chunks transformed: "
                               + chunk.getX() + "," + chunk.getZ());
            return true;
        }
        default: return false;
        }
    }
}
