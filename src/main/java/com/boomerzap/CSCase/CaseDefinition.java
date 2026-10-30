package com.boomerzap.CSCase;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * A case loaded from {@code data/<namespace>/cscase/<path>.json}.
 *
 * <p>Weights: an entry with an explicit {@code weight} uses it as-is. An entry without one gets
 * {@code tierWeight / (number of weightless entries in that tier)}, where the tier weight is the
 * rarity default (CS-style odds) unless overridden by {@code rarity_weights}. Weights are relative,
 * they do not need to sum to 100.
 */
public final class CaseDefinition {
    public static final Codec<CaseDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ComponentSerialization.CODEC.optionalFieldOf("name").forGetter(CaseDefinition::name),
            Codec.unboundedMap(CaseRarity.CODEC, Codec.doubleRange(0.0, 1.0E9))
                    .optionalFieldOf("rarity_weights", Map.of()).forGetter(CaseDefinition::rarityWeights),
            CaseEntry.CODEC.listOf().fieldOf("entries").forGetter(CaseDefinition::entries)
    ).apply(i, CaseDefinition::new)).validate(def -> def.entries.isEmpty()
            ? com.mojang.serialization.DataResult.error(() -> "A case needs at least one entry")
            : com.mojang.serialization.DataResult.success(def));

    private final Optional<Component> name;
    private final Map<CaseRarity, Double> rarityWeights;
    private final List<CaseEntry> entries;
    private final double[] weights;
    private final double totalWeight;

    public CaseDefinition(Optional<Component> name, Map<CaseRarity, Double> rarityWeights, List<CaseEntry> entries) {
        this.name = name;
        this.rarityWeights = Map.copyOf(rarityWeights);
        this.entries = List.copyOf(entries);

        Map<CaseRarity, Integer> weightlessPerTier = new EnumMap<>(CaseRarity.class);
        for (CaseEntry e : this.entries) {
            if (e.weight().isEmpty()) weightlessPerTier.merge(e.rarity(), 1, Integer::sum);
        }
        this.weights = new double[this.entries.size()];
        double total = 0;
        for (int n = 0; n < weights.length; n++) {
            CaseEntry e = this.entries.get(n);
            double w;
            if (e.weight().isPresent()) {
                w = e.weight().get();
            } else {
                double tier = this.rarityWeights.getOrDefault(e.rarity(), e.rarity().defaultWeight());
                w = tier / weightlessPerTier.get(e.rarity());
            }
            weights[n] = w;
            total += w;
        }
        this.totalWeight = total;
    }

    public Optional<Component> name() {
        return name;
    }

    public Map<CaseRarity, Double> rarityWeights() {
        return rarityWeights;
    }

    public List<CaseEntry> entries() {
        return entries;
    }

    public double weightOf(int index) {
        return weights[index];
    }

    /** Probability in 0..1 that entry {@code index} is the result. */
    public double chanceOf(int index) {
        return totalWeight <= 0 ? 1.0 / entries.size() : weights[index] / totalWeight;
    }

    /** Picks an entry index by weight. */
    public int rollIndex(RandomSource random) {
        if (totalWeight <= 0) return random.nextInt(entries.size());
        double r = random.nextDouble() * totalWeight;
        for (int n = 0; n < weights.length; n++) {
            r -= weights[n];
            if (r < 0) return n;
        }
        return weights.length - 1;
    }

    public CaseEntry roll(RandomSource random) {
        return entries.get(rollIndex(random));
    }

    /** Display name: explicit {@code name} or a prettified resource path ("my_case" becomes "My Case"). */
    public Component displayName(ResourceLocation id) {
        return name.orElseGet(() -> Component.literal(prettify(id.getPath())));
    }

    public static String prettify(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sb = new StringBuilder();
        for (String part : last.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
