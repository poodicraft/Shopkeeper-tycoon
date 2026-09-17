# Shopkeeper

A third-person 3D shop game for Android. You don't manage a shop from above — you
*are* the shopkeeper. You walk the aisles, carry crates out of the stockroom on
your shoulder, kneel to fill the shelves by hand, and stand behind the counter
scanning a customer's basket item by item while the queue watches.

Everything renders in real 3D with OpenGL ES 3.0: a skinned character you see from
behind, shadow-mapped lighting, normal-mapped surfaces, bloom and filmic tonemapping.
It is plain Java against the Android framework — **no game engine, no third-party
libraries, and not a single asset file**. Every mesh, texture, normal map, launcher
icon and sound effect is generated in code at startup, which is why the whole game
is a ~150 KB download.

**The built APK is at [`dist/shopkeeper-tycoon.apk`](dist/shopkeeper-tycoon.apk).**

```
adb install -r dist/shopkeeper-tycoon.apk
```

Or copy it to a phone and tap it. Android will ask you to allow installing from
this source, because it is signed with a self-signed key rather than one registered
with Google Play.

Needs Android 5.0 (API 21) or newer **and OpenGL ES 3.0**, which covers
essentially every Android phone made since about 2013. No permissions, no network,
no ads.

---

## Playing

| | |
|---|---|
| **Left thumb** | Virtual stick — walk. The base appears wherever you put your thumb down; push past the ring to run. |
| **Right side, drag** | Look around. The camera orbits your character. |
| **Pinch** | Pull the camera in or push it out. |
| **Green button** | The one context action. Its label is whatever you're standing next to. |

The whole game is that one button plus your feet.

**The loop.** Walk to the back-office desk in the far corner and order stock. It
arrives in the stockroom. Walk to the stockroom hatch, pick up a crate — you'll see
your character hoist it, and you'll move slower carrying it. Walk to a shelf and
press the button: you crouch or reach for the right shelf height and fill it a unit
at a time. Then get behind the till before the queue builds.

**Serving is manual.** Standing at the till with someone waiting, the button reads
*Serve customer*. Each item is scanned individually — you can see the running total
over the counter — and the customer only pays when the basket is done. A big basket
keeps you behind the counter while the rest of the shop waits.

**Prices matter.** Every shopper has their own idea of what a thing is worth. Price
below the base value and they buy happily; price well above it and they put it back
and walk out, which costs you reputation and, through that, footfall.

**Patience matters more.** Queueing drains it. The ring over a waiting customer's
head is how long you have. Run it out and they abandon their basket and leave.

**Upgrades** are bought at the same desk:

- **Scanner** — less time per item at the till
- **Marketing** — more people through the door
- **Decor** — shoppers tolerate higher prices
- **Shelving** — more units per shelf
- **Cashier** — works the till so you can stay on the floor, and keeps earning while the app is closed
- **Stocker** — fetches crates and fills shelves for you

Eight product tiers unlock as you level, from Bread at level 1 to Caviar at level 7.
Rent and wages come out at the end of each in-game day (three real minutes).
Progress saves automatically when the app pauses.

If the camera drag feels inverted to you, it's one sign in `GameView.orbit`.

---

## Building

### The tested path

`build.sh` drives the Android build tools directly — no Gradle, no Android Studio,
no network:

```
javac  →  dx  →  aapt package  →  zipalign  →  apksigner
```

On Debian or Ubuntu everything it needs is in the archive:

```bash
sudo apt-get install aapt dalvik-exchange zipalign apksigner \
                     android-sdk-platform-23 openjdk-17-jdk-headless
./build.sh
```

The APK lands in `build/`. A self-signed key is generated into `keystore/` on the
first run (and is git-ignored). Every tool path can be overridden by environment
variable — `AAPT`, `DX`, `ZIPALIGN`, `APKSIGNER`, `ANDROID_JAR`, `KEYSTORE`.

The code compiles against the API 23 platform jar but targets API 34. Every
framework call it makes exists in API 21+, so that pairing is safe.

### Android Studio

Gradle files are included and the source tree uses the standard layout, so the
project opens in Studio directly. They are pinned to Android Gradle Plugin 7.4.2
because the manifest keeps its `package` attribute, which `build.sh` needs and AGP
8 rejects.

This Gradle path is provided for convenience and was **not** exercised while
building the project — the environment had no access to Google's Maven repository,
so AGP could not be downloaded. `build.sh` is what produced the shipped APK.

---

## Tests

There is no device or emulator in the loop, so the checks that matter run on a
plain JVM:

```bash
./tools/run-tests.sh
```

**GeometryTest** — for every triangle of every primitive, the normal implied by the
vertex winding must agree with the normal stored on the vertices, and the tangent
basis must come out unit length and perpendicular to the normal. Back-face culling
makes a mis-wound face vanish silently and a bad tangent lights a surface from the
wrong direction; neither throws an error. This caught every cylinder and cone in
the first build rendering inside out, and later caught `quadOriented` deciding both
of a quad's triangles from the first one — which is meaningless when that triangle
is degenerate, as it is at every sphere pole.

