package com.boomerzap.CSCase;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import com.mojang.serialization.JsonOps;

/** Loads {@code data/<ns>/cscase/*.json} on every datapack reload. */
public class CaseLoader extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "cscase";

    private final HolderLookup.Provider registries;

    public CaseLoader(HolderLookup.Provider registries) {
        super(new Gson(), DIRECTORY);
        this.registries = registries;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        Map<ResourceLocation, CaseDefinition> loaded = new HashMap<>();
        files.forEach((id, json) -> CaseDefinition.CODEC.parse(ops, json)
                .resultOrPartial(err -> CSCase.LOGGER.error("Failed to load case {}: {}", id, err))
                .ifPresent(def -> loaded.put(id, def)));
        CaseManager.replaceAll(loaded);
        CSCase.LOGGER.info("Loaded {} loot case(s)", loaded.size());
    }
}
