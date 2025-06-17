package xyz.omegaware.addon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.utils.Utils;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModMetadata;
import xyz.omegaware.addon.commands.LinkCommand;
import xyz.omegaware.addon.commands.ShulkerQueueCommand;
import xyz.omegaware.addon.hud.OnlineTSRMembersHUD;
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
    public static final String MOD_ID = "omegaware-addons";
    public static ModMetadata MOD_META;
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("OmegaWare");
    public static final HudGroup HUD_GROUP = new HudGroup("OmegaWare");

    public static final BetterBaritoneBuild BETTER_BARITONE_BUILD = new BetterBaritoneBuild();

    public static File GetConfigFile(String key, String filename) {
        return new File(new File(new File(new File(MeteorClient.FOLDER, "omegaware"), key), Utils.getFileWorldName()), filename);
    }

    @Override
    public void onInitialize() {
        LOG.info("Initializing OmegaWare Addons");

        MOD_META = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow().getMetadata();

        // Modules
        Modules.get().add(new TPAAutomationModule());
        Modules.get().add(new BeaconRangeModule());
        Modules.get().add(new ChatFilterModule());
        Modules.get().add(new ItemFrameDupeModule());
        Modules.get().add(new BetterStashFinderModule());

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Modules.get().add(new TSRKitBotModule()); // Is not ready yet
        }

        if (BaritoneUtils.IS_AVAILABLE) {
            Modules.get().add(BETTER_BARITONE_BUILD);
        }

        Commands.add(new LinkCommand());
        Commands.add(new ShulkerQueueCommand());

        Hud.get().register(OnlineTSRMembersHUD.INFO);
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
    public String getWebsite() {
        return "https://github.com/Omega172/OmegaWare-Addons";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("Omega172", "OmegaWare-Addons", "1.21.4", null);
    }

    @Override
    public String getCommit() {
        String commit = MOD_META.getCustomValue(MOD_ID + ":commit").getAsString();
        return commit.isEmpty() ? null : commit;
    }
}