**SceneTest** — builds the entire world and the skinned character, checks every mesh
is well formed, that bone indices are real bones and weights sum to one, and that
the triangle budget stays inside what a phone GPU will hold up under.

**CharacterTest** — applies the real skinning matrices to the real mesh, the same way
the vertex shader will, and asserts the body stays a body: an unposed skeleton
leaves the mesh untouched, rotating a shoulder carries the hand but not the far
foot, a bent shin keeps its length, every animation state stays between 0.95 m and
2.1 m tall with no NaN, and the walk cycle lifts the ankle a walking amount rather
than a marching one. That last check is what caught the first gait overshooting
human hip and knee range by half again.

**SimTest** — plays the game. An autopilot paths across the floor and walks with the
same collision the real stick uses, so this covers reaching every shelf, the till,
the stockroom and the desk; the interaction prompts appearing in the right places;
the full order → carry → stock → serve loop; strict stock conservation; and five
trading days of economy. It caught the navigation grid clearing obstacles at a
smaller radius than the characters actually have, which produced paths that hug a
shelf corner and trap anyone who follows them.

**preview** — not a test, but the tool that makes the rest possible: `tools/preview`
renders the character to PNGs so proportions, clothing fit and texture density can
be judged rather than guessed.

**shaders** — every GLSL variant is extracted from the Java source and compiled as
OpenGL ES 3.00 with `glslangValidator`, so a syntax error shows up at build time
instead of as a black screen.

The `game` package has no Android imports at all, which is what lets the whole shop
be driven headlessly.

---

## How it is put together

```
app/src/main/java/com/poodicraft/shopkeeper/
  MainActivity.java     Lifecycle, wiring, save/load, every UI action
  GameView.java         GL thread: camera, touch, frame loop, overlay projection
  math/                 Vec2/Vec3, Mat4, quaternions, easing
  gl/                   Shader, VAO mesh, texture arrays, framebuffers,
                        procedural geometry, the third-person camera, and the
                        render pipeline
  art/                  Tiling noise, every material's pixels, the material table
  character/            19-bone rig, the skinned humanoid generator, procedural
                        animation
  world/                Shop layout, the room and its fittings, shelf stock,
                        and the bridge from simulation to draw calls
  game/                 Pure-Java simulation: player, customers, staff, products,
                        collision, A* navigation, economy, saves
  ui/                   Virtual stick, action button, HUD panels, world overlay
  audio/                Synthesised sound effects — no audio files
```

Some decisions worth knowing about.

**The look comes from texture arrays.** Thirty-two materials — tile, plaster, oak,
brushed steel, skin, denim, canvas, leather, corrugated card, printed packaging —
are generated as pixels at startup, each with a matching normal map derived from
its own height field. A material is a *vertex attribute* indexing into that array,
so the entire shop, mixing every surface it has, still draws in one call.

**One mesh, many people.** Everyone in the shop shares a single 5,000-triangle
skinned body. They differ by bone matrices, height, and a per-draw colour table
keyed on material — skin, hair, shirt, trousers and shoes are separate materials,
so one table turns the same geometry into a different person.

**The characters can be looked at offline.** `tools/preview` rasterises the same
meshes, the same skinning matrices and the same albedo textures to PNGs on a plain
JVM — seven camera angles plus a flat-fill silhouette, laid out as one contact
sheet. Building a character by reasoning alone produced eyes and a nose buried
inside the skull, an apron hanging off the chest as two flat slabs, and fabric
textures at one repeat per metre that rendered as masonry. Looking at the pictures
then found a second layer underneath: a torso lofted to collar height so there was
nowhere for a neck to go, limbs swept as straight cones with no knee or elbow, a
spine with no S so the body was a plank in profile, a head whose lowest point was
under the ear rather than at the chin — which tips the whole face back — and a skull
carrying the *sphere's* normals, so every feature modelled into it, the nose
included, was geometrically present and invisible on screen. None of that shows up
in a test that checks winding and bone weights. All of it is obvious in a picture,
and the ones that can be measured afterwards are now in `CharacterTest`.

**Animation is written, not authored.** There are no animation files. Poses are
built from curves — a walk cycle, a carry, a crouch, an arm reaching for a specific
shelf height, a scanning hand — and layered as blend weights, which is why a
shopkeeper can walk while carrying a crate and turn their head toward a customer at
the same time.

**Every quad decides its own winding.** `GeometryBuilder.quadOriented` picks the
winding that agrees with the vertices' own normals rather than trusting the call
site, because parametric patches are exactly where sign errors hide.

**One lock.** The GL thread holds `gameLock` across update-and-render; every UI
action takes the same lock. The overlay publishes *copies* of its projected labels,
so a draw in flight can never see a half-written one.

**Quality steps down on its own.** Frame time is smoothed and, if it stays long,
the renderer drops the shadow map and then the bloom and resolution. It never steps
back up unasked, so the picture does not oscillate while you are looking at it.

**Context loss is survivable.** Meshes and pixels live on the CPU side; a recreated
EGL context drops the GPU names and re-uploads rather than silently drawing nothing.

## Licence

Do what you like with it.
