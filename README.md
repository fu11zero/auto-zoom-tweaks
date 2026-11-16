# Auto Zoom Tweaks

Java mod for Project Zomboid B42 (ZombieBuddy). Every player (single player or any splitscreen
slot) gets their own auto zoom: on/off and a zoom level per situation, set in Options in place
of the vanilla "Auto zoom" row of per-player checkboxes.

Source: <https://github.com/fu11zero/auto-zoom-tweaks>

## Features

### Zoom level per player and situation

Vanilla: auto zoom is a tickbox per player slot; the levels are the two ends of the shared
"Zoom levels" list (closest when `shouldAutoZoomIn()`, farthest otherwise), so auto zoom and a
manual zoom list can't be set separately, and every player gets the same levels.

Patch: `MultiTextureFBO2.update` (`Patches.Update`, `@Patch.OnEnter(skipOn = true)`). When auto
zoom is on for `IsoPlayer.getPlayerIndex()` (and zoom is enabled, there is a camera character),
`AutoZoom.update` sets the target from that player's level for the situation and skips vanilla;
otherwise vanilla runs (manual zoom, the tutorial turning auto zoom off). Situations are vanilla
`shouldAutoZoomIn()` split in two: indoors (square not outside), melee (outside, not
running/sprinting, zombie < 6 tiles targeting or targeted in the last 4 s), outdoors (the rest).
The camera pan offset (`getRightClickOffX/Y` / 300, up to +100%, capped at 250%), the top zoom
speed (0.006 per frame at 60 fps) and `camera.center()` are vanilla; the motion is not (below). Only public members of
`MultiTextureFBO2` are used (`setTargetZoom`, `getZoom`, `setZoom`, `autoZoom`, `zoomEnabled`).

Levels: `~/Zomboid/AutoZoomTweaks.properties` (`player1..4.indoors|melee|outdoors|vehicle`, percent
from the vanilla default set 25..250). Defaults = what vanilla auto zoom did with the "Zoom
levels" option (`zoomLevels2x`, empty = all levels) when the file was created: its closest level
indoors and in melee, its farthest outdoors and in a vehicle (`200;75` → 75/75/200/200). Re-read
once a second when changed (`Settings.refresh`). "Zoom levels" keeps meaning manual zoom steps.

Manual zoom while auto zoom is on: as in vanilla it does nothing (the wheel / Back wheel set the
target, the next `update` overwrites it; the Back wheel hides Zoom+/- by `getAutoZoom`).

### Vehicle zoom by speed

Vanilla: in a vehicle the target is always `getMaxZoom()`, at any speed, and the camera pan is
ignored. Patch (`AutoZoom.target`): linear from the player's Outdoors level at 0 km/h to the
Vehicle level at `vehicleSpeedKmh` (80, properties only) in
`BaseVehicle.getCurrentAbsoluteSpeedKmHour()`; drivers and passengers alike, camera pan still
ignored. The speed is low-pass filtered first (`vehicleSpeedSmoothing`, 1.5 s time constant):
cruise control keeps speeding up and slowing down a little, and the raw speed made the target
wobble all the time.

### Smooth zoom motion

Vanilla (`MultiTextureFBO2.update`): the zoom moves toward the target at a constant step and
flips direction the very frame the target crosses it. With a target that follows the vehicle
speed this jerked the camera on every touch of gas or brake. Patch (`AutoZoom.step`): the zoom
has a velocity that changes by at most `zoomAcceleration` (0.4/s²) per second, tops out at the
vanilla speed (0.36/s) and is capped by `sqrt(2 a d)` so it brakes to stop at the target; when
the target turns around it slows down first. Real time (`getRealworldSecondsSinceLastUpdate`),
so the motion does not depend on FPS. Any zoom change from outside (manual zoom while auto zoom
was off) restarts it from rest. Applies to every situation, so indoor/outdoor switches ease in
and out too.

### Steady zoom while aiming a firearm

Vanilla: the situation is checked every frame, aiming or not. Stopping to aim ends the
running exception and a zombie within 6 tiles that targets the player switches to the melee
level, so the zoom moves the world under the crosshair exactly while the player lines up a
shot. Patch (`AutoZoom.target`): while `isAiming()` with an aimed firearm in the primary hand
(`HandWeapon.isAimedFirearm`), and `aimFreezeDelay` (1 s) after, the situation level is held at
the point where the zoom comes to rest (`zoom + v|v| / 2a`, no swing back). Melee weapons are
not affected: zooming in for melee is the point of the melee level. `aimFreeze=false` turns it off.

### Camera pan zoom (experimental)

Vanilla: the target grows by the camera pan distance / 300, up to +100%. With the mouse and
"Pan camera while aiming" every aim pans toward the cursor (`PlayerCamera.update`), so the zoom
follows the crosshair and moves what it points at. `panZoom=vanilla` (default) keeps that.
`panZoom=fixed`: `panZoomAmount` (50%) while the pan offset is over 30 px and the pan is on
purpose (gamepad RB, the pan camera key), nothing for the automatic aim pan (player 0, no
gamepad, pan key not held, the option on). Not `JoypadManager.isRBPressed`: GamepadTweaks remaps
it only inside `PlayerCamera.update`, so the pan offset itself tells. To be decided after a test.

### Options table

