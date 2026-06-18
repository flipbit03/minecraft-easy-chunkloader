# minecraft-easy-chunkloader

A free, fully open-source [Paper](https://papermc.io) plugin (Paper **26.1.2**, Java **25**)
that lets players keep chunks loaded with a simple placeable **Chunk Loader** block — so
farms, furnaces, redstone clocks and mob spawners keep running even when nobody is nearby.

Inspired by the (closed-source) [ChunkLoader](https://modrinth.com/plugin/chunkloader)
plugin, rebuilt from scratch and MIT-licensed.

## Features

- **Admin-distributed Chunk Loader block** (a renamed Lodestone by default — configurable).
  There is **no crafting recipe**: loaders can only be obtained from an admin via
  `/cl give`. Place one and the surrounding chunks stay loaded *and ticking*.
- **Grief-proof.** Once placed, a loader block **can only be mined by an admin/op** — normal
  players can't break, relocate or steal it. Loaders also shrug off explosions, fire and
  pistons, so an active loader (and its chunk tickets) can't be lost by accident.
- **Nicknames.** Rename the item in an anvil before placing (e.g. `Iron Farm`,
  `Gold Farm @ Nether`) or use `/cl name <text>` — the nickname shows up in the list and
  the management menu so you always know which coordinates do what.
- **Survives restarts.** Loaders are saved to `loaders.yml` and their chunk tickets are
  re-applied automatically on every server start (including worlds loaded later by
  Multiverse).
- **Management GUI** — `/cl` opens a view-only menu of your loaders (admins can shift-click
  to remove one). No teleporting. Admins can browse everyone's loaders with `/cl gui all`.
- **Per-player limits** via config or numeric permission, with an unlimited bypass.
- **Configurable radius** — load just the loader's chunk, or a square area around it.
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

## Installation

Requires a **Paper 26.1.2** server (or a Paper fork such as Purpur) on **Java 25**. It will
*not* run on plain Spigot/Bukkit — it uses Paper-only APIs.

1. Download `minecraft-easy-chunkloader-v<version>.jar` from the
   [Releases page](https://github.com/flipbit03/minecraft-easy-chunkloader/releases)
   (or build it yourself — see [Building](#building)).
2. Drop the jar into your server's `plugins/` folder.
3. Start (or restart) the server. A `plugins/minecraft-easy-chunkloader/config.yml` and an
   (empty) `loaders.yml` are created on first run.
4. Confirm it loaded: the console prints `minecraft-easy-chunkloader enabled`, and in-game
   `/cl help` lists the commands.

## Quick start

1. **Get a Chunk Loader** from an admin — there's no recipe. An admin runs
   `/cl give <player> [amount]` (or `/cl give <you> 1` for themselves).
2. *(optional)* **Name it.** Rename the item in an anvil — e.g. `Iron Farm` — so you can
   tell your loaders apart later.
3. **Place it** at your farm/contraption. You'll see a confirmation message and its chunk
   is now kept loaded and ticking. By default each loader covers exactly **one chunk** —
   raise `chunk-radius` in the config if you want a loader to cover a square area instead.
4. **Verify.** Walk far away (or have everyone log off) and your farm keeps running.
   Use `/cl list` or open the menu with `/cl` to see your loaders.
5. **Removing** a loader is admin-only (anti-grief): an admin mines the block (the item
   drops back, nickname intact), uses `/cl remove` while looking at it, or shift-clicks it
   in `/cl gui all`. Normal players cannot break or move a placed loader.

> Loaders persist across restarts automatically — there's nothing to re-arm after a reboot.

## Commands

All under `/chunkloader` (aliases `/cl`, `/chunkload`):

| Command | Description |
|---|---|
| `/cl` | Open the management GUI (your loaders) |
| `/cl gui [all]` | Open the GUI (`all` = every loader, admin only) |
| `/cl list [all]` | List loaders in chat |
| `/cl info` | Inspect the loader you are looking at / standing on |
| `/cl name <nickname...>` | Rename that loader (owner or admin) |
| `/cl remove` | Remove that loader, returns the item (**admin only**) |
| `/cl give <player> [amount]` | Give Chunk Loader items (**admin only**) |
| `/cl reload` | Reload `config.yml` (**admin only**) |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `chunkloader.use` | everyone | Place, break and inspect your own loaders |
| `chunkloader.admin` | op | Give items, manage others' loaders, reload |
| `chunkloader.unlimited` | op | Bypass the per-player loader limit |
| `chunkloader.limit.<n>` | — | Grant a specific per-player limit (highest granted wins) |

## Configuration

See [`src/main/resources/config.yml`](src/main/resources/config.yml): loader material,
chunk radius (and a `max-radius` safety cap), per-player limits, particle effect,
revalidation interval, and all messages.

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
