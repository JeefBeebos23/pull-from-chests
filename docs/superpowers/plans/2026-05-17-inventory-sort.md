# Inventory Sort Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Sort and Save Layout buttons to the player inventory screen; Sort applies a saved hotbar template (best tool wins per slot, best armor auto-equipped), then sorts the remaining inventory by category.

**Architecture:** Three tasks produce independently compilable units — SortPayload (C2S packet), HotbarLayout (client config read/write), InventorySorter (server sort logic) — wired together in PullFromChests.java and InventoryScreenMixin.java. Consistent with the existing Restock pattern: C2S packet → server executes → inventory auto-syncs to client.

**Tech Stack:** MC 26.1.2 (Mojmap), Fabric Loader 0.19.2, Fabric API 0.148.0+26.1.2, Java 25

---

## Known 26.1.2 API gotchas

- `CustomPacketPayload.Type<T>`: use `new Type<>(Identifier.fromNamespaceAndPath(...))` — NEVER `createType(String)` (prepends `minecraft:` causing illegal identifier)
- `PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC)` requires `StreamCodec<RegistryFriendlyByteBuf, T>` (not ByteBuf) when writing strings
- `BuiltInRegistries.ITEM.getHolder(ResourceKey.create(Registries.ITEM, id))` → `Optional<Holder.Reference<Item>>` — safer than `.get(Identifier)` whose nullability varies
- `BuiltInRegistries.ITEM.getKey(item)` → `@Nullable Identifier` — confirmed present
- `ItemStack.is(TagKey<Item>)` works server-side (tags loaded)
- Enchantment count: `stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size()` — verify `.size()` via javap if build fails; alternative: `.entrySet().size()`
- Armor slot: `ArmorItem ai = (ArmorItem) item; ai.getEquipmentSlot()` returns `EquipmentSlot` (HEAD/CHEST/LEGS/FEET)
- Inventory armor indices: slot 36=boots(FEET), 37=leggings(LEGS), 38=chestplate(CHEST), 39=helmet(HEAD)

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `src/main/java/com/jeefbeebos23/pull_from_chests/SortPayload.java` | C2S packet; carries 9-element hotbar layout as `List<String>` |
| Create | `src/main/java/com/jeefbeebos23/pull_from_chests/HotbarLayout.java` | Client-side: save/load hotbar layout JSON from config dir |
| Create | `src/main/java/com/jeefbeebos23/pull_from_chests/InventorySorter.java` | Server-side: all sort logic (hotbar layout, armor equip, category sort) |
| Modify | `src/main/java/com/jeefbeebos23/pull_from_chests/PullFromChests.java` | Register SortPayload type + server handler calling InventorySorter |
| Modify | `src/main/java/com/jeefbeebos23/pull_from_chests/mixin/InventoryScreenMixin.java` | Add Sort and Save Layout buttons |

`fabric.mod.json` and `pull_from_chests.mixins.json` require no changes.

---

## Task 1: SortPayload + register in PullFromChests

**Files:**
- Create: `src/main/java/com/jeefbeebos23/pull_from_chests/SortPayload.java`
- Modify: `src/main/java/com/jeefbeebos23/pull_from_chests/PullFromChests.java`

- [ ] **Step 1: Create `SortPayload.java`**

```java
package com.jeefbeebos23.pull_from_chests;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record SortPayload(List<String> hotbarLayout) implements CustomPacketPayload {

    public static final Type<SortPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(PullFromChests.MOD_ID, "sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortPayload> CODEC =
        StreamCodec.of(
            (buf, payload) -> {
                for (String s : payload.hotbarLayout()) {
                    buf.writeBoolean(s != null);
                    if (s != null) buf.writeUtf(s);
                }
            },
            buf -> {
                List<String> layout = new ArrayList<>(9);
                for (int i = 0; i < 9; i++) {
                    layout.add(buf.readBoolean() ? buf.readUtf() : null);
                }
                return new SortPayload(layout);
            }
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
```

- [ ] **Step 2: Register SortPayload in `PullFromChests.java` with a no-op handler**

Replace the current `onInitialize()` with:

