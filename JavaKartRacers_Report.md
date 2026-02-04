JavaKartRacers — Project Report
===============================

Author: Team JavaKartRacers (FRU, FAVOUR, HAPPI, KAPETE, KIMBI)
Date: 2026-02-04
Version: 2.0

Summary
-------
This document is a complete project report for JavaKartRacers. It describes the architecture, every Java class in the client and server packages with purpose and responsibilities, resource inventory (images and audio), database schema and setup, how to import/build/run the project using Eclipse on Windows, testing guidance (nitro, end-of-game/lobby flow), suggested screenshots and sample log excerpts to include in a submission, troubleshooting tips and recommended future work.

Table of contents
-----------------
- Project overview
- Architecture & networking
- Client package: per-class descriptions
- Server package: per-class descriptions
- Resources inventory (images + audio)
- Database schema & setup
- Build / Run instructions (Eclipse + command line)
- Testing checklist (manual tests to include)
- Recommended screenshots and where to take them
- Sample server/client logs to paste in the report
- Troubleshooting & known issues
- Suggested improvements / future work
- Appendix (SQL schema, conversion to Word instructions)


Project overview
----------------
JavaKartRacers is a Java-based networked kart racing prototype built with Swing for UI and plain TCP sockets for client-server communication. The client simulates the game locally (karts, racetrack, nitro) and sends periodic state updates to the server; the server manages lobbies, broadcasts opponent updates, records wins into a MySQL database, and coordinates game start/end.

Key features delivered
- Multiplayer lobby with player/kart/map selection and ready/unready states
- In-race local kart simulation for each client, including nitro with capacity bar
- Collision handling with server-broadcast collision events
- End-of-game screen with winner/loser messaging and a robust "Return to lobby" flow (non-blocking)
- Persistent storage of wins and races in MySQL
- Audio and image assets for polished UI and feedback

Architecture & networking
-------------------------
- Protocol: custom textual commands over TCP (single-line messages). Standard commands include LOGIN_REQUEST, REQUEST_PL_LOBBY_DATA, SEND_KART_DATA, SEND_COLLISION, RACE_WON, RACE_LOST, etc.
- Port: default server port 5000.
- Client-side concurrency: UI runs on the Event Dispatch Thread (EDT); network I/O is handled in a background thread (ServerHandler) to keep the UI responsive.
- Server-side concurrency: each client is handled by a ClientHandler thread; ClientManager manages connected clients and broadcasts.

Client package — class-by-class
------------------------------
Below are the classes in `src/game/client` with their responsibilities and important methods.

1. `Main`
- Purpose: client entry point. Initializes `GameClient`, loads audio and opens the main `Window`.
- Notes: constants with version and authors for the report cover.

2. `Window`
- Purpose: top-level JFrame that holds a single `BaseDisplay` JPanel.
- Notes: centralizes window configuration and visibility.

3. `BaseDisplay` (implements repaint loop)
- Purpose: central drawing panel. Hosts the active `Display` screen, dispatches key events and button clicks, and provides helpers to add Swing buttons and labels.
- Important methods: `setCurrentDisplay(Display)`, `addButton(...)`, `addLabel(...)`, `addUserInputBox(...)`.

4. `Display` (interface)
- Contract for all screens: `update(Graphics)`, `buttonHandler(Object)`, `keyHandler(int,boolean)`.

5. `MenuDisplay`
- Purpose: initial menu UI (login/registration, mute toggle).
- Behavior: uses `AudioManager` to play menu music, runs login/registration network calls off-EDT and navigates to `GameJoinDisplay` on success.

6. `GameJoinDisplay`
- Purpose: UI to join local or remote lobbies.
- Behavior: sets up `GameLobbyDisplay` via `createLocalLobby()` and triggers `ServerHandler.requestLobbyData()`.

7. `GameLobbyDisplay`
- Purpose: lobby UI for kart/map selection and ready states.
- Behavior: draws player slots, map preview, kart thumbnails; when the server triggers start, packages a `GameOptions` and starts `GameDisplay`.

8. `GameOptions`
- Purpose: data container passed from lobby to `Game` with choices, opponents, and main player settings.

9. `Game`
- Purpose: encapsulates race state: racetrack, players, laps, checkpoints, timer, and game lifecycle methods.
- Important: `startGameTimer()` calls `resetNitroForAll()` to reset nitro at race start; `winGame()` / `loseGame()` / `endGame()` set end type and switch to `GameOverDisplay`.
- Notes: includes `resetNitroForAll()` to ensure nitro bars are reset at race boundaries.

