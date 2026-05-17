# Inventory Sort Design

**Goal:** Add a Sort button to the player inventory screen that sorts items by category, applies a saved hotbar layout (with best-tool logic), and auto-equips the best armor from the inventory.

**Architecture:** Server-side sort triggered by a C2S `SortPayload`. Hotbar layout preference is stored client-side in a config JSON file and sent with the payload. Armor auto-equip and tool placement happen on the server before the category sort. Consistent with the existing Restock pattern.

**Tech Stack:** MC 26.1.2 (Mojmap), Fabric Loader 0.19.2, Fabric API 0.148.0+26.1.2, Java 25

---

## UI

Three buttons stacked on the right side of the inventory screen (all 70×20px):

```
[ Restock     ]   topPos + 60  (existing)
[ Sort        ]   topPos + 84  (new)
[ Save Layout ]   topPos + 108 (new)
```

- **Save Layout** — snapshots the player's current hotbar (slots 0–8) to `config/pull_from_chests_hotbar_layout.json` on the client. Each slot stores the item registry ID (e.g. `"minecraft:diamond_pickaxe"`) or `null` for empty.
- **Sort** — reads the saved layout file (all nulls if no file exists), sends a `SortPayload` to the server, which performs the full sort.

---

## Hotbar Layout File

Location: `<game dir>/config/pull_from_chests_hotbar_layout.json`

Format: a JSON array of 9 strings or nulls, one per hotbar slot.

```json
["minecraft:diamond_sword", "minecraft:diamond_pickaxe", null, "minecraft:cooked_beef", null, null, null, null, null]
```

Written when the player clicks "Save Layout." Read when the player clicks "Sort" and sent inside `SortPayload`. If no file exists, Sort treats all 9 slots as `null` (no hotbar preference).

---

## Sort Algorithm

Inputs: player inventory (slots 0–35), current armor (slots 36–39), saved hotbar layout (9 nullable item IDs).

**Step 1 — Collect pool**
Pull all items from slots 0–35 and the 4 armor slots (36–39) into a mutable list. These slots are fully cleared before repopulation. Off-hand (slot 40) is never touched.

**Step 2 — Apply hotbar layout**
For each of the 9 saved layout slots (index 0–8):
- If the saved ID is `null`: skip (slot will be filled in step 4 if items remain).
- If the saved ID is a tool type (detected via item tags `#minecraft:swords`, `#minecraft:pickaxes`, `#minecraft:axes`, `#minecraft:shovels`, `#minecraft:hoes`): find the best item in the pool belonging to that same tag. Best = highest material tier → most enchantments (as tiebreaker).
- Otherwise: find the first exact item match in the pool (`ItemStack.isSameItem`).
- Place the winner in hotbar slot, remove from pool. If no match, slot stays empty for now.

**Step 3 — Equip best armor**
For each armor slot in order (helmet 36, chestplate 37, leggings 38, boots 39):
- Find all armor pieces of the correct type from the pool, detected via `item instanceof Equipable eq && eq.getEquipmentSlot() == EquipmentSlot.HEAD` (or CHEST, LEGS, FEET for the other slots).
- Pick the best: highest material tier → most enchantments as tiebreaker.
- Place it in the armor slot, remove from pool. If no armor of that type exists, slot is left empty.

**Material tier ranking (highest to lowest):**
- Tools/weapons: Netherite(5) > Diamond(4) > Iron(3) > Stone(2) > Gold(1) > Wood(0)
- Armor: Netherite(5) > Diamond(4) > Iron(3) > Chain(2) > Gold(1) > Leather(0)
- Determined by checking `item instanceof TieredItem ti ? ti.getTier() : null` and mapping to a fixed score.

**Step 4 — Sort remaining pool**
Sort pool items by:
1. Category (ascending ordinal):
   - 0 Weapons — tagged `#minecraft:swords`, or is `Items.MACE`, `Items.BOW`, `Items.CROSSBOW`, `Items.TRIDENT`
   - 1 Tools — tagged `#minecraft:pickaxes`, `#minecraft:axes`, `#minecraft:shovels`, `#minecraft:hoes`, or is `Items.SHEARS`, `Items.FLINT_AND_STEEL`, `Items.FISHING_ROD`
   - 2 Armor — is `ArmorItem`
   - 3 Food — `item.getFoodProperties(null) != null`
   - 4 Blocks — `item instanceof BlockItem`
   - 5 Misc — everything else
2. Material tier descending (Netherite first).
3. Enchantment count descending.

**Step 5 — Repopulate**
Place sorted pool items into slots 9–35 (main inventory) first, then any remainder into unfilled hotbar slots (0–8, skipping slots already filled in step 2).

---

## New Files

| File | Responsibility |
|------|----------------|
| `src/main/java/.../SortPayload.java` | C2S packet: carries the 9-element hotbar layout as `List<String>` |
| `src/main/java/.../HotbarLayout.java` | Client-side: reads/writes `config/pull_from_chests_hotbar_layout.json` |
| `src/main/java/.../InventorySorter.java` | Server-side: full sort algorithm (steps 1–5 above) |

## Modified Files

| File | Change |
|------|--------|
| `src/main/java/.../PullFromChests.java` | Register `SortPayload` type + server handler |
| `src/main/java/.../mixin/InventoryScreenMixin.java` | Add Sort and Save Layout buttons |
| `src/main/resources/pull_from_chests.mixins.json` | No changes needed (mixin already registered) |
| `src/main/resources/fabric.mod.json` | No changes needed |
