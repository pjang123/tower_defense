# Tower Defense Game Updates & Fixes

## 🛡️ Player & Interaction Changes
* Make players invulnerable to everything.
* Players can be set on fire by the fire tower.
* Slimes can damage players if the player gets too close.
* Player arrows should disappear quickly after landing on the ground.

## 🗼 Tower & Spell Fixes
* Remove the horn sound when sending waves.
* Check if the damage storm spell actually does damage.
* Fix lightning symbol from the redstone tower not displaying properly.
* Fix scatter shot turret tower path so arrows stop getting stuck on the tower.
* Increase chorus tower cooldown across all tiers.

## 🧟 Mob Fixes & Balancing
* Fix Blazes not being slowed and missing the slow symbol.
* Fix slow effect not working on the Breeze.
* Fix Zoglin AI breaking and targeting players.
* Fix Breezes getting teleported upwards by the chorus tower.
* Increase Bee range (currently too small).
* Nerf witches (currently healing too much, too fast).
* Buff the amount of XP and gold awarded from mobs.

## 📊 UI, Match Flow & End of Game
* Add a game timer to the scoreboard display.
* **End of match sequence (triggered when one castle falls):**
  * Display a splash screen announcing the winner.
  * Display match stats in the chat, including:
    * List of each team’s players.
    * How long the match lasted.
  * Send the player's personal stats, including:
    * How many mobs they spawned.
    * How many mobs they killed / total damage dealt.
    * Total money earned during the entire game.
    * Total XP gained throughout the entire game.
* Do not kick players out of the match world. Instead, replace game items with a return to lobby compass; they can click the compass to return to the lobby.
