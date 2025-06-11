package xyz.omegaware.addon.modules;

import com.google.gson.*;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.utils.Logger;

import java.io.*;
import java.net.http.HttpResponse;
import java.nio.file.Files;

import static xyz.omegaware.addon.utils.ServerCheck.isNot6B6T;

public class TSRKitBotModule extends Module {
    public TSRKitBotModule() {
        super(OmegawareAddons.CATEGORY, "TSR-Clan-KitBot-API", "Make kit requests to the TSR Clan KitBot API.");
    }

    private final SettingGroup sgKits = this.settings.createGroup("Kits", false);

    private static final String apiUrl = "https://req.tsr-clan.org";

    private static String apiKey = null;

    private final Setting<Integer> pvpKit = sgKits.add(new IntSetting.Builder()
        .name("kit-pvp")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> cpvpKit = sgKits.add(new IntSetting.Builder()
        .name("kit-cpvp")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> refillKit = sgKits.add(new IntSetting.Builder()
        .name("kit-refill")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> griefKit = sgKits.add(new IntSetting.Builder()
        .name("kit-grief")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> hunterKit = sgKits.add(new IntSetting.Builder()
        .name("kit-hunter")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> mapartKit = sgKits.add(new IntSetting.Builder()
        .name("kit-mapart")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> highwayKit = sgKits.add(new IntSetting.Builder()
        .name("kit-highway")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> redstoneKit = sgKits.add(new IntSetting.Builder()
        .name("kit-redstone")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> buildKit = sgKits.add(new IntSetting.Builder()
        .name("kit-build")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build2Kit = sgKits.add(new IntSetting.Builder()
        .name("kit-build2")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build3Kit = sgKits.add(new IntSetting.Builder()
        .name("kit-build3")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build4Kit = sgKits.add(new IntSetting.Builder()
        .name("kit-build4")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build5Kit = sgKits.add(new IntSetting.Builder()
        .name("kit-build5")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> build6Kit = sgKits.add(new IntSetting.Builder()
        .name("kit-build6")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> toolsKit = sgKits.add(new IntSetting.Builder()
        .name("kit-tools")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> totemKit = sgKits.add(new IntSetting.Builder()
        .name("kit-totem")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    private final Setting<Integer> censoredKit = sgKits.add(new IntSetting.Builder()
        .name("kit-censored")
        .description("Number of this kit you want to order.")
        .defaultValue(0)
        .min(0)
        .sliderMax(27)
        .build()
    );

    public static boolean getIsLinked(String... code) {
        if (apiKey != null && !apiKey.isEmpty()) return true;

        if (DiscordIPC.getUser() == null) {
            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal("You must have the Discord Presence module enabled").formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            return false;
        }

        JsonObject payload = new JsonObject();
        assert MinecraftClient.getInstance().player != null;
        payload.addProperty("minecraft_username", MinecraftClient.getInstance().player.getName().getString());
        payload.addProperty("discord_id", DiscordIPC.getUser().id);
        payload.addProperty("retrieve_code", code.length > 0 ? code[0] : "");

        Http.Request request = Http.post(apiUrl + "/meteor/link")
            .header("Content-Type", "application/json")
            .bodyJson(payload.toString());

        HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);
        if (response.statusCode() == 200) {
            String message = response.body().get("message").getAsString();
            if (message != null && message.equals("Retrieval code sent to your Discord DMs.")) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Message: ").formatted(Formatting.GREEN))
                    .append(Text.literal(message).formatted(Formatting.WHITE))
                    .append(Text.literal("Grab the code and use the command .auth <CODE>").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return false;
            }

            // print api key to chat
            if (response.body().has("api_key")) {
                apiKey = response.body().get("api_key").getAsString();
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Set API Key: ").formatted(Formatting.GREEN))
                    .append(Text.literal(apiKey).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);

                saveApiKey(apiKey);
            } else {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("API Key not found.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }

            return apiKey != null && !apiKey.isEmpty();
        } else {
            if (response.body() == null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return false;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
            return false;
        }
    }

    private static void conditionallyPrintOrders(String... statusFlag) {
        if (!getIsLinked()) return;

        assert MinecraftClient.getInstance().player != null;
        Http.Request request = Http.get(apiUrl + "/order/history?minecraft_username=" + MinecraftClient.getInstance().player.getName().getString())
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey);

        HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);

        if (response.statusCode() == 200) {
            JsonArray orders = response.body().getAsJsonArray("orders");
            if (orders.isEmpty()) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("No order history.").formatted(Formatting.GREEN));
                ChatUtils.sendMsg(msg);
                return;
            }

            for (JsonElement order : orders) {
                JsonObject orderObj = order.getAsJsonObject();
                if (!orderObj.has("order_id") || !orderObj.has("status") || !orderObj.has("request_type") || !orderObj.has("quantity")) {
                    continue; // Skip if any required field is missing
                }

                String orderId = orderObj.get("order_id").isJsonNull() ? "null" : orderObj.get("order_id").getAsString();
                String status = orderObj.get("status").isJsonNull() ? "null" : orderObj.get("status").getAsString();
                String requestType = orderObj.get("request_type").isJsonNull() ? "null" : orderObj.get("request_type").getAsString();
                String quantity = orderObj.get("quantity").isJsonNull() ? "null" : orderObj.get("quantity").getAsString();

                boolean isValidStatus = false;
                for (String flag : statusFlag) {
                    if (status.equals(flag)) {
                        isValidStatus = true;
                        break;
                    }
                }
                if (!isValidStatus) continue;

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Order ID: ").formatted(Formatting.GREEN))
                    .append(Text.literal(orderId).formatted(Formatting.WHITE))
                    .append(Text.literal("\n | ").formatted(Formatting.WHITE))
                    .append(Text.literal("Status: ").formatted(Formatting.GREEN))
                    .append(Text.literal(status).formatted(Formatting.WHITE))
                    .append(Text.literal("\n | ").formatted(Formatting.WHITE))
                    .append(Text.literal("Request Type: ").formatted(Formatting.GREEN))
                    .append(Text.literal(requestType).formatted(Formatting.WHITE))
                    .append(Text.literal("\n | ").formatted(Formatting.WHITE))
                    .append(Text.literal("Quantity: ").formatted(Formatting.GREEN))
                    .append(Text.literal(quantity).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        } else {
            if (response.body() == null) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            Text msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
            ChatUtils.sendMsg(msg);
        }
    }

    private boolean loaded = false;

    @Override
    public void onActivate() {
        if (isNot6B6T()) {
            Logger.error("%s is only intended for use on 6b6t.", name.replace("-", " "));
            toggle();
            return;
        }

        if (loaded) return;
        apiKey = loadApiKey();
        loaded = true;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();


        WButton getBalanceButton = theme.button("Get Balance");
        getBalanceButton.action = () -> {
            if (!getIsLinked()) return;

            assert mc.player != null;
            Http.Request request = Http.get(apiUrl + "/user?minecraft_username=" + mc.player.getName().getString())
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey);

            HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);

            if (response.statusCode() == 200) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Balance: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("credits").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                if (response.body() == null) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        };
        hList.add(getBalanceButton);

        WButton getQueuePositionButton = theme.button("Get Queue Position");
        getQueuePositionButton.action = () -> {
            if (!getIsLinked()) return;

            assert mc.player != null;
            Http.Request request = Http.get(apiUrl + "/order/position?minecraft_username=" + mc.player.getName().getString())
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey);

            HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);

            if (response.statusCode() == 200) {
                if (response.body().has("message")) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Message: ").formatted(Formatting.GREEN))
                        .append(Text.literal(response.body().get("message").getAsString()).formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                } else {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Queue Position: ").formatted(Formatting.GREEN))
                        .append(Text.literal(response.body().get("position").getAsString()).formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                }
            } else {
                if (response.body() == null) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
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

            if (kitTotal == 0 || kitTotal < 0) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("You must select at least 1 kit.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            JsonObject payload = new JsonObject();
            assert mc.player != null;
            payload.addProperty("minecraft_username", mc.player.getName().getString());
            payload.addProperty("request_type", kitTotal > 1 ? "credits" : "normal");

            JsonArray kitOrders = new JsonArray();
            if (pvpKit.get() > 0) {
                JsonObject pvpOrder = new JsonObject();
                pvpOrder.addProperty("kit", "pvp");
                pvpOrder.addProperty("quantity", pvpKit.get());

                kitOrders.add(pvpOrder);
            }

            if (cpvpKit.get() > 0) {
                JsonObject cpvpOrder = new JsonObject();
                cpvpOrder.addProperty("kit", "cpvp");
                cpvpOrder.addProperty("quantity", cpvpKit.get());

                kitOrders.add(cpvpOrder);
            }

            if (refillKit.get() > 0) {
                JsonObject refillOrder = new JsonObject();
                refillOrder.addProperty("kit", "refill");
                refillOrder.addProperty("quantity", refillKit.get());

                kitOrders.add(refillOrder);
            }

            if (griefKit.get() > 0) {
                JsonObject griefOrder = new JsonObject();
                griefOrder.addProperty("kit", "grief");
                griefOrder.addProperty("quantity", griefKit.get());

                kitOrders.add(griefOrder);
            }

            if (hunterKit.get() > 0) {
                JsonObject hunterOrder = new JsonObject();
                hunterOrder.addProperty("kit", "hunter");
                hunterOrder.addProperty("quantity", hunterKit.get());

                kitOrders.add(hunterOrder);
            }

            if (mapartKit.get() > 0) {
                JsonObject mapartOrder = new JsonObject();
                mapartOrder.addProperty("kit", "mapart");
                mapartOrder.addProperty("quantity", mapartKit.get());

                kitOrders.add(mapartOrder);
            }

            if (highwayKit.get() > 0) {
                JsonObject highwayOrder = new JsonObject();
                highwayOrder.addProperty("kit", "highway");
                highwayOrder.addProperty("quantity", highwayKit.get());

                kitOrders.add(highwayOrder);
            }

            if (redstoneKit.get() > 0) {
                JsonObject redstoneOrder = new JsonObject();
                redstoneOrder.addProperty("kit", "redstone");
                redstoneOrder.addProperty("quantity", redstoneKit.get());

                kitOrders.add(redstoneOrder);
            }

            if (buildKit.get() > 0) {
                JsonObject buildOrder = new JsonObject();
                buildOrder.addProperty("kit", "build");
                buildOrder.addProperty("quantity", buildKit.get());

                kitOrders.add(buildOrder);
            }

            if (build2Kit.get() > 0) {
                JsonObject build2Order = new JsonObject();
                build2Order.addProperty("kit", "build2");
                build2Order.addProperty("quantity", build2Kit.get());

                kitOrders.add(build2Order);
            }

            if (build3Kit.get() > 0) {
                JsonObject build3Order = new JsonObject();
                build3Order.addProperty("kit", "build3");
                build3Order.addProperty("quantity", build3Kit.get());

                kitOrders.add(build3Order);
            }

            if (build4Kit.get() > 0) {
                JsonObject build4Order = new JsonObject();
                build4Order.addProperty("kit", "build4");
                build4Order.addProperty("quantity", build4Kit.get());

                kitOrders.add(build4Order);
            }

            if (build5Kit.get() > 0) {
                JsonObject build5Order = new JsonObject();
                build5Order.addProperty("kit", "build5");
                build5Order.addProperty("quantity", build5Kit.get());

                kitOrders.add(build5Order);
            }

            if (build6Kit.get() > 0) {
                JsonObject build6Order = new JsonObject();
                build6Order.addProperty("kit", "build6");
                build6Order.addProperty("quantity", build6Kit.get());

                kitOrders.add(build6Order);
            }

            if (toolsKit.get() > 0) {
                JsonObject toolsOrder = new JsonObject();
                toolsOrder.addProperty("kit", "tools");
                toolsOrder.addProperty("quantity", toolsKit.get());

                kitOrders.add(toolsOrder);
            }

            if (totemKit.get() > 0) {
                JsonObject totemOrder = new JsonObject();
                totemOrder.addProperty("kit", "totem");
                totemOrder.addProperty("quantity", totemKit.get());

                kitOrders.add(totemOrder);
            }

            if (censoredKit.get() > 0) {
                JsonObject censoredOrder = new JsonObject();
                censoredOrder.addProperty("kit", "censored");
                censoredOrder.addProperty("quantity", censoredKit.get());

                kitOrders.add(censoredOrder);
            }

            payload.add("kit_orders", kitOrders);

            Http.Request request = Http.post(apiUrl + "/order")
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .bodyJson(payload.toString());

            HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);
            if (response.statusCode() == 200) {
                if (response.body().has("message")) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Message: ").formatted(Formatting.GREEN))
                        .append(Text.literal(response.body().get("message").getAsString()).formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                } else if (response.body().has("error")) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Order Placed: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("order_id").getAsString()).formatted(Formatting.WHITE))
                    .append(Text.literal(" | ").formatted(Formatting.WHITE))
                    .append(Text.literal("Priority: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("priority").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                if (response.body() == null) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        };
        hList2.add(orderButton);

        WButton listActiveOrdersButton = theme.button("List Active Orders");
        listActiveOrdersButton.action = () -> conditionallyPrintOrders("pending");
        hList2.add(listActiveOrdersButton);

        WButton listCompletedOrdersButton = theme.button("List Completed Orders");
        listCompletedOrdersButton.action = () -> conditionallyPrintOrders("completed");
        hList2.add(listCompletedOrdersButton);

        WButton listFailedOrdersButton = theme.button("List Failed Orders");
        listFailedOrdersButton.action = () -> conditionallyPrintOrders("failed");
        hList2.add(listFailedOrdersButton);

        WHorizontalList hList3 = list.add(theme.horizontalList()).expandX().widget();

        WButton cancelAllButton = theme.button("Cancel All Orders");
        cancelAllButton.action = () -> {
            if (!getIsLinked()) return;

            JsonObject payload = new JsonObject();
            assert mc.player != null;
            payload.addProperty("minecraft_username", mc.player.getName().getString());

            Http.Request request = Http.post(apiUrl + "/order/cancel")
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .bodyJson(payload.toString());

            HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);

            if (response.statusCode() == 200) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Cancel All Orders: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("message").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                if (response.body() == null) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        };
        hList3.add(cancelAllButton);

        WLabel label = theme.label("Order ID: ");
        WTextBox textBox = theme.textBox("");
        textBox.minWidth = 100;

        WButton cancelButton = theme.button("Cancel Order");
        cancelButton.action = () -> {
            if (!getIsLinked()) return;

            if (textBox.get().isEmpty()) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Please enter an order ID.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            if (!textBox.get().matches("\\d+")) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Order ID must be a number.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("order_id", textBox.get());

            Http.Request request = Http.post(apiUrl + "/order/cancel")
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .bodyJson(payload.toString());

            HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);

            if (response.statusCode() == 200) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Order Cancelled: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("order_id").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                if (response.body() == null) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        };
        hList3.add(cancelButton);
        hList3.add(label);
        hList3.add(textBox);

        WHorizontalList hList4 = list.add(theme.horizontalList()).expandX().widget();

        WLabel amountLabel = theme.label("Amount: ");
        WTextBox amountTextBox = theme.textBox("");
        amountTextBox.minWidth = 100;

        WLabel targetLabel = theme.label("Target MC Username or Discord ID: ");
        WTextBox targetTextBox = theme.textBox("");
        targetTextBox.minWidth = 100;

        WButton sendTokensButton = theme.button("Send Tokens");
        sendTokensButton.action = () -> {
            if (!getIsLinked()) return;

            if (amountTextBox.get().isEmpty() || targetTextBox.get().isEmpty()) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Please enter an amount and a target Discord ID.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            if (!amountTextBox.get().matches("\\d+")) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Amount must be a number.").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            if (Integer.parseInt(amountTextBox.get()) <= 0) {
                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal("Amount must be at least 1").formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
                return;
            }

            JsonObject payload = new JsonObject();
            assert mc.player != null;
            payload.addProperty("from_minecraft_username", mc.player.getName().getString());
            payload.addProperty("to_discord_id", targetTextBox.get());
            payload.addProperty("amount", Integer.parseInt(amountTextBox.get()));

            Http.Request request = Http.post(apiUrl + "/transfer")
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .bodyJson(payload.toString());

            HttpResponse<JsonObject> response = request.sendJsonResponse(JsonObject.class);

            if (response.statusCode() == 200) {
                if (response.body().has("error")) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Tokens Sent: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("message").getAsString()).formatted(Formatting.WHITE))
                    .append(Text.literal(" | ").formatted(Formatting.WHITE))
                    .append(Text.literal("New Balance: ").formatted(Formatting.GREEN))
                    .append(Text.literal(response.body().get("from_balance").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            } else {
                if (response.body() == null) {
                    Text msg = OmegawareAddons.PREFIX.copy()
                        .append(Text.literal("Error: ").formatted(Formatting.RED))
                        .append(Text.literal("No response from server.").formatted(Formatting.WHITE));
                    ChatUtils.sendMsg(msg);
                    return;
                }

                Text msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Error: ").formatted(Formatting.RED))
                    .append(Text.literal(response.body().get("error").getAsString()).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        };
        hList4.add(sendTokensButton);
        hList4.add(amountLabel);
        hList4.add(amountTextBox);
        hList4.add(targetLabel);
        hList4.add(targetTextBox);

        return list;
    }

    private static void saveApiKey(String key) {
        File configFile = OmegawareAddons.GetConfigFile("tsr-kitbot-api", "kitbot.key");
        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();
            payload.addProperty("api_key", key);
            payload.addProperty("last_updated", System.currentTimeMillis());
            writer.append(payload.toString());
            writer.close();
        } catch (Exception ignored) {
            OmegawareAddons.LOG.error("Failed to save API Key to {}", configFile.toPath());
        }
    }

    private String loadApiKey() {
        File configFile = OmegawareAddons.GetConfigFile("tsr-kitbot-api", "kitbot.key");
        if (!configFile.exists()) {
            OmegawareAddons.LOG.warn("{} not found!", configFile.toPath());
            return null;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
            if (payload.has("api_key")) {
                return payload.get("api_key").getAsString();
            } else {
                OmegawareAddons.LOG.warn("No API key found!");
                return null;
            }
        } catch (IOException e) {
            OmegawareAddons.LOG.error("Failed to load API key: {}", e.getMessage());
            return null;
        }
    }
}