```java
package com.jeefbeebos23.pull_from_chests;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public class PullFromChests implements ModInitializer {

    public static final String MOD_ID = "pull_from_chests";
    public static final int RADIUS = 10;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(RestockPayload.TYPE, RestockPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RestockPayload.TYPE, (payload, context) ->
            context.server().execute(() -> restock(context.player()))
        );

        PayloadTypeRegistry.serverboundPlay().register(SortPayload.TYPE, SortPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {/* wired in Task 3 */})
        );
    }

    private static void restock(ServerPlayer player) {
        Inventory inv = player.getInventory();
        BlockPos origin = player.blockPosition();
        BlockPos.betweenClosed(
            origin.offset(-RADIUS, -RADIUS, -RADIUS),
            origin.offset(RADIUS, RADIUS, RADIUS)
        ).forEach(pos -> {
            if (player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
                restockFromChest(inv, chest);
            }
        });
    }

    private static void restockFromChest(Inventory playerInv, Container chest) {
        for (int pi = 0; pi < 36; pi++) {
            ItemStack playerStack = playerInv.getItem(pi);
            if (playerStack.isEmpty()) continue;
            if (playerStack.getCount() >= playerStack.getMaxStackSize()) continue;

            for (int ci = 0; ci < chest.getContainerSize(); ci++) {
                ItemStack chestStack = chest.getItem(ci);
                if (chestStack.isEmpty()) continue;
                if (!ItemStack.isSameItem(chestStack, playerStack)) continue;

                int space = playerStack.getMaxStackSize() - playerStack.getCount();
                int transfer = Math.min(space, chestStack.getCount());
                playerStack.grow(transfer);
                chestStack.shrink(transfer);
                chest.setChanged();
                if (playerStack.getCount() >= playerStack.getMaxStackSize()) break;
            }
        }
    }
}
```

- [ ] **Step 3: Build to verify `SortPayload` compiles**

```powershell
cd "c:\Users\wbgui\coding_projects\mc_mods\pull-from-chests"
.\gradlew.bat build 2>&1 | Select-Object -Last 15
```

Expected: `BUILD SUCCESSFUL`

