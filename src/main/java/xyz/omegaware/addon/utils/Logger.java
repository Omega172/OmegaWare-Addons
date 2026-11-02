package xyz.omegaware.addon.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public class Logger {

    public static final Component PREFIX = Component.empty()
        .append(Component.literal("[").withStyle(ChatFormatting.WHITE))
        .append(Component.literal("OmegaWare").withStyle(ChatFormatting.AQUA))
        .append(Component.literal("] ").withStyle(ChatFormatting.WHITE));

    private static final Component WARN = Component.empty()
        .append(Component.literal("[").withStyle(ChatFormatting.WHITE))
        .append(Component.literal("WARNING").withStyle(ChatFormatting.YELLOW))
        .append(Component.literal("] ").withStyle(ChatFormatting.WHITE));

    private static final Component ERROR = Component.empty()
        .append(Component.literal("[").withStyle(ChatFormatting.WHITE))
        .append(Component.literal("ERROR").withStyle(ChatFormatting.RED))
        .append(Component.literal("] ").withStyle(ChatFormatting.WHITE));

    /**
     * Sends a message to the chat with the given format string and arguments, prefixed with the OmegaWare prefix.
     * <pre>
     * Example:
     * Logger.info("Found %d %sdiamonds!", 10, Formatting.AQUA);
     * </pre>
     */
    public static void info(String message, Object... args) {
        ChatUtils.sendMsg(PREFIX.copy().append(Component.literal(String.format(message, args))));
    }

    /**
     * Sends a warning message to the chat with the given format string and arguments, prefixed with the OmegaWare prefix.
     * The message will be yellow in color.
     * <pre>
     * Example:
     * Logger.warn(%d %sdiamonds went missing", 5, Formatting.AQUA);
     * </pre>
     */
    public static void warn(String message, Object... args) {
        ChatUtils.sendMsg(PREFIX.copy().append(WARN).append(Component.literal(String.format(message, args))).withStyle(ChatFormatting.YELLOW));
    }

    /**
     * Sends an error message to the chat with the given format string and arguments, prefixed with the OmegaWare prefix.
     * The message will be red.
     * <pre>
     * Example:
     * Logger.error("those %d %sdiamonds turned out to be fake", 5, Formatting.AQUA);
     * </pre>
     */
    public static void error(String message, Object... args) {
        ChatUtils.sendMsg(PREFIX.copy().append(ERROR).append(Component.literal(String.format(message, args))).withStyle(ChatFormatting.RED));
    }
}
