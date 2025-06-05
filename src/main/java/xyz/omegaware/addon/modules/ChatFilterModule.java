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
import java.util.List;
import java.util.Set;

public class ChatFilterModule extends Module {
    public ChatFilterModule() {
        super(OmegawareAddons.CATEGORY, "6B6T-chat-filter", "This module filters chat messages based on selected criteria.");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgIgnoredUsers = this.settings.createGroup("Ignored Users");
    private final SettingGroup sgMessageStartFlags = this.settings.createGroup("Message Start Flags");
    private final SettingGroup sgMessageContains = this.settings.createGroup("Message Contains");

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

    private final Setting<Boolean> toggleIgnoredUsers = sgIgnoredUsers.add(new BoolSetting.Builder()
        .name("toggle-ignored-users")
        .description("Toggle ignoring of users.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> ignoredUsers = sgIgnoredUsers.add(new StringListSetting.Builder()
        .name("filtered-users-list")
        .description("A list of users to filter.")
        .defaultValue(List.of("user1", "user2", "user3"))
        .visible(toggleIgnoredUsers::get)
        .build()
    );

    private final Setting<Boolean> toggleMessageStartFlags = sgMessageStartFlags.add(new BoolSetting.Builder()
        .name("toggle-message-start-flags")
        .description("Toggle filtering of messages that start with a selected flag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> messageStartFlags = sgMessageStartFlags.add(new StringListSetting.Builder()
        .name("message-start-flags")
        .description("A list of flags that will get a message filtered if they are at the start of a players message.")
        .defaultValue(List.of("!", "$", "%", "#", ".", "?"))
        .visible(toggleMessageStartFlags::get)
        .build()
    );

    private final Setting<Boolean> toggleMessageContainsFlags = sgMessageContains.add(new BoolSetting.Builder()
        .name("toggle-message-contains-flags")
        .description("Toggle filtering of messages that contain a selected flag.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<String>> messageContainsFlags = sgMessageContains.add(new StringListSetting.Builder()
        .name("message-contains-flags")
        .description("A list of flags that will get a message filtered if they are contained in a players message.")
        .defaultValue(List.of("discord", ".gg", "https://", "aternos"))
        .visible(toggleMessageContainsFlags::get)
        .build()
    );

    private static Integer filteredCount = 0;
    private boolean loaded = false;

    private void incrementFilteredCount() {
        filteredCount++;
        saveFilteredCount();
    }

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
            incrementFilteredCount();
            return;
        }

        if (!message.contains("»")) return;

        String username = message.split(" » ")[0];
        if (username.equals(mc.player.getNameForScoreboard())) return;

        boolean isRanked = false;
        if (message.startsWith("[")) {
            isRanked = true;
            message = message.substring(message.indexOf("]") + 1).trim();
        }

        if (rankedOnly.get() && !isRanked) {
            event.cancel();
            incrementFilteredCount();
            return;
        }

        if (toggleIgnoredUsers.get() && !ignoredUsers.get().isEmpty() && ignoredUsers.get().contains(username)) {
            event.cancel();
            incrementFilteredCount();
            return;
        }

        message = message.substring(message.indexOf("»") + 1).trim();

        if (toggleMessageStartFlags.get() && !messageStartFlags.get().isEmpty()) {
            for (String flag : messageStartFlags.get()) {
                if (message.startsWith(flag)) {
                    event.cancel();
                    incrementFilteredCount();
                    return;
                }
            }
        }

        if (toggleMessageContainsFlags.get() && !messageContainsFlags.get().isEmpty()){
            for (String flag : messageContainsFlags.get()) {
                if (message.contains(flag)) {
                    event.cancel();
                    incrementFilteredCount();
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