If it fails with codec errors, check `RegistryFriendlyByteBuf` import path — in 26.1.2 it is `net.minecraft.network.RegistryFriendlyByteBuf`.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/jeefbeebos23/pull_from_chests/SortPayload.java
git add src/main/java/com/jeefbeebos23/pull_from_chests/PullFromChests.java
git commit -m "feat: add SortPayload C2S packet and register with no-op handler"
```

---

## Task 2: HotbarLayout (client config)

**Files:**
- Create: `src/main/java/com/jeefbeebos23/pull_from_chests/HotbarLayout.java`

- [ ] **Step 1: Create `HotbarLayout.java`**

No MC client-only imports — safe to load on either side. Reads/writes a 9-element JSON array.

```java
package com.jeefbeebos23.pull_from_chests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HotbarLayout {

    private static final Path CONFIG_PATH =
        FabricLoader.getInstance().getConfigDir()
            .resolve("pull_from_chests_hotbar_layout.json");
    private static final Gson GSON = new Gson();

    public static void save(List<String> layout) {
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(layout));
        } catch (IOException ignored) {}
    }

    public static List<String> load() {
        if (!Files.exists(CONFIG_PATH)) return emptyLayout();
        try {
            String json = Files.readString(CONFIG_PATH);
            java.lang.reflect.Type type = new TypeToken<List<String>>() {}.getType();
            List<String> layout = GSON.fromJson(json, type);
            if (layout == null || layout.size() != 9) return emptyLayout();
            return layout;
        } catch (Exception ignored) {
            return emptyLayout();
        }
    }

    private static List<String> emptyLayout() {
        return new ArrayList<>(Arrays.asList(new String[9]));
    }
}
```

- [ ] **Step 2: Commit**

```powershell
git add src/main/java/com/jeefbeebos23/pull_from_chests/HotbarLayout.java
git commit -m "feat: add HotbarLayout client config read/write"
```

---

## Task 3: InventorySorter + wire PullFromChests handler

**Files:**
- Create: `src/main/java/com/jeefbeebos23/pull_from_chests/InventorySorter.java`
- Modify: `src/main/java/com/jeefbeebos23/pull_from_chests/PullFromChests.java`

- [ ] **Step 1: Create `InventorySorter.java`**

Tier score uses `maxDamage` as proxy — correctly ranks Netherite(2031) > Diamond(1561) > Iron(250) > Stone(131) > Wood(59) > Gold(32) for tools, and Netherite > Diamond > Iron ≈ Chain > Gold > Leather for armor.

```java
package com.jeefbeebos23.pull_from_chests;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InventorySorter {

    public static void sort(ServerPlayer player, List<String> hotbarLayout) {
        Inventory inv = player.getInventory();

        // Step 1: collect pool from slots 0–35 and armor slots 36–39, clearing each slot
        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) pool.add(stack);
            inv.setItem(i, ItemStack.EMPTY);
        }

        // Step 2: apply hotbar layout — best matching item per saved slot
        for (int slot = 0; slot < 9; slot++) {
            String savedId = slot < hotbarLayout.size() ? hotbarLayout.get(slot) : null;
            if (savedId == null) continue;
            ItemStack best = findBestForLayout(pool, savedId);
            if (best != null) {
                inv.setItem(slot, best);
                pool.remove(best);
            }
        }

        // Step 3: equip best armor per slot
        for (EquipmentSlot armorSlot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack best = findBestArmor(pool, armorSlot);
            if (best != null) {
                inv.setItem(armorInventorySlot(armorSlot), best);
                pool.remove(best);
            }
        }

        // Step 4: sort remaining pool by category → tier desc → enchant count desc
        pool.sort(Comparator.comparingInt(InventorySorter::categoryOrder)
            .thenComparingInt(s -> -tierScore(s))
            .thenComparingInt(s -> -enchantCount(s)));

        // Step 5: fill main inventory slots 9–35 first, then remaining hotbar slots
        int idx = 0;
        for (int slot = 9; slot < 36 && idx < pool.size(); slot++) {
            if (inv.getItem(slot).isEmpty()) inv.setItem(slot, pool.get(idx++));
        }
        for (int slot = 0; slot < 9 && idx < pool.size(); slot++) {
            if (inv.getItem(slot).isEmpty()) inv.setItem(slot, pool.get(idx++));
        }
    }

    private static ItemStack findBestForLayout(List<ItemStack> pool, String savedId) {
        Identifier id = Identifier.parse(savedId);
        Item savedItem = BuiltInRegistries.ITEM
            .getHolder(ResourceKey.create(Registries.ITEM, id))
            .map(h -> h.value())
            .orElse(null);
        if (savedItem == null || savedItem == Items.AIR) return null;

        TagKey<Item> toolTag = toolTagFor(savedItem);
        if (toolTag != null) {
            return pool.stream()
                .filter(s -> s.is(toolTag))
                .max(Comparator.comparingInt(InventorySorter::tierScore)
                    .thenComparingInt(InventorySorter::enchantCount))
                .orElse(null);
        }

        ItemStack dummy = new ItemStack(savedItem);
        return pool.stream()
            .filter(s -> ItemStack.isSameItem(s, dummy))
            .findFirst()
            .orElse(null);
    }

    private static TagKey<Item> toolTagFor(Item item) {
        ItemStack dummy = new ItemStack(item);
        if (dummy.is(ItemTags.PICKAXES)) return ItemTags.PICKAXES;
        if (dummy.is(ItemTags.AXES))     return ItemTags.AXES;
        if (dummy.is(ItemTags.SHOVELS))  return ItemTags.SHOVELS;
        if (dummy.is(ItemTags.HOES))     return ItemTags.HOES;
        if (dummy.is(ItemTags.SWORDS))   return ItemTags.SWORDS;
        return null;
    }

    private static ItemStack findBestArmor(List<ItemStack> pool, EquipmentSlot slot) {
        return pool.stream()
            .filter(s -> s.getItem() instanceof ArmorItem ai && ai.getEquipmentSlot() == slot)
            .max(Comparator.comparingInt(InventorySorter::tierScore)
                .thenComparingInt(InventorySorter::enchantCount))
            .orElse(null);
    }

    private static int armorInventorySlot(EquipmentSlot slot) {
        return switch (slot) {
            case FEET  -> 36;
            case LEGS  -> 37;
            case CHEST -> 38;
            case HEAD  -> 39;
            default    -> -1;
        };
    }

    private static int tierScore(ItemStack stack) {
        return stack.getMaxDamage();
    }

    private static int enchantCount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
    }

    private static int categoryOrder(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(ItemTags.SWORDS) || item == Items.MACE || item == Items.BOW
                || item == Items.CROSSBOW || item == Items.TRIDENT) return 0;
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || item == Items.SHEARS || item == Items.FLINT_AND_STEEL
                || item == Items.FISHING_ROD) return 1;
        if (item instanceof ArmorItem) return 2;
        if (stack.get(DataComponents.FOOD) != null) return 3;
        if (item instanceof BlockItem) return 4;
        return 5;
    }
}
```

**If `ItemEnchantments.size()` causes a build error:** verify via:
```powershell
& "C:\Program Files\Java\jdk-25\bin\javap" -p "C:\Users\wbgui\.gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged-deobf\26.1.2\minecraft-merged-deobf-26.1.2.jar" net.minecraft.world.item.enchantment.ItemEnchantments 2>&1 | Select-String "size|entrySet|keySet"
```
Replace `.size()` with `.entrySet().size()` if needed.

- [ ] **Step 2: Wire handler in `PullFromChests.java`**

Replace the no-op sort handler added in Task 1:

```java
ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE, (payload, context) ->
    context.server().execute(() ->
        InventorySorter.sort(context.player(), payload.hotbarLayout()))
);
```

Full updated `onInitialize()`:

```java
@Override
public void onInitialize() {
    PayloadTypeRegistry.serverboundPlay().register(RestockPayload.TYPE, RestockPayload.CODEC);
    ServerPlayNetworking.registerGlobalReceiver(RestockPayload.TYPE, (payload, context) ->
        context.server().execute(() -> restock(context.player()))
    );

    PayloadTypeRegistry.serverboundPlay().register(SortPayload.TYPE, SortPayload.CODEC);
    ServerPlayNetworking.registerGlobalReceiver(SortPayload.TYPE, (payload, context) ->
        context.server().execute(() ->
            InventorySorter.sort(context.player(), payload.hotbarLayout()))
    );
}
```

- [ ] **Step 3: Build to verify server logic compiles**

```powershell
cd "c:\Users\wbgui\coding_projects\mc_mods\pull-from-chests"
.\gradlew.bat build 2>&1 | Select-Object -Last 20
```

Expected: `BUILD SUCCESSFUL`

Common failures and fixes:
- `cannot find symbol: method size()` on `ItemEnchantments` → replace `.size()` with `.entrySet().size()`
- `cannot find symbol: variable MACE` → `Items.MACE` may not exist in this snapshot; remove that condition
- `ArmorItem.getEquipmentSlot()` not found → use `((Equipable) item).getEquipmentSlot()` with import `net.minecraft.world.item.equipment.Equipable`

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/com/jeefbeebos23/pull_from_chests/InventorySorter.java
git add src/main/java/com/jeefbeebos23/pull_from_chests/PullFromChests.java
git commit -m "feat: add InventorySorter with hotbar layout, armor equip, and category sort"
```

