package xyz.omegaware.addon.commands;


import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.modules.TSRKitBotModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LinkCommand extends Command {
    public LinkCommand() {
        super("auth", "authorize with the KitBot api");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("2FACode", StringArgumentType.word()).executes(context -> {
            TSRKitBotModule.getIsLinked(StringArgumentType.getString(context, "2FACode"));
            return SINGLE_SUCCESS;
        }));
    }

    public static void saveApiKey(String key) {
        try {
            Path path = mc.runDirectory.toPath().resolve("config").resolve("kitbot.key");
            Files.createDirectories(path.getParent());
            Files.write(path, key.getBytes());
        } catch (IOException e) {
            OmegawareAddons.LOG.info("Failed to save API key: {}", e.getMessage());
        }
    }

    public static String loadApiKey() {
        try {
            Path path = mc.runDirectory.toPath().resolve("config").resolve("kitbot.key");
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path)).trim();
            }
        } catch (IOException e) {
            OmegawareAddons.LOG.info("Failed to load API key: {}", e.getMessage());
        }
        return null;
    }
}
