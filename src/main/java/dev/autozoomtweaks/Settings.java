package dev.autozoomtweaks;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import se.krka.kahlua.integration.annotations.LuaMethod;
import zombie.ZomboidFileSystem;
import zombie.core.Core;

// Zoom levels per player slot and situation in ~/Zomboid/AutoZoomTweaks.properties.
// The file is re-read when it changes (checked once a second), a missing file is created with
// defaults. The Options window (lua/client/AutoZoomTweaks/OptionsRow.lua) reads and writes it
// through the Lua globals below. Auto zoom on/off is not here: it stays the vanilla flag
// (Core.setAutoZoom, options.ini "autozoom=").
public final class Settings {
    private static final String FILE_NAME = "AutoZoomTweaks.properties";
    private static final long CHECK_INTERVAL_MS = 1000;

    public static final int PLAYERS = 4;
    public static final int INDOORS = 0;
    public static final int MELEE = 1;
    public static final int OUTDOORS = 2;
    public static final int VEHICLE = 3;
    public static final String[] SITUATIONS = {"indoors", "melee", "outdoors", "vehicle"};
    // MultiTextureFBO2.zoomLevelsDefault, the only levels the combos offer.
    private static final int[] ALLOWED_PERCENT = {25, 50, 75, 100, 125, 150, 175, 200, 225, 250};

    private static final String DEFAULTS = """
            # Auto Zoom Tweaks settings. Re-read automatically while the game runs.
            # Delete the file to restore defaults. The levels are also set in the game:
            # Options -> User interface -> Camera, the "Auto-zoom" table (turning auto zoom on/off is there too).

            # Zoom level in percent per player slot (player1 = the first splitscreen player) and
            # situation: 25 50 75 100 125 150 175 200 225 250 (larger = farther, more of the map).
            # indoors:  the player's square is not outside.
            # melee:    outside, not running, a zombie closer than 6 tiles targets the player or
            #           the player was targeted in the last 4 seconds.
            # outdoors: everything else outside.
            # vehicle:  driving or a passenger: the outdoors level standing still, growing with
            #           speed up to this level at vehicleSpeedKmh.
            # Defaults: what vanilla auto zoom did with the "Zoom levels" option at the time the
            # file was created: its closest level indoors and in melee, its farthest outdoors
            # and in a vehicle.
            %s
            # Speed at which the vehicle level is reached, km/h.
            vehicleSpeedKmh=80
            """;

    // [player][situation], zoom factor (1.0 = 100%).
    private static volatile float[][] levels;
    public static volatile float vehicleSpeedKmh = 80f;

    private static Path path;
    private static long lastCheck;
    private static long lastModified = Long.MIN_VALUE;

    private static int[] defaultPercent;

    private Settings() {}

