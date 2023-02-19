package com.cavetale.caves;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.bukkit.block.BlockFace;

@RequiredArgsConstructor
final class Context {
    protected final List<Runnable> deferredActions;
    protected final Random random;
    protected Set<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
    protected int height;
    protected boolean floor;
    protected boolean ceiling;
    protected boolean wall;
    protected boolean horizontal;
}
