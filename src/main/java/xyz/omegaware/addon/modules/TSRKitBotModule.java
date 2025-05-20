package xyz.omegaware.addon.modules;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WTexture;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.omegaware.addon.OmegawareAddons;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URL;
import java.util.Map;

public class TSRKitBotModule extends Module {
    private static final Logger log = LoggerFactory.getLogger(TSRKitBotModule.class);

    public TSRKitBotModule() {
        super(OmegawareAddons.CATEGORY, "TSR-Clan-KitBot-API", "Make kit requests to the TSR Clan KitBot API.");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private static final String apiUrl = "https://test.tsr-clan.org";

    public static String apiKey = null;

    private final Setting<Integer> pvpKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-pvp")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> cpvpKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-cpvp")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> refillKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-refill")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> griefKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-grief")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> hunterKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-hunter")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> mapartKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-mapart")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> highwayKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-highway")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> redstoneKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-name")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> buildKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-build")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build2Kit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-build2")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build3Kit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-build3")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build4Kit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-build4")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build5Kit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-build5")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build6Kit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-build6")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> toolsKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-tools")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> totemKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-totem")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> censoredKit = sgGeneral.add(new IntSetting.Builder()
        .name("kit-censored")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    public static class Response {
        public int responseCode;
        public String responseBody;
    }

    public static double jsonParseDouble(String key, String json) {
        int start = json.indexOf("\"" + key + "\":");
        if (start == -1) return -1;
        start += key.length() + 3;
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Double.parseDouble(json.substring(start, end).trim());
    }

    public static Integer jsonParseInt(String key, String json) {
        int start = json.indexOf("\"" + key + "\":");
        if (start == -1) return -1;
        start += key.length() + 3;
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Integer.parseInt(json.substring(start, end).trim());
    }

    public static boolean jsonParseBoolean(String key, String json) {
        int start = json.indexOf("\"" + key + "\":");
        if (start == -1) return false;
        start += key.length() + 3;
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return Boolean.parseBoolean(json.substring(start, end).trim());
    }

    public static String jsonParseString(String key, String json) {
        int start = json.indexOf("\"" + key + "\":");
        if (start == -1) return null;
        start += key.length() + 3;
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        return json.substring(start, end).trim().replaceAll("^\"|\"$", "");
    }

    private Response sendGetRequest(String endpoint, Map<String, ?> params, String payload) {
        try {
            StringBuilder urlBuilder = new StringBuilder(apiUrl + endpoint);
            if (params != null && !params.isEmpty()) {
                urlBuilder.append("?");
                for (Map.Entry<String, ?> entry : params.entrySet()) {
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                urlBuilder.deleteCharAt(urlBuilder.length() - 1); // Remove the last '&'
            }

            URL url = URI.create(urlBuilder.toString()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);

            if (payload != null) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(payload.getBytes());
            }

            int responseCode = connection.getResponseCode();

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();


            Response resp = new Response();
            resp.responseCode = responseCode;
            resp.responseBody = response.toString();
            return resp;

        } catch (ProtocolException e) {
            Response response = new Response();
            response.responseCode = -1;
            response.responseBody = "PE Error: " + e.getCause();
            return response;
        } catch (IOException e) {
            Response response = new Response();
            response.responseCode = -1;
            response.responseBody = "IO Error: " + e.getCause();
            return response;
        }
    }

    public static Response sendPostRequest(String endpoint, Map<String, ?> params, String payload) {
        try {
            StringBuilder urlBuilder = new StringBuilder(apiUrl + endpoint);
            if (params != null && !params.isEmpty()) {
                urlBuilder.append("?");
                for (Map.Entry<String, ?> entry : params.entrySet()) {
                    urlBuilder.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
                }
                urlBuilder.deleteCharAt(urlBuilder.length() - 1); // Remove the last '&'
            }

            URL url = URI.create(urlBuilder.toString()).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("x-api-key", apiKey);

            if (payload != null) {
                connection.setDoOutput(true);
                connection.getOutputStream().write(payload.getBytes());
            }

            int responseCode = connection.getResponseCode();

            InputStream stream;
            if (responseCode >= 400) {
                stream = connection.getErrorStream();
            } else {
                stream = connection.getInputStream();
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(stream));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            Response resp = new Response();
            resp.responseCode = responseCode;
            resp.responseBody = response.toString();
            return resp;
        } catch (ProtocolException e) {
            Response response = new Response();
            response.responseCode = -1;
            response.responseBody = "PE Error: " + e.getCause();
            return response;
        } catch (IOException e) {
            Response response = new Response();
            response.responseCode = -1;
            response.responseBody = "IO Error: " + e.getCause();
            return response;
        }
    }

    public static boolean getIsLinked(String... code) {
        if (apiKey != null && !apiKey.isEmpty()) return true;

        String Payload = "{\"minecraft_username\":\"" + MinecraftClient.getInstance().player.getName().getString() + "\", \"discord_id\":\"" + DiscordIPC.getUser().id + "\", \"retrieve_code\":\"" + (code.length > 0 ? code[0] : "") + "\"}";
        Response response = sendPostRequest("/meteor/link", null, Payload);
        if (response.responseCode == 200) {
            String message = jsonParseString("message", response.responseBody);

            if (message != null && message.equals("Retrieval code sent to your Discord DMs."))
            {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Message: ").formatted(Formatting.GREEN))
                    .append(Text.literal(message).formatted(Formatting.WHITE))
                    .append(Text.literal("Grab the code and use the command .auth code <CODE>").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
            apiKey = jsonParseString("api_key", response.responseBody);

            // print api key to chat
            if (apiKey != null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Set API Key: ").formatted(Formatting.GREEN))
                    .append(Text.literal(apiKey).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("API Key not found.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }

            return apiKey != null && !apiKey.isEmpty();
        } else {
            String errorMessage = jsonParseString("error", response.responseBody);
            if (errorMessage == null) {
                errorMessage = response.responseBody;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(errorMessage).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            return false;
        }
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();


        WButton getBalanceButton = theme.button("Get Balance");

        Map<String, ?> params = Map.of(
            "minecraft_username", mc.player.getName().getString()
        );
        getBalanceButton.action = () -> {
            if (!getIsLinked()) return;
            Response response = sendGetRequest("/user", params, null);
            if (response.responseCode == 200) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Balance: ").formatted(Formatting.GREEN))
                    .append(Text.literal(String.valueOf(jsonParseDouble("credits", response.responseBody))).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                String errorMessage = jsonParseString("error", response.responseBody);
            if (errorMessage == null) {
                errorMessage = response.responseBody;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(errorMessage).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            }
        };
        hList.add(getBalanceButton);

        WButton getQueuePositionButton = theme.button("Get Queue Position");
        getQueuePositionButton.action = () -> {
            if (!getIsLinked()) return;
            Response response = sendGetRequest("/order/position", params, null);
            if (response.responseCode == 200) {
                int queuePosition = jsonParseInt("position", response.responseBody);
                if (queuePosition < 0) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Message: ").formatted(Formatting.GREEN))
                        .append(Text.literal("No pending orders").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Queue Position: ").formatted(Formatting.GREEN))
                    .append(Text.literal(String.valueOf(queuePosition)).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                String errorMessage = jsonParseString("error", response.responseBody);
            if (errorMessage == null) {
                errorMessage = response.responseBody;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(errorMessage).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            }
        };
        hList.add(getQueuePositionButton);

        list.add(theme.label("You can select a maximum of 27 kits if you have tokens or 1 if you don't."));

        WHorizontalList hList2 = list.add(theme.horizontalList()).expandX().widget();

        WButton orderButton = theme.button("Place Order");
        orderButton.action = () -> {
            if (!getIsLinked()) return;
            int kitTotal = pvpKit.get() + cpvpKit.get() + refillKit.get() + griefKit.get() + hunterKit.get() + mapartKit.get() + highwayKit.get() + redstoneKit.get() + buildKit.get() + build2Kit.get() + build3Kit.get() + build4Kit.get() + build5Kit.get() + build6Kit.get() + toolsKit.get() + totemKit.get() + censoredKit.get();
            if (kitTotal > 27) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("You can only order a maximum of 27 kits.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            String payload = "{";
            payload += "\"minecraft_username\":\"" + mc.player.getName().getString() + "\",";
            payload += "\"request_type\":\"" + (kitTotal > 1 ? "credits" : "normal") + "\",";
            payload += "\"kit_orders\": [";
            if (pvpKit.get() > 0) payload += "{\"kit\":\"pvp\",\"quantity\":" + pvpKit.get() + "},";
            if (cpvpKit.get() > 0) payload += "{\"kit\":\"cpvp\",\"quantity\":" + cpvpKit.get() + "},";
            if (refillKit.get() > 0) payload += "{\"kit\":\"refill\",\"quantity\":" + refillKit.get() + "},";
            if (griefKit.get() > 0) payload += "{\"kit\":\"grief\",\"quantity\":" + griefKit.get() + "},";
            if (hunterKit.get() > 0) payload += "{\"kit\":\"hunter\",\"quantity\":" + hunterKit.get() + "},";
            if (mapartKit.get() > 0) payload += "{\"kit\":\"mapart\",\"quantity\":" + mapartKit.get() + "},";
            if (highwayKit.get() > 0) payload += "{\"kit\":\"highway\",\"quantity\":" + highwayKit.get() + "},";
            if (redstoneKit.get() > 0) payload += "{\"kit\":\"redstone\",\"quantity\":" + redstoneKit.get() + "},";
            if (buildKit.get() > 0) payload += "{\"kit\":\"build\",\"quantity\":" + buildKit.get() + "},";
            if (build2Kit.get() > 0) payload += "{\"kit\":\"build2\",\"quantity\":" + build2Kit.get() + "},";
            if (build3Kit.get() > 0) payload += "{\"kit\":\"build3\",\"quantity\":" + build3Kit.get() + "},";
            if (build4Kit.get() > 0) payload += "{\"kit\":\"build4\",\"quantity\":" + build4Kit.get() + "},";
            if (build5Kit.get() > 0) payload += "{\"kit\":\"build5\",\"quantity\":" + build5Kit.get() + "},";
            if (build6Kit.get() > 0) payload += "{\"kit\":\"build6\",\"quantity\":" + build6Kit.get() + "},";
            if (toolsKit.get() > 0) payload += "{\"kit\":\"tools\",\"quantity\":" + toolsKit.get() + "},";
            if (totemKit.get() > 0) payload += "{\"kit\":\"totem\",\"quantity\":" + totemKit.get() + "},";
            if (censoredKit.get() > 0) payload += "{\"kit\":\"censored\",\"quantity\":" + censoredKit.get() + "}";

            if (payload.endsWith(",")) {
                payload = payload.substring(0, payload.length() - 1);
            }

            payload += "]";
            payload += "}" ;

            Response response = sendPostRequest("/order", null, payload);
            if (response.responseCode == 200) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Order Placed: ").formatted(Formatting.GREEN))
                    .append(Text.literal(String.valueOf(jsonParseInt("order_id", response.responseBody))).formatted(Formatting.WHITE))
                    .append(Text.literal(" | ").formatted(Formatting.WHITE))
                    .append(Text.literal("Priority: ").formatted(Formatting.GREEN))
                    .append(Text.literal(String.valueOf(jsonParseBoolean("priority", response.responseBody))).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                String errorMessage = jsonParseString("error", response.responseBody);
            if (errorMessage == null) {
                errorMessage = response.responseBody;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(errorMessage).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            }
        };
        hList2.add(orderButton);

        WHorizontalList hList3 = list.add(theme.horizontalList()).expandX().widget();

        WLabel label = theme.label("Order ID: ");
        WTextBox textBox = theme.textBox("");
        textBox.minWidth = 80;

        WButton cancelButton = theme.button("Cancel Order");
        cancelButton.action = () -> {
            if (!getIsLinked() || textBox.get() == null) return;

            String payload = "{\"order_id\":" + textBox.get() + "}";

            Response response = sendPostRequest("/order/cancel", null, payload);
            if (response.responseCode == 200) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Order Cancelled: ").formatted(Formatting.GREEN))
                    .append(Text.literal(String.valueOf(jsonParseInt("order_id", response.responseBody))).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                String errorMessage = jsonParseString("error", response.responseBody);
            if (errorMessage == null) {
                errorMessage = response.responseBody;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(errorMessage).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            }
        };
        hList3.add(cancelButton);
        hList3.add(label);
        hList3.add(textBox);

        return list;
    }
}
