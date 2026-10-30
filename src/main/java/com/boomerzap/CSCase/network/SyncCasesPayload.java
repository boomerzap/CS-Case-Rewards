package com.boomerzap.CSCase.network;

import java.util.Map;

import com.boomerzap.CSCase.CSCase;
import com.boomerzap.CSCase.CaseDefinition;
import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a chunk of case definitions. {@code replace} is true for the first chunk of a sync. */
public record SyncCasesPayload(boolean replace, Map<ResourceLocation, CaseDefinition> cases) implements CustomPacketPayload {
    public static final Type<SyncCasesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CSCase.MODID, "sync_cases"));

    private static final Codec<Map<ResourceLocation, CaseDefinition>> MAP_CODEC =
            Codec.unboundedMap(ResourceLocation.CODEC, CaseDefinition.CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCasesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SyncCasesPayload::replace,
            ByteBufCodecs.fromCodecWithRegistries(MAP_CODEC), SyncCasesPayload::cases,
            SyncCasesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
