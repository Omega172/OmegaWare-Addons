package xyz.omegaware.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import xyz.omegaware.addon.modules.TSRKitBotModule;

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
}
