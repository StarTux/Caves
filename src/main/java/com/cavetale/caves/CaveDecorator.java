package com.cavetale.caves;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Axis;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.TreeType;
import org.bukkit.World;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.MushroomCow;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import static com.cavetale.caves.Blocks.set;

/**
 * Goal: Have 1 cave decoration for each Biome.Type (some may share).
 * Future: Have more than 1 variant via larger scale noise mapping.
 */
final class CaveDecorator {
    private final CavesPlugin plugin;
    private final SimplexNoiseGenerator noiseGenerator;
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

    CaveDecorator(final CavesPlugin plugin, final World world) {
        this.plugin = plugin;
        noiseGenerator = new SimplexNoiseGenerator(world.getSeed());
    }

    @RequiredArgsConstructor
    static final class Context {
        final List<Runnable> deferredActions;
        final Random random;
        Set<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
        int height;
        int skyLight;
        boolean floor;
        boolean ceiling;
        boolean wall;
        boolean horizontal;
    }

    void transformChunk(Chunk chunk) {
        World world = chunk.getWorld();
        final int cx = chunk.getX();
        final int cz = chunk.getZ();
        // Block => revealed faces
        Map<Block, Context> blocks = new HashMap<>();
        List<Runnable> deferredActions = new ArrayList<>();
        double dseed = noiseGenerator.noise(chunk.getX(), chunk.getZ());
        int seed = (int) (dseed * (double) Integer.MAX_VALUE);
        Random random = new Random(seed);
        for (int z = 0; z < 16; z += 1) {
            for (int x = 0; x < 16; x += 1) {
                final int hi = world.getHighestBlockYAt(cx * 16 + x,
                                                        cz * 16 + z);
                BLOCK:
                for (int y = 0; y < hi; y += 1) {
                    Block block = chunk.getBlock(x, y, z);
                    Context context = null;
                    if (block.isEmpty()) continue;
                    if (!canReplace(block)) continue;
                    for (BlockFace face : FACING_NEIGHBORS) {
                        Block nbor = block.getRelative(face);
                        if (nbor.getType() == Material.AIR) {
                            context = null;
                            blocks.remove(block);
                            continue BLOCK;
                        }
                        if (nbor.getType() == Material.CAVE_AIR) {
                            if (context == null) {
                                context = new Context(deferredActions, random);
                                blocks.put(block, context);
                            }
                            context.faces.add(face);
                            int skyLight = nbor.getLightFromSky();
                            if (skyLight > context.skyLight) {
                                context.skyLight = skyLight;
                            }
                        } else if (isInside(nbor)) {
                            for (BlockFace face2 : FACING_NEIGHBORS) {
                                if (nbor.getRelative(face2).getType() == Material.CAVE_AIR) {
                                    if (context == null) {
                                        context = new Context(deferredActions, random);
                                        blocks.put(block, context);
                                    }
                                    context.faces.add(face);
                                }
                            }
                        }
                    }
                    if (context != null) {
                        classify(block, context);
                    }
                }
            }
        }
        placeOres(blocks);
        for (Map.Entry<Block, Context> entry : blocks.entrySet()) {
            Block block = entry.getKey();
            Context context = entry.getValue();
            transform(block, context);
        }
        for (Runnable run : deferredActions) run.run();
    }

    boolean canReplace(Block block) {
        switch (block.getType()) {
        case STONE:
        case ANDESITE: case DIORITE: case GRANITE:
        case DIRT: case GRAVEL:
        case COAL_ORE: case IRON_ORE:
            return true;
        default:
            return false;
        }
    }

    boolean isInside(Block block) {
        if (block.isEmpty() || block.isLiquid()) return true;
        Material mat = block.getType();
        if (Tag.FENCES.isTagged(mat)) return true;
        if (Tag.FLOWERS.isTagged(mat)) return true;
        if (Tag.CROPS.isTagged(mat)) return true;
        if (Tag.RAILS.isTagged(mat)) return true;
        return false;
    }

