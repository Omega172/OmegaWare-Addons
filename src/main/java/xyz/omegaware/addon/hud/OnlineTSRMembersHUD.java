package xyz.omegaware.addon.hud;

import com.google.common.util.concurrent.AtomicDouble;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import xyz.omegaware.addon.OmegawareAddons;

import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class OnlineTSRMembersHUD extends HudElement {
    public static final HudElementInfo<OnlineTSRMembersHUD> INFO = new HudElementInfo<>(OmegawareAddons.HUD_GROUP, "Online TSR Members", "Displays and overlay of all online TSR members", OnlineTSRMembersHUD::new);

    public OnlineTSRMembersHUD() {
        super(INFO);
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Whether to show yourself in the list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> showBots = sgGeneral.add(new BoolSetting.Builder()
        .name("show-bots")
        .description("Whether to show the kitbots in the list.")
        .defaultValue(true)
        .build()
    );

    private static class User {
        String name;
        String[] mcNames;
        String rank;

        public User(String name, String[] mcNames, String rank) {
            this.name = name;
            this.mcNames = mcNames;
            this.rank = rank;
        }
    }

    // Replace this with the api call when it is implemented
    private final List<User> tsrMembers = List.of(
        new User("Bermani", new String[]{"Bermani"}, "Admin"),
        new User("Slay", new String[]{"slay_dev"}, "Admin"),
        new User("crystal", new String[]{""}, "Admin"),
        new User("monstro", new String[]{""}, "Admin"),
        new User("Pietty", new String[]{"Pietty"}, "Admin"),
        new User("Omega", new String[]{"LostEmotions", "LostFriendships", "WomenAreScary", "ElectricCallboy"}, "OmegaWare"),
        new User("Hastur", new String[]{"TheKingHastur", "chmoka90 "}, "Member"),
        new User("J26V5", new String[]{"J26V5"}, "Member"),
        new User("pyro", new String[]{""}, "Member"),
        new User("_kingdom_warrior_", new String[]{"kingdom_warrior"}, "Member"),
        new User("cerejo2", new String[]{"cerejo2", "cerejo222", "cerejo_2"}, "Member"),
        new User("Emily", new String[]{""}, "Member"),
        new User("heedi", new String[]{""}, "Member"),
        new User("QUITTING ALL DAY", new String[]{"brunokumar12", "blinkyman1234", "brunokumar112"}, "Member"),
        new User("StackedWithDonuts", new String[]{""}, "Member"),
        new User("Z", new String[]{""}, "Member"),
        new User("KitBot", new String[]{"royalburner", "Poolyin", "PoolyinHelper", "RoyalHelper", "TSRMANIA"}, "Bot")
    );

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null) return;
        //renderer.quad(x, y, getWidth(), getHeight(), Color.LIGHT_GRAY);

        // get all online players from tablist
        List<String> onlinePlayers = new ArrayList<>(mc.player.networkHandler.getPlayerList().stream().map(playerInfo -> playerInfo.getProfile().getName()).toList());

        AtomicDouble screenY = new AtomicDouble(y+4);

        renderer.text(" Online TSR Members: ", x, screenY.get(), Color.RED, true);
        screenY.addAndGet(renderer.textHeight(true)+1);
        double storedY = screenY.get();
        screenY.addAndGet(5);

        AtomicDouble largestWidth = new AtomicDouble(renderer.textWidth("Online TSR Members: ", true));

        onlinePlayers.forEach(player -> {
            // Check if the player is a TSR member
            tsrMembers.stream()
                .filter(member -> member.mcNames.length > 0 && List.of(member.mcNames).contains(player))
                .findFirst()
                .ifPresent(member -> {
                    if (!showBots.get() && member.rank.equals("Bot")) {
                        return;
                    }

                    if (!showSelf.get() && member.mcNames[0].equals(mc.player.getName().getString())) {
                        return;
                    }

                    // Render the member's name and rank
                    String displayText = " [";
                    renderer.text(displayText, x, screenY.get(), Color.WHITE, true);

                    Color color = switch (member.rank) {
                        case "Admin" -> Color.RED;
                        case "OmegaWare" -> Color.CYAN;
                        case "Member" -> Color.BLUE;
                        case "Bot" -> Color.GREEN;
                        default -> Color.WHITE;
                    };

                    renderer.text(member.rank, x + renderer.textWidth(displayText, true), screenY.get(), color, true);
                    displayText += member.rank;
                    renderer.text(String.format("] %s - %s", member.name, player), x + renderer.textWidth(displayText, true), screenY.get(), Color.WHITE, true);
                    displayText += String.format("] %s - %s", member.name, player);

                    if (renderer.textWidth(displayText, true) > largestWidth.get()) {
                        largestWidth.set(renderer.textWidth(displayText, true));
                    }

                    screenY.addAndGet(renderer.textHeight(true) + 2);
                });
        });

        renderer.quad(x, y, largestWidth.get() + 4, screenY.get() - y, new Color(0, 0, 0, 150));
        renderer.line(x, storedY, x + largestWidth.get(), storedY, Color.LIGHT_GRAY); // Separator line

        renderer.line(x, y, x + largestWidth.get() + 4, y, Color.LIGHT_GRAY); // Top line
        renderer.line(x, screenY.get(), x + largestWidth.get() + 4, screenY.get(), Color.LIGHT_GRAY); // Bottom line
        renderer.line(x, y, x, screenY.get(), Color.LIGHT_GRAY); // Left line
        renderer.line(x + largestWidth.get() + 4, y, x + largestWidth.get() + 4, screenY.get(), Color.LIGHT_GRAY); // Right line

        setSize(largestWidth.get() + 4, screenY.get() - y + 4);
    }
}
