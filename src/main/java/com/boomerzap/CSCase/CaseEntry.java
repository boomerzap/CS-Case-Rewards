package com.boomerzap.CSCase;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.item.ItemStack;

/**
 * One possible reward in a case.
 *
 * @param stack  the item given to the player (supports count and components)
 * @param rarity display tier / colour
 * @param weight optional absolute weight; when empty the tier's share is used (see {@link CaseDefinition})
 */
public record CaseEntry(ItemStack stack, CaseRarity rarity, Optional<Double> weight) {
    public static final Codec<CaseEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(CaseEntry::stack),
            CaseRarity.CODEC.optionalFieldOf("rarity", CaseRarity.MIL_SPEC).forGetter(CaseEntry::rarity),
            Codec.doubleRange(Double.MIN_NORMAL, 1.0E9).optionalFieldOf("weight").forGetter(CaseEntry::weight)
    ).apply(i, CaseEntry::new));
}
