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

    // --- concurrency / safety flags for storage fetching ---
    private volatile boolean isFetching = false; // ensure only one fetch job at a time
    private long lastInteractMs = 0L;
    private int openAttempts = 0;
    private final long INTERACT_DEBOUNCE_MS = 800L; // don't interact faster than this
    private final int MAX_OPEN_ATTEMPTS = 6; // abort after this many failed open tries

    // small delay after shift-click to let server sync (ms)
    private final long AFTER_SHIFTCLICK_DELAY_MS = 80L;

    // automated open tracking & double-check support
    private BlockPos lastAutomatedInteractPos = null;
    private long lastAutomatedInteractMs = 0L;

    // if Baritone returned home due to stuck, we pause the build to avoid auto-resume causing overlap
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

    // Anchor info for build consistency (kept but resume after fetch will ignore anchor)
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
        if (debugMode.get() && ticksStuck % 20 == 0) { // log roughly once per second while stuck (if debug)
            Logger.warn("Baritone may be stuck, ticks: %d", ticksStuck);
        }

        // 1 second = 20 ticks
        if (ticksStuck >= homeIfStuckTimeout.get() * 20) {
            Logger.error("Baritone is stuck, returning to home point...");

            ticksStuck = 0; // Reset the stuck counter
            lastBlockPos = mc.player.getBlockPos(); // Update the last block position

            eventQueue.clear();
            itemsToFetch.clear();
            pendingChecks.clear();
            doubleCheckPos.clear();
            doubleCheckInProgress = false;

            if (baritone != null) baritone.getPathingBehavior().cancelEverything();

            // Send Baritone home, but DO NOT auto-resume the build to avoid overlap/tumpang-tindih.
            stuckPaused = true;
            homeArrivalRetries = 0;
            eventQueue.add(new Event(false, () -> {
                if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
            }));

            // verification
            eventQueue.add(new Event(true, this::verifyArrivalAtHome));

            if (debugMode.get()) {
                Logger.info("Build paused due to stuck; re-issue build command manually when ready.");
            }
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

            // when finished, send home (user wanted home to be central place) or keep as-is?
            // Keep original behavior: don't auto-disconnect unless setting.
            return;
        }

        // capture explicit build commands (player typed)
        String trimmed = original.trim();
        if (trimmed.startsWith("> ")) trimmed = trimmed.substring(2).trim();
        String trimmedLower = trimmed.toLowerCase();

        if (trimmedLower.startsWith("build") || trimmedLower.startsWith("litematica")) {
            buildCommand = trimmed; // store original command
            // capture anchor when player issues build
            if (mc.player != null) {
                buildAnchorPos = mc.player.getBlockPos();
                buildAnchorYaw = mc.player.getYaw();
                buildAnchorSet = true;
                if (debugMode.get()) Logger.info("Build anchor captured at %s yaw=%.1f. Command: %s", buildAnchorPos.toShortString(), buildAnchorYaw, buildCommand);
            }
            // clear paused flag because user explicitly requested build
            stuckPaused = false;
            return;
        }

        if (trimmedLower.startsWith("stop") || trimmedLower.startsWith("cancel")) {
            buildCommand = "";
            eventQueue.clear();
            itemsToFetch.clear();
            pendingChecks.clear();
            doubleCheckPos.clear();
            doubleCheckInProgress = false;

            Logger.info("Stop received.");
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

        for (LinkedStorage ls : new ArrayList<>(linkedStorages)) {
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
                // after double-check, still not found -> final fail: send Baritone home and log error
                Logger.error("No linked storage contains the item: %s (confirmed after double-check)", pc.item.getName().getString());

                // clear jobs
                eventQueue.clear();
                itemsToFetch.clear();
                doubleCheckPos.clear();
                doubleCheckInProgress = false;

                // instruct baritone to go home and set pause
                stuckPaused = true;
                if (baritone != null) baritone.getPathingBehavior().cancelEverything();
                if (home != null) {
                    eventQueue.add(new Event(false, () -> {
                        if (baritone != null) baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(home));
                    }));
                    eventQueue.add(new Event(true, this::verifyArrivalAtHome));
                }

                // optionally disconnect
                if (disconnectOnError.get()) {
                    AutoReconnect autoReconnect = Modules.get().get(meteordevelopment.meteorclient.systems.modules.misc.AutoReconnect.class);
                    if (autoReconnect != null && autoReconnect.isActive()) autoReconnect.toggle();
                    String prefix = Logger.PREFIX.getString();
                    MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED)
                            + String.format("No linked storage contains the item: %s\n", pc.item.getName().getString()));
                    ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                    if (networkHandler != null) networkHandler.getConnection().disconnect(text);
                }
                // don't continue processing other pending checks after final fail (optional)
                return;
            }
        }

        // done processing all pending checks
        doubleCheckInProgress = false;
        doubleCheckPos.clear();

        // If we queued new fetches, ensure they start (pathToLinkedStorage already queued)
        if (!itemsToFetch.isEmpty()) {
            if (debugMode.get()) Logger.info("Double-check finished: scheduled fetch for found items.");
        }
    }

    // check player inventory quickly for item presence
    private boolean playerHasItem(Item item) {
        if (mc.player == null) return false;
        try {
            // iterate player's inventory main list
            for (ItemStack s : mc.player.getInventory().main) {
                if (s != null && !s.isEmpty() && s.getItem() == item && s.getCount() > 0) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) { loadLinkedStorages(); }

    private BlockPos lastBlockInteractPos = null;

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.world == null || !storageLinkMode.get()) return;

        // Manual player interaction sets lastBlockInteractPos and clears automated marker.
        lastBlockInteractPos = event.result.getBlockPos();
        lastAutomatedInteractPos = null; // any manual interaction cancels automated marker
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

        // 2) Handle linking storages when user has just interacted
        // We only update linked storage **if the open was manual by the player** (not automated by the module),
        // unless this pos was explicitly flagged for double-check indexing.
        if (mc.currentScreen == null) return; // no open screen => nothing to index

        if (lastBlockInteractPos == null) return;

        // If this open matches automated open and not flagged for double-check, skip updating
        if (lastAutomatedInteractPos != null && lastBlockInteractPos.equals(lastAutomatedInteractPos)
            && (System.currentTimeMillis() - lastAutomatedInteractMs) < 5000L
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

        // Use index-based iteration to safely remove while iterating
        boolean handled = false;
        for (int i = 0; i < linkedStorages.size(); i++) {
            LinkedStorage linkedStorage = linkedStorages.get(i);
            if (linkedStorage.blockPos.equals(lastBlockInteractPos)) {
                lastBlockInteractPos = null;
                linkedStorages.remove(i);

                LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
                if (newStorage != null) {
                    linkedStorages.add(newStorage);
                    saveLinkedStorages();
                    Logger.info("Updated linked storage at X=%s, Y=%s, Z=%s", blockEntity.getPos().getX(), blockEntity.getPos().getY(), blockEntity.getPos().getZ());
                }
                handled = true;
                break;
            }
        }
        if (handled) {
            // If this was part of a double-check, remove the pos from doubleCheckPos after indexing
            doubleCheckPos.remove(lastBlockInteractPos);
            return;
        }

        // If not handled above and storageLinkMode on, add new linked storage
        if (!storageLinkMode.get()) { lastBlockInteractPos = null; return; }

        if (blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
            LinkedStorage newStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
            lastBlockInteractPos = null;
            if (newStorage == null) return;
            if (newStorage.inventory.isEmpty()) {
                if (debugMode.get()) Logger.error("No items found in the linked storage!");
                return;
            }
            linkedStorages.add(newStorage);
            saveLinkedStorages();
            Logger.info("Linked Storage located at X=%s, Y=%s, Z=%s", blockEntity.getPos().getX(), blockEntity.getPos().getY(), blockEntity.getPos().getZ());
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
        File configFile = OmegawareAddOns_GetConfigFileSafe("better-build", "home.json");

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
        } catch (Exception ignored) {
            OmegawareAddons.LOG.info("Failed to save home to {}", configFile.toPath());
        }
    }

    // wrapper to avoid overshadowing static imports or different class names if any
    private File OmegawareAddOns_GetConfigFileSafe(String key, String filename) {
        return OmegawareAddOns_GetConfigFileWrapper(key, filename);
    }

    // we call the original static method
    private File OmegawareAddOns_GetConfigFileWrapper(String key, String filename) {
        return OmegawareAddons.GetConfigFile(key, filename);
    }

    private void loadHome() {
        File configFile = OmegawareAddOns_GetConfigFileSafe("better-build", "home.json");
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

        mc.setScreen(null); // Close any open screens to ensure that we can interact with the storage block

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

        int maxIndex = Math.min(SlotUtils.MAIN_END, handler.slots.size());

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
                // replace the linked storage entry safely
                for (int si = 0; si < linkedStorages.size(); si++) {
                    LinkedStorage ls = linkedStorages.get(si);
                    if (ls.blockPos.equals(linkedStorage.blockPos)) {
                        // remove the exact stack from the stored inventory by matching item
                        ItemStack stackInSlot = handler.getSlot(i).getStack();
                        linkedStorages.get(si).inventory.removeIf(itemStack -> itemStack.getItem() == stackInSlot.getItem());
                        break;
                    }
                }
                saveLinkedStorages();
            }

            count++;
            // perform shift-click to move item to player inventory
            InvUtils.shiftClick().slotId(i);

            // small delay after shift-click to let server sync
            try { Thread.sleep(AFTER_SHIFTCLICK_DELAY_MS); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }

            if (count >= storageItem.stacks) {
                break;
            }
        }

        // Update the single storageItem's remaining stacks
        int remaining = Math.max(0, storageItem.stacks - count);

        // Remove the processed item from itemsToFetch (we always remove the instance we processed)
        // Use removeIf to be safe matching by item + linkedStorage position
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

        // If we reached here, fetch finished (no more itemsToFetch)
        // Resume build automatically unless paused due to Home-if-stuck
        if (!buildCommand.isEmpty() && !stuckPaused) {
            if (debugMode.get()) Logger.info("Fetch complete; resuming build: %s", buildCommand);
            eventQueue.add(new Event(true, () -> {
                if (baritone != null) baritone.getCommandManager().execute(buildCommand);
            }));
        } else if (stuckPaused) {
            Logger.info("Fetch complete but build is paused due to previous Home-if-stuck event. Re-issue build to continue.");
        }
    }
}
