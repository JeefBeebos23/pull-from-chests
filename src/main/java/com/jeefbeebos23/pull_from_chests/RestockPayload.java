package com.jeefbeebos23.pull_from_chests;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record RestockPayload() implements CustomPacketPayload {

    public static final Type<RestockPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(PullFromChests.MOD_ID, "restock"));

    public static final StreamCodec<ByteBuf, RestockPayload> CODEC =
        StreamCodec.unit(new RestockPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
