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
import net.minecraft.util.Formatting;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.utils.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BetterBaritoneBuild extends Module {
    public BetterBaritoneBuild() {
        super(OmegawareAddons.CATEGORY, "better-baritone-build", "Enable this module to enhance Baritone's building capabilities with linked storage and item fetching features.");
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

    // concurrency/safety
    private volatile boolean isFetching = false;
    private long lastInteractMs = 0L;
    private int openAttempts = 0;
    private final long INTERACT_DEBOUNCE_MS = 800L;
    private final int MAX_OPEN_ATTEMPTS = 6;
    private final long AFTER_SHIFTCLICK_DELAY_MS = 80L;
    private BlockPos lastAutomatedInteractPos = null;
    private long lastAutomatedInteractMs = 0L;
    private boolean stuckPaused = false;
    private BlockPos buildAnchorPos = null;
    private float buildAnchorYaw = 0f;
    private boolean buildAnchorSet = false;
    private static final float YAW_TOLERANCE_DEGREES = 12.0f;
    private long lastHomeLogMs = 0L;
    private static final long HOME_LOG_THROTTLE_MS = 5000L;
    private int homeArrivalRetries = 0;
    private static final int HOME_ARRIVAL_MAX_RETRIES = 3;
    private static final long SCREEN_OPEN_TIMEOUT_MS = 1200L;

    private static class LinkedStorage {
        public BlockPos blockPos;
        List<ItemStack> inventory;

        public LinkedStorage() {
            this.blockPos = BlockPos.ORIGIN;
            this.inventory = new ArrayList<>();
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
    private final java.util.LinkedList<Event> eventQueue = new java.util.LinkedList<>();

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
            Logger.error("Baritone is not available!");
            toggle();
            return;
        }

        baritone = BaritoneAPI.getProvider().getBaritoneForMinecraft(MinecraftClient.getInstance());

        eventQueue.clear();
        itemsToFetch.clear();

        loadLinkedStorages();
        loadHome();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || mc.world == null) return;
        // simplified: same render as before
        for (LinkedStorage linkedStorage : linkedStorages) {
            if (linkedStorage == null || linkedStorage.blockPos == null || !mc.world.isPosLoaded(linkedStorage.blockPos))
                continue;
            event.renderer.box(linkedStorage.blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }

    private BlockPos home = null;
    private int ticksStuck = 0;
    private BlockPos lastBlockPos = null;

    @Override
    public WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();

        WButton printBtn = theme.button("Print Linked Storages");
        printBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            linkedStorages.forEach(linkedStorage -> sb.append(String.format("X=%s, Y=%s, Z=%s\n", linkedStorage.blockPos.getX(), linkedStorage.blockPos.getY(), linkedStorage.blockPos.getZ())));
            Logger.info("Linked Storages:\n%s", sb.toString());
        };
        hList.add(printBtn);

        WButton clearBtn = theme.button("Clear Linked Storages");
        clearBtn.action = () -> {
            linkedStorages.clear();
            saveLinkedStorages();
            Logger.info("Linked Storages cleared!");
        };
        hList.add(clearBtn);

        WButton setHomeBtn = theme.button("Set Home");
        setHomeBtn.action = () -> {
            if (mc.player == null || mc.world == null) return;
            home = mc.player.getBlockPos();
            ticksStuck = 0;
            lastBlockPos = null;
            saveHome();
            Logger.info("%sHome point set to:%s X=%s, Y=%s, Z=%s", Formatting.GREEN, Formatting.WHITE, home.getX(), home.getY(), home.getZ());
        };
        hList.add(setHomeBtn);

        return list;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (!isActive() || eventQueue.isEmpty()) return;
        Event queuedEvent = eventQueue.getFirst();
        if (queuedEvent.bWaitOnPath && (baritone == null || baritone.getPathingBehavior().hasPath())) {
            return;
        }
        try {
            queuedEvent.callback.run();
        } catch (Exception e) {
            if (debugMode.get()) Logger.warn("Exception while running queued event: %s", e.getMessage());
        }
        updateLinkedStorages();
        eventQueue.remove(queuedEvent);
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!isActive() || mc.world == null || mc.player == null || !homeIfStuck.get()) return;
        if (home == null) {
            Logger.error("Please set a home point using the \"Set Home\" button!");
            homeIfStuck.set(false);
            return;
        }
        if (buildCommand.isEmpty()) return;

        if (lastBlockPos == null) {
            lastBlockPos = mc.player.getBlockPos();
            return;
        }

        if (lastBlockPos.equals(mc.player.getBlockPos())) ticksStuck++; else { ticksStuck = 0; lastBlockPos = mc.player.getBlockPos(); return; }

        if (debugMode.get() && ticksStuck % 20 == 0) Logger.warn("Baritone may be stuck, ticks: %d", ticksStuck);

        if (ticksStuck >= homeIfStuckTimeout.get() * 20) {
            Logger.error("Baritone is stuck, returning to home point...");
            ticksStuck = 0;
            lastBlockPos = mc.player.getBlockPos();
            eventQueue.clear();
            itemsToFetch.clear();
            if (baritone != null) {
                try {
                    baritone.getPathingBehavior().cancelEverything();
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
                } catch (Exception ignored) {}
            }
            stuckPaused = true;
            homeArrivalRetries = 0;
            eventQueue.add(new Event(true, this::verifyArrivalAtHome));
            if (debugMode.get()) Logger.info("Build paused due to stuck; re-issue build command manually when ready.");
        }
    }

    private void verifyArrivalAtHome() {
        if (mc.player == null || home == null || baritone == null) return;
        if (mc.player.getBlockPos().equals(home)) { homeArrivalRetries = 0; if (debugMode.get()) Logger.info("Verified arrival at home: %s", home.toShortString()); return; }
        if (homeArrivalRetries < HOME_ARRIVAL_MAX_RETRIES) {
            homeArrivalRetries++;
            if (debugMode.get()) Logger.warn("Home arrival off by %s. Retrying (%d/%d).", mc.player.getBlockPos().toShortString(), homeArrivalRetries, HOME_ARRIVAL_MAX_RETRIES);
            try { baritone.getPathingBehavior().cancelEverything(); } catch (Exception ignored) {}
            try { baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home)); } catch (Exception ignored) {}
            eventQueue.add(new Event(true, this::verifyArrivalAtHome));
        } else {
            if (debugMode.get()) Logger.warn("Failed to perfectly arrive at home after retries. current=%s, desired=%s", mc.player.getBlockPos().toShortString(), home.toShortString());
            homeArrivalRetries = 0;
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String original = event.getMessage().getString();
        if (original == null || original.isEmpty()) return;
        String lower = original.toLowerCase().trim();

        if (lower.contains("[baritone]") && !lower.contains("omegaware")) {
            int index = lower.indexOf("[baritone]");
            String msg = lower.substring(index + 10).trim();
            if (msg.matches("\\d+x block\\{minecraft:[a-z_]+}.*")) {
                String[] parts = msg.split(" ");
                String blockCount = parts[0].replace("x", "").trim();
                int count = Integer.parseInt(blockCount);
                int stacks = (int) Math.ceil(count / 64.0);
                String blockMessage = parts[1];
                String blockName = blockMessage.substring(blockMessage.indexOf(':') + 1);
                int endIndex = blockName.indexOf('}');
                if (endIndex != -1) blockName = blockName.substring(0, endIndex);
                Identifier identifier = Identifier.of(blockName);
                Item item = Registries.ITEM.get(identifier).asItem();
                if (item == null) { if (debugMode.get()) Logger.error("Item not found: %s%s", Formatting.WHITE, blockName); return; }

                // check inventory first
                int playerCount = playerItemCount(item);
                if (playerCount >= count) { if (debugMode.get()) Logger.info("Player already has %d of %s; skipping fetch.", playerCount, blockName); return; }

                LinkedStorage linkedStorage = findItem(item);
                if (linkedStorage == null) {
                    // double-check flow simplified: notify and return (double-check code left out for brevity in this paste),
                    Logger.error("No linked storage contains the item: %s%s", Formatting.WHITE, item.getName().getString());
                    return;
                }

                itemsToFetch.add(new StorageItem(item, stacks + extraStacks.get(), linkedStorage));
                stuckPaused = false;
                pathToLinkedStorage(item, linkedStorage);
                return;
            }
        }

        String trimmed = original.trim();
        if (trimmed.startsWith("> ")) trimmed = trimmed.substring(2).trim();
        String tLower = trimmed.toLowerCase();

        if (tLower.startsWith("build") || tLower.startsWith("litematica")) {
            buildCommand = trimmed;
            stuckPaused = false;
            if (mc.player != null) { buildAnchorSet = true; buildAnchorPos = mc.player.getBlockPos(); buildAnchorYaw = mc.player.getYaw(); }
            if (debugMode.get()) Logger.info("Build command captured: %s", buildCommand);
            return;
        }

        if (tLower.startsWith("stop") || tLower.startsWith("cancel")) {
            buildCommand = "";
            eventQueue.clear();
            itemsToFetch.clear();
            Logger.info("Stop received.");
        }
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) { loadLinkedStorages(); }

    private BlockPos lastBlockInteractPos = null;

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.world == null || !storageLinkMode.get()) return;
        lastBlockInteractPos = event.result.getBlockPos();
        lastAutomatedInteractPos = null;
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!isActive() || mc.player == null || mc.world == null) return;

        // start fetch processing if queued and not already fetching
        if (!itemsToFetch.isEmpty() && !isFetching) {
            StorageItem itemToProcess = itemsToFetch.get(0);
            isFetching = true;
            MeteorExecutor.execute(() -> {
                try { moveSlots(itemToProcess, mc.player.currentScreenHandler); }
                finally {
                    try { Thread.sleep(120); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    isFetching = false;
                }
            });
        }

        if (mc.currentScreen == null) return;
        if (lastBlockInteractPos == null) return;

        if (lastAutomatedInteractPos != null && lastBlockInteractPos.equals(lastAutomatedInteractPos)
            && (System.currentTimeMillis() - lastAutomatedInteractMs) < 5000L) {
            lastBlockInteractPos = null;
            return;
        }

        BlockEntity blockEntity = mc.world.getBlockEntity(lastBlockInteractPos);
        if (blockEntity == null) { lastBlockInteractPos = null; return; }

        boolean handled = false;
        for (int i = 0; i < linkedStorages.size(); i++) {
            LinkedStorage linkedStorage = linkedStorages.get(i);
            if (linkedStorage.blockPos.equals(lastBlockInteractPos)) {
                lastBlockInteractPos = null;
                linkedStorages.remove(i);
                LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
                if (newStorage != null) { linkedStorages.add(newStorage); saveLinkedStorages(); Logger.info("Updated linked storage at X=%s, Y=%s, Z=%s", blockEntity.getPos().getX(), blockEntity.getPos().getY(), blockEntity.getPos().getZ()); }
                handled = true; break;
            }
        }
        if (handled) return;

        if (!storageLinkMode.get()) { lastBlockInteractPos = null; return; }
        if (blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
            lastBlockInteractPos = null;
            if (newStorage == null) return;
            if (newStorage.inventory.isEmpty()) { if (debugMode.get()) Logger.error("No items found in the linked storage!"); return; }
            linkedStorages.add(newStorage);
            saveLinkedStorages();
            Logger.info("Linked Storage located at X=%s, Y=%s, Z=%s", blockEntity.getPos().getX(), blockEntity.getPos().getY(), blockEntity.getPos().getZ());
        }
    }

    private void saveLinkedStorages() {
        updateLinkedStorages();
        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");
        try {
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
        if (!configFile.exists()) { OmegawareAddons.LOG.warn("{} not found!", configFile.toPath()); return; }
        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = GSON.fromJson(content, JsonObject.class);
            if (payload.has("linked_storages")) {
                JsonArray linkedStoragesArray = payload.getAsJsonArray("linked_storages");
                linkedStorages.clear();
                for (int i = 0; i < linkedStoragesArray.size(); i++) {
                    JsonObject storageJson = linkedStoragesArray.get(i).getAsJsonObject();
                    LinkedStorage linkedStorage = new LinkedStorage();
                    if (storageJson.has("blockPos")) linkedStorage.blockPos = BlockPos.fromLong(storageJson.get("blockPos").getAsLong());
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

    private void saveHome() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");
        try { configFile.getParentFile().mkdirs(); Writer writer = new FileWriter(configFile); JsonObject payload = new JsonObject(); if (home != null) payload.addProperty("home", home.asLong()); writer.append(payload.toString()); writer.close(); } catch (Exception ignored) { OmegawareAddons.LOG.info("Failed to save home to {}", configFile.toPath()); }
    }

    private void loadHome() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");
        if (!configFile.exists()) { OmegawareAddons.LOG.warn("{} not found!", configFile.toPath()); return; }
        try { String content = Files.readString(configFile.toPath()); JsonObject payload = GSON.fromJson(content, JsonObject.class); if (payload.has("home")) home = BlockPos.fromLong(payload.get("home").getAsLong()); } catch (Exception e) { OmegawareAddons.LOG.error("Failed to load home from {}: {}", configFile.toPath(), e.getMessage()); }
    }

    private void updateLinkedStorages() {
        if (mc.world == null || linkedStorages.isEmpty()) return;
        linkedStorages.removeIf(storage -> {
            if (storage == null) return true;
            if (!mc.world.isPosLoaded(storage.blockPos)) return false;
            if (storage.inventory == null || storage.inventory.isEmpty()) return true;
            return mc.world.getBlockState(storage.blockPos).isAir() || mc.world.getBlockEntity(storage.blockPos) == null;
        });
    }

    private LinkedStorage indexStorage(ScreenHandler screenHandler, BlockPos blockPos) {
        if (screenHandler == null) return null;
        LinkedStorage linkedStorage = new LinkedStorage(blockPos, new ArrayList<>());
        for (int i = 0; i < SlotUtils.indexToId(SlotUtils.MAIN_START) && i < screenHandler.slots.size(); i++) {
            ItemStack stack = screenHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) linkedStorage.inventory.add(stack.copy());
        }
        return linkedStorage;
    }

    private void pathToPos(BlockPos blockPos) {
        if (mc.player == null || mc.world == null) return;
        if (debugMode.get()) Logger.info("%sNavigating to:%s X=%s, Y=%s, Z=%s", Formatting.GREEN, Formatting.WHITE, blockPos.getX(), blockPos.getY(), blockPos.getZ());
        if (!ignoreY.get()) { if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos)); } else { if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos.withY(mc.player.getBlockY()))); }
    }

    private LinkedStorage findItem(Item item) {
        if (mc.player == null || mc.world == null) return null;
        linkedStorages.sort((a, b) -> Double.compare(a.blockPos.getSquaredDistance(mc.player.getBlockPos()), b.blockPos.getSquaredDistance(mc.player.getBlockPos())));
        for (LinkedStorage linkedStorage : linkedStorages) {
            for (ItemStack stack : linkedStorage.inventory) {
                if (stack.getItem() == item) return linkedStorage;
            }
        }
        return null;
    }

    private void pathToLinkedStorage(Item item, LinkedStorage linkedStorage) {
        if (mc.player == null || mc.interactionManager == null || linkedStorage == null) return;
        Logger.info("%sNavigating to storage containing:%s %s", Formatting.GREEN, Formatting.WHITE, item.getName().getString());
        openAttempts = 0;
        eventQueue.add(new Event(true, () -> pathToPos(linkedStorage.blockPos)));
        eventQueue.add(new Event(true, () -> {
            long now = System.currentTimeMillis();
            if (now - lastInteractMs < INTERACT_DEBOUNCE_MS) {
                if (debugMode.get()) Logger.warn("Interact debounced, skipping immediate interact (delta=%dms)", now - lastInteractMs);
                MeteorExecutor.execute(() -> { try { Thread.sleep(INTERACT_DEBOUNCE_MS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } eventQueue.add(new Event(true, () -> performStorageInteractSafely(linkedStorage))); });
                return;
            }
            performStorageInteractSafely(linkedStorage);
        }));
    }

    // !! CHANGED: DO NOT CALL moveSlots directly here; rely on InventoryEvent to trigger moveSlots.
    private void performStorageInteractSafely(LinkedStorage linkedStorage) {
        if (mc.player == null || mc.interactionManager == null || linkedStorage == null) return;

        if (openAttempts >= MAX_OPEN_ATTEMPTS) {
            Logger.error("Too many open attempts for storage at X=%s, Y=%s, Z=%s. Aborting fetch.", linkedStorage.blockPos.getX(), linkedStorage.blockPos.getY(), linkedStorage.blockPos.getZ());
            eventQueue.clear();
            itemsToFetch.clear();
            openAttempts = 0;
            return;
        }

        mc.setScreen(null);

        Vec3d hitPos = Vec3d.ofCenter(linkedStorage.blockPos);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, linkedStorage.blockPos, false);

        ActionResult result = ActionResult.PASS;
        try { result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); } catch (Exception ex) { if (debugMode.get()) Logger.warn("Exception interacting with block: %s", ex.getMessage()); }

        lastInteractMs = System.currentTimeMillis();
        openAttempts++;

        // mark automated open so onInventory won't treat as manual
        lastAutomatedInteractPos = linkedStorage.blockPos;
        lastAutomatedInteractMs = System.currentTimeMillis();

        // wait briefly for screen to open (but DO NOT call moveSlots here)
        long start = System.currentTimeMillis();
        boolean screenOpened = false;
        while (System.currentTimeMillis() - start < SCREEN_OPEN_TIMEOUT_MS) {
            if (mc.currentScreen != null) { screenOpened = true; break; }
            try { Thread.sleep(30); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }

        if (screenOpened || result.isAccepted()) {
            openAttempts = 0;
            if (debugMode.get()) Logger.info("Storage open accepted (attempt #%d). ScreenOpened=%b, result=%s", openAttempts, screenOpened, result.toString());
            // NOTE: do NOT call moveSlots directly; InventoryEvent handler will pick this up and call moveSlots.
        } else {
            if (debugMode.get()) Logger.warn("Interact not accepted (attempt #%d), result=%s — will retry if attempts left.", openAttempts, result.toString());
            MeteorExecutor.execute(() -> {
                try { Thread.sleep(250 + (openAttempts * 50)); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                eventQueue.add(new Event(true, () -> performStorageInteractSafely(linkedStorage)));
            });
        }
    }

    private void moveSlots(StorageItem storageItem, ScreenHandler handler) {
        if (mc.player == null) return;
        if (storageItem == null) return;

        boolean initial = true;
        int count = 0;
        List<Item> grabbedItems = new ArrayList<>();

        if (handler == null) {
            if (debugMode.get()) Logger.warn("moveSlots: no open screen handler to move items from.");
            return;
        }

        int maxIndex = Math.min(SlotUtils.MAIN_END, handler.slots.size());
        for (int i = 0; i < maxIndex; i++) {
            if (!handler.getSlot(i).hasStack()) continue;
            int sleep = initial ? 50 : 70; initial = false;
            try { Thread.sleep(sleep); } catch (InterruptedException e) { Thread.currentThread().interrupt(); OmegawareAddons.LOG.error("Interrupted while sleeping in moveSlots: {}", e.getMessage()); }
            if (mc.currentScreen == null || !Utils.canUpdate()) break;

            Item item = handler.getSlot(i).getStack().getItem();
            if (item != storageItem.item) continue;

            grabbedItems.add(item);
            LinkedStorage linkedStorage = storageItem.linkedStorage;
            if (linkedStorage != null) {
                for (int si = 0; si < linkedStorages.size(); si++) {
                    LinkedStorage ls = linkedStorages.get(si);
                    if (ls.blockPos.equals(linkedStorage.blockPos)) {
                        ItemStack stackInSlot = handler.getSlot(i).getStack();
                        linkedStorages.get(si).inventory.removeIf(itemStack -> itemStack.getItem() == stackInSlot.getItem());
                        break;
                    }
                }
                saveLinkedStorages();
            }

            count++;
            try { InvUtils.shiftClick().slotId(i); } catch (Exception ex) { try { handler.getSlot(i).takeStack(1); } catch (Exception ignored) {} }
            try { Thread.sleep(AFTER_SHIFTCLICK_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            if (count >= storageItem.stacks) break;
        }

        int remaining = Math.max(0, storageItem.stacks - count);

        itemsToFetch.removeIf(si -> {
            if (si.item == storageItem.item) {
                if (si.linkedStorage == null && storageItem.linkedStorage == null) return true;
                if (si.linkedStorage != null && storageItem.linkedStorage != null && si.linkedStorage.blockPos.equals(storageItem.linkedStorage.blockPos)) return true;
            }
            return false;
        });

        if (remaining > 0) {
            StorageItem newItem = new StorageItem(storageItem.item, remaining, storageItem.linkedStorage);
            itemsToFetch.add(0, newItem);
        }

        if (mc != null) {
            MinecraftClient.getInstance().execute(() -> { try { if (mc.currentScreen != null) mc.setScreen(null); } catch (Exception ignored) {} });
        }

        lastAutomatedInteractPos = null;
        lastAutomatedInteractMs = 0L;

        if (!itemsToFetch.isEmpty()) {
            StorageItem next = itemsToFetch.get(0);
            eventQueue.clear();
            pathToLinkedStorage(next.item, next.linkedStorage);
        } else if (!buildCommand.isEmpty()) {
            // !! CHANGED: queue resume build instead of executing immediately (avoids race conditions)
            if (!stuckPaused) {
                eventQueue.add(new Event(true, () -> {
                    try {
                        if (baritone != null) baritone.getCommandManager().execute(buildCommand);
                    } catch (Exception ex) {
                        if (debugMode.get()) Logger.warn("Failed to resume build via eventQueue: %s", ex.getMessage());
                    }
                }));
            } else {
                Logger.info("Build remains paused (home-if-stuck triggered earlier). Re-issue build command to resume.");
            }
        }
    }

    private static float angleDistanceDegrees(float a, float b) { float diff = a - b; diff %= 360.0f; if (diff < -180.0f) diff += 360.0f; if (diff > 180.0f) diff -= 360.0f; return diff; }

    private int playerItemCount(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        try {
            int invSize = mc.player.getInventory().size();
            for (int i = 0; i < invSize; i++) {
                try {
                    ItemStack s = mc.player.getInventory().getStack(i);
                    if (s != null && !s.isEmpty() && s.getItem() == item) total += s.getCount();
                } catch (Exception ignoredSlot) {}
            }
        } catch (Exception e) {
            try { for (int i = 0; i < 36; i++) try { ItemStack s = mc.player.getInventory().getStack(i); if (s != null && !s.isEmpty() && s.getItem() == item) total += s.getCount(); } catch (Exception ignoredSlot) {} } catch (Exception ignored) { if (debugMode.get()) Logger.warn("playerItemCount fallback failed: %s", e.getMessage()); return 0; }
        }
        return total;
    }
}
