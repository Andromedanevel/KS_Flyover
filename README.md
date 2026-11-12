# Flyover for Kiosk Satellite

Planes that are really flying over your home cross Kiosk Satellite's own screensaver, labelled with their flight number and aircraft type (`TP1352 · A20N`). The screensaver itself — weather, clock, photos — stays Kiosk Satellite's own; this plugin only adds the planes on top.

- Eastbound planes fly left to right, westbound right to left.
- A plane flies across, pauses, and comes round again while it stays in range. One that leaves finishes its pass.
- At night (after `sun.sun` sets) the plane turns grey with lit cabin windows, a blinking strobe, beacon and wingtip light.
- Visual only: every tap goes straight through to the screensaver, so dismissing it works as before.
- Hides while a full-screen Now Playing player covers the screensaver.
- Draws nothing between passes, so it costs no CPU while the sky is empty.

## Requirements

- Kiosk Satellite **2026.11.11** or newer, with **Settings > Plugin Manager > Enable Plugins** on.
- The **Flightradar24** integration in Home Assistant (HACS: `AlexandrErohin/home-assistant-flightradar24`), which provides `sensor.flightradar24_current_in_area`. Keep its radius modest (10–20 km) and its scan interval at 20–60 s.

Home Assistant does the flight lookup; the plugin only receives a short template result through Kiosk Satellite's own connection, so the tablet itself needs no internet access.

## Install

1. Create a **public** GitHub repository and push this folder to it.
2. Publish a release tagged `v1.0.0`. The included **Release** workflow tests and builds the plugin and attaches the ZIP, its checksum and the manifest (it takes a couple of minutes).
3. In Kiosk Satellite: **Plugin Manager > Add plugin**, paste the repository URL, **Preview**, **Trust and install**, then enable **Flyover**.
4. Run the **Demo: fly planes now** action from the plugin's page. It starts the screensaver and flies two demo planes for two minutes.

## Settings

| Setting | Default | |
| --- | --- | --- |
| Flights sensor | `sensor.flightradar24_current_in_area` | The Flightradar24 "Current in area" sensor |
| Maximum distance | 15 km | Only closer planes fly across |
| Planes at once | 2 | The nearest one or two |
| Only on screensaver views | empty | Comma-separated view names (the plugin status shows the current one). Empty = every screensaver |
| Height on screen | 28 % | Flight path distance from the top |
| Plane size | 64 dp | |
| Show flight number and type | on | |
| Contrail | on | |
| Navigation lights at night | on | |
| Crossing time | 40 s | One pass across the screen |
| Pause between passes | 15 s | |
| Frame rate | 30 fps | Lower it on older tablets |

The plugin status line shows the planes in range, whether the screensaver is up and which screensaver view is on screen.

## Develop

Requires Python 3, JDK 17+ and an Android SDK with build-tools (`ANDROID_HOME`).

```
python3 tools/test.py
python3 tools/build.py
```

For a quick local test use **Plugin Manager > Developer Tools > Install from ZIP** with `dist/flyover-<version>.zip`.

Built on the Kiosk Satellite plugin SDK 1 (`sdk/`, Apache-2.0, compile-time only, not packaged).

## License

Apache-2.0
