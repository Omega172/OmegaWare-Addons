package xyz.omegaware.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectEndEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import xyz.omegaware.addon.OmegawareAddons;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class BetterBaritoneBuild extends Module {
    public BetterBaritoneBuild() {
        super(OmegawareAddons.CATEGORY, "Better Baritone Build", "Enable this module to enhance Baritone's building capabilities with linked storage and item fetching features.");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Boolean> storageLinkMode = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-link-mode")
        .description("If enabled, all storage blocks you interact with will be linked to this module.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreY = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-ignore-y")
        .description("If enabled, the Y coordinate will be ignored when navigating to a block.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> homeIfStuck = sgGeneral.add(new BoolSetting.Builder()
        .name("home-if-stuck")
        .description("If enabled, Baritone will return set home point if it gets stuck while building.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> homeIfStuckTimeout = sgGeneral.add(new IntSetting.Builder()
        .name("home-if-stuck-timeout")
        .description("The timeout in seconds before Baritone returns to the home point if it gets stuck.")
        .defaultValue(15)
        .min(1)
        .sliderRange(5, 120)
        .visible(homeIfStuck::get)
        .build()
    );

    private final Setting<Boolean> highlightLinkedStorages = sgRender.add(new BoolSetting.Builder()
        .name("highlight-linked-storages")
        .description("If enabled, linked storages will be highlighted with a box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> invertHighlight = sgRender.add(new BoolSetting.Builder()
        .name("invert-highlight")
        .description("If enabled, the highlight will be inverted (i.e. highlighted blocks will not be highlighted).")
        .defaultValue(false)
        .visible(highlightLinkedStorages::get)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the shapes are rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(this::isActive)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("The side color of the rendering.")
            .defaultValue(new SettingColor(0, 255, 255, 40))
            .visible(() -> shapeMode.get().sides())
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("The line color of the rendering.")
            .defaultValue(new SettingColor(0, 255, 255, 255))
            .visible(() -> shapeMode.get().lines())
            .build()
    );

    private final Setting<Boolean> disconnectOnDone = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-done")
        .description("If enabled, the module will disconnect you from the server when it is done.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> disconnectOnError = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-on-error")
        .description("If enabled, the module will disconnect you from the server when it encounters an error.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> extraStacks = sgGeneral.add(new IntSetting.Builder()
        .name("extra-stacks")
        .description("The number of extra stacks to fetch from the linked storage.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("If enabled, the module will print debug information to the console.")
        .defaultValue(false)
        .build()
    );

    IBaritone baritone = null;

    private static class LinkedStorage {
        public BlockPos blockPos;
        List<ItemStack> inventory;

        public LinkedStorage() {
            this.blockPos = BlockPos.ORIGIN; // Default position
            this.inventory = new ArrayList<>(); // Default empty inventory
        }

        public LinkedStorage(BlockPos blockPos, List<ItemStack> inventory) {
            this.blockPos = blockPos;
            this.inventory = inventory;
        }
    }
    private final List<LinkedStorage> linkedStorages = new ArrayList<>();

    private static class Event {
        public boolean bWaitOnPath;
        public Runnable callback;

        public Event(boolean bWaitOnPath, Runnable callback) {
            this.bWaitOnPath = bWaitOnPath;
            this.callback = callback;
        }
    }
    private final List<Event> eventQueue = new ArrayList<>();

    private static class StorageItem {
        public Item item;
        public Integer stacks;
        public LinkedStorage linkedStorage;

        public StorageItem(Item item, Integer stacks, LinkedStorage linkedStorage) {
            this.item = item;
            this.stacks = stacks;
            this.linkedStorage = linkedStorage;
        }
    }
    private final List<StorageItem> itemsToFetch = new ArrayList<>();

    private String buildCommand = "";

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Error: ").formatted(Formatting.RED))
                .append(Text.literal("Baritone is not available!").formatted(Formatting.WHITE)));
            this.toggle();
            return;
        }

        baritone = BaritoneAPI.getProvider().getBaritoneForMinecraft(MinecraftClient.getInstance());

        eventQueue.clear();
        itemsToFetch.clear();

        loadLinkedStorages();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || mc.world == null || !highlightLinkedStorages.get()) return;

        if (!invertHighlight.get()) {
            for (LinkedStorage linkedStorage : linkedStorages) {
                if (linkedStorage == null || linkedStorage.blockPos == null || !mc.world.isPosLoaded(linkedStorage.blockPos))
                    continue;

                event.renderer.box(linkedStorage.blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        } else {
            for (BlockEntity blockEntity : Utils.blockEntities()) {
                if (!(blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity))
                    continue;

                BlockPos pos = blockEntity.getPos();
                if (mc.world.isPosLoaded(pos)) {
                    boolean isLinked = linkedStorages.stream().anyMatch(storage -> storage.blockPos.equals(pos));
                    if (!isLinked) {
                        event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                    }
                }
            }
        }
    }

    private BlockPos home = null;
    private int ticksStuck = 0;
    private BlockPos lastBlockPos = null;

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();

        WButton printBtn = theme.button("Print Linked Storages");
        printBtn.action = () -> {
            MutableText msg = OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Linked Storages: ").formatted(Formatting.GREEN));

            linkedStorages.forEach(linkedStorage -> msg.append(Text.literal(String.format("X=%s, Y=%s, Z=%s\n", linkedStorage.blockPos.getX(), linkedStorage.blockPos.getY(), linkedStorage.blockPos.getZ())).formatted(Formatting.WHITE)));

            ChatUtils.sendMsg(msg);
        };
        hList.add(printBtn);

        WButton clearBtn = theme.button("Clear Linked Storages");
        clearBtn.action = () -> {
            linkedStorages.clear();
            saveLinkedStorages();
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy().append(Text.literal("Linked Storages cleared!").formatted(Formatting.GREEN)));
        };
        hList.add(clearBtn);

        WButton setHomeBtn = theme.button("Set Home");
        setHomeBtn.action = () -> {
            if (mc.player == null || mc.world == null) return;

            home = mc.player.getBlockPos();
            ticksStuck = 0;
            lastBlockPos = null;

            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Home point set to: ").formatted(Formatting.GREEN))
                .append(Text.literal(String.format("X=%s, Y=%s, Z=%s", home.getX(), home.getY(), home.getZ())).formatted(Formatting.WHITE)));
        };
        hList.add(setHomeBtn);

        return list;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!isActive() || eventQueue.isEmpty()) return;

        Event queuedEvent = eventQueue.getFirst();

        if (queuedEvent.bWaitOnPath && baritone.getPathingBehavior().hasPath()) {
            return;
        }

        queuedEvent.callback.run();

        updateLinkedStorages();

        eventQueue.remove(queuedEvent);
    };

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!isActive() || mc.world == null || mc.player == null || !homeIfStuck.get()) return;
        if (mc.player.getBlockPos().equals(home)) {
            if (debugMode.get()) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Player is at home point.").formatted(Formatting.GREEN)));
            }
            ticksStuck = 0; // Reset the stuck counter if the player is at home
            lastBlockPos = null; // Reset the last block position
            return;
        }

        if (home == null) {
            // Yell at the player to set a home point
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Please set a home point using the \"Set Home\" button!").formatted(Formatting.RED)));
            homeIfStuck.set(false); // Disable the setting if no home point is set
            return;
        }

        if (buildCommand.isEmpty()) return;

        if (lastBlockPos == null) {
            lastBlockPos = mc.player.getBlockPos();
            return;
        }

        if (lastBlockPos.equals(mc.player.getBlockPos())) {
            ticksStuck++;
        } else {
            ticksStuck = 0;
            lastBlockPos = mc.player.getBlockPos(); // Update the last block position if the player has moved
            return;
        }

        if (debugMode.get()) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Baritone is stuck, ticks: ").formatted(Formatting.RED))
                .append(Text.literal(String.valueOf(ticksStuck)).formatted(Formatting.WHITE)));

            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal(String.format("Should return home: %s", ticksStuck >= homeIfStuckTimeout.get() * 20))).formatted(Formatting.GREEN));
        }

        // 1 second = 20 ticks
        if (ticksStuck >= homeIfStuckTimeout.get() * 20) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Baritone is stuck, returning to home point...").formatted(Formatting.RED)));

            eventQueue.clear();
            itemsToFetch.clear();

            baritone.getCommandManager().execute("stop");

            eventQueue.add(new Event(false, () -> {
                baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
            }));

            if (!buildCommand.isEmpty()) {
                eventQueue.add(new Event(true, () -> {
                    baritone.getCommandManager().execute(buildCommand);
                }));
            }
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String msg = event.getMessage().getString();
        if (msg == null || msg.isEmpty()) return;
        msg = msg.toLowerCase().trim();

        if (!msg.contains("[baritone]")) return;
        int index = msg.indexOf("[baritone]");

        msg = msg.substring(index+10).trim(); // Remove the "[Baritone]" part
        // 10x block{minecraft:black_concrete}[axis=x] 86x block{minecraft:red_concrete} 1x block{minecraft:birch_log}[axis=y]
        if (msg.matches("\\d+x block\\{minecraft:[a-z_]+}.*")) {
            String[] parts = msg.split(" ");

            String blockCount = parts[0].replace("x", "").trim();
            int count = Integer.parseInt(blockCount);
            int stacks = (int) Math.ceil(count / 64.0);

            String blockMessage = parts[1];
            String blockName = blockMessage.substring(blockMessage.indexOf(':') + 1);

            int endIndex = blockName.indexOf('}');
            if (endIndex != -1) {
                blockName = blockName.substring(0, endIndex);
            }

            Identifier identifier = Identifier.of(blockName);
            Item item = Registries.ITEM.get(identifier).asItem();

            if (item == null && debugMode.get()) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Item not found: ").formatted(Formatting.RED))
                    .append(Text.literal(blockName).formatted(Formatting.WHITE)));
                return;
            }

            pathToItemLocation(item, stacks+extraStacks.get());
            return;
        }

        if (msg.contains("done building")) {
            if (debugMode.get()) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Baritone has finished building!").formatted(Formatting.GREEN)));
            }

            if (disconnectOnDone.get()) {
                AutoReconnect autoReconnect = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
                if (autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }

                String prefix = OmegawareAddons.PREFIX.getString();
                MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED) + "Baritone has finished building!");

                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) {
                    networkHandler.getConnection().disconnect(text);
                }
            }
            event.cancel();
            return;
        }

        msg = msg.substring(2).trim(); // Remove the "> " part
        if (msg.startsWith("build") || msg.startsWith("litematica")) {
            buildCommand = msg;
            if (debugMode.get()) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Build command captured: ").formatted(Formatting.GREEN))
                    .append(Text.literal(buildCommand).formatted(Formatting.WHITE)));
            }
            event.cancel();
            return;
        }

        if (msg.startsWith("stop") || msg.startsWith("cancel")) {
            buildCommand = "";
            eventQueue.clear();
            itemsToFetch.clear();
            event.cancel();
        }
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) { loadLinkedStorages(); }

    private BlockPos lastBlockInteractPos = null;

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.world == null || !storageLinkMode.get()) return;

        lastBlockInteractPos = event.result.getBlockPos();
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!isActive() || mc.player == null || mc.world == null || mc.currentScreen == null) return;

        if (!itemsToFetch.isEmpty()) {
            itemsToFetch.forEach(storageItem -> {
                MeteorExecutor.execute(() -> {
                    if (debugMode.get()) {
                        String msg = String.format("Fetching %s stacks of %s from linked storage at X=%s, Y=%s, Z=%s", storageItem.stacks, storageItem.item.getName().getString(), storageItem.linkedStorage.blockPos.getX(), storageItem.linkedStorage.blockPos.getY(), storageItem.linkedStorage.blockPos.getZ());
                        ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy().append(Text.literal(msg).formatted(Formatting.GREEN)));
                    }
                    moveSlots(storageItem, mc.player.currentScreenHandler, SlotUtils.MAIN_END);
                });
            });
        }

        if (lastBlockInteractPos == null) return;

        if (storageLinkMode.get()) {
            BlockEntity blockEntity = mc.world.getBlockEntity(lastBlockInteractPos);
            if (blockEntity == null) return;

            if (blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
                for (LinkedStorage linkedStorage : linkedStorages) {
                    if (linkedStorage.blockPos.equals(lastBlockInteractPos)) {
                        lastBlockInteractPos = null;
                        linkedStorages.remove(linkedStorage);

                        LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
                        if (newStorage != null) {
                            linkedStorages.add(newStorage);
                            saveLinkedStorages();
                        }

                        return;
                    }
                }
                lastBlockInteractPos = null;

                LinkedStorage linkedStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
                if (linkedStorage == null) return;

                if (linkedStorage.inventory.isEmpty()) {
                    if (debugMode.get()) {
                        ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                            .append(Text.literal("No items found in the linked storage!").formatted(Formatting.RED)));
                    }
                    return;
                }
                linkedStorages.add(linkedStorage);
                saveLinkedStorages();


                MutableText msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Better Baritone Build: ").formatted(Formatting.GREEN))
                    .append(Text.literal(String.format("Linked Storage located at X=%s, Y=%s, Z=%s", blockEntity.getPos().getX(), blockEntity.getPos().getY(), blockEntity.getPos().getZ())).formatted(Formatting.WHITE));
                ChatUtils.sendMsg(msg);
            }
        }
    }

    private void saveLinkedStorages() {
        updateLinkedStorages();

        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();

            JsonArray linkedStoragesArray = new JsonArray();
            for (LinkedStorage linkedStorage : linkedStorages) {
                JsonObject storageJson = new JsonObject();
                storageJson.addProperty("blockPos", linkedStorage.blockPos.asLong());

                JsonObject inventoryJson = new JsonObject();
                for (ItemStack stack : linkedStorage.inventory) {
                    if (!stack.isEmpty()) {
                        String itemId = Registries.ITEM.getId(stack.getItem()).toString();
                        JsonObject itemData = new JsonObject();
                        itemData.addProperty("count", stack.getCount());
                        inventoryJson.add(itemId, itemData);
                    }
                }
                storageJson.add("inventory", inventoryJson);
                linkedStoragesArray.add(storageJson);
            }

            payload.add("linked_storages", linkedStoragesArray);


            writer.append(payload.toString());
            writer.close();
        } catch (Exception ignored) {
            OmegawareAddons.LOG.info("Failed to load Linked Storages to {}", configFile.toPath());
        }
    }

    private void loadLinkedStorages() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");
        if (!configFile.exists()) {
            OmegawareAddons.LOG.warn("{} not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = GSON.fromJson(content, JsonObject.class);
            if (payload.has("linked_storages")) {
                JsonArray linkedStoragesArray = payload.getAsJsonArray("linked_storages");
                linkedStorages.clear();

                for (int i = 0; i < linkedStoragesArray.size(); i++) {
                    JsonObject storageJson = linkedStoragesArray.get(i).getAsJsonObject();
                    LinkedStorage linkedStorage = new LinkedStorage();

                    if (storageJson.has("blockPos")) {
                        linkedStorage.blockPos = BlockPos.fromLong(storageJson.get("blockPos").getAsLong());
                    }

                    if (storageJson.has("inventory")) {
                        JsonObject inventoryJson = storageJson.getAsJsonObject("inventory");
                        linkedStorage.inventory = new ArrayList<>();

                        for (String itemId : inventoryJson.keySet()) {
                            Item item = Registries.ITEM.get(Identifier.of(itemId)).asItem();
                            if (item != null) {
                                JsonObject itemData = inventoryJson.getAsJsonObject(itemId);
                                int count = itemData.get("count").getAsInt();
                                linkedStorage.inventory.add(new ItemStack(item, count));
                            }
                        }
                    }

                    linkedStorages.add(linkedStorage);
                }
            }

        } catch (Exception e) {
            OmegawareAddons.LOG.error("Failed to load Linked Storages from {}: {}", configFile.toPath(), e.getMessage());
        }
    }

    private void updateLinkedStorages() {
        if (mc.world == null || linkedStorages.isEmpty()) return;

        linkedStorages.removeIf(storage -> {
            if (storage == null) return true;
            if (!mc.world.isPosLoaded(storage.blockPos)) return false;

            if (storage.inventory == null || storage.inventory.isEmpty()) {
                return true;
            }

            return mc.world.getBlockState(storage.blockPos).isAir() || mc.world.getBlockEntity(storage.blockPos) == null;
        });
    }

    private LinkedStorage indexStorage(ScreenHandler screenHandler, BlockPos blockPos) {
        if (screenHandler == null) return null;

        LinkedStorage linkedStorage = new LinkedStorage(blockPos, new ArrayList<>());
        for (int i = 0; i < SlotUtils.indexToId(SlotUtils.MAIN_START); i++) {
            ItemStack stack = screenHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                linkedStorage.inventory.add(stack.copy());
            }
        }

        return linkedStorage;
    }

    private void pathToPos(BlockPos blockPos) {
        if (mc.player == null || mc.world == null) return;

        if (debugMode.get()) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Navigating to: ").formatted(Formatting.GREEN))
                .append(Text.literal(String.format("X=%s, Y=%s, Z=%s", blockPos.getX(), blockPos.getY(), blockPos.getZ())).formatted(Formatting.WHITE)));
        }

        if (!ignoreY.get()) {
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos));
        } else baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos.withY(mc.player.getBlockY())));
    }

    private LinkedStorage findItem(Item item) {
        for (LinkedStorage linkedStorage : linkedStorages) {
            for (ItemStack stack : linkedStorage.inventory) {
                if (stack.getItem() == item) {
                    return linkedStorage;
                }
            }
        }

        return null;
    }

    private static class FindResult {
        public boolean found;
        public LinkedStorage linkedStorage;

        public FindResult(boolean found, LinkedStorage linkedStorage) {
            this.found = found;
            this.linkedStorage = linkedStorage;
        }
    }

    private void pathToItemLocation(Item item, @Nullable Integer stacks) {
        if (mc.player == null || mc.interactionManager == null) return;

        if (stacks == null) stacks = 1; // Default to 1 stack if not specified

        LinkedStorage linkedStorage = findItem(item);
        if (linkedStorage == null) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("No linked storage contains the item: ").formatted(Formatting.RED))
                .append(Text.literal(item.getName().getString()).formatted(Formatting.WHITE)));

            if (disconnectOnError.get()) {
                AutoReconnect autoReconnect = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
                if (autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }

                String prefix = OmegawareAddons.PREFIX.getString();
                MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED) + String.format("No linked storage contains the item: %s\n", item.getName().getString()));

                disconnectOnError.set(false); // Disable the setting to prevent infinite disconnects

                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) {
                    networkHandler.getConnection().disconnect(text);
                }
            }

            return;
        }

        ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
            .append(Text.literal("Navigating to storage containing: ").formatted(Formatting.GREEN))
            .append(Text.literal(item.getName().getString()).formatted(Formatting.WHITE)));

        itemsToFetch.add(new StorageItem(item, stacks, linkedStorage));

        eventQueue.add(new Event(true, () -> {
            pathToPos(linkedStorage.blockPos);
        }));

        eventQueue.add(new Event(true, () -> {
            mc.setScreen(null); // Close any open screens to ensure that we can interact with the storage block

            Vec3d hitPos = Vec3d.ofCenter(linkedStorage.blockPos);
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, linkedStorage.blockPos, false);

            ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); // Attempt to interact with the block
            if (result.isAccepted()) // If the interaction was successful, we can then make the player swing their hand
                mc.player.swingHand(Hand.MAIN_HAND);
        }));
    }

    private void moveSlots(StorageItem storageItem, ScreenHandler handler, int end) {
        if (mc.player == null) return;

        boolean initial = true;
        int count = 0;
        List<Item> grabbedItems = new ArrayList<>();
        for (int i = 0; i < end; i++) {
            if (!handler.getSlot(i).hasStack()) continue;

            int sleep;
            if (initial) {
                sleep = 50;
                initial = false;
            } else sleep = 70;
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                OmegawareAddons.LOG.error("Interrupted while sleeping in moveSlots: {}", e.getMessage());
            }

            // Exit if user closes screen or exit world
            if (mc.currentScreen == null || !Utils.canUpdate()) break;

            Item item = handler.getSlot(i).getStack().getItem();
            if (item != storageItem.item) continue;

            grabbedItems.add(item);

            count++;
            InvUtils.shiftClick().slotId(i);

            LinkedStorage linkedStorage = storageItem.linkedStorage;
            linkedStorages.remove(linkedStorage);
            linkedStorage.inventory.remove(handler.getSlot(i).getStack());
            linkedStorages.add(linkedStorage);
            saveLinkedStorages();

            if (count >= storageItem.stacks) {
                break;
            }
        }

        itemsToFetch.removeIf(element -> grabbedItems.contains(element.item));

        if (!buildCommand.isEmpty()) {
            eventQueue.add(new Event(true, () -> {
                baritone.getCommandManager().execute(buildCommand);
            }));
        }
    }
}
