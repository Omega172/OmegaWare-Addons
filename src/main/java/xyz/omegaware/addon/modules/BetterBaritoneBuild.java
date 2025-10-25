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
import java.util.*;

public class BetterBaritoneBuild extends Module {
    public BetterBaritoneBuild() {
        super(OmegawareAddons.CATEGORY, "better-baritone-build", "Enable this module to enhance Baritone's building capabilities with linked storage and item fetching features.");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");
    private final SettingGroup sgSafety = this.settings.createGroup("Safety & Resume");


    // --- General Settings ---
    private final Setting<Boolean> storageLinkMode = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-link-mode")
        .description("If enabled, all storage blocks you interact with will be linked to this module.")
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

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("If enabled, the module will print debug information to the console.")
        .defaultValue(false)
        .build()
    );


    // --- Safety & Resume Settings ---
    private final Setting<Boolean> homeIfStuck = sgSafety.add(new BoolSetting.Builder()
        .name("home-if-stuck")
        .description("If enabled, Baritone will return set home point if it gets stuck while building or fails double-check.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> homeIfStuckTimeout = sgSafety.add(new IntSetting.Builder()
        .name("home-if-stuck-timeout")
        .description("The timeout in seconds before Baritone returns to the home point if it gets stuck.")
        .defaultValue(15)
        .min(1)
        .sliderRange(5, 120)
        .visible(homeIfStuck::get)
        .build()
    );

    private final Setting<Boolean> autoResumeOnFetchComplete = sgSafety.add(new BoolSetting.Builder()
        .name("auto-resume-on-fetch")
        .description("Automatically resume building after all items have been fetched.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> requireAnchorMatchForResume = sgSafety.add(new BoolSetting.Builder()
        .name("require-anchor-match")
        .description("If auto-resume is on, only resume if player position and yaw match the initial build command anchor.")
        .defaultValue(true)
        .visible(autoResumeOnFetchComplete::get)
        .build()
    );

    private final Setting<Double> anchorPosTolerance = sgSafety.add(new DoubleSetting.Builder()
        .name("anchor-pos-tolerance")
        .description("Maximum distance (blocks) from anchor position allowed for auto-resume.")
        .defaultValue(2.0)
        .min(0.5)
        .sliderRange(0.5, 10.0)
        .visible(() -> autoResumeOnFetchComplete.get() && requireAnchorMatchForResume.get())
        .build()
    );

    private final Setting<Double> anchorYawTolerance = sgSafety.add(new DoubleSetting.Builder()
        .name("anchor-yaw-tolerance")
        .description("Maximum difference (degrees) from anchor yaw allowed for auto-resume.")
        .defaultValue(15.0)
        .min(1.0)
        .sliderRange(1.0, 90.0)
        .visible(() -> autoResumeOnFetchComplete.get() && requireAnchorMatchForResume.get())
        .build()
    );

    private final Setting<Integer> afterShiftClickDelay = sgSafety.add(new IntSetting.Builder()
        .name("after-shift-click-delay")
        .description("Delay (ms) after shift-clicking an item to allow server inventory sync. (Higher = safer but slower).")
        .defaultValue(350)
        .min(50)
        .sliderRange(50, 1000)
        .build()
    );

    private final Setting<Integer> afterFetchResumeDelay = sgSafety.add(new IntSetting.Builder()
        .name("after-fetch-resume-delay")
        .description("Delay (ms) after closing GUI and before resuming build, allowing full sync. (Higher = safer).")
        .defaultValue(400)
        .min(100)
        .sliderRange(100, 2000)
        .build()
    );

    // --- Render Settings ---
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
    
    // Deprecated, unused setting. Kept for config compatibility but hidden.
    private final Setting<Boolean> ignoreY = sgGeneral.add(new BoolSetting.Builder()
        .name("baritone-ignore-y")
        .description("DEPRECATED: This setting is no longer used.")
        .defaultValue(false)
        .visible(false) // Hide from GUI
        .build()
    );


    IBaritone baritone = null;

    // --- Concurrency / safety flags for storage fetching ---
    private volatile boolean isFetching = false; // ensure only one fetch job at a time
    private long lastInteractMs = 0L;
    private int openAttempts = 0;
    private final long INTERACT_DEBOUNCE_MS = 800L; // don't interact faster than this
    private final int MAX_OPEN_ATTEMPTS = 6; // abort after this many failed open tries

    // automated open tracking & double-check support
    private BlockPos lastAutomatedInteractPos = null;
    private long lastAutomatedInteractMs = 0L;
    private BlockPos lastBlockInteractPos = null; // last pos *player* interacted with

    // if Baritone returned home due to stuck/no items, we pause the build
    private boolean stuckPaused = false;

    // Double-check state
    private boolean doubleCheckInProgress = false;
    private final Set<BlockPos> doubleCheckPos = new HashSet<>(); // pos allowed to be indexed when opened by automated double-check
    private final List<PendingCheck> pendingChecks = new ArrayList<>();

    private static class PendingCheck {
        Item item;
        int stacks;
        PendingCheck(Item item, int stacks) { this.item = item; this.stacks = stacks; }
    }

    // Anchor info for build consistency
    private BlockPos buildAnchorPos = null;
    private float buildAnchorYaw = 0f;
    private boolean buildAnchorSet = false;

    // Timers to reduce debug spam
    private long lastHomeLogMs = 0L;
    private static final long HOME_LOG_THROTTLE_MS = 5000L; // 5 sec between "player at home" logs

    // home arrival retries
    private int homeArrivalRetries = 0;
    private static final int HOME_ARRIVAL_MAX_RETRIES = 3;

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
    private BlockPos home = null;
    private int ticksStuck = 0;
    private BlockPos lastBlockPos = null;


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
        pendingChecks.clear();
        doubleCheckPos.clear();
        doubleCheckInProgress = false;
        stuckPaused = false;
        buildAnchorSet = false;
        buildCommand = "";

        loadLinkedStorages();
        loadHome();
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
            // Yell at the player to set a home point
            Logger.error("Please set a home point using the \"Set Home\" button!");
            homeIfStuck.set(false); // Disable the setting if no home point is set
            return;
        }

        // If we're currently performing fetches/double-checks, don't treat it as stuck
        if (!itemsToFetch.isEmpty() || doubleCheckInProgress) {
            ticksStuck = 0;
            lastBlockPos = mc.player.getBlockPos();
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

        // reduced/stabilized logging:
        if (debugMode.get() && ticksStuck > 0 && ticksStuck % 20 == 0) { // log roughly once per second while stuck (if debug)
            Logger.warn("Baritone may be stuck, ticks: %d", ticksStuck);
        }

        // 1 second = 20 ticks
        if (ticksStuck >= homeIfStuckTimeout.get() * 20) {
            Logger.error("Baritone is stuck, returning to home point...");

            ticksStuck = 0; // Reset the stuck counter
            lastBlockPos = mc.player.getBlockPos(); // Update the last block position

            forceStopAndGoHome(true); // Go home AND set stuckPaused = true
        } else {
            // occasionally inform player if they are at home (throttled)
            if (mc.player.getBlockPos().equals(home)) {
                long now = System.currentTimeMillis();
                if (now - lastHomeLogMs > HOME_LOG_THROTTLE_MS) {
                    lastHomeLogMs = now;
                    if (debugMode.get()) {
                        Logger.info("%sPlayer is at home point.", Formatting.GREEN);
                    }
                }
            }
        }
    }

    // verifyArrivalAtHome will check if player is precisely at 'home' BlockPos and retry a few times if off by 1 block
    private void verifyArrivalAtHome() {
        if (mc.player == null || home == null) return;

        if (mc.player.getBlockPos().equals(home)) {
            // arrived exactly at home
            homeArrivalRetries = 0;
            if (debugMode.get()) Logger.info("Verified arrival at home: X=%d Y=%d Z=%d", home.getX(), home.getY(), home.getZ());
            return;
        }

        // if not exactly at home, attempt a few retries to correct path
        if (homeArrivalRetries < HOME_ARRIVAL_MAX_RETRIES) {
            homeArrivalRetries++;
            if (debugMode.get()) Logger.warn("Home arrival off by %s. Retrying path to exact home (attempt %d/%d).", mc.player.getBlockPos().toShortString(), homeArrivalRetries, HOME_ARRIVAL_MAX_RETRIES);
            if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
            // schedule another verification later
            eventQueue.add(new Event(true, this::verifyArrivalAtHome));
        } else {
            // give up after retries but reset counter
            if (debugMode.get()) Logger.warn("Failed to perfectly arrive at home after %d retries. Current pos: %s, desired home: %s", HOME_ARRIVAL_MAX_RETRIES, mc.player.getBlockPos().toShortString(), home.toShortString());
            homeArrivalRetries = 0;
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String original = event.getMessage().getString();
        if (original == null || original.isEmpty()) return;

        String msgLower = original.toLowerCase().trim();

        // capture baritone messages then normalise
        if (msgLower.contains("[baritone]")) {
            int index = msgLower.indexOf("[baritone]");
            msgLower = msgLower.substring(index + 10).trim();
        }

        // parse missing items notice
        if (msgLower.matches("\\d+x block\\{minecraft:[a-z_]+}.*")) {
            String[] parts = msgLower.split(" ");

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

            if (item == null) {
                if (debugMode.get()) Logger.error("Item not found: %s%s", Formatting.WHITE, blockName);
                return;
            }

            // If player already has the item in inventory, don't mark as missing
            if (playerHasItem(item)) {
                if (debugMode.get()) Logger.info("Item %s present in player inventory; skipping fetch.", item.getName().getString());
                return;
            }

            // If we already scheduled a pending check for this item -> ignore duplicate
            if (pendingChecks.stream().anyMatch(pc -> pc.item == item)) {
                if (debugMode.get()) Logger.info("Pending double-check already scheduled for %s", item.getName().getString());
                return;
            }

            LinkedStorage linkedStorage = findItem(item);
            if (linkedStorage == null) {
                // start double-check sequence: don't immediately error — re-open linked storages once to confirm
                pendingChecks.add(new PendingCheck(item, stacks + extraStacks.get()));
                if (!doubleCheckInProgress) scheduleDoubleCheckSequence();
                else if (debugMode.get()) Logger.info("Double-check already in progress; added pending item %s", item.getName().getString());
                return;
            }

            itemsToFetch.add(new StorageItem(item, stacks + extraStacks.get(), linkedStorage));

            // If user previously was stuckPaused, clear the pause if they explicitly triggered a build flow
            // (We assume a new fetch request indicates intent to continue)
            stuckPaused = false;

            pathToLinkedStorage(item, linkedStorage);
            return;
        }

        // done building
        if (msgLower.contains("done building")) {
            if (debugMode.get()) {
                Logger.info("Baritone has finished building!");
            }
            
            // Clear command and anchor
            buildCommand = "";
            buildAnchorSet = false;

            if (disconnectOnDone.get()) {
                AutoReconnect autoReconnect = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
                if (autoReconnect != null && autoReconnect.isActive()) {
                    autoReconnect.toggle();
                }

                String prefix = Logger.PREFIX.getString();
                MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED) + "Baritone has finished building!");

                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) {
                    networkHandler.getConnection().disconnect(text);
                }
            }
            return;
        }