Vanilla: `MainOptions:addUIPanel` builds the "Auto-zoom" row as one `ISTickBox` with four
options (Player 1-4). Patch (`lua/client/AutoZoomTweaks/OptionsRow.lua`): wraps `addUIPanel`,
finds the `autoZoom` `GameOption`, hides its tickbox (the label stays as the row title), adds
a header row (On, Indoors, Melee, Outdoors, Vehicle), one row per player (tickbox + four
combos, greyed out while the tickbox is off) and a description, moves every child below down,
grows the scroll height and puts one gamepad line per player in place of the vanilla one in
`joypadButtonsY`. The `GameOption`'s `toUI` / `apply` are replaced: on/off goes to
`Core.setAutoZoom` (options.ini as before), levels to `AutoZoomTweaks_setLevel` (Java,
`@LuaMethod` global). Without the Java globals (jar not approved) or the vanilla option the row
is left as is, with a warning in the log.
The "Zoom levels" row is removed (hidden, rows below moved up, its gamepad line and
`GameOption` dropped): with the table its only meaning would be the manual zoom steps, which
players confuse with the auto zoom levels. options.ini keeps its value, manual zoom still uses it.
Player tickboxes need `setWidthToFit()`: without `autoWidth` an `ISTickBox` stays as wide as
its box and the combos cover the player name.

## Vanilla auto zoom (42.21)

Code: `zombie.core.textures.MultiTextureFBO2` (decompile with `tools/decompile.sh
zombie/core/textures/MultiTextureFBO2`), Lua UI in `media/lua/client/OptionScreens/MainOptions.lua`
(`MainOptions:addUIPanel`).

### Options

- **Zoom** (`Core.optionZoom`, `options.ini` `zoom=`): zoom at all (`zoomEnabled`). Off: zoom
  is 1.0 for everyone.
- **Zoom levels** (`zoomLevels2x=200;75`, `zoomLevels1x` for tile scale 1, removed in 42.22): one
  list shared by all players, a tickbox per level of the default set
  `2.5 2.25 2.0 1.75 1.5 1.25 1.0 0.75 0.5 0.25` (100% is always in). Used for manual zoom
  (mouse wheel, Back wheel Zoom+/-, `doZoomScroll` / `getNextZoom`) and, by its two ends,
  for auto zoom.
- **Auto zoom** (`autozoom=true,true,true,true`, `Core.get/setAutoZoom(n)`,
  `MultiTextureFBO2.autoZoom[4]`): one tickbox per player slot 1-4, nothing else.
- **Reticle camera zoom** (`reticleCameraZoom`, `IsoReticle`): reticle size follows zoom; not
  part of auto zoom.

Zoom values: 1.0 = 100%; larger = farther (2.0 = "200%", more of the map), smaller = closer.

### `MultiTextureFBO2.update()`

Called from `Core.StartFrame(playerIndex, ...)` once per local player per frame;
the player is `IsoPlayer.getPlayerIndex()`, the character `IsoCamera.getCameraCharacter()`.

```
if !zoomEnabled: zoom = target = 1
if autoZoom[p] && character && zoomEnabled:
    target = shouldAutoZoomIn() ? zoomedInLevel : zoomedOutLevel
           // zoomedIn = zoomLevels[last] (smallest), zoomedOut = zoomLevels[0] (largest), set in create()
    target += min(dist(rightClickOffX, rightClickOffY) / 300, 1)    // camera pan (RMB / RB) zooms out up to +100%
    target = min(target, zoomLevels[0])
    if character.getVehicle(): target = getMaxZoom()                // always the farthest level in a car
    setTargetZoom(p, target)
step = 0.004 * GameTime multiplier / true multiplier * 1.5; ×5 when autoZoom[p] is off
zoom moves toward target by step (dirtyRecalcGridStackTime = 2 when it changes)
camera.center()   // private setCameraToCentre(): IsoCamera.cameras[p].center()
```

`shouldAutoZoomIn()` ("close" level):
1. square not `isOutside()` (indoors), regardless of anything else;
2. otherwise false when `isRunning() || isSprinting()`;
3. otherwise `closestZombie < 6 && isTargetedByZombie()`, or
   `lastTargeted < PerformanceSettings.getLockFPS() * 4` (targeted in the last ~4 s).

So there are really three situations (indoors, melee/threat, outdoors) plus the vehicle, but
only two levels (the ends of the shared list), the vehicle always uses the farthest one, and
the speed of the vehicle is ignored.

### What Lua can and can't do

Exposed: `getCore():getZoom(n)`, `getMinZoom/getMaxZoom`, `getNextZoom`, `doZoomScroll(n, del)`
(step along the level list), `get/setAutoZoom`, `isZoomEnabled`. Not exposed: `setTargetZoom` /
`setZoom` on `MultiTextureFBO2`. An arbitrary target (e.g. 150% not in the list) needs Java,
hence ZombieBuddy.

### Who else reads auto zoom

- `ISBackButtonWheel` (lines ~44, 78, 159-161): Zoom+/- in the Back wheel only when auto zoom
  is off for that player.
- `Tutorial1.lua:20`: turns it off for player 0.
- Mouse wheel zoom: `getNextZoom` / `doZoomScroll` in Java (check how it behaves with auto zoom
  on before deciding about manual override).

## Options screen (vanilla)

`MainOptions:addUIPanel()` builds the rows inline with absolute positions:
`ISLabel` (`UI_optionscreen_autozoom`) at `splitpoint`, `ISTickBox` at `splitpoint + 20` with
options `UI_optionscreen_player1..4`, `self.mainPanel:insertNewLineOfButtons(box)` for gamepad
navigation, `self.addY += box height + UI_BORDER_SPACING`, then
`GameOption:new('autoZoom', box)` with `toUI` / `apply` (`Core.setAutoZoom`) added to
`self.gameOptions`. The rows right above it are "Zoom levels" (`zoomLevels`), right below
"Pan camera while aiming". 42.21 → 42.22: only the "Zoom levels" option changed
(`zoomLevels1x` branch removed); the auto zoom row is the same.
