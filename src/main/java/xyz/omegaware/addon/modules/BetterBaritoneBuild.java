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
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- Settings Groups ---
    private final SettingGroup sgGeneral;
    private final SettingGroup sgRender;
    private final SettingGroup sgSafety;

    // --- General Settings ---
    private final Setting<Boolean> storageLinkMode;
    private final Setting<Integer> extraStacks;
    private final Setting<Boolean> disconnectOnDone;
    private final Setting<Boolean> disconnectOnError;
    private final Setting<Boolean> debugMode;

    // --- Safety & Resume Settings ---
    private final Setting<Boolean> homeIfStuck;
    private final Setting<Integer> homeIfStuckTimeout;
    private final Setting<Boolean> autoResumeOnFetchComplete;
    private final Setting<Boolean> requireAnchorMatchForResume;
    private final Setting<Double> anchorPosTolerance;
    private final Setting<Double> anchorYawTolerance;
    private final Setting<Integer> afterShiftClickDelay;
    private final Setting<Integer> afterFetchResumeDelay;
    private final Setting<Integer> guiWaitTimeout;

    // --- Render Settings ---
    private final Setting<Boolean> highlightLinkedStorages;
    private final Setting<Boolean> invertHighlight;
    private final Setting<ShapeMode> shapeMode;
    private final Setting<SettingColor> sideColor;
    private final Setting<SettingColor> lineColor;
    
    // Deprecated, unused setting
    private final Setting<Boolean> ignoreY;

    // --- Baritone ---
    IBaritone baritone = null;

    // --- Concurrency & Interaction Flags ---
    private long lastInteractMs = 0L;
    private int openAttempts = 0;
    private final long INTERACT_DEBOUNCE_MS = 800L;
    private final int MAX_OPEN_ATTEMPTS = 6;
    private BlockPos lastAutomatedInteractPos = null;
    private long lastAutomatedInteractMs = 0L;
    private BlockPos lastBlockInteractPos = null; // last pos *player* manually interacted with

    // --- (v8) State Machine Flags ---
    private boolean buildIsActive = false; // TRUE if a #build command is running
    private boolean stuckPaused = false;   // TRUE if anchor fails, or stuck timer hits, or *unexpected* pause
    
    // --- (v8) Double-Check State ---
    private final List<Item> itemsToDoubleCheck = new ArrayList<>();
    private boolean isDoubleCheckQueued = false; // Flag to prevent queueing it multiple times
    
    // --- Anchor & Home ---
    private BlockPos buildAnchorPos = null;
    private float buildAnchorYaw = 0f;
    private BlockPos home = null;
    
    // --- Timers ---
    private long lastHomeLogMs = 0L;
    private static final long HOME_LOG_THROTTLE_MS = 5000L;
    private int homeArrivalRetries = 0;
    private static final int HOME_ARRIVAL_MAX_RETRIES = 3;
    private int ticksStuck = 0;
    private BlockPos lastBlockPos = null;

    // --- Linked Storage ---
    private static class LinkedStorage {
        public BlockPos blockPos;
        List<ItemStack> inventory;
        public LinkedStorage() { this.blockPos = BlockPos.ORIGIN; this.inventory = new ArrayList<>(); }
        public LinkedStorage(BlockPos blockPos, List<ItemStack> inventory) { this.blockPos = blockPos; this.inventory = inventory; }
    }
    private final List<LinkedStorage> linkedStorages = new ArrayList<>();

    // --- (v8) Event Queue (The new "Brain") ---
    private static class Event {
        public boolean bWaitOnPath; // Does this event wait for Baritone to stop pathing?
        public Runnable callback;   // The code to run
        public String description;  // For debugging

        public Event(boolean bWaitOnPath, Runnable callback, String description) {
            this.bWaitOnPath = bWaitOnPath;
            this.callback = callback;
            this.description = description;
        }
    }
    private final java.util.LinkedList<Event> eventQueue = new java.util.LinkedList<>();


    // (MODIFIED) All settings are now initialized in the constructor
    public BetterBaritoneBuild() {
        super(OmegawareAddons.CATEGORY, "better-baritone-build", "Enable this module to enhance Baritone's building capabilities with linked storage and item fetching features.");

        // --- Initialize all Settings Groups and Settings *inside* the constructor ---
        sgGeneral = this.settings.getDefaultGroup();
        sgRender = this.settings.createGroup("Render");
        sgSafety = this.settings.createGroup("Safety & Resume");

        // --- General Settings ---
        storageLinkMode = sgGeneral.add(new BoolSetting.Builder()
            .name("storage-link-mode")
            .description("If enabled, all storage blocks you interact with will be linked to this module.")
            .defaultValue(false)
            .build()
        );

        extraStacks = sgGeneral.add(new IntSetting.Builder()
            .name("extra-stacks")
            .description("The number of extra stacks to fetch from the linked storage.")
            .defaultValue(0)
            .min(0)
            .sliderRange(0, 10)
            .build()
        );

        disconnectOnDone = sgGeneral.add(new BoolSetting.Builder()
            .name("disconnect-on-done")
            .description("If enabled, the module will disconnect you from the server when it is done.")
            .defaultValue(false)
            .build()
        );

        disconnectOnError = sgGeneral.add(new BoolSetting.Builder()
            .name("disconnect-on-error")
            .description("If enabled, the module will disconnect you from the server when it encounters an error.")
            .defaultValue(false)
            .build()
        );

        debugMode = sgGeneral.add(new BoolSetting.Builder()
            .name("debug-mode")
            .description("If enabled, the module will print debug information to the console.")
            .defaultValue(false)
            .build()
        );

        // --- Safety & Resume Settings ---
        homeIfStuck = sgSafety.add(new BoolSetting.Builder()
            .name("home-if-stuck")
            .description("If enabled, Baritone will return set home point if it gets stuck while building or fails double-check.")
            .defaultValue(false)
            .build()
        );

        homeIfStuckTimeout = sgSafety.add(new IntSetting.Builder()
            .name("home-if-stuck-timeout")
            .description("The timeout in seconds before Baritone returns to the home point if it gets stuck.")
            .defaultValue(15)
            .min(1)
            .sliderRange(5, 120)
            .visible(homeIfStuck::get)
            .build()
        );

        autoResumeOnFetchComplete = sgSafety.add(new BoolSetting.Builder()
            .name("auto-resume-on-fetch")
            .description("Automatically resume building after all items have been fetched.")
            .defaultValue(true)
            .build()
        );

        requireAnchorMatchForResume = sgSafety.add(new BoolSetting.Builder()
            .name("require-anchor-match")
            .description("If auto-resume is on, only resume if player position and yaw match the initial build command anchor.")
            .defaultValue(true)
            .visible(autoResumeOnFetchComplete::get)
            .build()
        );

        anchorPosTolerance = sgSafety.add(new DoubleSetting.Builder()
            .name("anchor-pos-tolerance")
            .description("Maximum distance (blocks) from anchor position allowed for auto-resume.")
            .defaultValue(2.0)
            .min(0.5)
            .sliderRange(0.5, 10.0)
            .visible(() -> autoResumeOnFetchComplete.get() && requireAnchorMatchForResume.get())
            .build()
        );

        anchorYawTolerance = sgSafety.add(new DoubleSetting.Builder()
            .name("anchor-yaw-tolerance")
            .description("Maximum difference (degrees) from anchor yaw allowed for auto-resume.")
            .defaultValue(15.0)
            .min(1.0)
            .sliderRange(1.0, 90.0)
            .visible(() -> autoResumeOnFetchComplete.get() && requireAnchorMatchForResume.get())
            .build()
        );

        afterShiftClickDelay = sgSafety.add(new IntSetting.Builder()
            .name("after-shift-click-delay")
            .description("Delay (ms) after shift-clicking an item to allow server inventory sync. (Higher = safer but slower).")
            .defaultValue(350)
            .min(50)
            .sliderRange(50, 1000)
            .build()
        );

        afterFetchResumeDelay = sgSafety.add(new IntSetting.Builder()
            .name("after-fetch-resume-delay")
            .description("Delay (ms) after closing GUI and before resuming build, allowing full sync. (Higher = safer).")
            .defaultValue(400)
            .min(100)
            .sliderRange(100, 2000)
            .build()
        );
        
        guiWaitTimeout = sgSafety.add(new IntSetting.Builder()
            .name("gui-wait-timeout")
            .description("Max time (ms) to wait for a GUI to open before failing the task.")
            .defaultValue(2000)
            .min(500)
            .sliderRange(500, 5000)
            .build()
        );

        // --- Render Settings ---
        highlightLinkedStorages = sgRender.add(new BoolSetting.Builder()
            .name("highlight-linked-storages")
            .description("If enabled, linked storages will be highlighted with a box.")
            .defaultValue(true)
            .build()
        );

        invertHighlight = sgRender.add(new BoolSetting.Builder()
            .name("invert-highlight")
            .description("If enabled, the highlight will be inverted (i.e. highlighted blocks will not be highlighted).")
            .defaultValue(false)
            .visible(highlightLinkedStorages::get)
            .build()
        );

        shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
                .name("shape-mode")
                .description("How the shapes are rendered.")
                .defaultValue(ShapeMode.Both)
                .visible(this::isActive)
                .build()
        );

        sideColor = sgRender.add(new ColorSetting.Builder()
                .name("side-color")
                .description("The side color of the rendering.")
                .defaultValue(new SettingColor(0, 255, 255, 40))
                .visible(() -> shapeMode.get().sides())
                .build()
        );

        lineColor = sgRender.add(new ColorSetting.Builder()
                .name("line-color")
                .description("The line color of the rendering.")
                .defaultValue(new SettingColor(0, 255, 255, 255))
                .visible(() -> shapeMode.get().lines())
                .build()
        );
        
        // Deprecated, unused setting
        ignoreY = sgGeneral.add(new BoolSetting.Builder()
            .name("baritone-ignore-y")
            .description("DEPRECATED: This setting is no longer used.")
            .defaultValue(false)
            .visible(() -> false) // Hide from GUI
            .build()
        );
    } // --- End of Constructor ---


    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            Logger.error("Baritone is not available!");
            toggle();
            return;
        }
        baritone = BaritoneAPI.getProvider().getBaritoneForMinecraft(MinecraftClient.getInstance());
        forceStopAndGoHome(false); // Clear everything
        buildIsActive = false;
        stuckPaused = false;
        buildCommand = "";
        buildAnchorPos = null;
        loadLinkedStorages();
        loadHome();
    }
    
    // Central reset function
    private void resetAllTasks() {
        eventQueue.clear();
        itemsToDoubleCheck.clear();
        isDoubleCheckQueued = false;
        if (baritone != null) baritone.getPathingBehavior().cancelEverything();
        // Note: Does NOT reset buildIsActive or stuckPaused
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
        // (v8) This is the "dumb" event queue processor. It just runs the queue.
        if (!isActive() || eventQueue.isEmpty()) return;
        
        Event queuedEvent = eventQueue.getFirst();

        // Don't run queue if pathing, *unless* it's a non-pathing event
        if (queuedEvent.bWaitOnPath && (baritone == null || baritone.getPathingBehavior().hasPath())) {
            return;
        }

        if (debugMode.get()) {
            if (queuedEvent.description.contains("Wait")) {
                if(ticksStuck % 20 == 0) Logger.info("Queue: %d left. Exec: %s", eventQueue.size(), queuedEvent.description);
            } else {
                Logger.info("Queue: %d left. Exec: %s", eventQueue.size(), queuedEvent.description);
            }
            ticksStuck = 0; // Reset stuck counter if queue is active
        }

        try {
            queuedEvent.callback.run();
        } catch (Exception e) {
            if (debugMode.get()) Logger.warn("Exception while running queued event: %s", e.getMessage());
            e.printStackTrace(); // Print stack trace for debugging
        }

        updateLinkedStorages();

        // Only remove if the callback didn't re-queue itself (which waitForGui does)
        if (!eventQueue.isEmpty() && eventQueue.getFirst() == queuedEvent) {
            eventQueue.removeFirst();
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        // (v8) This handler is *only* for the stuck timer
        if (!isActive() || mc.world == null || mc.player == null) return;
        
        // Stuck Logic: Only runs if all these are true:
        // 1. Setting is on
        // 2. Home is set
        // 3. A build is active
        // 4. We are NOT paused by anchor/error
        // 5. The event queue is EMPTY (we are not fetching/checking)
        if (homeIfStuck.get() && home != null && buildIsActive && !stuckPaused && eventQueue.isEmpty()) {
            
            if (lastBlockPos == null) {
                lastBlockPos = mc.player.getBlockPos();
            }

            if (lastBlockPos.equals(mc.player.getBlockPos())) {
                ticksStuck++;
            } else {
                ticksStuck = 0;
                lastBlockPos = mc.player.getBlockPos();
            }

            if (debugMode.get() && ticksStuck > 0 && ticksStuck % 20 == 0) {
                Logger.warn("Baritone may be stuck (in build mode), ticks: %d", ticksStuck);
            }

            // Timeout reached
            if (ticksStuck >= homeIfStuckTimeout.get() * 20) {
                Logger.error("Baritone is stuck, returning to home point...");
                ticksStuck = 0;
                lastBlockPos = mc.player.getBlockPos();
                forceStopAndGoHome(true); // Go home AND set stuckPaused = true
            }
        } else if (eventQueue.isEmpty() && !buildIsActive && home != null && mc.player.getBlockPos().equals(home)) {
            // Idle at home log
            long now = System.currentTimeMillis();
            if (now - lastHomeLogMs > HOME_LOG_THROTTLE_MS) {
                lastHomeLogMs = now;
                if (debugMode.get()) {
                    Logger.info("%sPlayer is idle at home point.", Formatting.GREEN);
                }
            }
        } else if (!eventQueue.isEmpty()) {
            // If queue is active, we are not stuck
            ticksStuck = 0;
            if (mc.player != null) lastBlockPos = mc.player.getBlockPos();
        }
    }

    // verifyArrivalAtHome will check if player is precisely at 'home' BlockPos and retry a few times if off by 1 block
    private void verifyArrivalAtHome() {
        if (mc.player == null || home == null) return;
        
        if (mc.player.getBlockPos().equals(home)) {
            homeArrivalRetries = 0;
            if (debugMode.get()) Logger.info("Verified arrival at home: X=%d Y=%d Z=%d", home.getX(), home.getY(), home.getZ());
            return; // Stop queueing
        }

        if (homeArrivalRetries < HOME_ARRIVAL_MAX_RETRIES) {
            homeArrivalRetries++;
            if (debugMode.get()) Logger.warn("Home arrival off by %s. Retrying path to exact home (attempt %d/%d).", mc.player.getBlockPos().toShortString(), homeArrivalRetries, HOME_ARRIVAL_MAX_RETRIES);
            if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
            eventQueue.add(new Event(true, this::verifyArrivalAtHome, "Verify Home Arrival"));
        } else {
            if (debugMode.get()) Logger.warn("Failed to perfectly arrive at home after %d retries. Current pos: %s, desired home: %s", HOME_ARRIVAL_MAX_RETRIES, mc.player.getBlockPos().toShortString(), home.toShortString());
            homeArrivalRetries = 0;
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String original = event.getMessage().getString();
        if (original == null || original.isEmpty()) return;

        // --- Get normalized Baritone message (if any) ---
        String msgLower = original.toLowerCase().trim();
        String baritoneMsg = null;
        if (msgLower.contains("[baritone]")) {
            int index = msgLower.indexOf("[baritone]");
            baritoneMsg = msgLower.substring(index + 10).trim();
        }

        // --- Handle Player-Sent Commands First ---
        String trimmed = original.trim();
        String commandPrefix = null;
        
        if (trimmed.startsWith("> ")) {
             trimmed = trimmed.substring(2).trim(); // Handle commands sent by player
             commandPrefix = ">";
        } else if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim(); // Handle Baritone commands sent via chat
            commandPrefix = "#";
        }
        
        String trimmedLower = trimmed.toLowerCase();
        
        // --- 1. Handle Build Command ---
        if (commandPrefix != null && (trimmedLower.startsWith("build") || trimmedLower.startsWith("litematica"))) {
            buildCommand = trimmed; 

            if (mc.player != null) {
                buildAnchorPos = mc.player.getBlockPos();
                buildAnchorYaw = mc.player.getYaw();
                if (debugMode.get()) Logger.info("Build anchor captured at %s yaw=%.1f. Command: %s", buildAnchorPos.toShortString(), buildAnchorYaw, buildCommand);
            }
            
            // (v8) This is a new job. Reset everything.
            resetAllTasks();
            buildIsActive = true;
            stuckPaused = false;
            ticksStuck = 0;
            lastBlockPos = (mc.player != null) ? mc.player.getBlockPos() : null;
            
            // Queue the initial command execution
            eventQueue.add(new Event(false, this::executeInitialBuildCommand, "Initial Build Command"));
            
            if (debugMode.get()) Logger.info("Build command received. Queued initial command execution.");
            return;
        }
        
        // --- (v8 NEW) Handle Resume Command ---
        if (commandPrefix != null && trimmedLower.equals("resume")) {
            if (!buildIsActive) {
                 if (debugMode.get()) Logger.info("Ignoring #resume, no build is active.");
                 return;
            }
            if (!stuckPaused) {
                if (debugMode.get()) Logger.info("Ignoring #resume, build is not paused.");
                return;
            }
            
            Logger.info("Manual #resume received. Clearing pause state and queueing resume logic.");
            stuckPaused = false;
            ticksStuck = 0;
            lastBlockPos = (mc.player != null) ? mc.player.getBlockPos() : null;
            
            // Queue the resume logic. This will re-run the build command.
            eventQueue.add(new Event(false, this::executeResumeLogic, "Manual Resume"));
            return;
        }

        // --- 2. Handle Stop/Cancel Command ---
        if (commandPrefix != null && (trimmedLower.equals("stop") || trimmedLower.equals("cancel"))) {
            buildIsActive = false;
            stuckPaused = false;
            buildCommand = "";
            buildAnchorPos = null;
            resetAllTasks(); // Clear everything
            Logger.info("Stop/Cancel received. All tasks cleared.");
            return;
        }
        
        // --- 3. Handle Baritone-Sent Messages ---
        if (baritoneMsg == null) return; // Not a baritone message, ignore

        // --- 3a. Handle Missing Items ---
        if (baritoneMsg.matches("\\d+x block\\{minecraft:[a-z_]+}.*")) {
            // (v8) Only process if a build is active. *DO NOT* check stuckPaused.
            if (!buildIsActive) return; 

            String[] parts = baritoneMsg.split(" ");
            String blockCount = parts[0].replace("x", "").trim();
            int count = Integer.parseInt(blockCount);
            int stacks = (int) Math.ceil(count / 64.0);
            String blockMessage = parts[1];
            String blockName = blockMessage.substring(blockMessage.indexOf(':') + 1);
            int endIndex = blockName.indexOf('}');
            if (endIndex != -1) blockName = blockName.substring(0, endIndex);

            Identifier identifier = Identifier.of(blockName);
            Item item = Registries.ITEM.get(identifier).asItem();
            if (item == null) {
                if (debugMode.get()) Logger.error("Item not found: %s%s", Formatting.WHITE, blockName);
                return;
            }

            if (playerHasItem(item)) {
                if (debugMode.get()) Logger.info("Item %s present in player inventory; skipping fetch.", item.getName().getString());
                return;
            }
            
            // Check if this item is *already* in the queue or double-check list
            String itemDesc = "Fetch " + item.getName().getString();
            String checkDesc = "DoubleCheck " + item.getName().getString();
            for (Event e : eventQueue) {
                if(e.description.startsWith(itemDesc) || e.description.startsWith(checkDesc)) {
                    if (debugMode.get()) Logger.info("Task for %s is already in queue.", item.getName().getString());
                    return;
                }
            }
            if (itemsToDoubleCheck.contains(item)) {
                 if (debugMode.get()) Logger.info("Task for %s is already in double-check list.", item.getName().getString());
                 return;
            }

            // Not found in queue, find in storage
            LinkedStorage linkedStorage = findItem(item);
            if (linkedStorage == null) {
                queueDoubleCheckTask(item, stacks + extraStacks.get());
            } else {
                queueFetchTask(item, stacks + extraStacks.get(), linkedStorage);
            }
            return;
        }

        // --- 3b. Handle "Done Building" ---
        if (baritoneMsg.contains("done building")) {
            if (debugMode.get()) Logger.info("Baritone has finished building!");
            buildIsActive = false;
            buildCommand = "";
            buildAnchorPos = null;
            resetAllTasks(); // Clear queues

            if (disconnectOnDone.get()) {
                AutoReconnect autoReconnect = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
                if (autoReconnect != null && autoReconnect.isActive()) autoReconnect.toggle();
                String prefix = Logger.PREFIX.getString();
                MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED) + "Baritone has finished building!");
                ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                if (networkHandler != null) networkHandler.getConnection().disconnect(text);
            }
            return;
        }
        
        // --- 3c. (v8) Handle Baritone's Own Pause ---
        if (baritoneMsg.contains("unable to do it. pausing.")) {
            if (!buildIsActive) return; // Not our build
            
            // (v8) CRITICAL: If the event queue is *not empty*, it means we are
            // *expecting* this message because we are fetching. Ignore it.
            if (!eventQueue.isEmpty()) {
                if (debugMode.get()) Logger.info("Baritone paused, but we are fetching. Ignoring.");
                return;
            }
            
            // If we are here, build is active AND queue is empty.
            // This is an *unexpected* pause.
            if (debugMode.get()) Logger.warn("Baritone has paused unexpectedly. Setting stuckPaused=true. User must manually resume.");
            stuckPaused = true;
            resetAllTasks(); // Clear queues, player must take over
            return;
        }
    }

    // A centralized function to stop all activity and send Baritone home.
    private void forceStopAndGoHome(boolean pauseBuild) {
        resetAllTasks(); // Clear all jobs
        buildIsActive = false; // The build job is over
        buildCommand = "";
        buildAnchorPos = null;

        if (pauseBuild) {
            stuckPaused = true;
            if (debugMode.get()) Logger.info("Build paused due to stuck/error; re-issue build command manually when ready.");
        }
        
        if (homeIfStuck.get() && home != null) {
            homeArrivalRetries = 0;
            eventQueue.add(new Event(false, () -> {
                if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
            }, "Path to Home"));
            eventQueue.add(new Event(true, this::verifyArrivalAtHome, "Verify Home Arrival"));
        }
    }
    
    // --- (v8) Event Queueing Functions ---
    
    // (v8) This is the *initial* command execution
    private void executeInitialBuildCommand() {
        if (!buildIsActive || stuckPaused || buildCommand.isEmpty() || baritone == null) {
            return;
        }
        
        if (debugMode.get()) Logger.info("Executing initial build command: %s", buildCommand);
        
        baritone.getPathingBehavior().cancelEverything();
        
        // We MUST execute on main thread
        MinecraftClient.getInstance().execute(() -> {
            try {
                if (baritone != null) {
                    baritone.getCommandManager().execute(buildCommand);
                }
            } catch (Exception e) {
                if (debugMode.get()) Logger.warn("Exception executing initial build command: %s", e.getMessage());
            }
        });
        // Now we wait for Baritone to send "missing item" messages
    }
    
    private void queueFetchTask(Item item, int stacks, LinkedStorage storage) {
        if (debugMode.get()) Logger.info("Queueing fetch task for %d stacks of %s", stacks, item.getName().getString());
        String itemDesc = "Fetch " + item.getName().getString();
        
        eventQueue.add(new Event(true, () -> pathToPos(storage.blockPos), "Path to " + itemDesc));
        eventQueue.add(new Event(true, () -> performStorageInteractSafely(storage), "Interact for " + itemDesc));
        eventQueue.add(new Event(false, () -> waitForGuiOpen(storage.blockPos, 0), "Wait GUI for " + itemDesc));
        eventQueue.add(new Event(false, () -> moveSlots(item, stacks), "MoveSlots for " + itemDesc));
        eventQueue.add(new Event(false, () -> {
            if(mc.currentScreen != null) mc.setScreen(null);
        }, "Close GUI for " + itemDesc));
        eventQueue.add(new Event(false, this::checkQueueAndResume, "CheckResume after " + itemDesc));
    }
    
    private void queueDoubleCheckTask(Item item, int stacks) {
        itemsToDoubleCheck.add(item); // Add to list of items we need to find
        
        // If a double-check is already queued, just add the item to the list and return.
        if (isDoubleCheckQueued) {
            if(debugMode.get()) Logger.info("Double-check already queued. Adding %s to list.", item.getName().getString());
            return;
        }

        if(debugMode.get()) Logger.info("Queueing double-check task for %s", item.getName().getString());
        isDoubleCheckQueued = true;
        
        eventQueue.add(new Event(false, () -> {
            if(debugMode.get()) Logger.info("Starting double-check sequence...");
        }, "Start DoubleCheck"));

        List<LinkedStorage> sortedStorages = new ArrayList<>(linkedStorages);
        if (mc.player != null) {
            sortedStorages.sort(Comparator.comparingDouble(ls -> ls.blockPos.getSquaredDistance(mc.player.getBlockPos())));
        }

        for (LinkedStorage ls : sortedStorages) {
            eventQueue.add(new Event(true, () -> pathToPos(ls.blockPos), "DC Path to " + ls.blockPos.toShortString()));
            eventQueue.add(new Event(true, () -> performStorageInteractSafely(ls), "DC Interact " + ls.blockPos.toShortString()));
            eventQueue.add(new Event(false, () -> waitForGuiOpen(ls.blockPos, 0), "DC Wait GUI " + ls.blockPos.toShortString()));
            eventQueue.add(new Event(false, () -> indexOpenedStorage(ls.blockPos), "DC Index " + ls.blockPos.toShortString()));
            eventQueue.add(new Event(false, () -> {
                if(mc.currentScreen != null) mc.setScreen(null);
            }, "DC Close GUI " + ls.blockPos.toShortString()));
        }

        eventQueue.add(new Event(false, this::finalizeDoubleCheck, "Finalize DoubleCheck"));
        eventQueue.add(new Event(false, this::checkQueueAndResume, "CheckResume after DoubleCheck"));
    }
    
    // (v8) Event callback to wait for GUI
    private void waitForGuiOpen(BlockPos pos, int ticksWaited) {
        if (mc.currentScreen instanceof HandledScreen) {
            if (debugMode.get()) Logger.info("GUI is open. Proceeding.");
            return; // GUI is open, continue queue
        }
        
        if (ticksWaited > (guiWaitTimeout.get() / 50)) { // 50ms per tick
             Logger.error("Timed out waiting for GUI at %s. Aborting task.", pos.toShortString());
             forceStopAndGoHome(true);
             return;
        }

        // GUI not open yet, re-queue this check for the next tick
        eventQueue.addFirst(new Event(false, () -> waitForGuiOpen(pos, ticksWaited + 1), "Wait GUI for " + pos.toShortString()));
    }
    
    // (v8) Event callback to index storage during double-check
    private void indexOpenedStorage(BlockPos pos) {
        if (mc.player == null || !(mc.currentScreen instanceof HandledScreen)) {
            if (debugMode.get()) Logger.warn("Attempted to index storage at %s but no GUI was open.", pos.toShortString());
            return;
        }
        
        ScreenHandler handler = mc.player.currentScreenHandler;
        LinkedStorage newStorage = indexStorage(handler, pos);
        
        linkedStorages.removeIf(ls -> ls.blockPos.equals(pos));
        
        if (newStorage != null && !newStorage.inventory.isEmpty()) {
            linkedStorages.add(newStorage);
            saveLinkedStorages();
            if (debugMode.get()) Logger.info("Double-check: Re-indexed storage at %s", pos.toShortString());
        } else if (debugMode.get()) {
            Logger.info("Double-check: Storage at %s was empty.", pos.toShortString());
        }
    }

    // (v8) Event callback to check if we should resume
    private void checkQueueAndResume() {
        if (!eventQueue.isEmpty()) {
            if (debugMode.get()) Logger.info("Task finished, but queue is not empty (%d tasks left). Not resuming yet.", eventQueue.size());
            return;
        }
        
        if (!buildIsActive || stuckPaused) {
            if (debugMode.get()) Logger.info("Event queue empty, but build is paused or inactive. Not resuming.");
            return;
        }
        
        if(debugMode.get()) Logger.info("Event queue is empty. Calling resume logic.");
        executeResumeLogic();
    }

    // (v8) Event callback to finalize double-check
    private void finalizeDoubleCheck() {
        if(debugMode.get()) Logger.info("Finalizing double-check for %d items...", itemsToDoubleCheck.size());
        isDoubleCheckQueued = false; // The check is complete
        boolean foundMissingItems = false;
        
        List<Item> itemsToRemove = new ArrayList<>();

        for (Item item : itemsToDoubleCheck) {
            if (playerHasItem(item)) {
                if (debugMode.get()) Logger.info("Double-check: item %s found in player inventory.", item.getName().getString());
                itemsToRemove.add(item);
                continue;
            }
            
            LinkedStorage found = findItem(item);
            if (found != null) {
                if (debugMode.get()) Logger.info("Double-check: item %s found in linked storage at %s, scheduling fetch.", item.getName().getString(), found.blockPos.toShortString());
                queueFetchTaskAtFront(item, 1, found); // Assume 1 stack, Baritone will ask for more if needed
                itemsToRemove.add(item);
            } else {
                Logger.error("No linked storage contains the item: %s (confirmed after double-check)", item.getName().getString());
                foundMissingItems = true;
            }
        }

        itemsToDoubleCheck.removeAll(itemsToRemove);

        if (foundMissingItems) {
            Logger.error("Failing build due to missing items after double-check. Returning home.");
            forceStopAndGoHome(true); // Go home AND set stuckPaused = true
        }
    }
    
    // (v8) Helper to add fetch tasks to the *front* of the queue
    private void queueFetchTaskAtFront(Item item, int stacks, LinkedStorage storage) {
        if (debugMode.get()) Logger.info("Queueing priority fetch task for %d stacks of %s", stacks, item.getName().getString());
        String itemDesc = "Fetch " + item.getName().getString();
        
        // Add tasks in reverse order to the front
        eventQueue.addFirst(new Event(false, this::checkQueueAndResume, "CheckResume after " + itemDesc));
        eventQueue.addFirst(new Event(false, () -> {
            if(mc.currentScreen != null) mc.setScreen(null);
        }, "Close GUI for " + itemDesc));
        eventQueue.addFirst(new Event(false, () -> moveSlots(item, stacks), "MoveSlots for " + itemDesc));
        eventQueue.addFirst(new Event(false, () -> waitForGuiOpen(storage.blockPos, 0), "Wait GUI for " + itemDesc));
        eventQueue.addFirst(new Event(true, () -> performStorageInteractSafely(storage), "Interact for " + itemDesc));
        eventQueue.addFirst(new Event(true, () -> pathToPos(storage.blockPos), "Path to " + itemDesc));
    }


    // check player inventory quickly for item presence
    private boolean playerHasItem(Item item) {
        if (mc.player == null) return false;
        try {
            int size = mc.player.getInventory().size();
            for (int i = 0; i < size; i++) {
                ItemStack s = mc.player.getInventory().getStack(i);
                if (s != null && !s.isEmpty() && s.getItem() == item && s.getCount() > 0) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) { loadLinkedStorages(); }


    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.world == null) return;
        if (!storageLinkMode.get()) {
            lastBlockInteractPos = null; // Don't track if link mode is off
            return;
        }
        lastBlockInteractPos = event.result.getBlockPos();
        lastAutomatedInteractPos = null;
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        // (v8) This event is now only for *manual* linking
        if (!isActive() || mc.player == null || mc.world == null) return;
        if (mc.currentScreen == null || lastBlockInteractPos == null) return;

        // If this open matches automated open, skip
        if (lastAutomatedInteractPos != null && lastBlockInteractPos.equals(lastAutomatedInteractPos)
            && (System.currentTimeMillis() - lastAutomatedInteractMs) < 5000L) {
            lastBlockInteractPos = null;
            return;
        }

        BlockEntity blockEntity = mc.world.getBlockEntity(lastBlockInteractPos);
        if (blockEntity == null) {
            lastBlockInteractPos = null;
            return;
        }
        
        BlockPos posToUpdate = lastBlockInteractPos;
        lastBlockInteractPos = null; // Consume the interact

        boolean handled = false;
        for (int i = 0; i < linkedStorages.size(); i++) {
            LinkedStorage linkedStorage = linkedStorages.get(i);
            if (linkedStorage.blockPos.equals(posToUpdate)) {
                linkedStorages.remove(i);
                LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, posToUpdate);
                if (newStorage != null) {
                    linkedStorages.add(newStorage);
                    saveLinkedStorages();
                    Logger.info("Updated linked storage at X=%s, Y=%s, Z=%s", posToUpdate.getX(), posToUpdate.getY(), posToUpdate.getZ());
                }
                handled = true;
                break;
            }
        }
        if (handled) return;

        if (!storageLinkMode.get()) return;

        if (blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, posToUpdate);
            if (newStorage == null || newStorage.inventory.isEmpty()) return;
            linkedStorages.add(newStorage);
            saveLinkedStorages();
            Logger.info("Linked Storage located at X=%s, Y=%s, Z=%s", posToUpdate.getX(), posToUpdate.getY(), posToUpdate.getZ());
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
            configFile.getParentFile().mkdirs();
            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();
            if (home != null) payload.addProperty("home", home.asLong());
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
        if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos));
    }

    private LinkedStorage findItem(Item item) {
        if (mc.player == null || mc.world == null) return null;
        linkedStorages.sort(Comparator.comparingDouble(a -> a.blockPos.getSquaredDistance(mc.player.getBlockPos())));
        for (LinkedStorage linkedStorage : linkedStorages) {
            for (ItemStack stack : linkedStorage.inventory) {
                if (stack.getItem() == item) return linkedStorage;
            }
        }
        return null;
    }

    // (v8) Event callback
    private void performStorageInteractSafely(LinkedStorage linkedStorage) {
        if (mc.player == null || mc.interactionManager == null || linkedStorage == null) return;

        if (openAttempts >= MAX_OPEN_ATTEMPTS) {
            Logger.error("Too many open attempts for storage at %s. Aborting.", linkedStorage.blockPos.toShortString());
            forceStopAndGoHome(true);
            return;
        }

        if (mc.currentScreen != null) {
             if (debugMode.get()) Logger.info("Screen is open, closing it before interact...");
             MinecraftClient.getInstance().execute(() -> mc.setScreen(null));
             eventQueue.addFirst(new Event(false, () -> performStorageInteractSafely(linkedStorage), "Re-Interact " + linkedStorage.blockPos.toShortString()));
             return;
        }

        Vec3d hitPos = Vec3d.ofCenter(linkedStorage.blockPos);
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, linkedStorage.blockPos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        lastInteractMs = System.currentTimeMillis();
        openAttempts++;

        if (result.isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
            lastAutomatedInteractPos = linkedStorage.blockPos;
            lastAutomatedInteractMs = System.currentTimeMillis();
            openAttempts = 0; // Reset on success
            if (debugMode.get()) Logger.info("Interact accepted (attempt #%d)", openAttempts);
        } else {
            if (debugMode.get()) Logger.warn("Interact not accepted (attempt #%d), result=%s", openAttempts, result.toString());
            eventQueue.addFirst(new Event(true, () -> performStorageInteractSafely(linkedStorage), "Re-Interact " + linkedStorage.blockPos.toShortString()));
        }
    }

    // (v8) Event callback
    private void moveSlots(Item itemToMove, int stacksToMove) {
        if (mc.player == null || !(mc.currentScreen instanceof HandledScreen)) {
            if(debugMode.get()) Logger.warn("moveSlots: no open screen handler. Task failed.");
            forceStopAndGoHome(true); // Fail the build
            return;
        }
        
        ScreenHandler handler = mc.player.currentScreenHandler;
        int count = 0;
        int maxIndex = Math.min(SlotUtils.indexToId(SlotUtils.MAIN_START), handler.slots.size());

        for (int i = 0; i < maxIndex; i++) {
            if (!handler.getSlot(i).hasStack()) continue;
            Item item = handler.getSlot(i).getStack().getItem();
            if (item != itemToMove) continue;

            if (lastAutomatedInteractPos != null) {
                for (LinkedStorage ls : linkedStorages) {
                    if (ls.blockPos.equals(lastAutomatedInteractPos)) {
                        ls.inventory.removeIf(itemStack -> itemStack.getItem() == itemToMove);
                        saveLinkedStorages();
                        break;
                    }
                }
            }

            count++;
            InvUtils.shiftClick().slotId(i);
            if (debugMode.get()) Logger.info("After shift-click: item=%s (attempt %d)", itemToMove.getName().getString(), count);
            try { Thread.sleep(afterShiftClickDelay.get()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            if (count >= stacksToMove) break;
        }
        lastAutomatedInteractPos = null;
        lastAutomatedInteractMs = 0L;
    }
    
    // (v8) Event callback, also called by #resume
    private void executeResumeLogic() {
        if (!autoResumeOnFetchComplete.get()) {
             if (debugMode.get()) Logger.info("Fetch complete. Auto-resume is disabled.");
             return;
        }
        if (stuckPaused) {
            Logger.info("Build is paused. Re-issue #resume or #build to continue.");
            return;
        }
        if (buildCommand.isEmpty()) {
             if (debugMode.get()) Logger.info("Event queue empty, but no build command is active. Waiting.");
             return;
        }
        
        // (v8) Anchor check is only performed if buildAnchorPos is set.
        // We set it on #build, and clear it (set to null) after first successful resume.
        if (requireAnchorMatchForResume.get() && buildAnchorPos != null) {
            if (mc.player == null) return;
            double distSq = mc.player.getBlockPos().getSquaredDistance(buildAnchorPos);
            float yawDiff = Math.abs(mc.player.getYaw() - buildAnchorYaw) % 360;
            if (yawDiff > 180) yawDiff = 360 - yawDiff;

            double posTolerance = anchorPosTolerance.get();
            double yawTolerance = anchorYawTolerance.get();

            if (distSq > (posTolerance * posTolerance) || yawDiff > yawTolerance) {
                Logger.info("Fetch complete, but player anchor does not match. Pausing build.");
                Logger.info("Dist: %.2f (Max: %.1f), YawDiff: %.1f (Max: %.1f)", Math.sqrt(distSq), posTolerance, yawDiff, yawTolerance);
                Logger.info("Move back to %s (Yaw: %.1f) and type #resume to continue.", buildAnchorPos.toShortString(), buildAnchorYaw);
                stuckPaused = true; // (v8) This is now a *real* pause
                return;
            } else {
                if (debugMode.get()) Logger.info("Anchor match confirmed. Resuming build.");
            }
        } else if (requireAnchorMatchForResume.get()) {
            if (debugMode.get()) Logger.info("Resuming build (anchor check already passed/cleared).");
        }
        
        // (CRITICAL) Consume the anchor. We are now resuming.
        buildAnchorPos = null; 

        // Try resume sequence
        MeteorExecutor.execute(() -> {
            try { Thread.sleep(afterFetchResumeDelay.get()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

            final int maxAttempts = 6;
            boolean resumed = false;

            if (baritone != null) {
                 try {
                    baritone.getPathingBehavior().cancelEverything();
                    if (debugMode.get()) Logger.info("Issued cancelEverything() to Baritone before resume.");
                 } catch (Exception e) {
                    if (debugMode.get()) Logger.warn("cancelEverything() threw: %s", e.getMessage());
                 }
                 try { Thread.sleep(150); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            for (int attempt = 1; attempt <= maxAttempts && !resumed; attempt++) {
                if (baritone == null || stuckPaused || !buildIsActive) break;

                if (debugMode.get()) Logger.info("Resume attempt %d: buildCommand='%s', stuckPaused=%b", attempt, buildCommand, stuckPaused);

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

                try { Thread.sleep(400); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

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

                try { Thread.sleep(300 + attempt * 100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }

            // final fallback
            if (!resumed && !stuckPaused && buildIsActive && mc.player != null && mc.getNetworkHandler() != null) {
                try {
                    if (debugMode.get()) Logger.info("Fallback: sending build command as chat message: %s", buildCommand);
                    MinecraftClient.getInstance().execute(() -> {
                        try {
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