10. `GameDisplay` (in-race)
- Purpose: the active race screen. Handles drawing of track, players, opponent karts, HUD (nitro bar), and key input.
- Important fields: `mainPlayer` (`ControlledPlayer`), `mainPlayerKart` (`Kart`), `opponents` (List<Player>), `KART_SEND_INTERVAL_MS` throttle for sending kart state.
- Networking: calls `ServerHandler.sendKart(kart)` at intervals (throttled). Now guarded so it stops when `isGameActive` false.
- Input: handles nitro key (configured via `ControlledPlayer` key bindings) — calls `Kart.startNitro()` and `Kart.stopNitro()`.
- Robustness: uses defensive copies when iterating opponent lists to avoid concurrent modification errors and wraps drawing in try/catch to avoid UI freezes.

11. `GamePauseDisplay`
- Purpose: pause menu during the race (resume, options, etc.).

12. `GameOverDisplay`
- Purpose: end-of-game screen showing "YOU WON!!" or "YOU LOST" and a "Return to menu" button.
- Important fixes implemented: removed duplicate big-labels by drawing the large text only in `update(Graphics)`; added alternate constructor `GameOverDisplay(int endType, String reason)` to show server-driven end messages without duplicating labels; `buttonHandler()` for "Return to menu" calls `handler.endGame()` quickly, shows `LoadingDisplay` immediately and runs `requestLobbyData()` on a background thread — then switches to `GameLobbyDisplay` on completion.

13. `LoadingDisplay`
- Purpose: a minimal loading overlay with animated dots used while lobby data is requested in background.

14. `Player` & `ControlledPlayer`
- `Player`: stores `playerNumber`, `Kart`, `name`, and `wins`.
- `ControlledPlayer`: extends `Player` and provides key bindings: arrow keys and `N` for nitro (default), accessible via getters.

15. `Kart`
- Purpose: physical and visual state of a kart. Handles position, rotation, speed, sprite selection, collisions, slip (banana), and nitro.
- Nitro implementation:
  - `nitroCapacity` (0-100%), `nitroActive`, `nitroDepleted`.
  - `startNitro()`/`stopNitro()` manage sound and activation; `resetNitro()` resets capacity and flags.
  - depletion/recharge rates and boost multiplier constants are in `Kart`.
- Collision / slip handling: supports scheduled collisions (timestamped) for server synchronization and a slip effect with sound.

16. `Racetrack`
- Purpose: loads track image, defines playable area, checkpoints, start positions and inner bounds used for wrong-way checks.

17. `Banana`
- Purpose: visual slipping obstacle; draws animated gif if available and provides a helper to spawn at a playable area random spot.

18. `AudioManager`
- Purpose: statically manages audio playback using javax.sound.sampled Clips. Loads files at startup and maps logic keys (e.g., "RACE_THEME", "NITRO_SOUND") to wav files.

Client notes
------------
- Images are loaded via class resources (getResource) — ensure resources remain on classpath (they are under `src/game/client/images/`).
- AudioManager currently loads audio by file path `./src/game/client/audio/...` — this works when run from the project root or IDE but may need classpath loading for a packaged JAR.

Server package — class-by-class
------------------------------
1. `Main` (server entry)
- Calls `ClientManager.establishConnection()` which sets up the server socket.

2. `ClientManager`
- Purpose: listens on server port (5000), accepts incoming sockets, and spawns `ClientHandler` threads; also broadcasts updates among clients.
- Includes a prune thread that disconnects clients that fail to send heartbeat within a configured timeout (default: 5 minutes in code snippet).

3. `ClientHandler`
- Purpose: per-client thread. Responsible for parsing incoming textual commands from clients and acting on them. It performs authentication, lobby assignment, forwarding kart data, broadcasting collisions, and recording wins.
- Important behaviors:
  - On `LOGIN_REQUEST` / `REGISTER_REQUEST` interacts with `DatabaseManager`.
  - On `REQUEST_PL_LOBBY_DATA` assigns a player number using `LobbyManager`, sets initial choices, and replies with `RESPOND_PL_LOBBY_DATA` (includes username and wins if known).
  - On `SEND_KART_DATA` forwards `SEND_OP_KART_DATA` to players in the current game.
  - On `SEND_COLLISION` deduplicates and broadcasts `BROADCAST_COLLISION` messages.
  - On `RACE_WON` calls `GameManager.sendRaceWinnerToAllPlayers(this)` and persists the win to DB.

