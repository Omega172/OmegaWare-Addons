package xyz.omegaware.addon.modules;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.OmegawareAddons;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TPAAutomationModule extends Module {
    public TPAAutomationModule() {
        super(OmegawareAddons.CATEGORY, "TPA-automations", "A module that automatically accepts or denies teleport requests based on a list of approved players.");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<String> approvedUsers = sgGeneral.add(new StringSetting.Builder()
        .name("approved-users")
        .description("A comma-separated list of approved users.")
        .defaultValue("user1,user2,user3")
        .build()
    );

    private final Setting<Boolean> acceptTSRBots = sgGeneral.add(new BoolSetting.Builder()
        .name("accept-tsr-bots")
        .description("Automatically accept teleport requests that are from the TSR bot users.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoDeny = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-deny")
        .description("Automatically deny teleport requests that are not from approved users.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printTpaDetected = sgGeneral.add(new BoolSetting.Builder()
        .name("print-tpa-detected")
        .description("Print a message when a teleport request is detected.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printTpaAccepted = sgGeneral.add(new BoolSetting.Builder()
        .name("print-tpa-accepted")
        .description("Print a message when a teleport request is accepted.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> printTpaIgnored = sgGeneral.add(new BoolSetting.Builder()
        .name("print-tpa-ignored")
        .description("Print a message when a teleport request is ignored.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> filterTpaMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-tpa-messages")
        .description("Filter out the servers TPA messages from the chat.")
        .defaultValue(true)
        .build()
    );

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive() || mc.player == null) return;

        String message = event.getMessage().getString();

        if (filterTpaMessages.get()) {
            Matcher matcher = Pattern.compile("^Type /tpy ([A-Za-z0-9_]{3,16}) to accept or /tpn \\1 to deny\\.$").matcher(message);
            if (matcher.matches()) {
                event.cancel();
                return;
            }

            matcher = Pattern.compile("^Request from ([A-Za-z0-9_]{3,16}) accepted\\!$").matcher(message);
            if (matcher.matches()) {
                event.cancel();
                return;
            }

            matcher = Pattern.compile("^Request from ([A-Za-z0-9_]{3,16}) denied\\!$").matcher(message);
            if (matcher.matches() && autoDeny.get()) {
                event.cancel();
                return;
            }
        }

        Matcher matcher = Pattern.compile("^([A-Za-z0-9_]{3,16}) wants to teleport to you\\.$").matcher(message);
        if (!matcher.matches()) return;

        String username = matcher.group(1);

        if (printTpaDetected.get()) {
            Text tpaDetected = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("TPA Detected: ").formatted(Formatting.RED))
                .append(Text.literal(username).formatted(Formatting.WHITE))
                .append(Text.literal("!").formatted(Formatting.WHITE));

            ChatUtils.sendMsg(tpaDetected);
        }

        Text accepted = OmegawareAddons.PREFIX.copy()
            .append(Text.literal("Auto-Accepted: ").formatted(Formatting.GREEN))
            .append(Text.literal(username).formatted(Formatting.WHITE))
            .append(Text.literal("!").formatted(Formatting.WHITE));

        Text ignored = OmegawareAddons.PREFIX.copy()
            .append(Text.literal("Ignored: ").formatted(Formatting.RED))
            .append(Text.literal(username).formatted(Formatting.WHITE))
            .append(Text.literal("!").formatted(Formatting.WHITE));

        Set<String> approvedUsersList = Set.of(approvedUsers.get().split(","));

        String[] TSRKitBotUsers = {
            "royalburner",
            "Poolyin",
            "PoolyinHelper",
            "RoyalHelper",
            "TSRMANIA"
        };

        if (approvedUsersList.contains(username) || (acceptTSRBots.get() && Set.of(TSRKitBotUsers).contains(username))) {
            ChatUtils.sendPlayerMsg("/tpy " + username);

            if (printTpaAccepted.get()) {
                ChatUtils.sendMsg(accepted);
            }
        } else {
            if (autoDeny.get()) {
                ChatUtils.sendPlayerMsg("/tpn " + username);

                if (printTpaIgnored.get()) {
                    ChatUtils.sendMsg(ignored);
                }
            }
        }

        if (filterTpaMessages.get() && printTpaDetected.get()) {
            event.cancel();
        }
    }
}
