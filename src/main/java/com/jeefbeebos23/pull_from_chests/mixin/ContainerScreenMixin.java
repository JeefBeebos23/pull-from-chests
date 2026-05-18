package com.jeefbeebos23.pull_from_chests.mixin;

import com.jeefbeebos23.pull_from_chests.SortChestPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {

    protected ContainerScreenMixin(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addChestSortButton(CallbackInfo ci) {
        if (!(this.menu instanceof ChestMenu)) return;
        addRenderableWidget(Button.builder(
            Component.literal("Sort"),
            btn -> ClientPlayNetworking.send(new SortChestPayload()))
            .pos(this.leftPos + this.imageWidth + 4, this.topPos + 4)
            .size(70, 20)
            .build()
        );
    }
}
