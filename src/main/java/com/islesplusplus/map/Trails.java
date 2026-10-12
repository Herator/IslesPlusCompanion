package com.islesplusplus.map;

import com.google.gson.Gson;
import com.islesplus.logging.IslesLog;
import com.islesplus.render.WorldTagRenderer;
import com.islesplus.ui.Theme;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// /path: record a route with named nodes, save it, show one on the maps and in the world
public final class Trails {
    public record Node(String name, double x, double y, double z) {}

    public static final class Trail {
        public String name;
        // feet positions, one per block walked. y rounded up to the half block so it sits on slabs and stairs
        public List<double[]> points = new ArrayList<>();
        public List<Node> nodes = new ArrayList<>();
        // the smoothed line through the points (x, y, z, x, y, z...), worked out again when a point is added
        private transient double[] smooth;
        private transient List<double[]> arrows;
        private transient int smoothedFor = -1;
    }

    private static final int SAVED = Theme.HUD_VERDIGRIS, RECORDING = Theme.HUD_GOLD;
    private static final int WORLD_RANGE = 96, LABEL_RANGE = 64;
    // direction arrows on the ground, this many blocks apart
    private static final double WORLD_CHEVRONS = 4;
    private static final Gson GSON = new Gson();

    // the one being recorded, or ended and waiting for /path save or /path discard
    public static Trail current;
    public static boolean recording;
    private static final List<Trail> saved = new ArrayList<>();
    // name of the started path, null = none
    private static String active;
    private static boolean loaded;

    private Trails() {}

    private static Path pathsDir() { return IslesMap.localDir().resolve("paths"); }
    private static Path activeFile() { return IslesMap.localDir().resolve("active_path.txt"); }

    public static List<Trail> all() {
        if (!loaded) {
            loaded = true;
            try {
                Path old = IslesMap.localDir().resolve("paths.json");
                if (Files.exists(old) && !Files.exists(pathsDir())) {
                    // before each path had its own file they were all in paths.json: split it up, keep the old one as .old
                    Store s = parse(Files.readString(old, StandardCharsets.UTF_8));
                    writeAll(pathsDir(), s.paths());
                    active = s.active();
                    saveActive();
                    Files.move(old, IslesMap.localDir().resolve("paths.json.old"));
                }
                saved.addAll(readAll(pathsDir()));
                if (Files.exists(activeFile())) active = Files.readString(activeFile(), StandardCharsets.UTF_8).trim();
            } catch (IOException | RuntimeException e) {
                IslesLog.runtimeInfo("[Isles++] saved paths unreadable: " + e);
            }
        }
        return saved;
    }

    // the old all-in-one paths.json
    record Store(List<Trail> paths, String active) {}

    static Store parse(String json) {
        Store s = GSON.fromJson(json, Store.class);
        List<Trail> out = new ArrayList<>();
        if (s != null && s.paths() != null) for (Trail t : s.paths()) if (valid(t)) out.add(t);
        return new Store(out, s == null ? null : s.active());
    }

    // fills in what a hand-edited file left out; false if there's no name to go by
    private static boolean valid(Trail t) {
        if (t == null || t.name == null || t.name.isBlank()) return false;
        if (t.points == null) t.points = new ArrayList<>();
        if (t.nodes == null) t.nodes = new ArrayList<>();
        t.points.removeIf(p -> p == null || p.length != 3);
        return true;
    }

