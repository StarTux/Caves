package com.cavetale.caves;

import java.util.EnumMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;

@RequiredArgsConstructor
final class Biomes {
    private final JavaPlugin plugin;
    final Map<Biome, Type> biomes = new EnumMap<>(Biome.class);
    boolean reportDuplicateBiomes = false;

    enum Type {
        COLD("SNOW", "ICE", "FROZEN", "GROVE"),
        MESA("BADLAND"),
        SAVANNA("SAVANNA"),
        MUSHROOM("MUSHROOM"),
        DESERT("DESERT"),
        JUNGLE("JUNGLE"),
        OCEAN("OCEAN", "BEACH", "SHORE"),
        MOUNTAIN("MOUNTAIN", "HILLS", "PEAKS"),
        SWAMP("SWAMP"),
        DARK_FOREST("DARK_FOREST"),
        SPRUCE("TAIGA", "SPRUCE"),
        PLAINS("PLAINS", "SUNFLOWER", "MEADOW"),
        FOREST("FOREST", "WOOD", "BIRCH"),
        RIVER("RIVER"),
        CAVES("CAVES"),
        NETHER("NETHER", "BASALT_DELTAS", "SOUL_SAND_VALLEY"),
        END("END", "VOID"),
        DEEP_DARK("DEEP_DARK"),
        CUSTOM("CUSTOM");

        public final String[] keywords;

        Type(final String... keywords) {
            this.keywords = keywords;
        }
    }

    void load() {
        for (Biome biome : Biome.values()) {
            for (Type type : Type.values()) {
                String name = biome.name();
                for (String keyword : type.keywords) {
                    if (name.contains(keyword)) {
                        Type exist = biomes.get(biome);
                        if (reportDuplicateBiomes && exist != null && exist != type) {
                            plugin.getLogger().info("Duplicate: " + biome + ": "
                                             + biomes.get(biome) + ", " + type);
                            continue;
                        }
                        biomes.put(biome, type);
                    }
                }
            }
            if (!biomes.containsKey(biome)) {
                plugin.getLogger().warning("No matching biome: " + biome);
            }
        }
    }

    public Type of(Biome biome) {
        return biomes.get(biome);
    }

    public Type of(Block block) {
        return biomes.get(block.getBiome());
    }
}
