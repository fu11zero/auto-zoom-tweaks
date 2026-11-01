package dev.autozoomtweaks;

import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.PerformanceSettings;
import zombie.core.textures.MultiTextureFBO2;
import zombie.iso.IsoCamera;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoUtils;
import zombie.iso.PlayerCamera;
import zombie.vehicles.BaseVehicle;

// Our version of MultiTextureFBO2.update() for a player with auto zoom on.
//
// Vanilla (42.21/42.22) has two auto zoom levels shared by every player, the ends of the
// "Zoom levels" option (shouldAutoZoomIn() ? smallest : largest), and in a vehicle it always
// takes the largest one, whatever the speed. Here every player slot has its own level indoors,
// in melee and outdoors, and in a vehicle the zoom goes from the outdoors level standing still
// to the vehicle level at Settings.vehicleSpeedKmh. Situations, the camera pan offset, the zoom
// step and the camera centering are the same as in vanilla.
public final class AutoZoom {
    // Largest of MultiTextureFBO2.zoomLevelsDefault: the camera pan offset zooms out up to it.
    private static final float FARTHEST = 2.5f;

    private AutoZoom() {}

    /** Runs instead of vanilla update(); returns false to let vanilla run (auto zoom off etc.). */
    public static boolean update(MultiTextureFBO2 fbo) {
        try {
            int p = IsoPlayer.getPlayerIndex();
            IsoGameCharacter character = IsoCamera.getCameraCharacter();
            if (p < 0 || p >= Settings.PLAYERS || !fbo.zoomEnabled || !fbo.autoZoom[p] || character == null
                    || IsoPlayer.players[p] == null) {
                return false;
            }
            Settings.refresh();
            fbo.setTargetZoom(p, target(p, character));
            step(fbo, p);
            PlayerCamera camera = IsoCamera.cameras[p];
            camera.center();
            return true;
        } catch (RuntimeException e) {
            logOnce(e);
            return false;
        }
    }

    private static float target(int p, IsoGameCharacter character) {
        BaseVehicle vehicle = character.getVehicle();
        float outdoors = Settings.level(p, Settings.OUTDOORS);
        if (vehicle != null) {
            // Vanilla: getMaxZoom() at any speed, camera pan ignored.
            float t = Math.min(Math.abs(vehicle.getCurrentAbsoluteSpeedKmHour()) / Settings.vehicleSpeedKmh, 1f);
            return outdoors + (Settings.level(p, Settings.VEHICLE) - outdoors) * t;
        }
        float zoom = Settings.level(p, situation(character));
        // Vanilla: panning the camera (RMB / RB while aiming) zooms out by up to +100%.
        float dist = IsoUtils.DistanceTo(IsoCamera.getRightClickOffX(), IsoCamera.getRightClickOffY(), 0f, 0f);
        float panned = zoom + Math.min(dist / 300f, 1f);
        return Math.max(zoom, Math.min(panned, FARTHEST));
    }

    // MultiTextureFBO2.shouldAutoZoomIn() split into its two "close" cases.
    private static int situation(IsoGameCharacter character) {
        IsoGridSquare square = character.getCurrentSquare();
        if (square != null && !square.isOutside()) {
            return Settings.INDOORS;
        }
        if (character instanceof IsoPlayer player) {
            if (player.isRunning() || player.isSprinting()) {
                return Settings.OUTDOORS;
            }
            if (player.closestZombie < 6f && player.isTargetedByZombie()
                    || player.lastTargeted < PerformanceSettings.getLockFPS() * 4) {
                return Settings.MELEE;
            }
        }
        return Settings.OUTDOORS;
    }

    // Same step as vanilla with auto zoom on (0.004 * 1.5 per frame at normal speed).
    private static void step(MultiTextureFBO2 fbo, int p) {
        float step = 0.004f * GameTime.instance.getMultiplier() / GameTime.instance.getTrueMultiplier() * 1.5f;
        float zoom = fbo.getZoom(p);
        float target = fbo.getTargetZoom(p);
        if (zoom == target) {
            return;
        }
        if (target > zoom) {
            zoom += step;
            if (zoom > target || Math.abs(zoom - target) < 0.001f) {
                zoom = target;
            }
        } else {
            zoom -= step;
            if (zoom < target || Math.abs(zoom - target) < 0.001f) {
                zoom = target;
            }
        }
        fbo.setZoom(p, zoom);
        IsoPlayer.players[p].dirtyRecalcGridStackTime = 2f;
    }

    private static boolean logged;

    private static void logOnce(RuntimeException e) {
        if (!logged) {
            logged = true;
            Log.error("auto zoom failed, vanilla takes over", e);
        }
    }
}
