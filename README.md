# BonescraftLand (Paper 1.20.1)

Features:
- Chunk-based land claims
- Build/break only in your own land (wilderness protected)
- Staff bypass permission (`bonescraft.bypass`)
- Optional builder rank to build anywhere (`bonescraft.build.anywhere`)
- Container protection: players can open containers, but cannot take items unless owner or permission (`bonescraft.container.take`)
- Playtime tracking per month (`/playtime`)

## Commands
- `/land help`
- `/land claim [size]` (size in chunks, e.g. 1, 2, 3)
- `/land unclaim` (your claim at current chunk)
- `/land info`
- `/land addmember <player>` / `/land removemember <player>`
- `/playtime [player] [YYYY-MM]`

## Permissions
See `plugin.yml`.

## Install
Copy the built jar from `target/` to your server `plugins/` folder.