    void classify(Block block, Context context) {
        // Figure out orientation and height
        if (context.faces.contains(BlockFace.UP)) {
            Block above = block.getRelative(0, 1, 0);
            while (isInside(above)) {
                context.height += 1;
                if (above.getY() == 255) break;
                above = above.getRelative(0, 1, 0);
            }
            context.floor = context.height >= 2;
        } else {
            context.floor = false;
        }
        if (!context.floor && context.faces.contains(BlockFace.DOWN)) {
            Block below = block.getRelative(0, -1, 0);
            context.height = 0;
            while (isInside(below)) {
                context.height += 1;
                if (below.getY() == 0) break;
                below = below.getRelative(0, -1, 0);
            }
            context.ceiling = context.height >= 2;
        } else {
            context.ceiling = false;
        }
        context.wall = !context.floor && !context.ceiling;
        context.horizontal = context.faces.contains(BlockFace.NORTH)
            || context.faces.contains(BlockFace.EAST)
            || context.faces.contains(BlockFace.SOUTH)
            || context.faces.contains(BlockFace.WEST);
    }

    /**
     * Place ores. The blocks map will be modified so that ore blocks
     * are removed.
     */
    void placeOres(Map<Block, Context> blocks) {
        List<Block> oreBlocks = blocks.keySet().stream()
            .filter(b -> b.getY() >= 16)
            .filter(b -> {
                    Context context = blocks.get(b);
                    return context.skyLight == 0 && context.horizontal;
                })
            .collect(Collectors.toList());
        if (oreBlocks.isEmpty()) return;
        Random random = blocks.values().iterator().next().random;
        // For comparison:
        // Vanilla iron ore tries to spawn 20 times per chunk
        // Vanilla coal ore tries to spawn 20 times per chunk
        // Many chunks have 500-1000 wall blocks; 800 / 200 = 4
        int total = (oreBlocks.size() - 1) / 200 + 1;
        System.out.println("total=" + total);
        if (total > 8) total = 8;
        total += random.nextInt(4);
        ORE_BLOCKS:
        for (int i = 0; i < total; i += 1) {
            if (oreBlocks.isEmpty()) break;
            Block origin = oreBlocks.get(random.nextInt(oreBlocks.size()));
            double noiseL = getNoise(origin, 8.0);
            double noiseS = getNoise(origin, 1.0);
            final Material ore;
            final int veinSize;
            if (noiseS > 0.25) {
                if (noiseL > 0) {
                    ore = Material.DIAMOND_ORE;
                    veinSize = rndDist(random, 4, 3);
                } else {
                    ore = Material.EMERALD_ORE;
                    veinSize = rndDist(random, 4, 3);
                }
            } else if (noiseS < -0.25) {
                if (noiseL > 0) {
                    ore = Material.GOLD_ORE;
                    veinSize = rndDist(random, 6, 4);
                } else {
                    ore = Material.IRON_ORE;
                    veinSize = rndDist(random, 6, 4);
                }
            } else {
                if (noiseL > 0) {
                    ore = Material.LAPIS_ORE;
                    veinSize = rndDist(random, 4, 3);
                } else {
                    ore = Material.REDSTONE_ORE;
                    veinSize = rndDist(random, 6, 4);
                }
            }
            List<Block> vein = growVein(origin, veinSize, random);
            for (Block block : vein) {
                set(block, ore);
                blocks.remove(block);
            }
            oreBlocks.removeAll(vein);
        }
    }

    int rndDist(Random random, int median, int dist) {
        return median + random.nextInt(dist + 1) - random.nextInt(dist + 1);
    }

    List<Block> growVein(Block origin, int size, Random random) {
        List<Block> vein = new ArrayList<>(size);
        vein.add(origin);
        List<Block> adjacent = new ArrayList<>();
        Block pivot = origin;
        for (int i = 1; i < size; i += 1) {
            for (BlockFace face : FACING_NEIGHBORS) {
                Block nbor = pivot.getRelative(face);
                if (vein.contains(nbor)) continue;
                if (adjacent.contains(nbor)) continue;
                if (!canReplace(nbor)) continue;
                if (Math.abs(nbor.getX() - origin.getX()) > 4) continue;
                if (Math.abs(nbor.getZ() - origin.getZ()) > 4) continue;
                if (Math.abs(nbor.getY() - origin.getY()) > 5) continue;
                adjacent.add(nbor);
            }
            if (adjacent.isEmpty()) break;
            pivot = adjacent.remove(random.nextInt(adjacent.size()));
            vein.add(pivot);
        }
        return vein;
    }

