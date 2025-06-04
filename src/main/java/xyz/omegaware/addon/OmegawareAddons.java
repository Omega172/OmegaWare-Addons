package xyz.omegaware.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.commands.LinkCommand;
import xyz.omegaware.addon.commands.ShulkerQueueCommand;
import xyz.omegaware.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

import java.io.File;

public class OmegawareAddons extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("OmegaWare");
    @SuppressWarnings("unused")
    public static final HudGroup HUD_GROUP = new HudGroup("OmegaWare");

    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "omegaware"), key), Utils.getFileWorldName()), filename);
    }

    public static String getCurrentServerAddress() {
        ServerInfo server = MinecraftClient.getInstance().getCurrentServerEntry();
        if (server == null) {
            return "singleplayer";
        }

        if (server.address == null || server.address.isEmpty()) {
            return "unknown";
        }

        return MinecraftClient.getInstance().getCurrentServerEntry().address;
    }

    public static final Text PREFIX = Text.empty()
        .append(Text.literal("[").formatted(Formatting.WHITE))
        .append(Text.literal("OmegaWare").formatted(Formatting.AQUA))
        .append(Text.literal("] ").formatted(Formatting.WHITE));

    @Override
    public void onInitialize() {
        LOG.info("Initializing OmegaWare Addons");

        // Modules
        Modules.get().add(new TPAAutomationModule());
        Modules.get().add(new BeaconRangeModule());
        Modules.get().add(new ChatFilterModule());
        //Modules.get().add(new TSRKitBotModule());
        Modules.get().add(new ItemFrameDupeModule());
        Modules.get().add(new BetterStashFinderModule());

        if (BaritoneUtils.IS_AVAILABLE) {
            //Modules.get().add(new TestModule());
        }

        Commands.add(new LinkCommand());
        Commands.add(new ShulkerQueueCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "xyz.omegaware.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Omega172", "OmegaWare-Addons");
    }
}
