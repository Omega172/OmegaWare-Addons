package xyz.omegaware.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.item.ItemStack;
import xyz.omegaware.addon.modules.ItemFrameDupeModule;
import xyz.omegaware.addon.utils.Logger;

public class ShulkerQueueCommand extends Command {
    public ShulkerQueueCommand() {
        super("shulkerqueue", "add or remove items from the shulker queue");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("add").executes(context -> {
            if (mc.player == null) {
                Logger.error("Player was somehow null");
                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) {
                Logger.error("You must hold an item in your main hand");
                return SINGLE_SUCCESS;
            }
            ItemFrameDupeModule.shulkerQueue.add(stack.copy());

            Logger.info("%sAdded %s to the shulker queue", ChatFormatting.GREEN, stack.getDisplayName());

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("remove").executes(context -> {
            if (mc.player == null) {
                Logger.error("Player was somehow null");
                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) {
                Logger.error("You must hold an item in your main hand");
                return SINGLE_SUCCESS;
            }
            if (!ItemFrameDupeModule.shulkerQueue.contains(stack.copy())) {
                Logger.error("Item is not in the shulker queue");
                return SINGLE_SUCCESS;
            }

            ItemFrameDupeModule.shulkerQueue.remove(stack.copy());

            Logger.info("%sRemoved%s %s from the shulker queue", ChatFormatting.RED, ChatFormatting.WHITE ,stack.getDisplayName());
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(context -> {
            if (mc.player == null) {
                Logger.error("Player was somehow null");
                return SINGLE_SUCCESS;
            }

            if (ItemFrameDupeModule.shulkerQueue.isEmpty()) {
                Logger.info("Shulker queue is empty");
            } else {
                StringBuilder sb = new StringBuilder("Shulker queue: ");
                ItemFrameDupeModule.shulkerQueue.forEach(itemStack -> sb.append(itemStack.getDisplayName().getString()).append("\n"));
                Logger.info(sb.toString());
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            if (mc.player == null) {
                Logger.error("Player was somehow null");

                return SINGLE_SUCCESS;
            }

            ItemFrameDupeModule.shulkerQueue.clear();

            Logger.info("Cleared the shulker queue");

            return SINGLE_SUCCESS;
        }));
    }
}
