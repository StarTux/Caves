package com.cavetale.caves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

@Getter
@RequiredArgsConstructor
final class Biomes {
    private final Logger logger;
    private final Map<Biome, Type> biomes = new HashMap<>();
    private final Map<Type, Set<Biome>> types = new EnumMap<>(Type.class);
    @Setter private boolean reportDuplicateBiomes = false;

    enum Type {
        COLD("SNOW", "ICE", "FROZEN"),
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
        FOREST("FOREST", "WOOD", "BIRCH", "CHERRY_GROVE"),
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

    public void load() {
        for (Biome biome : Biome.values()) {
            for (Type type : Type.values()) {
                String name = biome.name();
                for (String keyword : type.keywords) {
                    if (name.contains(keyword)) {
                        Type exist = biomes.get(biome);
                        if (reportDuplicateBiomes && exist != null && exist != type) {
                            logger.info("Duplicate: " + biome + ": "
                                        + biomes.get(biome) + ", " + type);
                            continue;
                        }
                        biomes.put(biome, type);
                        types.computeIfAbsent(type, t -> new HashSet<>()).add(biome);
                    }
                }
            }
            if (!biomes.containsKey(biome)) {
                logger.warning("No matching biome: " + biome);
            }
        }
    }

    public Type of(Biome biome) {
        return biomes.get(biome);
    }

    public Type of(Block block) {
        return biomes.get(block.getBiome());
    }

    public static void test() {
        Biomes biomes = new Biomes(Logger.getGlobal());
        biomes.setReportDuplicateBiomes(true);
        biomes.load();
        List<Biomes.Type> sorted = new ArrayList<>(biomes.getTypes().keySet());
        Collections.sort(sorted, Comparator.comparing(t -> biomes.getTypes().get(t).size()));
        for (Biomes.Type type : sorted) {
            System.out.println(biomes.getTypes().get(type).size() + " " + type + " " + biomes.getTypes().get(type));
        }
    }
}
