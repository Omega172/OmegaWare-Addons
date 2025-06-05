package xyz.omegaware.addon.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.OmegawareAddons;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Set;

public class ChatFilterModule extends Module {
    public ChatFilterModule() {
        super(OmegawareAddons.CATEGORY, "6B6T-chat-filter", "This module filters chat messages based on selected criteria.");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> rankedOnly = sgGeneral.add(new BoolSetting.Builder()
        .name("ranked-users-only")
        .description("Only show messages from players with a rank.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> filterServerMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("filter-server-messages")
        .description("Filter out the server's messages from the chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> ignoredUsers = sgGeneral.add(new StringSetting.Builder()
        .name("filtered-users")
        .description("A comma-separated list of users to filter.")
        .defaultValue("user1,user2,user3")
        .build()
    );

    private final Setting<String> messageStartFlags = sgGeneral.add(new StringSetting.Builder()
        .name("message-start-flags")
        .description("A comma-separated list of flags that will get a message filtered if they are at the start of a players message.")
        .defaultValue("!,$,%,#,.,?")
        .build()
    );

    private final Setting<String> messageContainsFlags = sgGeneral.add(new StringSetting.Builder()
        .name("message-contains-flags")
        .description("A comma-separated list of flags that will get a message filtered if they are contained in a players message.")
        .defaultValue("discord,.gg,https://,aternos")
        .build()
    );

    private static Integer filteredCount = 0;
    private boolean loaded = false;

    private void saveFilteredCount() {
        File configFile = OmegawareAddons.GetConfigFile("chat-filter", "filtered.count");

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();
            payload.addProperty("count", filteredCount);
            writer.append(payload.toString());
            writer.close();
        } catch (Exception ignored) {
            OmegawareAddons.LOG.info("Failed to save Filtered Message count to {}", configFile.toPath());
        }
    }

    public void loadFilteredCount() {
        if (loaded) return;

        File configFile = OmegawareAddons.GetConfigFile("chat-filter", "filtered.count");
        if (!configFile.exists()) {
            OmegawareAddons.LOG.warn("{} not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
            if (payload.has("count")) {
                filteredCount = payload.get("count").getAsInt();
                loaded = true;
            }
        } catch (IOException e) {
            OmegawareAddons.LOG.error("Failed to load Filtered Message count from {}: {}", configFile.toPath(), e.getMessage());
        }
    }

    @Override
    public void onActivate() {
        if (!OmegawareAddons.is6B6T()) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("The 6B6T Chat Filter module is only intended for use on 6b6t.").formatted(Formatting.RED)));
            this.toggle();
            return;
        }

        loadFilteredCount();
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive() || mc.player == null) return;
        String message = event.getMessage().getString();

        if ((message.startsWith("Welcome to 6b6t.org") || message.startsWith("You can vote! Type /vote") || message.startsWith("---------------------------")) && filterServerMessages.get()) {
            event.cancel();
            filteredCount++;
            saveFilteredCount();
            return;
        }

        if (!message.contains("»")) return;

        boolean isRanked = false;
        if (message.startsWith("[")) {
            isRanked = true;
            message = message.substring(message.indexOf("]") + 1).trim();
        }

        if (rankedOnly.get() && !isRanked) {
            event.cancel();
            filteredCount++;
            saveFilteredCount();
            return;
        }

        String username = message.split(" » ")[0];
        message = message.substring(message.indexOf("»") + 1).trim();

        if (username.equals(mc.player.getNameForScoreboard())) return;

        Set<String> ignoredUsersList = Set.of(ignoredUsers.get().split(","));
        if (!ignoredUsersList.isEmpty() && ignoredUsersList.contains(username)) {
            event.cancel();
            filteredCount++;
            saveFilteredCount();
            return;
        }

        Set<String> messageStartFlagsList = Set.of(messageStartFlags.get().split(","));
        if (!messageStartFlagsList.isEmpty()) {
            for (String flag : messageStartFlagsList) {
                if (message.startsWith(flag)) {
                    event.cancel();
                    filteredCount++;
                    saveFilteredCount();
                    return;
                }
            }
        }

        Set<String> messageContainsFlagsList = Set.of(messageContainsFlags.get().split(","));
        if (!messageContainsFlagsList.isEmpty()) {
            for (String flag : messageContainsFlagsList) {
                if (message.contains(flag)) {
                    event.cancel();
                    filteredCount++;
                    saveFilteredCount();
                    return;
                }
            }
        }

        // Little thing for me as the Developer :)
        if (username.equals("LostEmotions") || username.equals("LostFriendships")) {
            Text msg = Text.literal("[").formatted(Formatting.WHITE)
                .append(Text.literal("OmegaWare").formatted(Formatting.AQUA))
                .append(Text.literal("] "))
                .append(username).formatted(Formatting.AQUA)
                .append(Text.literal(" » ").formatted(Formatting.WHITE))
                .append(Text.literal(message).formatted(Formatting.AQUA));
            event.setMessage(msg);
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();

        WButton btn = theme.button("Print number of filtered messages");
        btn.action = () -> {
            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Total Filtered Messages: ").formatted(Formatting.GREEN))
                .append(Text.literal(filteredCount.toString()).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
        };
        hList.add(btn);

        return list;
    }
}
