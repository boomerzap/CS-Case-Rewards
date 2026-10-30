package com.boomerzap.CSCase.item;

import java.util.List;

import javax.annotation.Nullable;

import com.boomerzap.CSCase.CSCase;
import com.boomerzap.CSCase.CaseDefinition;
import com.boomerzap.CSCase.CaseManager;
import com.boomerzap.CSCase.client.ClientHooks;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.ChatFormatting;

/** A sealed case. The case it opens is stored in the {@code cscase:case_id} component. */
public class CaseItem extends Item {
    public CaseItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(ResourceLocation caseId) {
        return create(caseId, 1);
    }

    public static ItemStack create(ResourceLocation caseId, int count) {
        ItemStack stack = new ItemStack(CSCase.CASE.get(), count);
        stack.set(CSCase.CASE_ID.get(), caseId);
        return stack;
    }

    @Nullable
    public static ResourceLocation getCaseId(ItemStack stack) {
        return stack.get(CSCase.CASE_ID.get());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            // Opening is requested from the preview screen; the server does the real work.
            ClientHooks.openPreview(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation id = getCaseId(stack);
        CaseDefinition def = CaseManager.get(id);
        if (id != null && def != null) {
            return def.displayName(id);
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = getCaseId(stack);
        if (id == null || CaseManager.get(id) == null) {
            tooltip.add(Component.translatable("cscase.tooltip.unknown_case", String.valueOf(id)).withStyle(ChatFormatting.RED));
            return;
        }
        tooltip.add(Component.translatable("cscase.tooltip.open").withStyle(ChatFormatting.GRAY));
        if (flag.isAdvanced()) {
            tooltip.add(Component.literal(id.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
