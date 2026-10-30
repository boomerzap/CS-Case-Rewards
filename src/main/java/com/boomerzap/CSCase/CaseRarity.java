package com.boomerzap.CSCase;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Rarity tiers, named and coloured after Counter-Strike item grades.
 * {@code defaultWeight} is the tier's relative chance, used when an entry has no explicit weight.
 */
public enum CaseRarity implements StringRepresentable {
    CONSUMER("consumer", 0xFFB0C3D9, 100.0),
    INDUSTRIAL("industrial", 0xFF5E98D9, 60.0),
    MIL_SPEC("mil_spec", 0xFF4B69FF, 79.92),
    RESTRICTED("restricted", 0xFF8847FF, 15.98),
    CLASSIFIED("classified", 0xFFD32CE6, 3.20),
    COVERT("covert", 0xFFEB4B4B, 0.64),
    EXCEEDINGLY_RARE("exceedingly_rare", 0xFFE4AE39, 0.26);

    public static final Codec<CaseRarity> CODEC = StringRepresentable.fromEnum(CaseRarity::values);

    private final String id;
    private final int argb;
    private final double defaultWeight;

    CaseRarity(String id, int argb, double defaultWeight) {
        this.id = id;
        this.argb = argb;
        this.defaultWeight = defaultWeight;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    /** Opaque ARGB colour. */
    public int argb() {
        return argb;
    }

    /** RGB without alpha, for use with {@code Style.withColor}. */
    public int rgb() {
        return argb & 0xFFFFFF;
    }

    public double defaultWeight() {
        return defaultWeight;
    }

    public Component displayName() {
        return Component.translatable("cscase.rarity." + id);
    }

    public static CaseRarity byOrdinal(int ordinal) {
        CaseRarity[] all = values();
        return all[Math.floorMod(ordinal, all.length)];
    }
}
