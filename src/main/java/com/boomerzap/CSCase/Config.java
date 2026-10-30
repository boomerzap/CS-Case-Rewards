package com.boomerzap.CSCase;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server config (serverconfig/cscase-server.toml). */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue OPEN_TICKS = BUILDER
            .comment("Length of the opening animation in ticks (20 ticks = 1 second).",
                    "The reward is handed over when the animation ends.")
            .defineInRange("openTicks", 140, 40, 600);

    public static final ModConfigSpec.BooleanValue ANNOUNCE = BUILDER
            .comment("Broadcast a chat message when a player pulls a rare item.")
            .define("announceDrops", true);

    public static final ModConfigSpec.EnumValue<CaseRarity> ANNOUNCE_MIN_RARITY = BUILDER
            .comment("Minimum rarity that gets announced when announceDrops is enabled.")
            .defineEnum("announceMinRarity", CaseRarity.COVERT);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private Config() {}
}
