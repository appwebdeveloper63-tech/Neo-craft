# NeoCraft v3.0 — Android Voxel Sandbox Game

A feature-rich Minecraft-inspired voxel game built in Kotlin with OpenGL ES 2.0.

---

## ✅ What's Implemented in v3

### 🌍 World Generation
- 14 biomes: Plains, Forest, Birch Forest, Taiga, Desert, Ocean, Deep Ocean, Mountains, Tundra, Savanna, Jungle, Swamp, Badlands, Mushroom Island, Rivers
- Infinite procedural terrain (seed-based Perlin/FBM noise)
- Ravines, large cave systems, underground lava/water lakes
- Dungeon structures (mossy cobblestone rooms)
- 7+ tree species with unique shapes (giant jungle trees, acacia, dark oak, crimson/warped)
- Giant mushrooms on Mushroom Island biomes
- Bedrock layer, deepslate below y=8

### 🧱 Block System (118 blocks)
- Stone family: granite, diorite, andesite, smooth stone, stone bricks, cracked/mossy/chiseled variants
- Deepslate family with deepslate ore variants
- 7 wood types with leaves: Oak, Birch, Spruce, Jungle, Acacia, Dark Oak, Crimson, Warped
- Nether blocks: netherrack, soul sand, nether brick, magma block, shroomlight, wart block
- Aquatic: prismarine, sea lantern
- Functional: crafting table, furnace, chest, torch, lantern, bookshelf, TNT
- Wool (6 colours), Concrete (4 colours), Terracotta, Copper block, Amethyst, Calcite, Tuff, Sculk
- Honey block, Slime block, Blackstone family

### ⛏️ Mining & Ores
- 11 ore types: Coal, Iron, Gold, Diamond, Emerald, Lapis, Redstone, Copper, Ancient Debris + Deepslate variants
- Block hardness & breaking time system
- Gravity blocks: sand, gravel, red sand fall realistically
- Block drops added to inventory on break

### 👾 Mobs
- Framework ready (no AI yet — planned for v4)

### 🧑 Player Mechanics
- Health, hunger, air systems
- Fall damage (>3 block drops hurt)
- Fire damage from lava contact
- Drowning damage when underwater
- XP & levelling system (earn XP by mining)
- Fly mode (double-tap jump, Creative only)
- Sneaking, sprinting, swimming
- 36-slot inventory (9 hotbar + 27 main)
- Game modes: Survival, Creative, Adventure, Spectator

### 🏗️ Building
- Block placement and breaking
- Survival: uses from inventory, Creative: infinite
- 7-stage break progress bar

### ⚙️ Game Systems
- Day/night cycle with sun, moon, sunset colours
- Dynamic render distance (auto-adjusts based on FPS)
- Chunk unloading for distant chunks (memory management)
- Auto-save every 2 minutes + save on quit
- World persistence (chunks + player state saved to device storage)
- Pause menu (Back button)
- Crafting UI (basic recipes)
- Inventory UI (all 36 slots)
- Score system

---

## 📱 Building a Debug APK (Android Studio)

### Prerequisites
- Android Studio Hedgehog (2023.1+) or newer
- JDK 17 (bundled with Android Studio)
- Android SDK with Build Tools 34+

### Steps

1. **Open the project**
   - Launch Android Studio → `Open` → select the `NeoCraft` folder (contains `settings.gradle`)

2. **Sync Gradle**
   - Click "Sync Now" when prompted, or: `File → Sync Project with Gradle Files`

3. **Connect device or start emulator**
   - Physical device: Enable Developer Options + USB Debugging
   - Emulator: API 21+ with x86_64 image recommended

4. **Build & Run**
   - Click the ▶ **Run** button (or `Shift+F10`)
   - Choose your device → APK installs automatically

5. **Get the APK file**
   - `Build → Build Bundle(s) / APK(s) → Build APK(s)`
   - APK location: `app/build/outputs/apk/debug/app-debug.apk`
   - Share/install via ADB: `adb install app-debug.apk`

---

## 🏪 Play Store Publishing Steps

