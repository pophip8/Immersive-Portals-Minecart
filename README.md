# Immersive Portals + Minecart

French / English bilingual Fabric fork for Minecraft Java 26.3. It adds linked see-through portals, seamless travel, and dimension-to-dimension travel for vanilla minecarts, including chest contents and riders.

## Source layout

- `portals-26.3/` — Minecraft mod, Fabric adapter, bundled Optics source, tests, and build scripts.
- `VolmLib/` — source dependency used by the build.

The mod id remains `wormholes` so existing portal data and network behavior remain recognizable. The packaged mod version is 3.1.0-bilingual.1.

## Build

Requires JDK 25 and Fabric Loader for Minecraft 26.3. On Windows, run `portals-26.3/COMPILER.ps1` from PowerShell. The source distribution includes the Gradle wrapper. The Fabric API dependency is listed in the mod metadata.

## Minecart track setup

Place real, straight, level rails through the bottom of both portal openings, perpendicular to each portal surface. Extend the track on both sides. Power powered rails as usual. Existing links are retained when upgrading; rails that were previously displayed as portal surface decoration must be placed again as physical track.

## License and upstream attribution

The mod is distributed under GPL-3.0. See `LICENSE` and `portals-26.3/LICENSE.md`. The code is derived from [Wormholes](https://github.com/VolmitSoftware/Wormholes); see `portals-26.3/PERSONNALISATION.md` and `portals-26.3/THIRD-PARTY-NOTICES.md` for attribution, adapted files, dependency licenses, and commit references. Optics is bundled from its source repository, with its license and notices included; VolmLib's license and notices are also included.

This is a community fork; it is not an official Immersive Portals release.
