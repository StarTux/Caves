package com.cavetale.caves;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.junit.Test;

public final class BiomesTest {
    @Test
    public void test() {
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
