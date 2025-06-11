package xyz.omegaware.addon.utils;

import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Logger {

    public static final Text PREFIX = Text.empty()
        .append(Text.literal("[").formatted(Formatting.WHITE))
        .append(Text.literal("OmegaWare").formatted(Formatting.AQUA))
        .append(Text.literal("] ").formatted(Formatting.WHITE));

    /**
     * Sends a message to the chat with the given format string and arguments, prefixed with the OmegaWare prefix.
     * <pre>
     * Example:
     * Logger.info("Found %d %sdiamonds!", 10, Formatting.AQUA);
     * </pre>
     */
    public static void info(String message, Object... args) {
        ChatUtils.sendMsg(PREFIX.copy().append(Text.literal(String.format(message, args))));
    }

    /**
     * Sends a warning message to the chat with the given format string and arguments, prefixed with the OmegaWare prefix.
     * The message will be yellow in color.
     * <pre>
     * Example:
     * Logger.warn( %d %sdiamonds went missing", 5, Formatting.AQUA);
     * </pre>
     */
    public static void warn(String message, Object... args) {
        ChatUtils.sendMsg(PREFIX.copy().append(Text.literal(String.format(message, args))).formatted(Formatting.YELLOW));
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
        ChatUtils.sendMsg(PREFIX.copy().append(Text.literal(String.format(message, args))).formatted(Formatting.RED));
    }
}
