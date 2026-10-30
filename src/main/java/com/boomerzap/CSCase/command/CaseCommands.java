package com.boomerzap.CSCase.command;

import java.util.Collection;
import java.util.Locale;

import com.boomerzap.CSCase.CaseDefinition;
import com.boomerzap.CSCase.CaseEntry;
import com.boomerzap.CSCase.CaseManager;
import com.boomerzap.CSCase.CaseOpening;
import com.boomerzap.CSCase.item.CaseItem;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * <pre>
 * /cscase give    &lt;players&gt; &lt;case&gt; [count]  hand out sealed case items
 * /cscase open    &lt;players&gt; &lt;case&gt;          play the opening animation now, reward on completion
 * /cscase instant &lt;players&gt; &lt;case&gt;          roll and hand over the reward immediately, no GUI
 * /cscase list                              list loaded cases
 * /cscase info    &lt;case&gt;                    show contents and drop chances
 * </pre>
 * All are permission level 2, so quest systems that run commands as the server can use them directly.
 */
public final class CaseCommands {
    private static final SuggestionProvider<CommandSourceStack> CASE_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggestResource(CaseManager.ids(), builder);

    private static final int MAX_CASE_STACK = 16;

    private CaseCommands() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cscase")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("case", ResourceLocationArgument.id()).suggests(CASE_IDS)
                                        .executes(ctx -> give(ctx, 1))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                                .executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "count")))))))
                .then(Commands.literal("open")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("case", ResourceLocationArgument.id()).suggests(CASE_IDS)
                                        .executes(ctx -> open(ctx, false)))))
                .then(Commands.literal("instant")
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("case", ResourceLocationArgument.id()).suggests(CASE_IDS)
                                        .executes(ctx -> open(ctx, true)))))
                .then(Commands.literal("list").executes(CaseCommands::list))
                .then(Commands.literal("info")
                        .then(Commands.argument("case", ResourceLocationArgument.id()).suggests(CASE_IDS)
                                .executes(CaseCommands::info))));
    }

    private static ResourceLocation requireCase(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "case");
        if (CaseManager.get(id) == null) {
            ctx.getSource().sendFailure(Component.translatable("cscase.command.unknown_case", id.toString()));
            return null;
        }
        return id;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        ResourceLocation id = requireCase(ctx);
        if (id == null) return 0;
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");

        for (ServerPlayer player : players) {
            int left = count;
            while (left > 0) {
                ItemStack stack = CaseItem.create(id, Math.min(left, MAX_CASE_STACK));
                left -= stack.getCount();
                player.getInventory().add(stack);
                if (!stack.isEmpty()) player.drop(stack, false);
            }
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("cscase.command.give",
                count, CaseManager.get(id).displayName(id), players.size()), true);
        return players.size();
    }

    private static int open(CommandContext<CommandSourceStack> ctx, boolean instant) throws CommandSyntaxException {
        ResourceLocation id = requireCase(ctx);
        if (id == null) return 0;
        int ok = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(ctx, "targets")) {
            if (instant ? CaseOpening.grantInstantly(player, id) : CaseOpening.startAnimated(player, id)) {
                ok++;
            }
        }
        final int opened = ok;
        if (opened == 0) {
            ctx.getSource().sendFailure(Component.translatable("cscase.command.open_failed"));
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("cscase.command.open",
                    CaseManager.get(id).displayName(id), opened), true);
        }
        return opened;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        var ids = CaseManager.ids();
        if (ids.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("cscase.command.list.empty"), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("cscase.command.list.header", ids.size()), false);
        ids.stream().sorted().forEach(id -> {
            CaseDefinition def = CaseManager.get(id);
            ctx.getSource().sendSuccess(() -> Component.literal(" - " + id + " (" + def.entries().size() + " items)"), false);
        });
        return ids.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ResourceLocation id = requireCase(ctx);
        if (id == null) return 0;
        CaseDefinition def = CaseManager.get(id);
        ctx.getSource().sendSuccess(() -> Component.translatable("cscase.command.info.header",
                def.displayName(id), def.entries().size()), false);
        for (int n = 0; n < def.entries().size(); n++) {
            CaseEntry entry = def.entries().get(n);
            String chance = String.format(Locale.ROOT, "%7.3f%% ", def.chanceOf(n) * 100.0);
            ctx.getSource().sendSuccess(() -> Component.literal(chance)
                    .append(CaseOpening.describe(entry.stack(), entry.rarity())), false);
        }
        return def.entries().size();
    }
}
