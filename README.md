# Flyover for Kiosk Satellite

Planes that are really flying over your home cross Kiosk Satellite's own screensaver. Each one is labelled with its flight number and aircraft type (`TP1352 · A20N`), its route (`AMS → LIS`, `AMSTERDAM → LISBON`) or both.

Your screensaver stays exactly as it is — weather, clock or photos. Flyover only adds the planes on top.

- Eastbound planes fly left to right, westbound right to left, with a contrail behind them.
- A plane flies across, pauses and comes round again while it stays in range. One that leaves finishes its pass.
- At night the plane turns grey with lit cabin windows and blinking strobe, beacon and wingtip lights.
- Visual only: every tap goes straight through to the screensaver, so dismissing it works as before.
- Steps aside while a full-screen Now Playing player covers the screensaver.
- Draws nothing between passes, so an empty sky costs no CPU.

## Requirements

- Kiosk Satellite **2026.11.11** or newer, with **Settings > Plugin Manager > Enable Plugins** on.
- The **Flightradar24** integration for Home Assistant ([AlexandrErohin/home-assistant-flightradar24](https://github.com/AlexandrErohin/home-assistant-flightradar24), in HACS). It provides `sensor.flightradar24_current_in_area`. A radius of 10–20 km and a scan interval of 30–60 seconds work well.

## Installation

### Community Plugins (Recommended)

Flyover is listed in [Community Plugins](https://kiosksatellite.com/plugins/). Use the button below to open it in your Kiosk Satellite and install it.

[![Install in Kiosk Satellite](https://kiosksatellite.com/img/badges/plugin-add.svg)](https://kiosksatellite.com/redirect/plugin/?repository=Andromedanevel/ks-flyover)

Or search for `Flyover` in **Settings > Plugin Manager > Browse plugins** in Kiosk Satellite.

### Manual

1. Open **Settings > Plugin Manager** in Kiosk Satellite and turn on **Enable Plugins**.
2. Choose **Add plugin**, paste `https://github.com/Andromedanevel/ks-flyover` and choose **Preview**.
3. Review the plugin and choose **Trust and install**, then enable **Flyover**.

Run the **Demo: fly planes now** action from the plugin's page or the kiosk drawer to see it straight away: it starts the screensaver and flies two demo planes for two minutes.

## Settings

| Setting | Default | |
| --- | --- | --- |
| Flights sensor | `sensor.flightradar24_current_in_area` | The Flightradar24 "Current in area" sensor |
| Maximum distance | 15 km | Only closer planes fly across |
| Planes at once | 2 | The nearest one or two |
| Only on screensaver views | empty | Comma-separated view names, as the plugin status shows them. Empty shows planes over every screensaver |
| Height on screen | 28 % | Distance of the flight path from the top |
| Plane size | 64 dp | |
| Show label | on | |
| Label | Flight number and type | Or airport codes, cities, or flight number and airport codes. Planes without a known route show their flight number |
| Contrail | on | |
| Navigation lights at night | on | After `sun.sun` sets |
| Crossing time | 40 s | One pass across the screen |
| Pause between passes | 15 s | |
| Frame rate | 30 fps | Synced to the screen refresh. 60 is smoothest, 30 is lighter |

The plugin's status line shows the planes in range, whether the screensaver is up and which screensaver view is on screen.

## Data and privacy

Flyover sends nothing anywhere. Home Assistant's Flightradar24 integration does the flight lookup. Flyover asks Home Assistant, through Kiosk Satellite's own connection, for one short template result: the nearest one or two airborne flights within the maximum distance (flight number, aircraft type, heading, airports and cities) and whether `sun.sun` has set. The tablet itself needs no internet access for this.

Flyover is not affiliated with Flightradar24.

## Develop

Requires Python 3, JDK 17+ and an Android SDK with build-tools (`ANDROID_HOME`).

```
python3 tools/test.py
python3 tools/build.py
```

For a quick local test use **Plugin Manager > Developer Tools > Install from ZIP** with `dist/flyover-<version>.zip`. Releasing is described in [RELEASING.md](RELEASING.md).

Built on the Kiosk Satellite plugin SDK 1 (`sdk/`, Apache-2.0, compile-time only, not packaged).

## License

Apache-2.0
