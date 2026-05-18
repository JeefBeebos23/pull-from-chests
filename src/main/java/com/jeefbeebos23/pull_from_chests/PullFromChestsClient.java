package com.jeefbeebos23.pull_from_chests;

import com.jeefbeebos23.pull_from_chests.mixin.ScreenInvoker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;

public class PullFromChestsClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
            if (!(acs.getMenu() instanceof ChestMenu chestMenu)) return;

            int imageWidth = 176;
            int imageHeight = 114 + chestMenu.getRowCount() * 18;
            int leftPos = (scaledWidth - imageWidth) / 2;
            int topPos = (scaledHeight - imageHeight) / 2;

            ((ScreenInvoker) screen).invokeAddRenderableWidget(
                Button.builder(
                    Component.literal("Sort"),
                    btn -> ClientPlayNetworking.send(new SortChestPayload()))
                    .pos(leftPos + imageWidth + 4, topPos + 4)
                    .size(70, 20)
                    .build()
            );
        });
    }
}