### Step 1 — Generate a Release Keystore (do this ONCE, keep it safe!)
```bash
keytool -genkey -v \
  -keystore neocraft-release-key.jks \
  -alias neocraft \
  -keyalg RSA -keysize 2048 \
  -validity 10000
```
Store the `.jks` file and passwords somewhere **very safe** — you cannot republish updates without it.

### Step 2 — Configure Signing in `app/build.gradle`
Uncomment and fill in the `signingConfigs.release` block:
```groovy
signingConfigs {
    release {
        storeFile     file("path/to/neocraft-release-key.jks")
        storePassword "your_keystore_password"
        keyAlias      "neocraft"
        keyPassword   "your_key_password"
    }
}
buildTypes {
    release {
        signingConfig signingConfigs.release   // ← uncomment this line
        ...
    }
}
```
> ⚠️ Never commit passwords to Git. Use environment variables or `local.properties` instead.

### Step 3 — Build a Release AAB (App Bundle)
```
Build → Generate Signed Bundle / APK → Android App Bundle → Release
```
Output: `app/build/outputs/bundle/release/app-release.aab`

### Step 4 — Google Play Console
1. Go to [play.google.com/console](https://play.google.com/console)
2. Create a new app → fill title, category (Game → Arcade), description
3. Upload the `.aab` to **Internal Testing** first
4. Complete the **Content Rating questionnaire** (select: no violence, no user data)
5. Complete **Data Safety** form (select: no data collected)
6. Fill in **Store Listing**: screenshots (at least 2), feature graphic (1024×500), icon (512×512)
7. Set price (Free recommended for initial launch)
8. Submit for **review** — typically takes 1–3 days

### Step 5 — Release
- Once approved, promote from Internal Testing → Production

---

## 🎮 Controls
| Action | Control |
|---|---|
| Move | Left joystick (left half of screen) |
| Look | Drag right half of screen |
| Jump | JUMP button |
| Break block | BREAK button (hold) |
| Place block | PLACE button |
| Sneak | SNEAK button |
| Sprint | SPRINT button |
| Open inventory | INV button |
| Open crafting | CRAFT button |
| Scroll hotbar | Volume Up / Down |
| Fly (Creative) | Double-tap JUMP |
| Pause menu | Back button |

---

## 📁 Project Structure
```
app/src/main/
├── java/com/neocraft/game/
│   ├── MainActivity.kt          — Entry point, UI wiring
│   ├── GameSurfaceView.kt       — OpenGL surface wrapper
│   ├── GameRenderer.kt          — Main game loop (GL thread)
│   ├── engine/
│   │   ├── Camera.kt            — View + projection matrices
│   │   ├── ShaderProgram.kt     — GLSL shader loader
│   │   ├── SkyRenderer.kt       — Day/night sky dome
│   │   └── TextureAtlas.kt      — Procedural 256×256 atlas (118 tiles)
│   ├── world/
│   │   ├── BlockType.kt         — 118 block definitions + atlas map
│   │   ├── Chunk.kt             — Mesh builder (AO, greedy-ready)
│   │   ├── Constants.kt         — Global constants
│   │   ├── CraftingSystem.kt    — Recipe definitions + craft logic
│   │   ├── GameMode.kt          — Survival / Creative / Adventure / Spectator
│   │   ├── Inventory.kt         — 36-slot inventory system
│   │   ├── SaveSystem.kt        — Binary chunk + player persistence
│   │   ├── World.kt             — Chunk map, block access, gravity, save
│   │   └── WorldGen.kt          — 14-biome procedural terrain
│   ├── player/
│   │   └── Player.kt            — Movement, physics, stats, inventory
│   └── ui/
│       ├── HudView.kt           — HUD, inventory, crafting, pause UI
│       └── TouchController.kt   — Multi-touch input handler
└── assets/shaders/
    ├── block.vert / block.frag  — Opaque block shader (AO + fog + lighting)
    ├── water.vert / water.frag  — Animated water shader
    └── sky.vert / sky.frag      — Gradient sky dome shader
```