4. `GameManager`
- Purpose: central game state on server: players in-game list, chosen map, weather flag, and `initiateGame()` to start a game. It also handles sending a race winner to other players and clearing state on end.
- Includes collision dedup logic to avoid spamming the same collision repeatedly.

5. `DatabaseManager`
- Purpose: MySQL helper used for authentication, registration, recording a win, recording a race, retrieving player wins, and a debug dump.
- Important: the JDBC URL and DB credentials are hard-coded in the file; the MySQL connector jar is in the repo and needs to be added to the project's classpath.

Resources inventory
-------------------
Location: `src/game/client/images/` and `src/game/client/audio/`

Images (high level)
- UI images (`images/ui/`) — menu backgrounds, lobby backgrounds, buttons, ready/not-ready symbols, player labels, lap icons, player pointer, wrong-way indicator, loading gif, join hover, etc.
- Racetrack images (`images/racetrack/`) — racetrack0..2.png map backgrounds, spectators gifs, Banana.gif, weather0..2.gif, race countdown images.
- Kart images (`images/kart/`) — kartOption thumbnails and multiple `styleX` directories containing 16 rotation frames each (kart0..kart15.png) per kart style.

Audio files (`src/game/client/audio/`)
- menuTheme.wav — menu background music (looped)
- raceTheme.wav — race background music (looped)
- gameOver.wav, gameWin.wav — end game sounds
- collision.wav, newLap.wav, slipSound.wav, nitroSound.wav, raceCountdown.wav, button.wav — short sound effects

Resource usage notes
- Images are loaded with `getResource("images/...")` so they must remain in the `src` path and be copied to the classpath by the IDE/build.
- AudioManager currently accesses audio files using relative file path `./src/game/client/audio/...` (works in dev/IDE). If packaging into a JAR, switch to classpath stream loading.

Database schema & setup (SQL)
-----------------------------
Use this SQL snippet to create the minimal database schema required by `DatabaseManager`:

```sql
CREATE DATABASE IF NOT EXISTS javakart_db;
USE javakart_db;

CREATE TABLE players (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) NOT NULL UNIQUE,
  password VARCHAR(255) NOT NULL,
  total_wins INT DEFAULT 0
);

CREATE TABLE races (
  race_id INT AUTO_INCREMENT PRIMARY KEY,
  winner_id INT,
  race_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (winner_id) REFERENCES players(id) ON DELETE SET NULL
);
```

- Edit `DatabaseManager.URL`, `USER`, `PASS` to match your MySQL instance before running the server.
- Add `mysql-connector-j-9.5.0.jar` to Eclipse build path or classpath before running.

Build / run instructions (Eclipse, Windows)
-------------------------------------------
Prerequisites:
- Java JDK (11+ recommended)
- Eclipse IDE for Java Developers
- MySQL server with schema created (see SQL above)
- mysql-connector JAR available in repository

Eclipse import steps:
1. File → Import → Existing Projects into Workspace. Select `JavaKartRacers` folder.
2. If not auto-detected, create a new Java project and set `src` as the source folder.
3. Add external JAR to build path: right-click project → Build Path → Configure Build Path → Libraries → Add External JARs... → choose `mysql-connector-j-9.5.0.jar`.
4. Edit `DatabaseManager` to set correct DB credentials if necessary.
5. Run server: Right-click `game.server.Main` → Run As → Java Application.
6. Run one or more clients: Right-click `game.client.Main` → Run As → Java Application.

Command-line (optional)

From project root, to compile and run (example):

```cmd
javac -d bin @sources.txt
java -cp ".;bin;mysql-connector-j-9.5.0/mysql-connector-j-9.5.0.jar" game.server.Main
java -cp ".;bin;mysql-connector-j-9.5.0/mysql-connector-j-9.5.0.jar" game.client.Main
```

Testing checklist (manual tests to include in report)
----------------------------------------------------
1. Lobby join test
- Steps: Run server, start client, login or register, join local lobby.
- Expected: RESPOND_PL_LOBBY_DATA is received; player assigned a numeric slot; lobby shows other players and kart options.

2. Nitro test
- Steps: Start a game with at least one client (local testing ok), press N to trigger nitro.
- Expected: blue nitro bar drains; kart accelerates by boost multiplier while nitro active; when capacity hits 0, further N presses have no effect until next race start.

3. End-of-game & Return-to-Lobby
- Steps: Let a player win the race. On losers' screens, click "Return to Menu".
- Expected: Loading overlay appears immediately; client stops sending `SEND_KART_DATA` to server; server receives `REQUEST_PL_LOBBY_DATA`; client transitions to `GameLobbyDisplay` when server responds.