        // capture explicit build commands (player typed)
        String trimmed = original.trim();
        if (trimmed.startsWith("> ")) trimmed = trimmed.substring(2).trim(); // Handle commands sent by player
        String trimmedLower = trimmed.toLowerCase();

        // Check for Baritone commands sent via chat (e.g., #build)
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
            trimmedLower = trimmed.toLowerCase();
        }

        if (trimmedLower.startsWith("build") || trimmedLower.startsWith("litematica")) {
            // Store the full command including the prefix (if any) Baritone expects
            if (original.trim().startsWith("#")) {
                 buildCommand = original.trim().substring(1).trim(); // Store command without '#'
            } else if (original.trim().startsWith("> ")) {
                buildCommand = original.trim().substring(2).trim(); // Store command without '> '
            } else {
                 buildCommand = original.trim(); // Store as-is (e.g. if sent from Baritone's own input)
            }

            // capture anchor when player issues build
            if (mc.player != null) {
                buildAnchorPos = mc.player.getBlockPos();
                buildAnchorYaw = mc.player.getYaw();
                buildAnchorSet = true;
                if (debugMode.get()) Logger.info("Build anchor captured at %s yaw=%.1f. Command: %s", buildAnchorPos.toShortString(), buildAnchorYaw, buildCommand);
            }
            // clear paused flag because user explicitly requested build
            stuckPaused = false;
            ticksStuck = 0; // Reset stuck counter on new command
            lastBlockPos = mc.player.getBlockPos();
            
            if (debugMode.get()) Logger.info("Build command received, stuckPaused cleared.");
            return;
        }

        if (trimmedLower.startsWith("stop") || trimmedLower.startsWith("cancel")) {
            buildCommand = "";
            buildAnchorSet = false;
            eventQueue.clear();
            itemsToFetch.clear();
            pendingChecks.clear();
            doubleCheckPos.clear();
            doubleCheckInProgress = false;
            stuckPaused = false;

            Logger.info("Stop received. All tasks cleared.");
        }
    }

    // A centralized function to stop all activity and send Baritone home.
    private void forceStopAndGoHome(boolean pauseBuild) {
        eventQueue.clear();
        itemsToFetch.clear();
        pendingChecks.clear();
        doubleCheckPos.clear();
        doubleCheckInProgress = false;

        if (pauseBuild) {
            stuckPaused = true;
            if (debugMode.get()) Logger.info("Build paused due to stuck/error; re-issue build command manually when ready.");
        }

        if (baritone != null) baritone.getPathingBehavior().cancelEverything();

        if (homeIfStuck.get() && home != null) {
            homeArrivalRetries = 0;
            eventQueue.add(new Event(false, () -> {
                if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
            }));

            // verification
            eventQueue.add(new Event(true, this::verifyArrivalAtHome));
        }
    }

    // schedule double-check: iterate all linked storages and open them once (they will be allowed to be indexed)
    private void scheduleDoubleCheckSequence() {
        if (linkedStorages.isEmpty()) {
            // nothing to check -> finalize immediately
            finalizeDoubleCheck();
            return;
        }

        doubleCheckInProgress = true;
        if (debugMode.get()) Logger.info("Starting double-check of linked storages for pending items...");

        // Create a copy to iterate over, sorted by distance
        List<LinkedStorage> sortedStorages = new ArrayList<>(linkedStorages);
        if (mc.player != null) {
            sortedStorages.sort(Comparator.comparingDouble(ls -> ls.blockPos.getSquaredDistance(mc.player.getBlockPos())));
        }

        for (LinkedStorage ls : sortedStorages) {
            // mark this pos so when we open it during double-check, onInventory will treat it as indexable
            doubleCheckPos.add(ls.blockPos);

            // path then interact (events will run sequentially)
            eventQueue.add(new Event(true, () -> pathToPos(ls.blockPos)));
            eventQueue.add(new Event(true, () -> {
                // this performStorageInteractSafely will NOT mark as automated (because pos in doubleCheckPos)
                performStorageInteractSafely(ls);
            }));
            // add a small no-op delay event to allow onInventory to trigger after opening
            eventQueue.add(new Event(true, () -> {
                // no-op; just yield to allow inventory events to run
            }));
        }

        // schedule finalizer after all checks
        eventQueue.add(new Event(true, this::finalizeDoubleCheck));
    }

    // finalize double check: process pendingChecks -> either queue fetch or send home+error
    private void finalizeDoubleCheck() {
        if (pendingChecks.isEmpty()) {
            doubleCheckInProgress = false;
            doubleCheckPos.clear();
            if (debugMode.get()) Logger.info("Double-check complete: no pending items.");
            return;
        }

        // Keep copy then clear pending to avoid reentrancy
        List<PendingCheck> toProcess = new ArrayList<>(pendingChecks);
        pendingChecks.clear();

        boolean allItemsFound = true;

        for (PendingCheck pc : toProcess) {
            // check player inventory again
            if (playerHasItem(pc.item)) {
                if (debugMode.get()) Logger.info("Double-check: item %s found in player inventory.", pc.item.getName().getString());
                // no need to fetch
                continue;
            }

            LinkedStorage found = findItem(pc.item);
            if (found != null) {
                if (debugMode.get()) Logger.info("Double-check: item %s found in linked storage at %s, scheduling fetch.", pc.item.getName().getString(), found.blockPos.toShortString());
                itemsToFetch.add(new StorageItem(pc.item, pc.stacks, found));
                pathToLinkedStorage(pc.item, found);
            } else {
                // after double-check, still not found
                Logger.error("No linked storage contains the item: %s (confirmed after double-check)", pc.item.getName().getString());
                allItemsFound = false;

                // optional disconnect
                if (disconnectOnError.get()) {
                    AutoReconnect autoReconnect = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
                    if (autoReconnect != null && autoReconnect.isActive()) autoReconnect.toggle();
                    String prefix = Logger.PREFIX.getString();
                    MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED)
                            + String.format("No linked storage contains the item: %s\n", pc.item.getName().getString()));
                    ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                    if (networkHandler != null) networkHandler.getConnection().disconnect(text);
                }
                // don't break; log all missing items
            }
        }

        // If any item was definitively not found, stop everything and go home.
        if (!allItemsFound) {
            Logger.error("Failing build due to missing items after double-check. Returning home.");
            forceStopAndGoHome(true); // Go home AND set stuckPaused = true
        } else {
            // All items were found (or were already in inventory), fetch tasks are queued.
            if (debugMode.get()) Logger.info("Double-check finished: scheduled fetch for all found items.");
        }

        // done processing all pending checks
        doubleCheckInProgress = false;
        doubleCheckPos.clear();
    }

    // check player inventory quickly for item presence
    private boolean playerHasItem(Item item) {
        if (mc.player == null) return false;
        try {
            // iterate player's inventory using public API
            int size = mc.player.getInventory().size();
            for (int i = 0; i < size; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s != null && !s.isEmpty() && s.getItem() == item && s.getCount() > 0) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) { loadLinkedStorages(); }


    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.world == null) return;

        // Manual player interaction sets lastBlockInteractPos and clears automated marker.
        lastBlockInteractPos = event.result.getBlockPos();
        lastAutomatedInteractPos = null; // any manual interaction cancels automated marker

        if (!storageLinkMode.get()) {
            lastBlockInteractPos = null; // Don't track if link mode is off
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!isActive() || mc.player == null || mc.world == null) return;

        // 1) If we have items to fetch and we're not already fetching, start processing only the first item.
        if (!itemsToFetch.isEmpty() && !isFetching) {
            // pick the first item (FIFO)
            StorageItem itemToProcess = itemsToFetch.get(0);

            // Submit fetching task and mark fetching flag
            isFetching = true;
            MeteorExecutor.execute(() -> {
                try {
                    // moveSlots expects a ScreenHandler; pass currentScreenHandler (may be null -> handled inside)
                    moveSlots(itemToProcess, mc.player.currentScreenHandler);
                } finally {
                    // allow next fetch to run (after a tiny delay to avoid spamming)
                    try { Thread.sleep(120); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    isFetching = false;
                }
            });
        }

        // 2) Handle linking/updating storages
        if (mc.currentScreen == null) return; // no open screen => nothing to index
        if (lastBlockInteractPos == null) return; // no manual interact pending

        // If this open matches automated open and not flagged for double-check, skip updating
        if (lastAutomatedInteractPos != null && lastBlockInteractPos.equals(lastAutomatedInteractPos)
            && (System.currentTimeMillis() - lastAutomatedInteractMs) < 5000L // 5 sec window
            && !doubleCheckPos.contains(lastAutomatedInteractPos)) {
            // This was an automated interaction (we're fetching). Don't treat it as manual linking/updating.
            lastBlockInteractPos = null;
            return;
        }

        BlockEntity blockEntity = mc.world.getBlockEntity(lastBlockInteractPos);
        if (blockEntity == null) {
            lastBlockInteractPos = null;
            return;
        }
        
        // Grab the pos *before* clearing lastBlockInteractPos
        BlockPos posToUpdate = lastBlockInteractPos;
        boolean wasDoubleCheck = doubleCheckPos.contains(posToUpdate);

        // Use index-based iteration to safely remove while iterating
        boolean handled = false;
        for (int i = 0; i < linkedStorages.size(); i++) {
            LinkedStorage linkedStorage = linkedStorages.get(i);
            if (linkedStorage.blockPos.equals(posToUpdate)) {
                lastBlockInteractPos = null; // Consume the interact
                linkedStorages.remove(i);

                LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, posToUpdate);
                if (newStorage != null) {
                    linkedStorages.add(newStorage);
                    saveLinkedStorages();
                    if (!wasDoubleCheck) { // Don't spam log during double-checks
                        Logger.info("Updated linked storage at X=%s, Y=%s, Z=%s", posToUpdate.getX(), posToUpdate.getY(), posToUpdate.getZ());
                    } else if (debugMode.get()) {
                         Logger.info("Double-check: Re-indexed storage at %s", posToUpdate.toShortString());
                    }
                }
                handled = true;
                break;
            }
        }
        
        // If this was part of a double-check, remove the pos from doubleCheckPos after indexing
        if (wasDoubleCheck) {
            doubleCheckPos.remove(posToUpdate);
        }

        if (handled) return;

        // If not handled above and storageLinkMode on, add new linked storage
        if (!storageLinkMode.get()) { lastBlockInteractPos = null; return; }

        if (blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, posToUpdate);
            lastBlockInteractPos = null; // Consume the interact
            if (newStorage == null) return;
            if (newStorage.inventory.isEmpty()) {
                if (debugMode.get()) Logger.error("No items found in the linked storage!");
                return;
            }
            linkedStorages.add(newStorage);
            saveLinkedStorages();
            Logger.info("Linked Storage located at X=%s, Y=%s, Z=%s", posToUpdate.getX(), posToUpdate.getY(), posToUpdate.getZ());
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
                        // Use item ID as key, store count
                        inventoryJson.addProperty(itemId, stack.getCount());
                    }
                }
                storageJson.add("inventory", inventoryJson);
                linkedStoragesArray.add(storageJson);
            }

            payload.add("linked_storages", linkedStoragesArray);


            writer.append(payload.toString());
            writer.close();
        } catch (Exception e) {
            OmegawareAddons.LOG.warn("Failed to save Linked Storages to {}: {}", configFile.toPath(), e.getMessage());
        }
    }

    private void loadLinkedStorages() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");
        if (!configFile.exists()) {
            //noinspection LoggingSimilarMessage
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
                            try {
                                Item item = Registries.ITEM.get(Identifier.of(itemId)).asItem();
                                if (item != null) {
                                    int count = inventoryJson.get(itemId).getAsInt();
                                    linkedStorage.inventory.add(new ItemStack(item, count));
                                }
                            } catch (Exception itemEx) {
                                 OmegawareAddons.LOG.warn("Failed to load item from config: {}", itemId);
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

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();

            if (home != null) {
                payload.addProperty("home", home.asLong());
            }

            writer.append(payload.toString());
            writer.close();
        } catch (Exception e) {
            OmegawareAddons.LOG.warn("Failed to save home to {}: {}", configFile.toPath(), e.getMessage());
        }
    }

    private void loadHome() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");
        if (!configFile.exists()) {
            OmegawareAddons.LOG.warn("{} not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = GSON.fromJson(content, JsonObject.class);
            if (payload.has("home")) {
                home = BlockPos.fromLong(payload.get("home").getAsLong());
            }
        } catch (Exception e) {
            OmegawareAddons.LOG.error("Failed to load home from {}: {}", configFile.toPath(), e.getMessage());
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
        // Iterate only container slots (not player inventory)
        for (int i = 0; i < SlotUtils.indexToId(SlotUtils.MAIN_START) && i < screenHandler.slots.size(); i++) {
            ItemStack stack = screenHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                linkedStorage.inventory.add(stack.copy());
            }
        }

        return linkedStorage;
    }

    private void pathToPos(BlockPos blockPos) {
        if (mc.player == null || mc.world == null) return;

        if (debugMode.get()) Logger.info("%sNavigating to:%s X=%s, Y=%s, Z=%s", Formatting.GREEN, Formatting.WHITE, blockPos.getX(), blockPos.getY(), blockPos.getZ());

        if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos));
    }

    private LinkedStorage findItem(Item item) {
        // Sort the linked storages by distance to the player
        if (mc.player == null || mc.world == null) return null;
        linkedStorages.sort((a, b) -> {
            double distanceA = a.blockPos.getSquaredDistance(mc.player.getBlockPos());
            double distanceB = b.blockPos.getSquaredDistance(mc.player.getBlockPos());
            return Double.compare(distanceA, distanceB);
        });
        // Iterate through the linked storages and check if the item is present in any of them
        for (LinkedStorage linkedStorage : linkedStorages) {
            for (ItemStack stack : linkedStorage.inventory) {
                if (stack.getItem() == item) {
                    return linkedStorage;
                }
            }
        }

        return null;
    }

    private void pathToLinkedStorage(Item item, LinkedStorage linkedStorage) {
        if (mc.player == null || mc.interactionManager == null || linkedStorage == null) return;

        Logger.info("%sNavigating to storage containing:%s %s", Formatting.GREEN, Formatting.WHITE, item.getName().getString());

        // Reset open attempts per new path
        openAttempts = 0;

        // 1) Path to storage
        eventQueue.add(new Event(true, () -> pathToPos(linkedStorage.blockPos)));

        // 2) After arriving, attempt to interact (debounced)
        eventQueue.add(new Event(true, () -> {
            long now = System.currentTimeMillis();
            if (now - lastInteractMs < INTERACT_DEBOUNCE_MS) {
                if (debugMode.get()) Logger.warn("Interact debounced, skipping immediate interact (delta=%dms)", now - lastInteractMs);
                // requeue a safer interact attempt slightly later
                MeteorExecutor.execute(() -> {
                    try { Thread.sleep(INTERACT_DEBOUNCE_MS); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                    // schedule on next tick via event queue
                    eventQueue.add(new Event(true, () -> {
                        performStorageInteractSafely(linkedStorage);
                    }));
                });
                return;
            }

            performStorageInteractSafely(linkedStorage);
        }));
    }

    // helper that attempts to interact but enforces max attempts and updates timestamp
    private void performStorageInteractSafely(LinkedStorage linkedStorage) {
        if (mc.player == null || mc.interactionManager == null || linkedStorage == null) return;

        if (openAttempts >= MAX_OPEN_ATTEMPTS) {
            Logger.error("Too many open attempts for storage at X=%s, Y=%s, Z=%s. Aborting fetch.", linkedStorage.blockPos.getX(), linkedStorage.blockPos.getY(), linkedStorage.blockPos.getZ());
            // fail-safe: clear queue so player regains control
            eventQueue.clear();
            itemsToFetch.clear();
            openAttempts = 0;
            return;
        }

        // Close any open screens *first* to ensure that we can interact with the storage block
        if (mc.currentScreen != null) {
             MinecraftClient.getInstance().execute(() -> mc.setScreen(null));
             // Add a small delay event to let the screen close before interacting
             eventQueue.addFirst(new Event(false, () -> {
                // Wait one tick
             }));
             eventQueue.addFirst(new Event(false, () -> {
                performStorageInteractSafely(linkedStorage); // Re-run this function *after* the delay
             }));
             return; // Exit this attempt, let the re-queued one run
        }

        Vec3d hitPos = Vec3d.ofCenter(linkedStorage.blockPos);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, linkedStorage.blockPos, false);

        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); // Attempt to interact with the block
        lastInteractMs = System.currentTimeMillis();
        openAttempts++;

        boolean wasDoubleCheckPos = doubleCheckPos.contains(linkedStorage.blockPos);

        if (result.isAccepted()) { // If the interaction was successful, we can then make the player swing their hand
            mc.player.swingHand(Hand.MAIN_HAND);

            // If this open is part of a double-check, do NOT mark as automated (so onInventory will index it).
            if (!wasDoubleCheckPos) {
                // mark that we automated this open so onInventory won't treat it as manual linking/updating
                lastAutomatedInteractPos = linkedStorage.blockPos;
                lastAutomatedInteractMs = System.currentTimeMillis();
            } else {
                // allow indexing once - doubleCheckPos will be cleared by onInventory after indexing
                if (debugMode.get()) Logger.info("Performed double-check open on %s", linkedStorage.blockPos.toShortString());
            }

            if (debugMode.get()) Logger.info("Interact accepted (attempt #%d)", openAttempts);
        } else {
            if (debugMode.get()) Logger.warn("Interact not accepted (attempt #%d), result=%s", openAttempts, result.toString());
            // Re-queue the interact attempt if it failed but we have attempts left
             eventQueue.add(new Event(true, () -> {
                performStorageInteractSafely(linkedStorage);
             }));
        }
    }

    private void moveSlots(StorageItem storageItem, ScreenHandler handler) {
        if (mc.player == null) return;
        if (storageItem == null) return;

        boolean initial = true;
        int count = 0;
        List<Item> grabbedItems = new ArrayList<>();

        // If handler is null (no screen), nothing to move
        if (handler == null) {
            if (debugMode.get()) Logger.warn("moveSlots: no open screen handler to move items from.");
            // give up on this attempt - allow next attempt later
            return;
        }

        // Only check container slots (index 0 to main_start - 1)
        int maxIndex = Math.min(SlotUtils.indexToId(SlotUtils.MAIN_START), handler.slots.size());

        for (int i = 0; i < maxIndex; i++) {
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

            // Update linked storage inventory safely: find matching linkedStorage object and update
            LinkedStorage linkedStorage = storageItem.linkedStorage;
            if (linkedStorage != null) {
                for (int si = 0; si < linkedStorages.size(); si++) {
                    LinkedStorage ls = linkedStorages.get(si);
                    if (ls.blockPos.equals(linkedStorage.blockPos)) {
                        ItemStack stackInSlot = handler.getSlot(i).getStack();
                        // Find and remove *one* stack representation
                        for(int invIdx = 0; invIdx < ls.inventory.size(); invIdx++) {
                            if (ls.inventory.get(invIdx).getItem() == stackInSlot.getItem()) {
                                ls.inventory.remove(invIdx);
                                break;
                            }
                        }
                        break;
                    }
                }
                saveLinkedStorages();
            }

            count++;
            // perform shift-click to move item to player inventory
            InvUtils.shiftClick().slotId(i);

            // After shift-click, wait a bit and then verify player inventory increased for that item.
            // We'll wait up to a short timeout to allow server sync.
            final long perClickTimeoutMs = 1200L; // wait up to 1.2s for server to sync after shift-click
            long startWait = System.currentTimeMillis();
            boolean seen = false;
            while (System.currentTimeMillis() - startWait < perClickTimeoutMs) {
                try { Thread.sleep(60); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                if (getPlayerItemCount(storageItem.item) > 0) { // if player has at least 1 of this item now
                    seen = true;
                    break;
                }
            }
            if (debugMode.get()) {
                Logger.info("After shift-click: item=%s seenInInv=%s (attempt %d), playerCount=%d", storageItem.item.getName().getString(), seen, count, getPlayerItemCount(storageItem.item));
            }

            // small delay after shift-click to let server sync more (configurable)
            try { Thread.sleep(afterShiftClickDelay.get()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            if (count >= storageItem.stacks) {
                break;
            }
        }

        // Update the single storageItem's remaining stacks
        int remaining = Math.max(0, storageItem.stacks - count);

        // Remove the processed item from itemsToFetch (we always remove the instance we processed)
        itemsToFetch.removeIf(si -> {
            if (si.item == storageItem.item) {
                if (si.linkedStorage == null && storageItem.linkedStorage == null) return true;
                if (si.linkedStorage != null && storageItem.linkedStorage != null && si.linkedStorage.blockPos.equals(storageItem.linkedStorage.blockPos))
                    return true;
            }
            return false;
        });

        if (remaining > 0) {
            // re-add with updated remaining stacks
            StorageItem newItem = new StorageItem(storageItem.item, remaining, storageItem.linkedStorage);
            itemsToFetch.add(0, newItem); // push front to retry ASAP
        }

        // close the screen on the client thread so player regains control automatically
        if (mc != null) {
            MinecraftClient.getInstance().execute(() -> {
                try {
                    if (mc.currentScreen != null) mc.setScreen(null);
                } catch (Exception ignored) { /* ignore errors while trying to close */ }
            });
        }

        // clear automated-interact marker now that we've finished handling the screen
        lastAutomatedInteractPos = null;
        lastAutomatedInteractMs = 0L;

        // If there are still items to fetch, schedule pathing to next storage
        if (!itemsToFetch.isEmpty()) {
            // schedule path to next storage (first in list)
            StorageItem next = itemsToFetch.get(0);
            eventQueue.clear(); // clear stale events and requeue
            pathToLinkedStorage(next.item, next.linkedStorage);
            return;
        }

        // --- FETCH COMPLETE ---
        // If we reached here, fetch finished (no more itemsToFetch)
        
        // 1. Check if we should auto-resume at all
        if (!autoResumeOnFetchComplete.get()) {
             if (debugMode.get()) Logger.info("Fetch complete. Auto-resume is disabled.");
             return;
        }

        // 2. Check if build is paused (stuck/error)
        if (stuckPaused) {
            Logger.info("Fetch complete but build is paused due to earlier Home-if-stuck event. Re-issue build to continue.");
            return;
        }

        // 3. Check if we have a build command
        if (buildCommand.isEmpty()) {
             if (debugMode.get()) Logger.info("Fetch complete, but no build command is active. Waiting.");
             return;
        }
        
        // 4. Check Anchor (if required)
        if (requireAnchorMatchForResume.get() && buildAnchorSet) {
            if (mc.player == null) return; // Can't check
            double distSq = mc.player.getBlockPos().getSquaredDistance(buildAnchorPos);
            float yawDiff = Math.abs(mc.player.getYaw() - buildAnchorYaw) % 360;
            if (yawDiff > 180) yawDiff = 360 - yawDiff;

            double posTolerance = anchorPosTolerance.get();
            double yawTolerance = anchorYawTolerance.get();

            if (distSq > (posTolerance * posTolerance) || yawDiff > yawTolerance) {
                Logger.info("Fetch complete, but player anchor does not match. Pausing build.");
                Logger.info("Dist: %.2f (Max: %.1f), YawDiff: %.1f (Max: %.1f)", Math.sqrt(distSq), posTolerance, yawDiff, yawTolerance);
                Logger.info("Move back to %s (Yaw: %.1f) and type #build to resume.", buildAnchorPos.toShortString(), buildAnchorYaw);
                stuckPaused = true; // Use the pause flag to prevent resume
                return; // Do not execute resume
            } else {
                if (debugMode.get()) Logger.info("Anchor match confirmed. Resuming build.");
            }
        }


        // 5. --- All checks passed. Proceed with resume ---
        if (debugMode.get()) Logger.info("Fetch complete; verifying inventory before resuming build: %s", buildCommand);

        // For safety, wait up to a short total timeout for inventory to reflect items (in case of server lag).
        final long totalWaitMs = 2000L; // wait up to 2s
        long start = System.currentTimeMillis();
        boolean ok = false;
        while (System.currentTimeMillis() - start < totalWaitMs) {
            // Simpler check: ensure player's inventory contains any one item that was fetched in this run.
            boolean anyFound = false;
            for (Item it : grabbedItems) {
                if (getPlayerItemCount(it) > 0) { anyFound = true; break; }
            }
            if (anyFound || grabbedItems.isEmpty()) { // If no items were grabbed (e.g. already in inv), just proceed
                ok = true; 
                break; 
            }
            try { Thread.sleep(120); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }

        if (!ok) {
            if (debugMode.get()) Logger.warn("Inventory verification failed after fetch; items not present yet. Will still attempt resume but may retry.");
        } else {
            if (debugMode.get()) Logger.info("Inventory verification passed; player inventory updated.");
        }

        // Try resume sequence with stronger Baritone state reset + retries
        MeteorExecutor.execute(() -> {
            try {
                // longer initial delay to allow server+client inventory sync (configurable)
                Thread.sleep(afterFetchResumeDelay.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            final int maxAttempts = 6;
            boolean resumed = false;

            // **CRITICAL FIX:** Reset Baritone state *before* retrying command
            if (baritone != null) {
                 try {
                    baritone.getPathingBehavior().cancelEverything();
                    if (debugMode.get()) Logger.info("Issued cancelEverything() to Baritone before resume.");
                 } catch (Exception e) {
                    if (debugMode.get()) Logger.warn("cancelEverything() threw: %s", e.getMessage());
                 }
                 // Small pause to let Baritone clear state
                 try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            for (int attempt = 1; attempt <= maxAttempts && !resumed; attempt++) {
                if (baritone == null || stuckPaused) break; // Check stuckPaused again in case it changed

                // Debug snapshot: what we will use to resume
                if (debugMode.get()) {
                    Logger.info("Resume attempt %d: buildCommand='%s', itemsToFetchEmpty=%b, stuckPaused=%b", attempt, buildCommand, itemsToFetch.isEmpty(), stuckPaused);
                }

                // Execute build command on main thread
                final int att = attempt;
                MinecraftClient.getInstance().execute(() -> {
                    try {
                        if (baritone != null) {
                            baritone.getCommandManager().execute(buildCommand);
                            if (debugMode.get()) Logger.info("Executed build command on main thread (attempt %d).", att);
                        }
                    } catch (Exception e) {
                        if (debugMode.get()) Logger.warn("Exception executing build command: %s", e.getMessage());
                    }
                });

                // Wait a bit for Baritone to start pathing
                try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

                // Check if Baritone began pathing (if yes, consider resume successful)
                try {
                    if (baritone != null && baritone.getPathingBehavior().hasPath()) {
                        resumed = true;
                        if (debugMode.get()) Logger.info("Resume successful on attempt %d.", attempt);
                        break;
                    } else {
                        if (debugMode.get()) Logger.info("Resume attempt %d: Baritone has no path yet.", attempt);
                    }
                } catch (Exception e) {
                    if (debugMode.get()) Logger.warn("Error while checking path status: %s", e.getMessage());
                }

                // small backoff before next attempt
                try { Thread.sleep(300 + attempt * 100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            // final fallback: send as chat if still no path (some servers expect chat commands)
            if (!resumed && !stuckPaused && mc.player != null && mc.getNetworkHandler() != null) {
                try {
                    // Send as a chat command (starting with #)
                    String chatCmd = "#" + buildCommand.trim();

                    if (debugMode.get()) Logger.info("Fallback: sending build command as chat message: %s", chatCmd);
                    MinecraftClient.getInstance().execute(() -> {
                        try {
                            // Use sendChatCommand for '#' commands
                            mc.getNetworkHandler().sendChatCommand(buildCommand.trim());
                        } catch (Exception e) {
                            if (debugMode.get()) Logger.warn("Fallback chat send failed: %s", e.getMessage());
                        }
                    });

                } catch (Exception e) {
                    if (debugMode.get()) Logger.warn("Fallback chat step failed: %s", e.getMessage());
                }
            }
        });
    }

    // Helper: count how many of 'item' player currently has in their inventory
    private int getPlayerItemCount(Item item) {
        if (mc.player == null) return 0;
        int total = 0;
        try {
            int size = mc.player.getInventory().size();
            for (int i = 0; i < size; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s != null && !s.isEmpty() && s.getItem() == item) total += s.getCount();
            }
        } catch (Exception ignored) {}
        return total;
    }
}
