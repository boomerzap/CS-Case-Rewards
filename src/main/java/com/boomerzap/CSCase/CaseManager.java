package com.boomerzap.CSCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * Holds the currently known cases. On the server this is filled by {@link CaseLoader};
 * on a client it is filled by the sync packet. Both write the same static map, which is
 * fine for a single-player integrated server because the data is identical.
 */
public final class CaseManager {
    private static volatile Map<ResourceLocation, CaseDefinition> cases = Map.of();

    private CaseManager() {}

    public static void replaceAll(Map<ResourceLocation, CaseDefinition> newCases) {
        cases = Collections.unmodifiableMap(new HashMap<>(newCases));
    }

    public static void putAll(Map<ResourceLocation, CaseDefinition> more) {
        Map<ResourceLocation, CaseDefinition> copy = new HashMap<>(cases);
        copy.putAll(more);
        cases = Collections.unmodifiableMap(copy);
    }

    @Nullable
    public static CaseDefinition get(@Nullable ResourceLocation id) {
        return id == null ? null : cases.get(id);
    }

    public static Set<ResourceLocation> ids() {
        return cases.keySet();
    }

    public static Map<ResourceLocation, CaseDefinition> all() {
        return cases;
    }
}
