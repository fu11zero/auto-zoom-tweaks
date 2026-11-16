# Changelog

Player-facing change notes, newest first. Each section is copied as is into the Steam Workshop
change notes when the mod is updated. Keep it in English, short, one line per change.

## Unreleased

## Update: 16 Nov @ 6:11pm

- Changed: the zoom speeds up and slows down smoothly instead of moving at a fixed speed and turning around at once; no more camera twitching on gas, brake and cruise control.
- Changed: the vehicle zoom follows the average speed over about a second and a half, not the instant one.
- Added: vehicleSpeedSmoothing and zoomAcceleration in AutoZoomTweaks.properties.
- Changed: while aiming a firearm the zoom holds still, so a zombie coming close no longer zooms in under the crosshair; the situation is checked again a second after aiming ends (aimFreeze, aimFreezeDelay).
- Added: panZoom=fixed in AutoZoomTweaks.properties (experimental): a fixed zoom out while panning the camera on purpose instead of following the cursor, none for the automatic pan while aiming with the mouse.

## Update: 12 Nov @ 10:25am

- Fixed: "invalid signature" in ZombieBuddy, the Java part blocked: the signature is removed again, ZombieBuddy asks to approve the mod as before.

## Update: 12 Nov @ 5:00am

- Changed: the Java part is signed by the author (ZombieBuddy shows "FullZero"; trust the author once and mod updates no longer ask for approval).

## Release: 1 Nov @ 11:53am

- Added: auto zoom per player with its own level indoors, in melee, outdoors and in a vehicle.
- Added: in a vehicle the zoom grows with speed, from the Outdoors level standing still to the Vehicle level at 80 km/h.
- Changed: the Options "Auto-zoom" row is a table per player (On, Indoors, Melee, Outdoors, Vehicle), filled at first from your "Zoom levels"; the "Zoom levels" row is gone (manual zoom keeps the saved levels).