---

## Task 4: InventoryScreenMixin — Sort and Save Layout buttons

**Files:**
- Modify: `src/main/java/com/jeefbeebos23/pull_from_chests/mixin/InventoryScreenMixin.java`

- [ ] **Step 1: Replace `InventoryScreenMixin.java` with three-button version**

Consolidates the existing Restock button and adds Sort + Save Layout buttons in one `@Inject`:

```java
package com.jeefbeebos23.pull_from_chests.mixin;

import com.jeefbeebos23.pull_from_chests.HotbarLayout;
import com.jeefbeebos23.pull_from_chests.RestockPayload;
import com.jeefbeebos23.pull_from_chests.SortPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends AbstractContainerScreen<InventoryMenu> {

    protected InventoryScreenMixin(InventoryMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addButtons(CallbackInfo ci) {
        int bx = this.leftPos + this.imageWidth + 4;

        addRenderableWidget(Button.builder(
            Component.literal("Restock"),
            btn -> ClientPlayNetworking.send(new RestockPayload()))
            .pos(bx, this.topPos + 60)
            .size(70, 20)
            .build()
        );

        addRenderableWidget(Button.builder(
            Component.literal("Sort"),
            btn -> ClientPlayNetworking.send(new SortPayload(HotbarLayout.load())))
            .pos(bx, this.topPos + 84)
            .size(70, 20)
            .build()
        );

        addRenderableWidget(Button.builder(
            Component.literal("Save Layout"),
            btn -> {
                var mc = Minecraft.getInstance();
                if (mc.player == null) return;
                List<String> layout = new ArrayList<>(9);
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    var regId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                    layout.add(stack.isEmpty() || regId == null ? null : regId.toString());
                }
                HotbarLayout.save(layout);
            })
            .pos(bx, this.topPos + 108)
            .size(70, 20)
            .build()
        );
    }
}
```

- [ ] **Step 2: Build final JAR**

```powershell
cd "c:\Users\wbgui\coding_projects\mc_mods\pull-from-chests"
.\gradlew.bat build 2>&1 | Select-Object -Last 15
```

Expected: `BUILD SUCCESSFUL`. JAR at `build\libs\pull-from-chests-1.0.0.jar`.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/com/jeefbeebos23/pull_from_chests/mixin/InventoryScreenMixin.java
git commit -m "feat: add Sort and Save Layout buttons to inventory screen"
```

---

## Task 5: Deploy + push

- [ ] **Step 1: Copy JAR to mods folder**

```powershell
Copy-Item "build\libs\pull-from-chests-1.0.0.jar" "$env:APPDATA\.minecraft\mods\pull-from-chests-1.0.0.jar" -Force
```

- [ ] **Step 2: Push main branch to GitHub**

```powershell
git push origin main
```

- [ ] **Step 3: Update the download branch with new JAR**

```powershell
$jar = Resolve-Path "build\libs\pull-from-chests-1.0.0.jar"
Copy-Item $jar "$env:TEMP\pull-from-chests-1.0.0.jar" -Force
git checkout -f download
Remove-Item "pull-from-chests-1.0.0.jar" -ErrorAction SilentlyContinue
Copy-Item "$env:TEMP\pull-from-chests-1.0.0.jar" "pull-from-chests-1.0.0.jar"
git add pull-from-chests-1.0.0.jar
git commit -m "release: pull-from-chests v1.0.0 with inventory sort"
git push origin download
git checkout main
```