    /**
     * Defaults from the vanilla "Zoom levels" option (options.ini zoomLevels2x; the Options window
     * no longer shows it, see OptionsRow.lua): vanilla auto zoom goes to the closest of those
     * levels indoors and in melee (MultiTextureFBO2.zoomedInLevel), to the farthest outdoors and
     * always in a vehicle (zoomedOutLevel, getMaxZoom). Parsed like setZoomLevelsFromOption: 100%
     * is always in, an empty option means every level.
     */
    private static int[] defaultPercent() {
        if (defaultPercent != null) {
            return defaultPercent;
        }
        int min = 100;
        int max = 100;
        String option = null;
        try {
            option = Core.getInstance().getOptionZoomLevels2x();
        } catch (RuntimeException e) {
            Log.error("can't read the zoom levels option", e);
        }
        boolean any = false;
        if (option != null) {
            for (String part : option.split(";")) {
                try {
                    int v = Integer.parseInt(part.trim());
                    if (isAllowed(v)) {
                        min = Math.min(min, v);
                        max = Math.max(max, v);
                        any = true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!any) {
            min = ALLOWED_PERCENT[0];
            max = ALLOWED_PERCENT[ALLOWED_PERCENT.length - 1];
        }
        defaultPercent = new int[] {min, min, max, max};
        Log.info("defaults from zoom levels \"" + option + "\": " + min + "/" + min + "/" + max + "/" + max);
        return defaultPercent;
    }

    private static String defaultsText() {
        int[] d = defaultPercent();
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < PLAYERS; p++) {
            for (int s = 0; s < SITUATIONS.length; s++) {
                sb.append(key(p, s)).append('=').append(d[s]).append('\n');
            }
        }
        return DEFAULTS.replace("%s\n", sb.append('\n').toString());
    }

    public static float level(int player, int situation) {
        float[][] l = levels;
        if (l == null) {
            levels = l = defaults();
        }
        return l[player][situation];
    }

    /** Level in percent, for the Options window. */
    @LuaMethod(name = "AutoZoomTweaks_getLevel", global = true)
    public static double luaGetLevel(double player, String situation) {
        refresh();
        int p = (int) player;
        int s = situationIndex(situation);
        if (p < 0 || p >= PLAYERS || s < 0) {
            return 100;
        }
        return Math.round(level(p, s) * 100f);
    }

    /** Stores the level (percent) in the file; false if it's not one of the allowed levels. */
    @LuaMethod(name = "AutoZoomTweaks_setLevel", global = true)
    public static boolean luaSetLevel(double player, String situation, double percent) {
        int p = (int) player;
        int s = situationIndex(situation);
        int v = (int) Math.round(percent);
        if (p < 0 || p >= PLAYERS || s < 0 || !isAllowed(v)) {
            return false;
        }
        if (Math.round(level(p, s) * 100f) == v) {
            return true;
        }
        return set(key(p, s), Integer.toString(v));
    }

    private static String key(int player, int situation) {
        return "player" + (player + 1) + "." + SITUATIONS[situation];
    }

    private static int situationIndex(String name) {
        for (int i = 0; i < SITUATIONS.length; i++) {
            if (SITUATIONS[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAllowed(int percent) {
        for (int a : ALLOWED_PERCENT) {
            if (a == percent) {
                return true;
            }
        }
        return false;
    }

    private static float[][] defaults() {
        float[][] l = new float[PLAYERS][SITUATIONS.length];
        for (int p = 0; p < PLAYERS; p++) {
            for (int s = 0; s < SITUATIONS.length; s++) {
                l[p][s] = defaultPercent()[s] / 100f;
            }
        }
        return l;
    }

    /** Sets {@code key=value} in the file, keeping the rest of it, and re-reads it. */
    private static boolean set(String key, String value) {
        try {
            lastCheck = 0;
            refresh(); // creates the file if it's missing
            List<String> lines = new ArrayList<>(Files.readAllLines(path, StandardCharsets.UTF_8));
            String line = key + "=" + value;
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (isKeyLine(lines.get(i).trim(), key)) {
                    lines.set(i, line);
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add(line);
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
            lastModified = Files.getLastModifiedTime(path).toMillis();
            load();
            return true;
        } catch (IOException | RuntimeException e) {
            Log.error("can't write " + key, e);
            return false;
        }
    }

    private static boolean isKeyLine(String line, String key) {
        if (!line.startsWith(key)) {
            return false;
        }
        String rest = line.substring(key.length()).trim();
        return rest.startsWith("=") || rest.startsWith(":");
    }

    /** Cheap to call every frame. */
    public static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK_INTERVAL_MS) {
            return;
        }
        lastCheck = now;
        try {
            if (path == null) {
                path = Path.of(ZomboidFileSystem.instance.getCacheDir(), FILE_NAME);
            }
            if (!Files.exists(path)) {
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    w.write(defaultsText());
                }
                Log.info("created " + path);
            }
            long modified = Files.getLastModifiedTime(path).toMillis();
            if (modified == lastModified) {
                return;
            }
            lastModified = modified;
            load();
        } catch (IOException | RuntimeException e) {
            Log.error("can't read settings", e);
        }
    }

    private static void load() throws IOException {
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        float[][] l = new float[PLAYERS][SITUATIONS.length];
        StringBuilder sb = new StringBuilder("settings loaded:");
        for (int pl = 0; pl < PLAYERS; pl++) {
            sb.append(" player").append(pl + 1).append('=');
            for (int s = 0; s < SITUATIONS.length; s++) {
                int v = percent(p, key(pl, s), defaultPercent()[s]);
                l[pl][s] = v / 100f;
                sb.append(s == 0 ? "" : "/").append(v);
            }
        }
        levels = l;
        vehicleSpeedKmh = num(p, "vehicleSpeedKmh", 80f, 1f, 1000f);
        sb.append(" vehicleSpeedKmh=").append(vehicleSpeedKmh);
        Log.info(sb.toString());
    }

    private static int percent(Properties p, String key, int def) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        try {
            int n = Integer.parseInt(v.trim().replace("%", ""));
            if (isAllowed(n)) {
                return n;
            }
        } catch (NumberFormatException ignored) {
        }
        Log.warn("bad " + key + "=" + v + ", using " + def);
        return def;
    }

    private static float num(Properties p, String key, float def, float min, float max) {
        String v = p.getProperty(key);
        if (v == null) {
            return def;
        }
        try {
            float f = Float.parseFloat(v.trim());
            if (f >= min && f <= max) {
                return f;
            }
        } catch (NumberFormatException ignored) {
        }
        Log.warn("bad " + key + "=" + v + ", using " + def);
        return def;
    }
}
