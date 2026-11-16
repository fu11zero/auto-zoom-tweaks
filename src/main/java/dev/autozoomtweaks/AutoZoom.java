package dev.autozoomtweaks;

import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.core.PerformanceSettings;
import zombie.core.textures.MultiTextureFBO2;
import zombie.core.Core;
import zombie.input.GameKeyboard;
import zombie.input.KeybindId;
import zombie.inventory.types.HandWeapon;
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
// to the vehicle level at Settings.vehicleSpeedKmh. Situations, the camera pan offset, the top
// zoom speed and the camera centering are the same as in vanilla.
//
// Vanilla moves the zoom at a constant speed straight to the target and flips direction the
// moment the target crosses it. With the target following the vehicle speed that is a jerk on
// every touch of gas or brake, and a constant twitch on cruise control, which keeps speeding up
// and slowing down a little. So the speed is low-pass filtered (Settings.vehicleSpeedSmoothing)
// and the zoom has inertia: it accelerates up to the vanilla speed, brakes to stop on the target
// and, when the target turns around, slows down before going back (Settings.zoomAcceleration).
//
// Aiming a firearm: vanilla keeps switching situations under the crosshair. Stopping to aim
// ends the running exception and a zombie within 6 tiles turns on the melee level, so the
// world slides away exactly while the player lines up a shot. With Settings.aimFreeze the
// situation level is held from the start of firearm aiming until aimFreezeDelay seconds after.
//
// Camera pan: vanilla zooms out by the pan distance / 300 (up to +100%). With the mouse and
// "Pan camera while aiming" any aiming pans toward the cursor, so the zoom follows the
// crosshair and moves the target under it. Settings.panZoom picks vanilla or "fixed": a fixed
// zoom out while the player pans on purpose (gamepad RB, the pan camera key), none for the
// automatic pan while aiming with the mouse.
public final class AutoZoom {
    // Largest of MultiTextureFBO2.zoomLevelsDefault: the camera pan offset zooms out up to it.
    private static final float FARTHEST = 2.5f;

    // Vanilla auto zoom step: 0.004 * 1.5 per frame at 60 fps, zoom units per second.
    private static final float MAX_SPEED = 0.004f * 1.5f * 60f;

