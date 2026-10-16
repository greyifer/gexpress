G'NETWORK ADMIN CONTROL
=======================

Open "GNetwork Admin Console.exe" from your Desktop. It is a native Windows
control panel styled after G'Lib's administration menus.

FIRST RUN
---------
1. Open Backend in the left sidebar.
2. Select "Install required tools" if Railway CLI is not ready.
3. Select "Railway login" and complete Railway's browser authentication.
4. In Railway, open the G'Network Production environment.
5. Right-click the backend service (not PostgreSQL).
6. Choose "Copy SSH Command".
7. Paste it into Production target and select "Save backend target".

MANAGING ACCESS
---------------
- Accounts: create or reset a MODERATOR or OWNER account for a verified UUID.
- Revoke access: disable the account and delete all active admin sessions.
- Audit history: owner-side production access ledger showing which moderator
  opened moderation data. Message bodies are never included.
- Backend > In-app private shell: run commands inside the Railway service
  without opening a separate Windows terminal.

EDITING THE WEBSITE
-------------------
1. Open Website in the left sidebar.
2. Select "Load live website" to read the current production content.
3. Edit the site name, colours, or the selected page's content and visibility.
4. Select "Save & publish". Open copies of the public site update automatically.

The desktop editor preserves every page's existing sections and buttons. Use
"Advanced builder" when you want to add, remove, or reorder those blocks. Both
editors save to the same production data, and desktop saves are recorded in the
admin audit history as WEBSITE_UPDATE.

CONNECTING A G'EXPRESS SERVER
-----------------------------
1. Start the Minecraft server once with the updated G'Express mod.
2. Open that server's config/gexpress-console.json file.
3. For Wepwawet, allocate a second port and enter the server IP or hostname
   followed by that port in Game servers. A domain is not required.
4. Add the owner token from the same config file.
5. Select the profile, then use Test connection.

The bridge listens only on 127.0.0.1:8765 by default. For a hosted server, set
bindAddress to 0.0.0.0 and port to the allocated port. Direct remote requests
and responses are encrypted and authenticated end-to-end with keys derived from
the random owner token. The token itself is not sent over the connection.

The Skin shop page controls the live title, subtitle, colours, layout style,
case visibility, personal-offer count and rotation length, plus every eligible
skin's price and rotation eligibility. Save & update live immediately persists
the accepted settings on the selected server and broadcasts them to players.

The Developer page replaces the removed in-game Developer tab. It loads and
publishes the selected server's complete G'Express configuration as JSON. The
server validates and migrates it before saving and broadcasting the result.

The player must open K > Friends & Chat once before you provision their UUID.
When you create/reset an account, the initial password is entered and confirmed
inside the graphical app. The player is forced to choose a new password on
first login.

TOOL REPAIR
-----------
If Railway CLI is missing or damaged, open Backend and select
"Repair Railway CLI" or "Repair / reinstall". The button remains available
even when a broken Railway launcher file exists.

"In-app private shell" requires three things:
1. Railway CLI must be installed and logged in.
2. Railway SSH access must be configured from Backend.
3. The Production target must be saved from Railway's "Copy SSH Command".

The SSH setup creates or reuses a key under:
  %USERPROFILE%\.ssh
Only its public half is registered with Railway. The private key remains local
and is never copied into the executable, its config, or GitHub.

SECURITY
--------
- Passwords exist in app memory only long enough to send them through encrypted
  SSH standard input, then the mutable password buffer is cleared.
- Passwords are never placed in command arguments, environment variables,
  config files, logs, or GitHub.
- Player UUIDs entered for an action are not saved.
- Only the non-secret Railway service target is stored locally at:
  %LOCALAPPDATA%\GNetworkAdminConsole\railway-target.txt
- Game-server owner tokens are stored with Windows DPAPI encryption for the
  current Windows user in the same application-data directory.
- Direct game-server traffic uses authenticated encryption, timestamps, and
  one-time request nonces so it can safely use a raw allocated port.
- Never paste a password, DATABASE_URL, admin token, or private message into
  GitHub, Discord, screenshots, or support chats.

BUILDING THE CONSOLE
--------------------
Run build.ps1 from this folder. Use build.ps1 -InstallDesktop to compile,
self-test, and replace the Desktop executable after closing the running app.
