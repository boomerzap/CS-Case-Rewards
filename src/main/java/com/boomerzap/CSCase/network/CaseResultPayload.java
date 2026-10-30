package com.boomerzap.CSCase.network;

import com.boomerzap.CSCase.CSCase;
import com.boomerzap.CSCase.CaseRarity;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Server to client: the rolled result; the client plays the opening animation landing on {@code won}. */
public record CaseResultPayload(ResourceLocation caseId, ItemStack won, CaseRarity rarity, int ticks)
        implements CustomPacketPayload {
    public static final Type<CaseResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CSCase.MODID, "case_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CaseResultPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, CaseResultPayload::caseId,
            ItemStack.STREAM_CODEC, CaseResultPayload::won,
            ByteBufCodecs.idMapper(CaseRarity::byOrdinal, CaseRarity::ordinal), CaseResultPayload::rarity,
            ByteBufCodecs.VAR_INT, CaseResultPayload::ticks,
            CaseResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
