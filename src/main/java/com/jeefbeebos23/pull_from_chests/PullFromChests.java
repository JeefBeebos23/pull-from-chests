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
