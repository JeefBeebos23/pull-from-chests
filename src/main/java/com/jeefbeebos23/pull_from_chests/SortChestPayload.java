package com.jeefbeebos23.pull_from_chests;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SortChestPayload() implements CustomPacketPayload {

    public static final Type<SortChestPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(PullFromChests.MOD_ID, "sort_chest"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SortChestPayload> CODEC =
        StreamCodec.unit(new SortChestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
