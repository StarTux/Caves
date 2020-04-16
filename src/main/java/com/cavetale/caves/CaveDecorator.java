package com.cavetale.caves;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import lombok.Setter;
import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.noise.SimplexNoiseGenerator;

/**
 * Goal: Have 1 cave decoration for each Biome.Type (some may share).
 * Future: Have more than 1 variant via larger scale noise mapping.
 */
final class CaveDecorator {
    private final CavesPlugin plugin;
    private final SimplexNoiseGenerator noiseGenerator;
    private final Random random = ThreadLocalRandom.current();
    private static final BlockFace[] FACING_NEIGHBORS = {
        BlockFace.UP,
        BlockFace.DOWN,
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST
    };
    private static final BlockFace[] HORIZONTAL_NEIGHBORS = {
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST
    };
    @Setter private Biomes.Type biome = null; // debug

    CaveDecorator(CavesPlugin plugin, World world) {
        this.plugin = plugin;
        noiseGenerator = new SimplexNoiseGenerator(world.getSeed());
    }

    void transformChunk(Chunk chunk) {
        World world = chunk.getWorld();
        final int cx = chunk.getX();
        final int cz = chunk.getZ();
        // Block => revealed faces
        Map<Block, Set<BlockFace>> wallBlocks = new HashMap<>();
        for (int z = 0; z < 16; z += 1) {
            for (int x = 0; x < 16; x += 1) {
                final int hi = world.getHighestBlockYAt(cx * 16 + x,
                                                        cz * 16 + z);
                BLOCK: for (int y = 0; y < hi; y += 1) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.isEmpty()) continue;
                    switch (block.getType()) {
                    case STONE:
                    case ANDESITE: case DIORITE: case GRANITE:
                    case DIRT: case GRAVEL:
                    case COAL_ORE:
                        break; // OK
                    default:
                        continue BLOCK;
                    }
                    for (BlockFace face : FACING_NEIGHBORS) {
                        Block nbor = block.getRelative(face);
                        Material mat = block.getType();
                        if (mat == Material.AIR) {
                            wallBlocks.remove(block);
                            continue BLOCK;
                        }
                        if (nbor.getType() == Material.CAVE_AIR
                            || (mat.isTransparent() && nbor.getLightFromSky() == 0)
                            || (nbor.isLiquid() && nbor.getLightFromSky() == 0)) {
                            Set<BlockFace> faceSet = wallBlocks.get(block);
                            if (faceSet == null) {
                                faceSet = EnumSet.noneOf(BlockFace.class);
                                wallBlocks.put(block, faceSet);
                            }
                            faceSet.add(face);
                        }
                    }
                }
            }
        }
        for (Map.Entry<Block, Set<BlockFace>> entry : wallBlocks.entrySet()) {
            wallBlock(entry.getKey(), entry.getValue());
        }
    }

    void wallBlock(Block block, Set<BlockFace> faces) {
        // Figure out orientation and height
        int height = 0;
        final boolean floor;
        final boolean ceiling;
        final boolean wall;
        if (faces.contains(BlockFace.UP)) {
            Block above = block.getRelative(0, 1, 0);
            while (above.isEmpty() || above.getType().isTransparent()) {
                height += 1;
                if (above.getY() == 255) break;
                above = above.getRelative(0, 1, 0);
            }
            floor = height >= 2;
            height = height;
        } else {
            floor = false;
        }
        if (faces.contains(BlockFace.DOWN)) {
            Block below = block.getRelative(0, -1, 0);
            height = 0;
            while (below.isEmpty() || below.getType().isTransparent()) {
                height += 1;
                if (below.getY() == 0) break;
                below = below.getRelative(0, -1, 0);
            }
            ceiling = height >= 2;
        } else {
            ceiling = false;
        }
        wall = !floor && !ceiling
            && (faces.contains(BlockFace.NORTH) || faces.contains(BlockFace.EAST)
                || faces.contains(BlockFace.SOUTH) || faces.contains(BlockFace.WEST));
        wallBlock(block, faces, height, floor, ceiling, wall);
    }

    boolean wallBlock(Block block, Set<BlockFace> faces, int height,
                               boolean floor, boolean ceiling, boolean wall) {
        Biomes.Type theBiome = biome != null
            ? biome
            : plugin.biomes.of(block.getBiome());
        switch (theBiome) {
        case COLD: return wallBlockCold(block, faces, height, floor, ceiling, wall);
        case JUNGLE: return wallBlockJungle(block, faces, height, floor, ceiling, wall);
        case DESERT: return wallBlockDesert(block, faces, height, floor, ceiling, wall);
        case MUSHROOM: return wallBlockMushroom(block, faces, height, floor, ceiling, wall);
        case OCEAN: return wallBlockOcean(block, faces, height, floor, ceiling, wall);
        case MOUNTAIN: return wallBlockMountain(block, faces, height, floor, ceiling, wall);
        case SWAMP: return wallBlockSwamp(block, faces, height, floor, ceiling, wall);
        default: return false;
        }
    }

    boolean wallBlockCold(Block block, Set<BlockFace> faces, int height,
                          boolean floor, boolean ceiling, boolean wall) {
        double noise = getNoise(block, 8.0);
        if (noise < -0.75) {
            block.setType(Material.DIRT, false);
        } else if (noise < 0) {
            block.setType(Material.SNOW_BLOCK, false);
        } else if (noise < 0.5) {
            block.setType(Material.PACKED_ICE, false);
        } else {
            block.setType(Material.ICE, false);
        }
        if (ceiling && height > 1) {
            // Icicles
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.5) {
                int len = random.nextInt(Math.min(4, height)) + 1;
                for (int i = 1; i <= len; i += 1) {
                    block.getRelative(0, -i, 0).setType(Material.ICE, false);
                }
            }
        } else if (floor && height > 1) {
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.5) {
                int len = random.nextInt(Math.min(4, height)) + 1;
                for (int i = 1; i <= len; i += 1) {
                    block.getRelative(0, i, 0).setType(Material.ICE, false);
                }
            }
        }
        return true;
    }

    boolean wallBlockDesert(Block block, Set<BlockFace> faces, int height,
                            boolean floor, boolean ceiling, boolean wall) {
        double noise = getNoise(block, 8.0);
        if (noise < -0.75) {
            Axis axis = Blocks.randomArray(Axis.values(), random);
            block.setBlockData(Blocks.oriented(Material.BONE_BLOCK, axis), false);
        } else if (noise < 0) {
            block.setType(Material.SAND, false);
        } else if (noise < 0.5) {
            block.setType(Material.SANDSTONE, false);
        } else {
            block.setType(Material.SMOOTH_SANDSTONE, false);
        }
        if (floor && height > 1) {
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.33) {
                block.setType(Material.SAND, false);
                Block cactus = block.getRelative(0, 1, 0);
                int len = 1 + random.nextInt(Math.min(3, height));
                CACTI: for (int i = 0; i < len; i += 1) {
                    for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                        if (!cactus.getRelative(face).isEmpty()) {
                            break CACTI;
                        }
                    }
                    cactus.setType(Material.CACTUS, false);
                    cactus = cactus.getRelative(0, 1, 0);
                }
            } else if (noise2 < -0.5) {
                block.getRelative(0, 1, 0).setType(Material.DEAD_BUSH, false);
            }
        }
        return true;
    }

    boolean wallBlockJungle(Block block, Set<BlockFace> faces, int height,
                            boolean floor, boolean ceiling, boolean wall) {
        double noise = getNoise(block, 8.0);
        if (floor) {
            if (noise < 0) {
                block.setType(Material.GRASS_BLOCK, false);
            } else {
                block.setType(Material.GRASS_PATH, false);
            }
            if (height > 1) {
                double noise2 = getNoise(block, 1.0);
                if (noise2 > 0.33) {
                    // Bushes
                    Block log = block.getRelative(0, 1, 0);
                    Axis axis = Blocks.randomArray(Axis.values(), random);
                    log.setType(Material.JUNGLE_LOG, false);
                    for (BlockFace face : FACING_NEIGHBORS) {
                        if (face == BlockFace.DOWN) continue;
                        Block leaf = log.getRelative(face);
                        if (leaf.isEmpty()) {
                            leaf.setBlockData(Blocks.leaves(Material.JUNGLE_LEAVES), false);
                        }
                    }
                } else if (noise2 < 0) {
                    // Grass
                    block.setType(Material.GRASS_BLOCK, false);
                    if (height >= 2 && noise2 < -0.5) {
                        // Tall
                        block.getRelative(0, 1, 0).setBlockData(Blocks.lower(Material.TALL_GRASS), false);
                        block.getRelative(0, 2, 0).setBlockData(Blocks.upper(Material.TALL_GRASS), false);
                    } else {
                        block.getRelative(0, 1, 0).setType(Material.GRASS, false);
                    }
                }
            }
        } else if (ceiling) {
            if (noise < 0) {
                block.setType(Material.MOSSY_COBBLESTONE, false);
            } else {
                block.setType(Material.COBBLESTONE, false);
            }
            // Leaves with vines
            if (height > 1) {
                double noise2 = getNoise(block, 1.0);
                if (noise2 > 0.33) {
                    block.setType(Material.JUNGLE_LOG);
                    Block leaf = block.getRelative(0, -1, 0);
                    leaf.setBlockData(Blocks.leaves(Material.JUNGLE_LEAVES), false);
                    for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                        Block vine = leaf.getRelative(face);
                        BlockFace vineFace = face.getOppositeFace();
                        BlockData data = Blocks.facing(Material.VINE, vineFace);
                        int len = 1 + random.nextInt(height);
                        for (int i = 0; i < len && vine.isEmpty(); i += 1) {
                            vine.setBlockData(data, false);
                            vine = vine.getRelative(0, -1, 0);
                        }
                    }
                }
            }
        } else {
            // Walls
            if (noise < -0.5) {
                block.setType(Material.MOSSY_STONE_BRICKS, false);
            } else if (noise < 0) {
                block.setType(Material.STONE, false);
            } else if (noise < 0.5) {
                block.setType(Material.STONE_BRICKS, false);
            } else { // > 0.5
                block.setType(Material.CRACKED_STONE_BRICKS, false);
            }
        }
        return true;
    }

    /**
     * Mycelium on the floor, mushroom stem and blocks make up the
     * ceiling. Large and small mushrooms sprouting everywhere.
     */
    boolean wallBlockMushroom(Block block, Set<BlockFace> faces, int height,
                              boolean floor, boolean ceiling, boolean wall) {
        double noise = getNoise(block, 8.0);
        if (floor) {
            block.setType(Material.MYCELIUM, false);
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.6) {
                // Try to grow large
                if (noise < 0.1) {
                    if (!block.getWorld().generateTree(block.getLocation().add(0, 1, 0),
                                                       TreeType.BROWN_MUSHROOM)) {
                        block.getRelative(0, 1, 0).setType(Material.BROWN_MUSHROOM, false);
                    }
                } else {
                    if (!block.getWorld().generateTree(block.getLocation().add(0, 1, 0),
                                                       TreeType.RED_MUSHROOM)) {
                        block.getRelative(0, 1, 0).setType(Material.RED_MUSHROOM, false);
                    }
                }
            } else if (noise2 > 0.3) {
                // Small mushrooms
                if (noise < 0) {
                    block.getRelative(0, 1, 0).setType(Material.BROWN_MUSHROOM, false);
                } else {
                    block.getRelative(0, 1, 0).setType(Material.RED_MUSHROOM, false);
                }
            }
        } else if (ceiling) {
            double noise2 = getNoise(block, 1.0);
            if (noise2 < -0.5) { // noise2!
                block.setType(Material.GLOWSTONE, false);
            } else if (noise < 0) { // NOT noise2
                block.setType(Material.BROWN_MUSHROOM_BLOCK, false);
            } else {
                block.setType(Material.RED_MUSHROOM_BLOCK, false);
            }
        } else if (wall) {
            if (noise < -0.25) {
                block.setType(Material.BROWN_MUSHROOM_BLOCK, false);
            } else if (noise > 0.25) {
                block.setType(Material.RED_MUSHROOM_BLOCK, false);
            } else {
                block.setType(Material.MUSHROOM_STEM, false);
            }
        }
        return true;
    }

    /**
     * Underwater caves with prismarine and puddles of water. The
     * floor is sand and gravel. The ceiling is lit by sea
     * lanterns. Water drips from the ceiling.
     */
    boolean wallBlockOcean(Block block, Set<BlockFace> faces, int height,
                           boolean floor, boolean ceiling, boolean wall) {
        if (floor) {
            double noise = getNoise(block, 8.0);
            if (noise < -0.6 || noise > 0.6) {
                boolean empty = false;
                for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                    if (block.getRelative(face).isEmpty()) {
                        empty = true;
                        break;
                    }
                }
                if (empty) {
                    block.setType(Material.SAND, false);
                } else {
                    block.setType(Material.WATER, false);
                }
            } else if (noise < 0) {
                block.setType(Material.SAND, false);
            } else {
                block.setType(Material.GRAVEL, false);
            }
        } else if (ceiling) {
            double noise = getNoise(block, 8.0);
            if (noise < -0.5) {
                block.setType(Material.OBSIDIAN, false);
            } else if (noise < 0) {
                block.setType(Material.PRISMARINE, false);
            } else if (noise < 0.5) {
                block.setType(Material.DARK_PRISMARINE, false);
            } else {
                block.setType(Material.PRISMARINE_BRICKS, false);
            }
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.6) {
                block.setType(Material.SEA_LANTERN, false);
            } else if (noise2 < -0.6) {
                block.setType(Material.WATER, true);
            }
        } else {
            double noise = getNoise(block, 8.0);
            if (noise < -0.5) {
                block.setType(Material.MOSSY_STONE_BRICKS, false);
            } else if (noise < 0) {
                block.setType(Material.PRISMARINE_BRICKS, false);
            } else if (noise < 0.5) {
                block.setType(Material.PRISMARINE, false);
            } else {
                block.setType(Material.MOSSY_COBBLESTONE, false);
            }
        }
        return true;
    }

    boolean wallBlockMountain(Block block, Set<BlockFace> faces, int height,
                              boolean floor, boolean ceiling, boolean wall) {
        
        return true;
    }

    boolean wallBlockSwamp(Block block, Set<BlockFace> faces, int height,
                           boolean floor, boolean ceiling, boolean wall) {
        return true;
    }

    void makeRaftersBelow(Material mat, Block block, int interval, boolean beams) {
        boolean rafterX = block.getX() % interval == 0;
        boolean rafterZ = block.getZ() % interval == 0;
        Block pillar = block.getRelative(0, -1, 0);
        if (rafterX && rafterZ) {
            if (!beams) {
                pillar.setBlockData(Blocks.oriented(mat, Axis.X), false);
            } else {
                BlockData data = Blocks.oriented(mat, Axis.Y);
                while (pillar.isEmpty() || pillar.isLiquid() || pillar.getType().isTransparent()) {
                    pillar.setBlockData(data, false);
                    pillar = pillar.getRelative(0, -1, 0);
                }
            }
        } else if (rafterX) {
            pillar.setBlockData(Blocks.oriented(mat, Axis.Z), false);
        } else if (rafterZ) {
            pillar.setBlockData(Blocks.oriented(mat, Axis.X), false);
        }
    }

    double getNoise(Block block, double scale) {
        return noiseGenerator.noise(block.getX() / scale,
                                    block.getY() / scale,
                                    block.getZ() / scale);
    }

    void onChunkDecorate(Chunk chunk) {
        // transformChunk(chunk);
    }
}
