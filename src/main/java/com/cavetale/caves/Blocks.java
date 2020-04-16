package com.cavetale.caves;

import java.util.Random;
import java.util.function.Function;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Lantern;
import org.bukkit.block.data.type.Leaves;

public final class Blocks {
    private Blocks() { }

    /**
     * Orient if possible.
     */
    public static BlockData oriented(Material mat, Axis axis) {
        BlockData data = mat.createBlockData();
        if (data instanceof Orientable) {
            ((Orientable) data).setAxis(axis);
        }
        return data;
    }

    public static BlockData direct(Material mat, BlockFace face) {
        BlockData data = mat.createBlockData();
        if (data instanceof Directional) {
            ((Directional) data).setFacing(face);
        }
        return data;
    }

    public static BlockData facing(Material mat, BlockFace face) {
        BlockData data = mat.createBlockData();
        if (data instanceof MultipleFacing) {
            ((MultipleFacing) data).setFace(face, true);
        }
        return data;
    }

    public static BlockData leaves(Material mat) {
        BlockData data = mat.createBlockData();
        if (data instanceof Leaves) {
            ((Leaves) data).setDistance(1);
        }
        return data;
    }

    public static BlockData upper(Material mat) {
        BlockData data = mat.createBlockData();
        if (data instanceof Bisected) {
            ((Bisected) data).setHalf(Bisected.Half.TOP);
        }
        return data;
    }

    public static BlockData lower(Material mat) {
        BlockData data = mat.createBlockData();
        if (data instanceof Bisected) {
            ((Bisected) data).setHalf(Bisected.Half.BOTTOM);
        }
        return data;
    }

    public static BlockData waterlogged(Material mat) {
        BlockData data = mat.createBlockData();
        if (data instanceof Waterlogged) {
            ((Waterlogged) data).setWaterlogged(true);
        }
        return data;
    }

    public static BlockData hangingLantern(boolean hanging) {
        BlockData data = Material.LANTERN.createBlockData();
        ((Lantern) data).setHanging(true);
        return data;
    }

    public static <T> T randomArray(T[] ls, Random random) {
        return ls[random.nextInt(ls.length)];
    }

    public static void set(Block block, Material material) {
        block.setType(material, false);
    }

    public static void set(Block block, BlockData data) {
        block.setBlockData(data, false);
    }

    public static void set(Block block, int x, int y, int z, Material material) {
        block.getRelative(x, y, z).setType(material, false);
    }

    public static void set(Block block, int x, int y, int z, BlockData data) {
        block.getRelative(x, y, z).setBlockData(data, false);
    }

    public static boolean makeRaftersBelow(Block block, int interval, int x, int z,
                                           Function<Block, Material> fun) {
        boolean rafterX = block.getX() % interval == x;
        boolean rafterZ = block.getZ() % interval == z;
        Block pillar = block.getRelative(0, -1, 0);
        if (rafterX && rafterZ) {
            while (pillar.isEmpty() || pillar.isLiquid()) {
                Material material = fun.apply(pillar);
                BlockData data = oriented(material, Axis.Y);
                set(pillar, data);
                pillar = pillar.getRelative(0, -1, 0);
            }
            return true;
        } else if (rafterX) {
            set(pillar, oriented(fun.apply(pillar), Axis.Z));
            return true;
        } else if (rafterZ) {
            set(pillar, oriented(fun.apply(pillar), Axis.X));
            return true;
        } else {
            return false;
        }
    }
}
