package xyz.omegaware.addon.utils;

import net.minecraft.client.network.ServerInfo;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ServerCheck {

    public static boolean isNot6B6T() {
        if (System.getenv("env").equals("dev")) return false; // Bypass check in dev environment
        if (mc.isIntegratedServerRunning()) return true;
        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null) return false;
        return !server.name.endsWith("6b6t.org");
    }

//    Idk how to turn off the module from here
//    public static void checkIf6B6T() {
//        if (isNot6B6T()) {
//            Logger.error("%s is only intended for use on 6b6t.org.");
//            // toggle off the module
//        }
//    }
}
