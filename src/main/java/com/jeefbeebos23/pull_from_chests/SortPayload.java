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
