package com.jeefbeebos23.pull_from_chests;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

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
        Identifier id = Identifier.tryParse(savedId);
        if (id == null) return null;
        Item savedItem = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
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
            .filter(s -> {
                Equippable eq = s.get(DataComponents.EQUIPPABLE);
                return eq != null && eq.slot() == slot;
            })
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

    public static void sortChest(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof ChestMenu chestMenu)) return;

        Container container = chestMenu.getContainer();
        int size = container.getContainerSize();

        List<ItemStack> pool = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) pool.add(stack);
            container.setItem(i, ItemStack.EMPTY);
        }

        pool.sort(Comparator.comparingInt(InventorySorter::categoryOrder)
            .thenComparingInt(s -> -tierScore(s))
            .thenComparingInt(s -> -enchantCount(s)));

        for (int i = 0; i < pool.size(); i++) {
            container.setItem(i, pool.get(i));
        }

        container.setChanged();
        chestMenu.broadcastChanges();
    }

    private static int categoryOrder(ItemStack stack) {
        Item item = stack.getItem();
        if (stack.is(ItemTags.SWORDS) || item == Items.MACE || item == Items.BOW
                || item == Items.CROSSBOW || item == Items.TRIDENT) return 0;
        if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)
                || item == Items.SHEARS || item == Items.FLINT_AND_STEEL
                || item == Items.FISHING_ROD) return 1;
        if (stack.get(DataComponents.EQUIPPABLE) != null
                && isArmorSlot(stack.get(DataComponents.EQUIPPABLE).slot())) return 2;
        if (stack.get(DataComponents.FOOD) != null) return 3;
        if (item instanceof BlockItem) return 4;
        return 5;
    }

    private static boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }
}
