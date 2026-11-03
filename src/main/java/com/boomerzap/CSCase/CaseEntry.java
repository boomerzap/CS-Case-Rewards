package com.boomerzap.CSCase;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

/**
 * One possible reward in a case.
 *
 * @param stack  the item given to the player (supports count and components)
 * @param rarity display tier / colour
 * @param weight optional absolute weight; when empty the tier's share is used (see {@link CaseDefinition})
 */
public record CaseEntry(ItemStack stack, CaseRarity rarity, Optional<Double> weight) {
    /** Largest {@code count} accepted for one reward. Vanilla's own stack codec stops at 99. */
    public static final int MAX_COUNT = 10_000;

    private static final Codec<ItemStack> BASE_STACK_CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(ItemStack::getItemHolder),
            ExtraCodecs.intRange(1, MAX_COUNT).optionalFieldOf("count", 1).forGetter(ItemStack::getCount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(ItemStack::getComponentsPatch)
    ).apply(i, ItemStack::new));

    /**
     * Like the vanilla item stack codec, but {@code count} may exceed the 99 limit. The reward is split into
     * normal stacks when it is handed over.
     */
    public static final Codec<ItemStack> STACK_CODEC = BASE_STACK_CODEC.validate(stack ->
            stack.isEmpty() ? DataResult.error(() -> "Reward item must not be air") : DataResult.success(stack));

    public static final Codec<CaseEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            STACK_CODEC.fieldOf("item").forGetter(CaseEntry::stack),
            CaseRarity.CODEC.optionalFieldOf("rarity", CaseRarity.MIL_SPEC).forGetter(CaseEntry::rarity),
            Codec.doubleRange(Double.MIN_NORMAL, 1.0E9).optionalFieldOf("weight").forGetter(CaseEntry::weight)
    ).apply(i, CaseEntry::new));
}
