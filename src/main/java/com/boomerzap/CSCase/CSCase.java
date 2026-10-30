package com.boomerzap.CSCase;

import org.slf4j.Logger;

import com.boomerzap.CSCase.command.CaseCommands;
import com.boomerzap.CSCase.item.CaseItem;
import com.boomerzap.CSCase.network.CaseNetwork;
import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(CSCase.MODID)
public class CSCase {
    public static final String MODID = "cscase";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Which case a case item opens: a resource location such as {@code cscase:example}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> CASE_ID =
            COMPONENTS.register("case_id", () -> DataComponentType.<ResourceLocation>builder()
                    .persistent(ResourceLocation.CODEC)
                    .networkSynchronized(ResourceLocation.STREAM_CODEC)
                    .build());

    public static final DeferredItem<CaseItem> CASE =
            ITEMS.register("case", () -> new CaseItem(new Item.Properties().stacksTo(16)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("cases",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.cscase"))
                    .icon(() -> CASE.get().getDefaultInstance())
                    .displayItems((params, output) ->
                            CaseManager.ids().stream().sorted().forEach(id -> output.accept(CaseItem.create(id))))
                    .build());

    public CSCase(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        TABS.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);

        modBus.addListener(CaseNetwork::register);

        NeoForge.EVENT_BUS.addListener(CaseEvents::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(CaseEvents::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(CaseEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(CaseEvents::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(CaseEvents::onServerStopping);
        NeoForge.EVENT_BUS.addListener(CaseCommands::register);
    }
}
