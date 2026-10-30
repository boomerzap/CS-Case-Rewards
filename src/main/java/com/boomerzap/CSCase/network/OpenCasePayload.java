package com.boomerzap.CSCase.network;

import com.boomerzap.CSCase.CSCase;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;

/** Client to server: "open the case I'm holding in this hand". */
public record OpenCasePayload(int handOrdinal) implements CustomPacketPayload {
    public static final Type<OpenCasePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CSCase.MODID, "open_case"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCasePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OpenCasePayload::handOrdinal,
            OpenCasePayload::new);

    public OpenCasePayload(InteractionHand hand) {
        this(hand.ordinal());
    }

    public InteractionHand hand() {
        InteractionHand[] all = InteractionHand.values();
        return all[Math.floorMod(handOrdinal, all.length)];
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