    boolean transform(Block block, Context context) {
        Biomes.Type theBiome = biome != null
            ? biome
            : plugin.biomes.of(block.getBiome());
        switch (theBiome) {
        case COLD: return transformCold(block, context);
        case JUNGLE: return transformJungle(block, context);
        case DESERT: return transformDesert(block, context);
        case MUSHROOM: return transformMushroom(block, context);
        case OCEAN: return transformOcean(block, context);
        case MOUNTAIN:
            return transformMountain(block, context,
                                     Material.OAK_LOG, Material.STRIPPED_OAK_LOG);
        case SWAMP: return transformSwamp(block, context);
        case SPRUCE:
            return transformMountain(block, context,
                                     Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG);
        case DARK_FOREST: return transformSwamp(block, context);
        case PLAINS: case FOREST: case SAVANNA:
            return transformFlowers(block, context);
        case RIVER: return transformRiver(block, context);
        case MESA: return transformMesa(block, context);
        default: return false;
        }
    }

    boolean transformCold(Block block, Context context) {
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
        if (context.ceiling && context.height > 1) {
            // Icicles
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.5) {
                int len = context.random.nextInt(Math.min(4, context.height)) + 1;
                for (int i = 1; i <= len; i += 1) {
                    block.getRelative(0, -i, 0).setType(Material.ICE, false);
                }
            }
        } else if (context.floor && context.height > 1) {
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.5) {
                int len = context.random.nextInt(Math.min(4, context.height)) + 1;
                for (int i = 1; i <= len; i += 1) {
                    block.getRelative(0, i, 0).setType(Material.ICE, false);
                }
            }
        }
        return true;
    }

    boolean transformDesert(Block block, Context context) {
        double noise = getNoise(block, 8.0);
        if (noise < -0.75) {
            Axis axis = Blocks.randomArray(Axis.values(), context.random);
            block.setBlockData(Blocks.oriented(Material.BONE_BLOCK, axis), false);
        } else if (noise < 0) {
            block.setType(Material.SAND, false);
        } else if (noise < 0.5) {
            block.setType(Material.SANDSTONE, false);
        } else {
            block.setType(Material.SMOOTH_SANDSTONE, false);
        }
        if (context.floor && context.height > 1) {
            double noise2 = getNoise(block, 1.0);
            if (noise2 > 0.33) {
                block.setType(Material.SAND, false);
                Block cactus = block.getRelative(0, 1, 0);
                int len = 1 + context.random.nextInt(Math.min(3, context.height));
                CACTI:
                for (int i = 0; i < len; i += 1) {
                    if (!cactus.isEmpty()) break CACTI;
                    for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                        if (!cactus.getRelative(face).isEmpty()) {
                            break CACTI;
                        }
                    }
                    cactus.setType(Material.CACTUS, false);
                    cactus = cactus.getRelative(0, 1, 0);
                }
            } else if (noise2 < -0.5) {
                Block above = block.getRelative(0, 1, 0);
                if (above.isEmpty()) {
                    set(above, Material.DEAD_BUSH);
                }
            }
        }
        return true;
    }

    boolean transformJungle(Block block, Context context) {
        double noise = getNoise(block, 8.0);
        if (context.floor) {
            if (noise < 0) {
                block.setType(Material.GRASS_BLOCK, false);
            } else {
                block.setType(Material.GRASS_PATH, false);
            }
            if (context.height > 1) {
                double noise2 = getNoise(block, 1.0);
                if (noise2 > 0.33) {
                    // Bushes
                    Block log = block.getRelative(0, 1, 0);
                    Axis axis = Blocks.randomArray(Axis.values(), context.random);
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
                    Block above = block.getRelative(0, 1, 0);
                    if (above.isEmpty() && context.height >= 2 && noise2 < -0.5) {
                        // Tall
                        set(above, Blocks.lower(Material.TALL_GRASS));
                        set(above, 0, 1, 0, Blocks.upper(Material.TALL_GRASS));
                    } else {
                        block.getRelative(0, 1, 0).setType(Material.GRASS, false);
                    }
                }
            }
        } else if (context.ceiling) {
            if (noise < 0) {
                block.setType(Material.MOSSY_COBBLESTONE, false);
            } else {
                block.setType(Material.COBBLESTONE, false);
            }
            // Leaves with vines
            if (context.height > 1) {
                double noise2 = getNoise(block, 1.0);
                if (noise2 > 0.33) {
                    block.setType(Material.JUNGLE_LOG);
                    Block leaf = block.getRelative(0, -1, 0);
                    leaf.setBlockData(Blocks.leaves(Material.JUNGLE_LEAVES), false);
                    for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                        Block vine = leaf.getRelative(face);
                        BlockFace vineFace = face.getOppositeFace();
                        BlockData data = Blocks.facing(Material.VINE, vineFace);
                        int len = 1 + context.random.nextInt(context.height);
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
    boolean transformMushroom(Block block, Context context) {
        double noise = getNoise(block, 8.0);
        if (context.floor) {
            block.setType(Material.MYCELIUM, false);
            Block above = block.getRelative(0, 1, 0);
            if (above.isEmpty()) {
                double noise2 = getNoise(above, 1.0);
                if (noise2 > 0.6) {
                    // Try to grow large
                    if (noise < 0.1) {
                        context.deferredActions.add(() -> {
                                if (!block.getWorld().generateTree(above.getLocation(),
                                                                   TreeType.BROWN_MUSHROOM)) {
                                    set(above, Material.BROWN_MUSHROOM);
                                }
                            });
                    } else {
                        context.deferredActions.add(() -> {
                                if (!block.getWorld().generateTree(above.getLocation(),
                                                                   TreeType.RED_MUSHROOM)) {
                                    set(above, Material.RED_MUSHROOM);
                                }
                            });
                    }
                } else if (noise2 < -0.8) {
                    spawnBrownMushroomCow(above.getLocation().add(0.5, 0, 0.5));
                } else if (noise2 > 0.3) {
                    // Small mushrooms
                    if (noise < 0) {
                        set(above, Material.BROWN_MUSHROOM);
                    } else {
                        set(above, Material.RED_MUSHROOM);
                    }
                }
            }
        } else if (context.ceiling) {
            double noise2 = getNoise(block, 1.0);
            if (noise2 < -0.5) { // noise2!
                block.setType(Material.GLOWSTONE, false);
            } else if (noise < 0) { // NOT noise2
                block.setType(Material.BROWN_MUSHROOM_BLOCK, false);
            } else {
                block.setType(Material.RED_MUSHROOM_BLOCK, false);
            }
        } else if (context.wall) {
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
    boolean transformOcean(Block block, Context context) {
        if (context.floor) {
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
        } else if (context.ceiling) {
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
                block.setType(Material.WATER, true);
                if (noise2 > 0.7) {
                    set(block, 0, 1, 0, Material.SEA_LANTERN);
                }
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

    /**
     * Abandoned mineshafts with wooden rafters. The walls lit by
     * redstone torches, the rafters rarely by lanterns.
     */
    boolean transformMountain(Block block, Context context,
                              Material log, Material strippedLog) {
        if (context.ceiling) {
            final double scale = 128.0;
            double noiseX = getNoise(block, scale);
            int dx = (int) Math.floor(noiseX * 3.0 + 3.0);
            int dz = (int) Math.floor(noiseX * 3.0 + 3.0);
            boolean raft = context.height < 8
                && Blocks.makeRaftersBelow(block, 6, dx, dz, b -> {
                        double noise = getNoise(b, 8);
                        return noise < 0.4 ? log : strippedLog;
                    });
            if (context.height > 2 && raft) {
                Block below = block.getRelative(0, -2, 0);
                if (below.isEmpty()) {
                    double noiseS = getNoise(below, 1.0);
                    if (Math.abs(noiseS) < 0.005) {
                        set(below, Blocks.hangingLantern(true));
                    }
                }
            }
            double noise = getNoise(block, 8.0);
            if (noise > 0.5) {
                set(block, Material.GRAVEL);
            } else if (noise > 0) {
                set(block, Material.STONE);
            } else if (noise > -0.5) {
                set(block, Material.ANDESITE);
            } else {
                set(block, Material.SPRUCE_PLANKS);
            }
        } else if (context.floor) {
            if (context.horizontal) {
                if (!context.faces.contains(BlockFace.DOWN)
                    && context.faces.contains(BlockFace.UP)
                    && block.getRelative(BlockFace.UP).isEmpty()) {
                    List<BlockFace> faces = context.faces.stream()
                        .filter(f -> f.getModY() == 0)
                        .collect(Collectors.toList());
                    if (faces.size() == 1) {
                        BlockFace face = faces.get(0).getOppositeFace();
                        set(block, Blocks.direct(Material.COBBLESTONE_STAIRS, face));
                    } else if (faces.size() > 1) {
                        set(block, Material.COBBLESTONE_SLAB);
                    } else {
                        set(block, Material.COBBLESTONE);
                    }
                } else {
                    set(block, Material.POLISHED_ANDESITE);
                }
            } else {
                double noise = getNoise(block, 8.0);
                if (noise < -0.8) {
                    set(block, Material.CRACKED_STONE_BRICKS);
                } else if (noise < -0.6) {
                    set(block, Material.STONE_BRICKS);
                } else if (noise < 0) {
                    set(block, Material.STONE);
                } else if (noise > 0.6) {
                    set(block, Material.MOSSY_COBBLESTONE);
                } else {
                    set(block, Material.COBBLESTONE);
                }
            }
        } else if (context.wall) {
            double noiseS = getNoise(block, 1.0);
            if (noiseS > 0.5) {
                set(block, Material.MOSSY_STONE_BRICKS);
            } else if (noiseS < -0.5) {
                set(block, Material.CRACKED_STONE_BRICKS);
            } else {
                double noise = getNoise(block, 8.0);
                if (noise < 0) {
                    set(block, Material.STONE);
                } else if (noise > 0.6) {
                    set(block, Material.POLISHED_ANDESITE);
                } else {
                    set(block, Material.ANDESITE);
                }
            }
            if (Math.abs(noiseS) > 0.8) {
                List<BlockFace> hor = new ArrayList<>(4);
                for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                    if (context.faces.contains(face)) hor.add(face);
                }
                if (!hor.isEmpty()) {
                    BlockFace face = hor.get(context.random.nextInt(hor.size()));
                    Block torch = block.getRelative(face);
                    if (torch.isEmpty()) {
                        if (noiseS > 0.9) {
                            set(torch, Blocks.direct(Material.WALL_TORCH, face));
                        } else {
                            set(torch, Blocks.direct(Material.REDSTONE_WALL_TORCH, face));
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Dirt and clay floor with puddles of water and lily pads.
     * Slime stalactites.
     */
    boolean transformSwamp(Block block, Context context) {
        if (context.floor) {
            double noise = getNoise(block, 8.0);
            if (noise < 0) {
                // Water puddle
                boolean empty = false;
                for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                    if (context.faces.contains(face)) {
                        empty = true;
                        break;
                    }
                    Block nbor = block.getRelative(face);
                    if (!nbor.getType().isSolid() && !nbor.isLiquid()) {
                        empty = true;
                        break;
                    }
                }
                if (empty) {
                    // Border
                    set(block, Material.GRASS_BLOCK);
                    double noiseS = getNoise(block, 1.0);
                    if (noiseS > 0.8) {
                        spawnBrownMushroomCow(block.getLocation().add(0.5, 1.0, 0.5));
                    //     int len = 1 + context.random.nextInt(Math.min(3, context.height));
                    //     for (int i = 1; i <= len; i += 1) {
                    //         set(block, 0, i, 0, Material.SUGAR_CANE);
                    //     }
                    }
                } else {
                    Block below = block.getRelative(0, -1, 0);
                    double noiseBelow = getNoise(block, 6.0);
                    if (noiseBelow > 0.2) {
                        set(below, Material.CLAY);
                    } else {
                        set(below, Material.DIRT);
                    }
                    // Puddles with lilies and seagrass
                    double noiseS = getNoise(block, 1.0);
                    if (noiseS > 0.3) {
                        set(block, Material.SEAGRASS);
                    } else {
                        set(block, Material.WATER);
                    }
                    Block above = block.getRelative(0, 1, 0);
                    if (above.isEmpty()) {
                        double noiseAbove = getNoise(above, 1.0);
                        if (noiseAbove > 0.3) {
                            set(above, Material.LILY_PAD);
                        }
                    }
                }
            } else {
                // Land
                set(block, Material.GRASS_BLOCK);
                Block above = block.getRelative(0, 1, 0);
                if (above.isEmpty()) {
                    double noiseS = getNoise(above, 1.0);
                    // mushroom, orchid, grass, dead bush, sugar cane
                    if (noiseS < 0.5) {
                        if (noiseS > 0.4) {
                            set(above, Material.DEAD_BUSH);
                        } else if (noiseS > 0.3) {
                            set(above, Blocks.lower(Material.TALL_GRASS));
                            set(above, 0, 1, 0, Blocks.upper(Material.TALL_GRASS));
                        } else if (noiseS > 0.2) {
                            set(above, Material.GRASS);
                        } else if (noiseS > 0.1) {
                            set(above, Material.BLUE_ORCHID);
                        } else if (noiseS > 0.0) {
                            set(above, Material.BROWN_MUSHROOM);
                        } else if (noiseS > -0.1) {
                            spawnBrownMushroomCow(above.getLocation().add(0.5, 0, 0.5));
                            // int len = 1 + context.random.nextInt(Math.min(3, context.height));
                            // for (int i = 0; i < len; i += 1) {
                            //     set(above, 0, i, 0, Material.SUGAR_CANE);
                            // }
                        } else if (noiseS > -0.2) {
                            set(above, Material.DEAD_BUSH);
                        }
                    }
                }
            }
        } else if (context.wall) {
            double noise = getNoise(block, 8.0);
            if (noise > 0) {
                set(block, Material.SAND);
            } else {
                set(block, Material.DIRT);
            }
        } else if (context.ceiling) {
            double noiseS = getNoise(block, 1.0);
            if (noiseS > 0.4 && context.height > 1) {
                int len = 1 + context.random.nextInt(Math.min(4, context.height));
                for (int i = 0; i < len; i += 1) {
                    set(block, Material.SLIME_BLOCK);
                    block = block.getRelative(0, -1, 0);
                }
            } else {
                set(block, Blocks.leaves(Material.OAK_LEAVES));
                if (noiseS < -0.3) {
                    set(block, 0, 1, 0, Material.OAK_WOOD);
                }
                // Hanging vines
                for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                    if (!context.faces.contains(face)) continue;
                    Block nbor = block.getRelative(face);
                    int len = 1 + context.random.nextInt(context.height);
                    for (int i = 0; i < len && nbor.isEmpty(); i += 1) {
                        set(nbor, Blocks.facing(Material.VINE, face.getOppositeFace()));
                        nbor = nbor.getRelative(0, -1, 0);
                    }
                }
            }
        }
        return true;
    }

    void spawnBrownMushroomCow(Location loc) {
        loc.getWorld().spawn(loc, MushroomCow.class, mc -> {
                mc.setVariant(MushroomCow.Variant.BROWN);
            });
    }

    /**
     * Grassy floor with flowers. A natural look. Beehives.
     */
    boolean transformFlowers(Block block, Context context) {
        if (context.floor) {
            set(block, Material.GRASS_BLOCK);
            Block above = block.getRelative(0, 1, 0);
            if (above.isEmpty()) {
                double noiseS = getNoise(above, 1.0);
                if (noiseS > 0.2) {
                    double noise = getNoise(above, 8.0);
                    int flower = (int) (noise * 10.0);
                    switch (flower) {
                    case -8:
                        set(above, Material.DANDELION); break;
                    case -7:
                        set(above, Material.POPPY); break;
                    case -6:
                        set(above, Material.BLUE_ORCHID); break;
                    case -5:
                        set(above, Material.ALLIUM); break;
                    case -4:
                        set(above, Material.AZURE_BLUET); break;
                    case -3:
                        set(above, Material.RED_TULIP); break;
                    case -2:
                        set(above, Material.ORANGE_TULIP); break;
                    case -1:
                        set(above, Material.WHITE_TULIP); break;
                    case 0:
                        set(above, Material.PINK_TULIP); break;
                    case 1:
                        set(above, Material.OXEYE_DAISY); break;
                    case 2:
                        set(above, Material.CORNFLOWER); break;
                    case 3:
                        set(above, Material.LILY_OF_THE_VALLEY); break;
                    case 4:
                        set(above, Material.WITHER_ROSE); break;
                    case 5:
                        if (context.height < 2) break;
                        set(above, Blocks.lower(Material.SUNFLOWER));
                        set(above, 0, 1, 0, Blocks.upper(Material.SUNFLOWER));
                        break;
                    case 6:
                        if (context.height < 2) break;
                        set(above, Blocks.lower(Material.LILAC));
                        set(above, 0, 1, 0, Blocks.upper(Material.LILAC));
                        break;
                    case 7:
                        if (context.height < 2) break;
                        set(above, Blocks.lower(Material.ROSE_BUSH));
                        set(above, 0, 1, 0, Blocks.upper(Material.ROSE_BUSH));
                        break;
                    case 8:
                        if (context.height < 2) break;
                        set(above, Blocks.lower(Material.PEONY));
                        set(above, 0, 1, 0, Blocks.upper(Material.PEONY));
                        break;
                    default: break;
                    }
                } else if (noiseS < -0.5) {
                    set(above, Blocks.lower(Material.TALL_GRASS));
                    set(above, 0, 1, 0, Blocks.upper(Material.TALL_GRASS));
                } else if (noiseS < -0.2) {
                    set(above, Material.GRASS);
                }
            }
        } else if (context.ceiling) {
            double noise = getNoise(block, 8);
            if (noise < 0) {
                set(block, Material.DIRT);
            } else if (noise > 0.5) {
                set(block, Material.COARSE_DIRT);
            } else {
                set(block, Material.GRANITE);
            }
        } else if (context.wall) {
            double noise = getNoise(block, 8);
            if (noise < 0) {
                set(block, Material.SAND);
            } else if (noise > 0.5) {
                set(block, Material.ANDESITE);
            } else {
                set(block, Material.STONE);
            }
            double noiseS = getNoise(block, 1.0);
            if (noiseS > 0.95) {
                List<BlockFace> hor = new ArrayList<>(4);
                for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                    if (context.faces.contains(face)) hor.add(face);
                }
                if (!hor.isEmpty()) {
                    BlockFace face = hor.get(context.random.nextInt(hor.size()));
                    Block hive = block.getRelative(face);
                    if (hive.isEmpty()) {
                        set(hive, Blocks.direct(Material.BEE_NEST, face));
                        Beehive beehive = (Beehive) hive.getState();
                        Bee bee = (Bee) block.getWorld().spawnEntity(block.getLocation(),
                                                                     EntityType.BEE);
                        if (bee != null) {
                            beehive.addEntity(bee);
                            beehive.update();
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * Sand, gravel, clay. The ceiling is made of clay and diorite.
     */
    boolean transformRiver(Block block, Context context) {
        if (context.floor) {
            double noise = getNoise(block, 8);
            if (noise < -0.5) {
                set(block, Material.CLAY);
            } else if (noise > 0.5) {
                set(block, Material.SAND);
            } else {
                set(block, Material.DIRT);
            }
        } else if (context.ceiling || context.wall) {
            double noise = getNoise(block, 8);
            if (noise < 0) {
                set(block, Material.CLAY);
            } else {
                set(block, Material.DIORITE);
            }
        }
        return true;
    }

    boolean transformMesa(Block block, Context context) {
        if (context.floor) {
            double noise = getNoise(block, 8);
            if (noise > 0.5) {
                set(block, Material.RED_SANDSTONE);
            } else {
                set(block, Material.RED_SAND);
                Block above = block.getRelative(0, 1, 0);
                if (above.isEmpty()) {
                    double noiseS = getNoise(above, 1);
                    if (noiseS > 0.3) {
                        set(above, Material.DEAD_BUSH);
                    } else if (noiseS < -0.4) {
                        int len = 1 + context.random.nextInt(Math.min(3, context.height));
                        CACTUS:
                        for (int i = 0; i < len; i += 1) {
                            if (!above.isEmpty()) break;
                            for (BlockFace face : HORIZONTAL_NEIGHBORS) {
                                if (!above.getRelative(face).isEmpty()) break CACTUS;
                            }
                            set(above, Material.CACTUS);
                            above = above.getRelative(0, 1, 0);
                        }
                    }
                }
            }
        } else if (context.ceiling) {
            set(block, Material.RED_SANDSTONE);
        } else if (context.wall) {
            switch (block.getY() % 7) {
            case 0: set(block, Material.RED_TERRACOTTA); break;
            case 1: set(block, Material.ORANGE_TERRACOTTA); break;
            case 2: set(block, Material.YELLOW_TERRACOTTA); break;
            case 3: set(block, Material.WHITE_TERRACOTTA); break;
            case 4: set(block, Material.LIGHT_GRAY_TERRACOTTA); break;
            case 5: set(block, Material.BROWN_TERRACOTTA); break;
            case 6: set(block, Material.TERRACOTTA); break;
            default: break;
            }
        }
        return true;
    }

    int getIntNoise(Block block, double scale, int factor) {
        double noise = getNoise(block, scale);
        int val = (int) (noise * factor);
        return val;
    }

    double getNoise(Block block, double scale) {
        return noiseGenerator.noise(block.getX() / scale,
                                    block.getY() / scale,
                                    block.getZ() / scale);
    }

    void onChunkDecorate(Chunk chunk) {
        transformChunk(chunk);
    }
}