    static List<Trail> readAll(Path dir) throws IOException {
        List<Trail> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (var files = Files.list(dir)) {
            for (Path f : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                try {
                    Trail t = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Trail.class);
                    if (valid(t) && find(out, t.name) == null) out.add(t);
                } catch (RuntimeException e) {
                    IslesLog.runtimeInfo("[Isles++] skipped unreadable path " + f.getFileName() + ": " + e);
                }
            }
        }
        return out;
    }

    // one file per path, named after it; files of deleted or renamed paths are removed
    static void writeAll(Path dir, List<Trail> list) throws IOException {
        Files.createDirectories(dir);
        Set<String> written = new HashSet<>();
        for (Trail t : list) {
            String base = fileName(t.name), file = base + ".json";
            for (int n = 2; !written.add(file.toLowerCase()); n++) file = base + " (" + n + ").json";
            Files.writeString(dir.resolve(file), GSON.toJson(t), StandardCharsets.UTF_8);
        }
        try (var files = Files.list(dir)) {
            for (Path f : files.toList())
                if (f.toString().endsWith(".json") && !written.contains(f.getFileName().toString().toLowerCase())) Files.delete(f);
        }
    }

    // the name with what Windows won't have in a file name swapped for _
    static String fileName(String name) {
        String s = name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("[. ]+$", "");
        return s.isEmpty() ? "_" : s;
    }

    private static void save() {
        try {
            writeAll(pathsDir(), saved);
            saveActive();
        } catch (IOException e) {
            IslesLog.runtimeInfo("[Isles++] could not save paths: " + e);
        }
    }

    // starting or stopping a path only changes which one is shown, no need to write the paths again
    private static void saveActive() throws IOException {
        if (active == null) Files.deleteIfExists(activeFile());
        else Files.writeString(activeFile(), active, StandardCharsets.UTF_8);
    }

    private static void saveActiveOrLog() {
        try {
            saveActive();
        } catch (IOException e) {
            IslesLog.runtimeInfo("[Isles++] could not save paths: " + e);
        }
    }

    private static Trail find(List<Trail> list, String name) {
        return list.stream().filter(t -> t.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public static Trail find(String name) {
        return find(all(), name);
    }

    public static Trail active() {
        return active == null ? null : find(active);
    }

    public static String nextName() {
        for (int n = 1; ; n++) {
            String name = "Path " + n;
            if (find(name) == null) return name;
        }
    }

    // a point per block walked, so a one block bump between two points doesn't get cut through
    static void record(Trail t, double x, double y, double z) {
        double[] p = {Math.floor(x), Math.ceil(y * 2 - 0.02) / 2, Math.floor(z)};
        if (t.points.isEmpty() || !java.util.Arrays.equals(t.points.getLast(), p)) t.points.add(p);
    }

    public static void tick(MinecraftClient client) {
        if (recording && client.player != null && grounded(client.player))
            record(current, client.player.getX(), client.player.getY(), client.player.getZ());
    }

    // mid-jump points would make the line hop up and down, so only record standing on something
    private static boolean grounded(ClientPlayerEntity p) {
        return p.isOnGround() || p.isClimbing() || p.isTouchingWater();
    }

    // --- commands, each returns the chat message ---

    public static String record(ClientPlayerEntity player) {
        if (recording) return "Already recording a path. /path end or /path save to finish it.";
        if (current != null) return "The last path isn't saved yet. /path save to keep it or /path discard to throw it away.";
        current = new Trail();
        recording = true;
        record(current, player.getX(), player.getY(), player.getZ());
        return "Recording a path. /path node add <name> to drop a node, /path end or /path save when you're done.";
    }

    public static String start(String name) {
        Trail t = find(name);
        if (t == null) return "No path called " + name + ".";
        active = t.name;
        saveActiveOrLog();
        return "Started path: " + t.name + ".";
    }

    public static String stop() {
        if (active() == null) return "No path started.";
        String name = active;
        active = null;
        saveActiveOrLog();
        return "Stopped path: " + name + ".";
    }

    public static String addNode(ClientPlayerEntity player, String name) {
        if (!recording) return "Not recording a path. /path record first.";
        if (grounded(player)) record(current, player.getX(), player.getY(), player.getZ());
        current.nodes.add(new Node(name, player.getX(), player.getEyeY(), player.getZ()));
        return "Node added: " + name + ".";
    }

    public static String end() {
        if (!recording) return "Not recording a path.";
        recording = false;
        return "Path ended with " + current.nodes.size() + " nodes. /path save [name] to keep it or /path discard to throw it away.";
    }

    public static String save(String name) {
        if (current == null) return "No path to save. /path record first.";
        if (name == null || name.isBlank()) name = nextName();
        if (find(name) != null) return "There's already a path called " + name + ". Pick another name or /path delete it first.";
        current.name = name;
        all().add(current);
        save();
        current = null;
        recording = false;
        return "Path saved: " + name + ".";
    }

    public static String discard() {
        if (current == null) return "No path to discard.";
        current = null;
        recording = false;
        return "Path thrown away.";
    }

    public static String rename(Trail t, String name) {
        Trail other = find(name);
        if (other != null && other != t) return "There's already a path called " + name + ".";
        if (t.name.equalsIgnoreCase(String.valueOf(active))) active = name;
        String old = t.name;
        t.name = name;
        save();
        return "Path renamed: " + old + " -> " + name + ".";
    }

    public static String delete(String name) {
        Trail t = find(name);
        if (t == null) return "No path called " + name + ".";
        saved.remove(t);
        if (t.name.equalsIgnoreCase(String.valueOf(active))) active = null;
        save();
        return "Path deleted: " + t.name + ".";
    }

    // --- maps ---

    static void draw(DrawContext ctx, List<MapPois.Shown> drawn, int x, int y, int w, int h, double cx, double cz, float ppb) {
        Trail started = active();
        if (started == null && current == null) return;
        // moved by the same part of a pixel as the map tiles, else the line wobbles as you walk
        double ox = x + w / 2.0 - cx * ppb, oy = y + h / 2.0 - cz * ppb;
        ctx.enableScissor(x, y, x + w, y + h);
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) ox, (float) oy);
        double left = x - ox, top = y - oy;
        if (started != null) mapLine(ctx, started, left, top, w, h, ppb, SAVED);
        if (current != null) mapLine(ctx, current, left, top, w, h, ppb, RECORDING);
        ctx.getMatrices().popMatrix();
        ctx.disableScissor();
        if (started != null) mapNodes(ctx, drawn, started, ox, oy, x, y, w, h, ppb, SAVED);
        if (current != null) mapNodes(ctx, drawn, current, ox, oy, x, y, w, h, ppb, RECORDING);
    }

    // how far back from the edge a step's ramp is at the top, so the rounded-off corner stays clear of the block
    private static final double STEP_CLEAR = 0.25;

    // corner cutting (Chaikin) passes: each one halves the corners' angle, 3 reads as a curve
    private static final int SMOOTHING = 3;
    // how far in from a long straight bit's ends a corner is cut, in blocks
    private static final double CUT = 0.5;
    // how far over the edge of a walked block the line may go: a diagonal walk clips the corners between blocks
    private static final double GRAZE = 0.3;
    // most points one straight stretch can skip: the check is quadratic and reruns every block while recording
    private static final int STRAIGHT_MAX = 32;

    // the line through the block centres, corners rounded, steps ramped without cutting into the higher block
    static double[] smooth(Trail t) {
        if (t.smoothedFor == t.points.size()) return t.smooth;
        List<double[]> pts = new ArrayList<>();
        double[] prev = null;
        for (double[] p : straightened(t.points)) {
            // neighbouring blocks only: across a jump gap a straight slope follows stairs better
            if (prev != null && p[1] != prev[1] && Math.abs(p[0] - prev[0]) <= 1 && Math.abs(p[2] - prev[2]) <= 1) {
                boolean up = p[1] > prev[1];
                double f = up ? 0.5 - STEP_CLEAR : 0.5 + STEP_CLEAR;
                pts.add(new double[]{prev[0] + 0.5 + (p[0] - prev[0]) * f, Math.max(p[1], prev[1]), prev[2] + 0.5 + (p[2] - prev[2]) * f});
            }
            pts.add(new double[]{p[0] + 0.5, p[1], p[2] + 0.5});
            prev = p;
        }
        for (int pass = 0; pass < SMOOTHING && pts.size() > 2; pass++) {
            List<double[]> cut = new ArrayList<>(pts.size() * 2);
            cut.add(pts.getFirst());
            for (int i = 0; i + 1 < pts.size(); i++) {
                // a quarter in from each end, at most CUT on long bits so corners don't round over unwalked blocks
                double f = Math.min(0.25, CUT / dist(pts.get(i), pts.get(i + 1)));
                cut.add(lerp(pts.get(i), pts.get(i + 1), f));
                cut.add(lerp(pts.get(i), pts.get(i + 1), 1 - f));
            }
            cut.add(pts.getLast());
            pts = cut;
        }
        double[] out = new double[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++) System.arraycopy(pts.get(i), 0, out, i * 3, 3);
        t.smooth = out;
        t.arrows = chevrons(out, WORLD_CHEVRONS);
        t.smoothedFor = t.points.size();
        return out;
    }

    // an arrow every `spacing` flat blocks along the line, as x, y, z, dx, dz
    static List<double[]> chevrons(double[] pts, double spacing) {
        List<double[]> out = new ArrayList<>();
        double next = spacing / 2;   // the first one a little way in, not on the start
        for (int i = 3; i < pts.length; i += 3) {
            double dx = pts[i] - pts[i - 3], dz = pts[i + 2] - pts[i - 1], len = Math.sqrt(dx * dx + dz * dz);
            if (len == 0) continue;
            for (; next <= len; next += spacing) {
                double f = next / len;
                out.add(new double[]{pts[i - 3] + dx * f, pts[i - 2] + (pts[i + 1] - pts[i - 2]) * f, pts[i - 1] + dz * f, dx / len, dz / len});
            }
            next -= len;
        }
        return out;
    }

    // on flat stretches skip points the line can go straight past over walked blocks, else diagonals wobble
    private static List<double[]> straightened(List<double[]> points) {
        List<double[]> out = new ArrayList<>();
        for (int from = 0; from < points.size(); ) {
            int to = from;
            Set<Long> walked = new HashSet<>();
            walked.add(cell((int) points.get(from)[0], (int) points.get(from)[2]));
            while (to + 1 < points.size() && points.get(to + 1)[1] == points.get(from)[1]) {
                to++;
                walked.add(cell((int) points.get(to)[0], (int) points.get(to)[2]));
            }
            out.add(points.get(from));
            for (int a = from, b; a < to; a = b) {
                b = a + 1;
                while (b < to && b - a < STRAIGHT_MAX && overWalked(points.get(a), points.get(b + 1), walked)) b++;
                out.add(points.get(b));
            }
            from = to + 1;
        }
        return out;
    }

    // the straight line between two block centres only crosses (or grazes) blocks walked on
    private static boolean overWalked(double[] a, double[] b, Set<Long> walked) {
        double dx = b[0] - a[0], dz = b[2] - a[2];
        int n = (int) Math.ceil(Math.hypot(dx, dz) * 4);
        for (int k = 1; k < n; k++) {
            double x = a[0] + 0.5 + dx * k / n, z = a[2] + 0.5 + dz * k / n;
            if (!grazesWalked(x, z, walked)) return false;
        }
        return true;
    }

    private static final int[][] GRAZE_DIRS = {{0, 0}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}};

    private static boolean grazesWalked(double x, double z, Set<Long> walked) {
        for (int[] d : GRAZE_DIRS)
            if (walked.contains(cell((int) Math.floor(x + d[0] * GRAZE), (int) Math.floor(z + d[1] * GRAZE)))) return true;
        return false;
    }

    private static long cell(int x, int z) { return (long) x << 32 | (z & 0xffffffffL); }

    private static double dist(double[] a, double[] b) { return Math.sqrt(sq(b[0] - a[0]) + sq(b[1] - a[1]) + sq(b[2] - a[2])); }

    private static double[] lerp(double[] a, double[] b, double f) {
        return new double[]{a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f, a[2] + (b[2] - a[2]) * f};
    }

    // left/top: the map's corner in map space
    private static void mapLine(DrawContext ctx, Trail t, double left, double top, int w, int h, float ppb, int argb) {
        double[] pts = smooth(t);
        double lx = 0, ly = 0;
        boolean first = true;
        for (int i = 0; i < pts.length; i += 3) {
            double sx = pts[i] * ppb, sy = pts[i + 2] * ppb;
            if (!first) {
                double dx = sx - lx, dy = sy - ly;
                if (dx * dx + dy * dy < 1) continue;   // zoomed out: wait until it's moved a pixel
                boolean off = Math.max(sx, lx) < left || Math.min(sx, lx) > left + w || Math.max(sy, ly) < top || Math.min(sy, ly) > top + h;
                if (!off) {
                    int n = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
                    for (int k = 0; k < n; k++) {
                        int px = (int) Math.round(lx + dx * k / n), py = (int) Math.round(ly + dy * k / n);
                        ctx.fill(px - 1, py - 1, px + 1, py + 1, argb);
                    }
                }
            }
            lx = sx;
            ly = sy;
            first = false;
        }
    }

    private static void mapNodes(DrawContext ctx, List<MapPois.Shown> drawn, Trail t, double ox, double oy, int x, int y, int w, int h,
                                 float ppb, int argb) {
        String path = t.name != null ? t.name : "Recording";
        for (Node n : t.nodes) {
            int mx = (int) Math.round(n.x() * ppb), my = (int) Math.round(n.z() * ppb);
            int sx = (int) Math.round(ox + mx), sy = (int) Math.round(oy + my);   // on screen, for the hover
            if (sx < x + 2 || sx > x + w - 3 || sy < y + 2 || sy > y + h - 3) continue;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((float) ox, (float) oy);
            ctx.fill(mx - 2, my - 2, mx + 3, my + 3, Theme.HUD_SHADOW);
            ctx.fill(mx - 1, my - 1, mx + 2, my + 2, argb);
            ctx.getMatrices().popMatrix();
            int bx = (int) Math.floor(n.x()), by = (int) Math.floor(n.y()), bz = (int) Math.floor(n.z());
            drawn.add(new MapPois.Shown(() -> List.of(Text.literal(n.name()).withColor(MapPois.NAME),
                Text.literal(path).withColor(MapPois.COUNT), Text.empty(),
                Text.literal("Click to navigate!").withColor(MapPois.NAVIGATE)), bx, by, bz, sx, sy));
        }
    }

    // --- world: the line on the ground + node labels at head height ---

    public static void render(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!IslesMap.available() || client.world == null || ctx.consumers() == null || ctx.matrices() == null) return;
        Trail started = active();
        if (started == null && current == null) return;
        Camera camera = client.gameRenderer.getCamera();
        Vec3d cam = camera.getCameraPos();
        if (started != null) renderTrail(ctx, client, camera, cam, started, SAVED);
        if (current != null) renderTrail(ctx, client, camera, cam, current, RECORDING);
        if (ctx.consumers() instanceof VertexConsumerProvider.Immediate immediate) immediate.draw();
    }

    private static void renderTrail(WorldRenderContext ctx, MinecraftClient client, Camera camera, Vec3d cam, Trail t, int argb) {
        MatrixStack m = ctx.matrices();
        MatrixStack.Entry e = m.peek();
        VertexConsumer vc = ctx.consumers().getBuffer(RenderLayers.lines());
        double[] p = smooth(t);
        for (int i = 3; i < p.length; i += 3) {
            if (sq(p[i - 3] - cam.x) + sq(p[i - 1] - cam.z) > WORLD_RANGE * WORLD_RANGE) continue;
            line(vc, e, (float) (p[i - 3] - cam.x), (float) (p[i - 2] + 0.1 - cam.y), (float) (p[i - 1] - cam.z),
                (float) (p[i] - cam.x), (float) (p[i + 1] + 0.1 - cam.y), (float) (p[i + 2] - cam.z), argb);
        }
        for (double[] c : t.arrows) {
            if (sq(c[0] - cam.x) + sq(c[2] - cam.z) > WORLD_RANGE * WORLD_RANGE) continue;
            float dx = (float) c[3], dz = (float) c[4];
            float tx = (float) (c[0] - cam.x) + dx * 0.25f, ty = (float) (c[1] + 0.1 - cam.y), tz = (float) (c[2] - cam.z) + dz * 0.25f;
            for (int side = -1; side <= 1; side += 2)
                line(vc, e, tx - dx * 0.5f - dz * 0.35f * side, ty, tz - dz * 0.5f + dx * 0.35f * side, tx, ty, tz, argb);
        }
        for (Node n : t.nodes) {
            if (sq(n.x() - cam.x) + sq(n.y() - cam.y) + sq(n.z() - cam.z) > LABEL_RANGE * LABEL_RANGE) continue;
            WorldTagRenderer.drawTag(client, m, cam, camera.getYaw(), camera.getPitch(),
                n.x(), n.y(), n.z(), "● " + n.name(), 0xFF000000 | argb, 0, 1f, ctx.consumers());
        }
    }

    private static void line(VertexConsumer vc, MatrixStack.Entry e, float x0, float y0, float z0, float x1, float y1, float z1, int argb) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len == 0) return;
        vc.vertex(e, x0, y0, z0).color(argb).normal(e, dx / len, dy / len, dz / len).lineWidth(3f);
        vc.vertex(e, x1, y1, z1).color(argb).normal(e, dx / len, dy / len, dz / len).lineWidth(3f);
    }

    private static double sq(double v) { return v * v; }
}