4. Collision synchronization
- Steps: Cause a collision in the race. Observe logs and client visuals.
- Expected: Server broadcasts `BROADCAST_COLLISION` and clients schedule & show collision effect at the timestamp provided.

Recommended screenshots
-----------------------
- Main menu with username fields and menu background.
- Lobby screen showing player slots and kart selection.
- Race screenshot showing racetrack and multiple karts (HUD visible) with nitro bar partially drained.
- Nitro full, nitro used: two screenshots side-by-side showing before and after using nitro (bar decreases and kart speed increases).
- GameOver display showing single "YOU LOST" message with reason and the "Return to menu" button.
- Loading overlay screenshot when transitioning back to lobby.

Sample logs to include in the report
-----------------------------------
Paste short server log excerpts that correlate to actions. Example fragments you can include:

1) SEND_KART_DATA stream during race (server logs):
```
[Server] Received from client: SEND_KART_DATA 2 125.0 2.0 300.20288 86.37195
[Server] parsed -> command='SEND_KART_DATA' user='2' (len=1) pass='125.0 2.0 300.20288 86.37195' (len=28)
```

2) RACE_WON and DB persist:
```
[Server] Received from client: RACE_WON
[Server] parsed -> command='RACE_WON' user='null' (len=0) pass='null' (len=0)
[DB] recordWin(MIGUEL) => updated=1
[Server] createPlayerLobbyData: player=1 kartChoice=0 mapChoice=0
```

3) Return-to-lobby flow (expected sequence):
```
[Client] GameOverDisplay: user clicked ReturnToMenu
[Client] ServerHandler: endGame() called, isGameActive set to false
[Client] ServerHandler: sending REQUEST_PL_LOBBY_DATA
[Server] Received from client: REQUEST_PL_LOBBY_DATA
[Server] parsed -> command='REQUEST_PL_LOBBY_DATA' user='null' pass='null'
[Server] createPlayerLobbyData: player=1 kartChoice=0 mapChoice=0
[Client] Received RESPOND_PL_LOBBY_DATA and switched to GameLobbyDisplay
```

Suggested screenshots filenames:
- docs/screenshots/menu.png
- docs/screenshots/lobby.png
- docs/screenshots/findGame.png
- docs/screenshots/race_nitro_after.png
- docs/screenshots/gamepause.png
- docs/screenshots/race_nitro_before.png
- docs/screenshots/gameover.png

Sample logs
-----------
- docs/screenshots/data.png
- docs/screenshots/serverlog.png
- docs/screenshots/clientlog.png

- docs/screenshots/race_nitro_before.png
Troubleshooting & known issues
-----------------------------
- If audio fails, make sure WAV files are present and that your JDK supports the format; switching to classpath loading is recommended before packaging.
- Database connection errors: check `DatabaseManager` credentials and that MySQL is running; add JDBC driver to classpath.
- If the UI freezes: look for any blocking network call performed on the EDT; fix by moving to a background thread (this project already uses background threads for login/registration and lobby requests).
- Duplicate end-game messages: earlier bug fixed by removing duplicate label creation and drawing large text only in `update(Graphics)`.

Suggested improvements (report discussion)
-----------------------------------------
- Security: store hashed passwords (bcrypt) instead of plain text.
- Packaging: move audio resources to classpath and update `AudioManager` to load via `getResourceAsStream` so a packaged JAR works.
- Network: implement server-authoritative nitro (client sends nitro intent, server validates and broadcasts) so all clients consistently observe boosts.
- Testing: add unit tests for `Kart.updatePosition()` including nitro depletion/recharge edge cases.
- UX: add a retry/time-out in `LoadingDisplay` when lobby request fails.

Appendix: SQL (also saved as `create_schema.sql` in the `docs` folder) and how to convert this report to Word
-----------------------------------------------------------------------------------------------------------
- The `create_schema.sql` file is saved next to this report. Use it to build the database.

Convert Markdown report to Word (.docx)
--------------------------------------
Recommended (requires Pandoc):
1. Install Pandoc (https://pandoc.org/installing.html).
2. Run from project `docs` folder:

```cmd
pandoc JavaKartRacers_Report.md -o JavaKartRacers_Report.docx
```

Alternatively, opening the Markdown file in Microsoft Word (recent versions) will import Markdown and allow you to save as `.docx`.

---

End of report



