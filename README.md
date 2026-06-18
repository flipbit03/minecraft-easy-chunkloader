# minecraft-easy-chunkloader

A free, fully open-source [Paper](https://papermc.io) plugin (Paper **26.1.2**, Java **25**)
that lets players keep chunks loaded with a simple placeable **Chunk Loader** block — so
farms, furnaces, redstone clocks and mob spawners keep running even when nobody is nearby.

Inspired by the (closed-source) [ChunkLoader](https://modrinth.com/plugin/chunkloader)
plugin, rebuilt from scratch and MIT-licensed.

## Features

- **Placeable Chunk Loader block** (a renamed Lodestone by default — configurable).
  Place it and the surrounding chunks stay loaded *and ticking*.
- **Nicknames.** Rename the item in an anvil before placing (e.g. `Iron Farm`,
  `Gold Farm @ Nether`) or use `/cl name <text>` — the nickname shows up in the list and
  the management menu so you always know which coordinates do what.
- **Survives restarts.** Loaders are saved to `loaders.yml` and their chunk tickets are
  re-applied automatically on every server start (including worlds loaded later by
  Multiverse).
- **Management GUI** — `/cl` opens a menu of your loaders; left-click to teleport to one,
  shift-click to remove it. Admins can browse everyone's loaders with `/cl gui all`.
- **Per-player limits** via config or numeric permission, with an unlimited bypass.
- **Configurable radius** — load just the loader's chunk, or a square area around it.
- **Tamper-resistant** — loader blocks shrug off explosions, fire and pistons so an active
  loader (and its tickets) can't be lost by accident; only a deliberate break removes one.
- **Crafting recipe** (toggleable) so loaders are obtainable in survival.
- **Ambient particles** above active loaders so you can see them working.
- Every message is MiniMessage and lives in `config.yml` — fully re-skinnable / translatable.

## How it works

The plugin uses Paper's **plugin chunk ticket** API — no NMS, no packets:

- On place of a Chunk Loader item, the plugin calls
  [`World#addPluginChunkTicket`](https://jd.papermc.io/paper/26.1.2/org/bukkit/World.html)
  for every chunk in the loader's radius. A plugin ticket keeps a chunk fully loaded and
  ticking until it is explicitly removed.
- `loaders.yml` is the source of truth. On enable the plugin re-applies a ticket for every
  saved loader; on disable it releases them. Overlapping loaders share tickets, so removing
  one never unloads a chunk another still needs.
- A periodic revalidation pass drops "orphaned" loaders whose block was removed by
  something that fires no break event (e.g. WorldEdit) and re-asserts tickets.

The radius is frozen per-loader at creation time, so changing `chunk-radius` later never
silently resizes existing loaders.

## Commands

All under `/chunkloader` (aliases `/cl`, `/chunkload`):

| Command | Description |
|---|---|
| `/cl` | Open the management GUI (your loaders) |
| `/cl gui [all]` | Open the GUI (`all` = every loader, admin only) |
| `/cl list [all]` | List loaders in chat |
| `/cl info` | Inspect the loader you are looking at / standing on |
| `/cl name <nickname...>` | Rename that loader |
| `/cl remove` | Remove that loader (returns the item) |
| `/cl give <player> [amount]` | Give Chunk Loader items (admin) |
| `/cl reload` | Reload `config.yml` (admin) |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `chunkloader.use` | everyone | Place, break and inspect your own loaders |
| `chunkloader.admin` | op | Give items, manage others' loaders, reload |
| `chunkloader.unlimited` | op | Bypass the per-player loader limit |
| `chunkloader.limit.<n>` | — | Grant a specific per-player limit (highest granted wins) |

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml): loader material,
chunk radius (and a `max-radius` safety cap), per-player limits, crafting toggle, particle
effect, revalidation interval, and all messages.

## Building

Requires JDK 25 and Gradle 9+ (wrapper included):

```bash
./gradlew build
```

The plugin jar lands in `build/libs/minecraft-easy-chunkloader-v<version>.jar` — drop it in
your server's `plugins/` folder.

Releases are built and attached automatically by GitHub Actions: publish a `vX.Y.Z` GitHub
release and the [`Release`](.github/workflows/release.yml) workflow builds the jar and
uploads it to the release. Every push / PR is also built by the
[`Build`](.github/workflows/build.yml) workflow.

## License

[MIT](LICENSE) — fully open source. Do whatever you like with it.
