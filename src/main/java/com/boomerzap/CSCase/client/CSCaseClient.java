package com.boomerzap.CSCase.client;

import com.boomerzap.CSCase.CSCase;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only entry point; not loaded on dedicated servers. */
@Mod(value = CSCase.MODID, dist = Dist.CLIENT)
public class CSCaseClient {
    public CSCaseClient(ModContainer container) {
        // Gives the mod a config screen in the Mods list.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
