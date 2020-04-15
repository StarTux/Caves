package com.cavetale.caves;

import java.util.Random;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.type.Leaves;

public final class Blocks {
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

    public static <T> T randomArray(T[] ls, Random random) {
        return ls[random.nextInt(ls.length)];
    }
}
