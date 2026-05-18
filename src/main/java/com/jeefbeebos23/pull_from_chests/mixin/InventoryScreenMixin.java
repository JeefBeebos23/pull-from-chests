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
            .pos(bx, this.topPos + 84)
            .size(70, 20)
            .build()
        );

        addRenderableWidget(Button.builder(
            Component.literal("Sort"),
            btn -> ClientPlayNetworking.send(new SortPayload(HotbarLayout.load())))
            .pos(bx, this.topPos + 108)
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
            .pos(bx, this.topPos + 132)
            .size(70, 20)
            .build()
        );
    }
}