    // Per player slot: zoom velocity, the zoom we set last frame (anything else changed it ->
    // start from rest), the filtered vehicle speed and the vehicle it belongs to.
    private static final float[] velocity = new float[Settings.PLAYERS];
    private static final float[] lastZoom = new float[Settings.PLAYERS];
    private static final float[] speedKmh = new float[Settings.PLAYERS];
    private static final BaseVehicle[] speedVehicle = new BaseVehicle[Settings.PLAYERS];
    // Per player slot: firearm aim freeze, the held situation level and the time left to hold.
    private static final boolean[] holding = new boolean[Settings.PLAYERS];
    private static final float[] heldLevel = new float[Settings.PLAYERS];
    private static final float[] holdLeft = new float[Settings.PLAYERS];
    // Camera pan offsets (screen pixels) below this don't count as panning for panZoom=fixed:
    // the camera is returning to the center.
    private static final float PAN_THRESHOLD = 30f;

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
            float dt = Math.min(Math.max(GameTime.instance.getRealworldSecondsSinceLastUpdate(), 0f), 0.1f);
            fbo.setTargetZoom(p, target(fbo, p, character, dt));
            step(fbo, p, dt);
            PlayerCamera camera = IsoCamera.cameras[p];
            camera.center();
            return true;
        } catch (RuntimeException e) {
            logOnce(e);
            return false;
        }
    }

    private static float target(MultiTextureFBO2 fbo, int p, IsoGameCharacter character, float dt) {
        BaseVehicle vehicle = character.getVehicle();
        float outdoors = Settings.level(p, Settings.OUTDOORS);
        if (vehicle != null) {
            // Vanilla: getMaxZoom() at any speed, camera pan ignored.
            float speed = Math.abs(vehicle.getCurrentAbsoluteSpeedKmHour());
            if (speedVehicle[p] != vehicle) {
                speedVehicle[p] = vehicle;
                speedKmh[p] = speed;
            } else {
                float tau = Settings.vehicleSpeedSmoothing;
                speedKmh[p] += tau > 0f ? (speed - speedKmh[p]) * (1f - (float) Math.exp(-dt / tau)) : speed - speedKmh[p];
            }
            float t = Math.min(speedKmh[p] / Settings.vehicleSpeedKmh, 1f);
            return outdoors + (Settings.level(p, Settings.VEHICLE) - outdoors) * t;
        }
        speedVehicle[p] = null;
        float pan = panZoom(p, character);
        float zoom = Settings.level(p, situation(character));
        if (Settings.aimFreeze && aimingFirearm(character)) {
            holdLeft[p] = Settings.aimFreezeDelay;
        } else {
            holdLeft[p] -= dt;
        }
        if (holdLeft[p] > 0f) {
            if (!holding[p]) {
                // Hold where the zoom will come to rest now, not where it is: no swing back.
                float accel = Settings.zoomAcceleration;
                float v = velocity[p];
                float rest = fbo.getZoom(p) + v * Math.abs(v) / (2f * accel);
                heldLevel[p] = Math.max(0.25f, Math.min(rest - pan, FARTHEST));
                holding[p] = true;
            }
            zoom = heldLevel[p];
        } else {
            holding[p] = false;
        }
        return Math.max(zoom, Math.min(zoom + pan, FARTHEST));
    }

    private static boolean aimingFirearm(IsoGameCharacter character) {
        return character instanceof IsoPlayer player && player.isAiming()
                && player.getPrimaryHandItem() instanceof HandWeapon weapon && HandWeapon.isAimedFirearm(weapon);
    }

    // Zoom out added for the camera pan (PlayerCamera.update, IsoCamera.getRightClickOffX/Y).
    private static float panZoom(int p, IsoGameCharacter character) {
        float dist = IsoUtils.DistanceTo(IsoCamera.getRightClickOffX(), IsoCamera.getRightClickOffY(), 0f, 0f);
        if (!Settings.panZoomFixed) {
            return Math.min(dist / 300f, 1f); // vanilla
        }
        if (dist < PAN_THRESHOLD || !(character instanceof IsoPlayer player) || isAutoPan(p, player)) {
            return 0f;
        }
        return Settings.panZoomAmount;
    }

    // PlayerCamera.update: player 0 on mouse and keyboard, aiming with "Pan camera while aiming"
    // on and the pan camera key not held: the camera follows the cursor by itself. On a gamepad
    // the camera pans only with RB held, so any pan there is on purpose.
    private static boolean isAutoPan(int p, IsoPlayer player) {
        return p == 0 && player.getJoypadBind() == -1 && !GameKeyboard.isKeyDown(KeybindId.PAN_CAMERA)
                && Core.getInstance().getOptionPanCameraWhileAiming();
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

    // Vanilla moves zoom by a fixed step toward the target every frame (bang-bang). Here the
    // zoom velocity changes by at most zoomAcceleration per second, tops out at the vanilla
    // speed and is limited by the distance left, so the zoom eases in, eases out and never
    // turns around abruptly.
    private static void step(MultiTextureFBO2 fbo, int p, float dt) {
        float zoom = fbo.getZoom(p);
        float target = fbo.getTargetZoom(p);
        if (Math.abs(zoom - lastZoom[p]) > 1e-4f) {
            velocity[p] = 0f; // zoom changed elsewhere (manual zoom, auto zoom was off): from rest
        }
        float accel = Settings.zoomAcceleration;
        float v = velocity[p];
        float error = target - zoom;
        if (zoom == target && v == 0f) {
            lastZoom[p] = zoom;
            return;
        }
        float dv = accel * dt;
        if (Math.abs(error) < 0.001f && Math.abs(v) <= dv) {
            zoom = target;
            v = 0f;
        } else {
            // Fastest speed from which we can still stop at the target: v^2 = 2 a d.
            float wanted = Math.signum(error) * Math.min(MAX_SPEED, (float) Math.sqrt(2f * accel * Math.abs(error)));
            v += Math.max(-dv, Math.min(dv, wanted - v));
            float move = v * dt;
            if (Math.signum(move) == Math.signum(error) && Math.abs(move) >= Math.abs(error) && Math.abs(v) <= 2f * dv) {
                zoom = target; // would step over the target at crawling speed
                v = 0f;
            } else {
                zoom += move;
            }
        }
        velocity[p] = v;
        lastZoom[p] = zoom;
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
