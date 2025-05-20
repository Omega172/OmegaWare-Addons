package xyz.omegaware.addon.commands;


import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.modules.TSRKitBotModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static xyz.omegaware.addon.modules.TSRKitBotModule.jsonParseString;

public class LinkCommand extends Command {
    public LinkCommand() {
        super("auth", "authorize with the KitBot api");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("code").then(argument("2FACode", StringArgumentType.word()).executes(context -> {
            String argument = StringArgumentType.getString(context, "2FACode");
            TSRKitBotModule.getIsLinked(argument);
            return SINGLE_SUCCESS;
        })));
    }

    public static void saveApiKey(String key) {
        try {
            Path path = mc.runDirectory.toPath().resolve("config").resolve("kitbot.key");
            Files.createDirectories(path.getParent());
            Files.write(path, key.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String loadApiKey() {
        try {
            Path path = mc.runDirectory.toPath().resolve("config").resolve("kitbot.key");
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path)).trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
