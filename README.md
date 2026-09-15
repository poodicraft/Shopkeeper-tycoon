# Shopkeeper Tycoon

A 3D shop-management game for Android. You run a small corner shop: stock the
shelves, set your prices, keep the queue moving, and reinvest the takings until
you are selling caviar instead of bread.

Everything renders in real 3D with OpenGL ES 2.0 — an orbiting camera, animated
shoppers who path around the aisles, a day/night cycle — and the whole thing is
plain Java against the Android framework. No game engine, no third-party
libraries, no assets on disk: every mesh, texture-free material, icon and sound
effect is generated in code.

**The built APK is at [`dist/shopkeeper-tycoon.apk`](dist/shopkeeper-tycoon.apk)** (~100 KB).

```
adb install -r dist/shopkeeper-tycoon.apk
```

Or copy it to a phone and open it. Android will ask you to allow installing from
this source, because the APK is signed with a self-signed key rather than one
registered with Google Play.

Requires Android 5.0 (API 21) or newer and a device with OpenGL ES 2.0, which in
practice means any Android phone made in the last decade. No permissions, no
network access, no ads.

---

## Playing

| | |
|---|---|
| **One finger drag** | Orbit the camera around the shop |
| **Two fingers drag** | Pan across the floor |
| **Pinch** | Zoom in and out |
| **Tap a shelf** | Empty shelf with goods out back: restock it instantly. Otherwise open the shelf editor |
| **Tap the till or the shopper at it** | Hurry the transaction along |
| **Tap a browsing shopper** | See what they came in for |

The loop is: buy stock into the stockroom → put it on a shelf → price it → serve
the queue → spend the profit.

**Prices matter.** Every shopper has their own idea of what a thing is worth. Price
below the base value and they buy happily; price well above it and they put it
back and leave, which costs you satisfaction. Satisfaction feeds back into how
many people come through the door.

**The till is the bottleneck.** Serving customers yourself by tapping is roughly
twice as fast as leaving them to it. Once you can afford a **Cashier** the queue
runs itself — and keeps earning while the app is closed.

**Upgrades**

- **Register** — faster checkout, shorter queues
- **Marketing** — more footfall
- **Decor** — shoppers tolerate higher prices
- **Shelving** — more units per shelf
- **Cashier** — works the till for you, including offline
- **Stocker** — carries goods from the stockroom to whichever shelf is emptiest

Eight product tiers unlock as you level up, from Bread at level 1 to Caviar at
level 7. Rent and wages come out at the end of each in-game day (three real
minutes), so an expensive shop has to earn its keep.

Progress saves automatically when the app pauses.

---

## Building

### The tested path

`build.sh` drives the Android build tools directly — no Gradle, no Android
Studio, no network access:

```
javac  →  dx  →  aapt package  →  zipalign  →  apksigner
```

On Debian or Ubuntu everything it needs is in the archive:

```bash
sudo apt-get install aapt dalvik-exchange zipalign apksigner \
                     android-sdk-platform-23 openjdk-17-jdk-headless
./build.sh
```

The APK lands in `build/shopkeeper-tycoon.apk`. A self-signed key is generated
into `keystore/` on the first run (and is git-ignored). Every tool path can be
overridden by environment variable — `AAPT`, `DX`, `ZIPALIGN`, `APKSIGNER`,
`ANDROID_JAR`, `KEYSTORE`, and so on — if your SDK lives somewhere else.

The code compiles against the API 23 platform jar but targets API 34. It only
calls framework APIs available in API 21+, so that combination is safe.

### Android Studio

Gradle files are included (`settings.gradle`, `build.gradle`, `app/build.gradle`)
and the source tree already uses the standard layout, so the project opens in
Android Studio directly. They are pinned to Android Gradle Plugin 7.4.2 because
the manifest keeps its `package` attribute, which `build.sh` needs and AGP 8
rejects.

Note that this Gradle path is provided for convenience and was **not** exercised
while building this project — the environment it was developed in had no access
to Google's Maven repository, so AGP could not be downloaded. `build.sh` is the
path that actually produced the shipped APK.

---

## Tests

There is no emulator in the loop, so the checks that matter run on a plain JVM:

```bash
./tools/run-tests.sh
```

- **GeometryTest** — for every triangle of every primitive, the normal implied by
  the vertex winding must agree with the normal stored on the vertices. Back-face
  culling makes a mis-wound face vanish silently, and nothing else would catch
  it. It found the cylinders and cones rendering inside out.
- **SceneTest** — builds the entire 3D scene and checks the triangle budget. The
  static shop is one draw call.
- **SimTest** — pathfinding to every shelf, the customer state machine end to
  end, six simulated days under an autopilot shopkeeper, and a strict
  stock-conservation check. It found shoppers rejecting every shelf because of a
  scoring bug, and stock being destroyed when a customer put something back on a
  shelf that had been refilled meanwhile.
- **shaders** — the GLSL is extracted from the Java source and compiled as
  OpenGL ES 1.00 with `glslangValidator`, so a syntax error surfaces at build
  time instead of as a black screen.

The game package has no Android imports at all, which is what lets the whole
economy be driven headlessly.

---

## How it is put together

```
app/src/main/java/com/poodicraft/shopkeeper/
  MainActivity.java     Lifecycle, wiring, save/load, all UI actions
  GameView.java         GL thread: camera, gestures, frame loop, overlay projection
  math/                 Vec3, Mat4, easing helpers
  gl/                   Shader program, VBO mesh, procedural mesh builder,
                        orbit camera with picking, the lit draw service
  game/                 Pure-Java simulation: products, shelves, customers,
                        staff, A* navigation, economy, saves
  scene/                Procedural geometry for the shop and the people in it
  ui/                   Code-built HUD, bottom sheets, and the 2D overlay
  audio/                Synthesised sound effects — no audio files
```

A few decisions worth knowing about:

**One lock.** The GL thread holds `gameLock` for the whole of update-and-render;
every UI action takes the same lock. Camera moves and taps are posted onto the GL
thread with `queueEvent`, so picking uses a consistent camera.

**The static shop is a single mesh.** Floor, walls, windows, counter, till,
plants and lamps are baked into one vertex buffer at startup — 3,496 triangles,
one draw call. Only shelves, goods and people are drawn per-object.

**Meshes keep their vertex data.** If the EGL context is lost, every buffer name
from the old context is dropped and the geometry is re-uploaded rather than
silently drawing nothing.

**The overlay copies rather than shares.** The GL thread projects world anchors
into screen positions; `commit()` copies the values into objects the UI thread
owns, so a draw in flight can never see a half-written label.

**Shaders avoid `mat3(mat4)`.** GLSL ES 1.00 has no matrix-from-matrix
constructor, so the normal matrix is applied column by column.

## Licence

Do what you like with it.
