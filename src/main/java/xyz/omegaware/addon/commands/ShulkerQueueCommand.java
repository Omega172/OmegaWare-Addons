package xyz.omegaware.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.modules.ItemFrameDupeModule;

public class ShulkerQueueCommand extends Command {
    public ShulkerQueueCommand() {
        super("shulkerqueue", "add or remove items from the shulker queue");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("add").executes(context -> {
            if (mc.player == null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Player was somehow null").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("You must hold an item in your main hand").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }
            ItemFrameDupeModule.shulkerQueue.add(stack.copy());

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Added ").formatted(Formatting.GREEN))
                .append(stack.toHoverableText())
                .append(Text.literal(" to the shulker queue").formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("remove").executes(context -> {
            if (mc.player == null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Player was somehow null").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("You must hold an item in your main hand").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }
            if (!ItemFrameDupeModule.shulkerQueue.contains(stack.copy())) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Item is not in the shulker queue").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }

            ItemFrameDupeModule.shulkerQueue.remove(stack.copy());

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Removed ").formatted(Formatting.RED))
                .append(stack.toHoverableText())
                .append(Text.literal(" from the shulker queue").formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(context -> {
            if (mc.player == null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Player was somehow null").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }

            if (ItemFrameDupeModule.shulkerQueue.isEmpty()) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Shulker queue is empty").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Shulker queue: ").formatted(Formatting.YELLOW));
                ChatUtils.sendMsg(msg);

                ItemFrameDupeModule.shulkerQueue.forEach(itemStack -> {
                    Text itemText = itemStack.toHoverableText();
                    ChatUtils.sendMsg(itemText);
                });
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            if (mc.player == null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Player was somehow null").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                return SINGLE_SUCCESS;
            }

            ItemFrameDupeModule.shulkerQueue.clear();

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Cleared the shulker queue").formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);

            return SINGLE_SUCCESS;
        }));
    }
}
