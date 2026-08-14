# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0-alpha] - 2026-08-13

### Added

- Dynamic view distance system for fast-moving players with a global compute 
  budget and different costs for resident, generated, and ungenerated chunks.
- Nonblocking chunk lookups for player-controlled collision, fluid, and
  NeoForge position checks on 0 view distance
- Added the generated `config/supersonic-chunks.properties` configuration file.
- Added settings for budget capacity and refill rate, chunk costs, view-distance
  recovery, fast-player detection, direct movement, and movement packet limits.
- Added namespaced dimension-height overrides using
  `height.<namespace>:<dimension>`. The Nether defaults to
  `height.minecraft:the_nether=128`; other dimensions default to their maximum Y.

### Changed

- Changed license to GNU Lesser General Public License v3.

### Fixed

- Fixed a crash caused by loading too many chunks while flying at high speeds

## [0.1.1-alpha] - 2026-08-13

### Fixed

- Fixed height-cache creation log spam caused by typo.

## [0.1.0-alpha] - 2026-08-13

### Added

- Initial Fabric and NeoForge release for Minecraft 26.2.
- Added high-speed player movement through unloaded chunks when the movement
  path stays above cached terrain heights.
- Added per-dimension terrain-height caches updated as chunks load and unload.
- Increased the per-tick player movement packet limit from 5 to 30.
