package com.islesplusplus.map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrailsTest {
    @Test
    void testRecordSpacing() {
        Trails.Trail t = new Trails.Trail();
        Trails.record(t, 0.5, 64, 0.5);
        Trails.record(t, 0.9, 64, 0.2);   // same block, skipped
        Trails.record(t, 1.5, 64, 0.5);
        Trails.record(t, 2.5, 64.5, 0.5); // stair step: half a block up, not a whole one
        Trails.record(t, 2.6, 64.3, 0.6); // snow layer rounds up to the half block, same point
        Trails.record(t, 3.5, 65, 0.5);
        assertEquals(4, t.points.size());
        assertArrayEquals(new double[]{2, 64.5, 0}, t.points.get(2));
        assertArrayEquals(new double[]{3, 65, 0}, t.points.get(3));
    }

    @Test
    void testSaveLoad(@TempDir Path dir) throws IOException {
        Trails.Trail t = new Trails.Trail();
        t.name = "Mine run";
        t.points.add(new double[]{1, 2, 3});
        t.points.add(new double[]{-4, 5.5, -6});
        t.nodes.add(new Trails.Node("Cave", 1.5, 3.62, 3.5));
        Trails.Trail odd = new Trails.Trail();
        odd.name = "To: the farm?";
        Trails.writeAll(dir, List.of(t, odd));
        assertTrue(Files.exists(dir.resolve("Mine run.json")));
        assertTrue(Files.exists(dir.resolve("To_ the farm_.json")));

        List<Trails.Trail> back = Trails.readAll(dir);
        assertEquals(2, back.size());
        Trails.Trail r = back.stream().filter(x -> x.name.equals("Mine run")).findFirst().orElseThrow();
        assertArrayEquals(new double[]{-4, 5.5, -6}, r.points.get(1));
        assertEquals(new Trails.Node("Cave", 1.5, 3.62, 3.5), r.nodes.get(0));

        // renamed: the old file goes, deleted: its file goes
        t.name = "Mine";
        Trails.writeAll(dir, List.of(t));
        try (var files = Files.list(dir)) {
            assertEquals(List.of("Mine.json"), files.map(f -> f.getFileName().toString()).toList());
        }
    }

    @Test
    void testOldAllInOneFileStillReads() {
        Trails.Store s = Trails.parse("{\"paths\":[{\"name\":\"Old\",\"points\":[[1,64,2]]},{\"points\":[]}],\"active\":\"Old\"}");
        assertEquals("Old", s.active());
        assertEquals(1, s.paths().size());
        assertArrayEquals(new double[]{1, 64, 2}, s.paths().get(0).points.get(0));
    }

    @Test
    void testSmoothNeverCutsThroughBlocks() {
        // flat, up a block onto x=2, flat, down a block onto x=4, flat
        Trails.Trail t = new Trails.Trail();
        int[][] walk = {{0, 64}, {1, 64}, {2, 65}, {3, 65}, {4, 64}, {5, 64}};
        for (int[] p : walk) t.points.add(new double[]{p[0], p[1], 0});
        double[] s = Trails.smooth(t);
        assertTrue(s.length / 3 > walk.length, "corners got cut into more points");
        assertEquals(0.5, s[0]);
        assertEquals(5.5, s[s.length - 3]);
        for (int i = 0; i < s.length; i += 3) {
            // over the raised blocks (x 2..4) the line is on top of them
            if (s[i] > 2 && s[i] < 4) assertTrue(s[i + 1] >= 65, "cuts into the step at x=" + s[i] + " y=" + s[i + 1]);
        }
        t.points.add(new double[]{6, 64, 0});
        assertEquals(6.5, Trails.smooth(t)[Trails.smooth(t).length - 3], "worked out again after a new point");
    }

    @Test
    void testJumpGapSlopesInsteadOfFloating() {
        // jumped up some stairs: nothing recorded in the air between x=1 and the landing at x=4, two blocks up
        Trails.Trail t = new Trails.Trail();
        int[][] walk = {{0, 64}, {1, 64}, {4, 66}, {5, 66}};
        for (int[] p : walk) t.points.add(new double[]{p[0], p[1], 0});
        double[] s = Trails.smooth(t);
        for (int i = 0; i < s.length; i += 3) {
            // the stairs go up along the gap, so the line should too, not climb at the start and float across
            if (s[i] > 2 && s[i] < 4) assertEquals(64 + (s[i] - 1.5) * 2 / 3, s[i + 1], 0.1, "floats over the gap at x=" + s[i]);
        }
    }

    @Test
    void testStraightDiagonalWalkIsStraight() {
        // walking in a straight line at a shallow angle crosses blocks in a staircase: 2 across, 1 over, ...
        Trails.Trail t = new Trails.Trail();
        for (int x = 0; x <= 20; x++) Trails.record(t, x + 0.5, 64, x / 2 + 0.5);
        double[] s = Trails.smooth(t);
        double x0 = s[0], z0 = s[2], dx = s[s.length - 3] - x0, dz = s[s.length - 1] - z0, len = Math.hypot(dx, dz);
        for (int i = 0; i < s.length; i += 3) {
            double off = Math.abs((s[i] - x0) * dz - (s[i + 2] - z0) * dx) / len;
            assertTrue(off < 0.1, "zigzags " + off + " off the line at x=" + s[i]);
        }
    }

    @Test
    void testLineStaysOverTheBlocksWalked() {
        // 10 east, 10 north, then a curve back: the line may round corners but not cut across blocks never walked on
        Trails.Trail t = new Trails.Trail();
        java.util.Set<Long> walked = new java.util.HashSet<>();
        int[][] walk = new int[31][];
        for (int i = 0; i <= 10; i++) walk[i] = new int[]{i, 0};
        for (int i = 1; i <= 10; i++) walk[10 + i] = new int[]{10, i};
        for (int i = 1; i <= 10; i++) walk[20 + i] = new int[]{10 - i, 10 + i / 3};
        for (int[] p : walk) {
            Trails.record(t, p[0] + 0.5, 64, p[1] + 0.5);
            walked.add((long) p[0] << 32 | (p[1] & 0xffffffffL));
        }
        double[] s = Trails.smooth(t);
        for (int i = 0; i < s.length; i += 3) {
            boolean near = false;
            for (int dx = -1; dx <= 1 && !near; dx++)
                for (int dz = -1; dz <= 1 && !near; dz++) {
                    int bx = (int) Math.floor(s[i] + dx * 0.3), bz = (int) Math.floor(s[i + 2] + dz * 0.3);
                    near = walked.contains((long) bx << 32 | (bz & 0xffffffffL));
                }
            assertTrue(near, "off the walked blocks at " + s[i] + ", " + s[i + 2]);
        }
    }

    @Test
    void testChevronsPointAlongTheWalk() {
        // 10 blocks east, then a step straight up (no flat distance), then 10 blocks back west
        double[] pts = {0, 64, 0, 10, 64, 0, 10, 65, 0, 0, 65, 0};
        List<double[]> c = Trails.chevrons(pts, 4);
        assertEquals(5, c.size());   // at 2, 6, 10 walked, then 14 and 18
        assertArrayEquals(new double[]{2, 64, 0, 1, 0}, c.get(0));
        assertEquals(10, c.get(2)[0], 1e-9);
        assertArrayEquals(new double[]{6, 65, 0, -1, 0}, c.get(3));
        assertTrue(Trails.chevrons(new double[]{1, 2, 3}, 4).isEmpty());
    }

    @Test
    void testBadJson() {
        assertTrue(Trails.parse("{}").paths().isEmpty());
        List<Trails.Trail> back = Trails.parse("{\"paths\":[{\"name\":\"a\",\"points\":[[1,2],[1,2,3]]},{\"points\":[]}]}").paths();
        assertEquals(1, back.size());
        assertEquals(1, back.get(0).points.size());
        assertTrue(back.get(0).nodes.isEmpty());
    }
}
