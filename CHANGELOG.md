# Changelog

## 1.1.0

- **Planes match their aircraft type**: airliner, widebody, jumbo (747, A380), four-engine, regional and business jets with rear engines, turboprops, light aircraft and helicopters each get their own silhouette and size, chosen from the ICAO type code and Flightradar24's category. Propellers and rotors spin; smaller and lower-flying aircraft cross more slowly and leave no contrail. Turn off **Match the aircraft type** for the single airliner shape.
- The plugin status line shows planes in the chosen **Label** style, like the screensaver.
- The demo now flies an airliner and a helicopter.

## 1.0.1

- Smoother flight: frames are now synced to the screen refresh and timed from it, so each frame moves the plane the same distance. A lower frame rate skips refreshes evenly instead of drifting against them.
- Each plane, contrail and label is drawn once into an image; a frame only moves that image, so far less work per frame.

## 1.0.0

First public release.

- Real overhead planes from the Home Assistant Flightradar24 integration fly across Kiosk Satellite's own screensaver, on a touch-through overlay.
- Label choice: flight number and type, airport codes, cities, or flight number and airport codes.
- Contrail, night look with navigation lights, two lanes for two planes, direction from the heading.
- Steps aside for a full-screen Now Playing player; optional limit to chosen screensaver views.
- **Demo: fly planes now** action.

Releases 0.0.1 and 0.0.2 were private test builds.
