package xyz.omegaware.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalGetToBlock;
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
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.utils.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

class EventRegistry {
    public static final EventRegistry INSTANCE = new EventRegistry();

    public static class Event {
        public enum EventType {
            Resume,
            PathToPos,
            InteractWithBlock,
            FetchItems
        }
        public EventType type;
        public boolean bWaitOnPath;
        public Runnable callback;

        public Event(EventType type, boolean bWaitOnPath, Runnable callback) {
            this.type = type;
            this.bWaitOnPath = bWaitOnPath;
            this.callback = callback;
        }
    } private final List<Event> eventQueue = new ArrayList<>();

    public void clear() {
        eventQueue.clear();
    }

    public void push(Event event) {
        eventQueue.add(event);
    }

    private void remove(Event event) {
        eventQueue.remove(event);
    }

    public boolean isEmpty() {
        return eventQueue.isEmpty();
    }

    public List<Event> getAll() {
        return new ArrayList<>(eventQueue);
    }

    public Event next() {
        if (eventQueue.isEmpty()) return null;
        Event event = eventQueue.getFirst();
        remove(event);
        return event;
    }

    public boolean eventExists(Event.EventType type) {
        return eventQueue.stream().anyMatch(event -> event.type == type);
    }
}

class StorageRegistry {
    public static final StorageRegistry INSTANCE = new StorageRegistry();

    public static class Storage {
        public BlockPos blockPos;
        public List<ItemStack> inventory;

        public Storage() {
            this.blockPos = BlockPos.ORIGIN;
            this.inventory = new ArrayList<>();
        }

        public Storage(BlockPos blockPos, List<ItemStack> inventory) {
            this.blockPos = blockPos;
            this.inventory = inventory;
        }

        public boolean hasItem(Item item) {
            for (ItemStack stack : inventory) {
                if (stack.getItem().equals(item)) {
                    return true;
                }
            }
            return false;
        }
    } private final List<Storage> storages = new ArrayList<>();

    public void clear() {
        storages.clear();
    }

    public void add(Storage storage) {
        storages.add(storage);
    }

    public List<Storage> getAll() {
        return new ArrayList<>(storages);
    }

    public Storage indexStorage(ScreenHandler screenHandler, BlockPos blockPos) {
        if (screenHandler == null || blockPos == null) return null;

        int max = 27; // Default size for most chests, shulker boxes, etc.
        if (screenHandler.getType() == ScreenHandlerType.GENERIC_9X6) max = 27 * 2;

        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < max; i++) {
            ItemStack stack = screenHandler.getSlot(i).getStack();
            if (!stack.isEmpty()) {
                inventory.add(stack);
            }
        }

        return new Storage(blockPos, inventory);
    }

    public Storage find(BlockPos blockPos) {
        for (Storage storage : storages) {
            if (storage.blockPos.equals(blockPos)) {
                return storage;
            }
        }
        return null;
    }

    public Storage findItem(Item item) {
        for (Storage storage : storages) {
            if (storage.hasItem(item)) {
                return storage;
            }
        }
        return null;
    }

    public void findItemAndPath(Item item) {
        Storage storage = findItem(item);
        if (storage != null) {
            Logger.info("%s Navigating to storage containing:%s %s", Formatting.GREEN, Formatting.WHITE, item.getName().getString());
            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.PathToPos, true, () -> OmegawareAddons.BETTER_BARITONE_BUILD.pathToPos(storage.blockPos)));
            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.InteractWithBlock, true, () -> {
                if (mc.player == null || mc.interactionManager == null) {
                    Logger.error("Player or interaction manager is null!");
                    return;
                }
                mc.setScreen(null); // Close any open screens to ensure that we can interact with the storage block

                Vec3d hitPos = Vec3d.ofCenter(storage.blockPos);
                BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, storage.blockPos, false);

                ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit); // Attempt to interact with the block
                if (OmegawareAddons.BETTER_BARITONE_BUILD.debugMode.get()) {
                    Logger.info("Attempted interact with block at %s, result: %s", storage.blockPos, result.isAccepted());
                }

                if (result.isAccepted()) // If the interaction was successful, we can then make the player swing their hand
                    mc.player.swingHand(Hand.MAIN_HAND);
            }));
        }
    }

    public void update() {
        storages.removeIf(storage -> {
            if (storage == null || storage.blockPos == null) return true;
            assert MinecraftClient.getInstance().world != null;
            if (!MinecraftClient.getInstance().world.isPosLoaded(storage.blockPos)) return false;

            if (storage.inventory == null || storage.inventory.isEmpty()) {
                return true;
            }

            return MinecraftClient.getInstance().world.getBlockState(storage.blockPos).isAir() || MinecraftClient.getInstance().world.getBlockEntity(storage.blockPos) == null;
        });
    }

    public void updateStorage(BlockPos blockPos, List<ItemStack> inventory) {
        Storage storage = find(blockPos);
        if (storage != null) {
            storage.inventory = inventory;
        }
    }

    void save() {
        update();

        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();

            JsonArray linkedStoragesArray = new JsonArray();
            for (Storage storage : storages) {
                JsonObject storageJson = new JsonObject();
                storageJson.addProperty("blockPos", storage.blockPos.asLong());

                JsonObject inventoryJson = new JsonObject();
                for (ItemStack stack : storage.inventory) {
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

    void load() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "linked_storages.json");
        if (!configFile.exists()) {
            //noinspection LoggingSimilarMessage
            OmegawareAddons.LOG.warn("Config file \"{}\" not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = new GsonBuilder().setPrettyPrinting().create().fromJson(content, JsonObject.class);
            if (payload.has("linked_storages")) {
                JsonArray linkedStoragesArray = payload.getAsJsonArray("linked_storages");
                storages.clear();

                for (int i = 0; i < linkedStoragesArray.size(); i++) {
                    JsonObject storageJson = linkedStoragesArray.get(i).getAsJsonObject();
                    Storage storage = new Storage();

                    if (storageJson.has("blockPos")) {
                        storage.blockPos = BlockPos.fromLong(storageJson.get("blockPos").getAsLong());
                    }

                    if (storageJson.has("inventory")) {
                        JsonObject inventoryJson = storageJson.getAsJsonObject("inventory");
                        storage.inventory = new ArrayList<>();

                        for (String itemId : inventoryJson.keySet()) {
                            Item item = Registries.ITEM.get(Identifier.of(itemId)).asItem();
                            if (item != null) {
                                JsonObject itemData = inventoryJson.getAsJsonObject(itemId);
                                int count = itemData.get("count").getAsInt();
                                storage.inventory.add(new ItemStack(item, count));
                            }
                        }
                    }

                    storages.add(storage);
                }
            }

        } catch (Exception e) {
            OmegawareAddons.LOG.error("Failed to load Linked Storages from {}: {}", configFile.toPath(), e.getMessage());
        }
    }
}

class Home {
    public static final Home INSTANCE = new Home();
    private static BlockPos pos;

    public void setHome(BlockPos home) {
        pos = home;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isSet() {
        return pos != null;
    }

    void save() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");

        try {
            //noinspection ResultOfMethodCallIgnored
            configFile.getParentFile().mkdirs();

            Writer writer = new FileWriter(configFile);
            JsonObject payload = new JsonObject();
            payload.addProperty("home", pos.asLong());
            writer.append(payload.toString());
            writer.close();
        } catch (Exception ignored) {
            OmegawareAddons.LOG.info("Failed to save Home to {}", configFile.toPath());
        }
    }

    void load() {
        File configFile = OmegawareAddons.GetConfigFile("better-build", "home.json");
        if (!configFile.exists()) {
            //noinspection LoggingSimilarMessage
            OmegawareAddons.LOG.warn("Config file \"{}\" not found!", configFile.toPath());
            return;
        }

        try {
            String content = Files.readString(configFile.toPath());
            JsonObject payload = new GsonBuilder().setPrettyPrinting().create().fromJson(content, JsonObject.class);
            if (payload.has("home")) {
                pos = BlockPos.fromLong(payload.get("home").getAsLong());
            }
        } catch (Exception e) {
            OmegawareAddons.LOG.error("Failed to load Home from {}: {}", configFile.toPath(), e.getMessage());
        }
    }
}

class FetchRegistry {
    public static final FetchRegistry INSTANCE = new FetchRegistry();

    public static class Material {
        public Item item;
        public int stacks;

        public Material(Item item, int stacks) {
            this.item = item;
            this.stacks = stacks;
        }
    } private final List<Material> fetchList = new ArrayList<>();

    public void clear() {
        fetchList.clear();
    }

    public void add(Material material) {
        fetchList.add(material);
    }

    public List<Material> get() {
        return new ArrayList<>(fetchList);
    }

    public Material find(Item item) {
        for (Material material : fetchList) {
            if (material.item.equals(item)) {
                return material;
            }
        }
        return null;
    }

    public boolean hasItem(Item item) {
        return find(item) != null;
    }

    public Material updateMaterial(Material material, int newStacks) {
        Material existingMaterial = find(material.item);
        if (existingMaterial != null) {
            existingMaterial.stacks = newStacks;
            return existingMaterial;
        }
        return null;
    }

    public void update() {
        fetchList.removeIf(material -> material == null || material.item == null || material.stacks <= 0);
    }

    public boolean isEmpty() {
        return fetchList.isEmpty();
    }
}

public class BetterBaritoneBuild extends Module {
    public BetterBaritoneBuild() {
        super(OmegawareAddons.CATEGORY, "better-baritone-build", "Enable this module to enhance Baritone's building capabilities with linked storage and item fetching features.");
    }

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

    public final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("If enabled, the module will print debug information to the console.")
        .defaultValue(false)
        .build()
    );

    // Globals
    IBaritone baritone = null;
    private static String buildCommand = "";
    private static EventRegistry.Event currentEvent = null;
    private BlockPos lastBlockInteractPos = null;

    @Override
    public void onActivate() {
        if (!BaritoneUtils.IS_AVAILABLE) {
            Logger.error("Baritone is not available!");
            toggle();
            return;
        }

        baritone = BaritoneAPI.getProvider().getBaritoneForMinecraft(MinecraftClient.getInstance());

        currentEvent = null;
        EventRegistry.INSTANCE.clear();
        FetchRegistry.INSTANCE.clear();

        StorageRegistry.INSTANCE.load();
        Home.INSTANCE.load();
    }

    @Override
    public void onDeactivate() {
        currentEvent = null;
        EventRegistry.INSTANCE.clear();
        FetchRegistry.INSTANCE.clear();

        StorageRegistry.INSTANCE.save();
        Home.INSTANCE.save();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList hList = list.add(theme.horizontalList()).expandX().widget();

        WButton printBtn = theme.button("Print Linked Storages");
        printBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            StorageRegistry.INSTANCE.getAll().forEach(storage -> {
                if (storage.blockPos == null) return;
                sb.append(String.format("X=%s, Y=%s, Z=%s\n", storage.blockPos.getX(), storage.blockPos.getY(), storage.blockPos.getZ()));
            });

            Logger.info("Linked Storages:\n%s", sb.toString());
        };
        hList.add(printBtn);

        WButton clearBtn = theme.button("Clear Linked Storages");
        clearBtn.action = () -> {
            StorageRegistry.INSTANCE.clear();
            Logger.info("Linked Storages cleared!");
        };
        hList.add(clearBtn);

        WButton setHomeBtn = theme.button("Set Home");
        setHomeBtn.action = () -> {
            if (mc.player == null || mc.world == null) return;

            Home.INSTANCE.setHome(mc.player.getBlockPos());
            Home.INSTANCE.save();

            BlockPos home = Home.INSTANCE.getPos();
            Logger.info("%sHome point set to:%s X=%s, Y=%s, Z=%s", Formatting.GREEN, Formatting.WHITE, home.getX(), home.getY(), home.getZ());
        };
        hList.add(setHomeBtn);

        WHorizontalList hlist2 = list.add(theme.horizontalList()).expandX().widget();
        WButton printFetchListBtn = theme.button("Print Fetch List");
        printFetchListBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            FetchRegistry.INSTANCE.get().forEach(material -> {
                sb.append(String.format("Item: %s, Stacks: %d\n", material.item.getName().getString(), material.stacks));
            });

            Logger.info("Fetch List:\n%s", sb.toString());
        };
        hlist2.add(printFetchListBtn);
        WButton clearFetchListBtn = theme.button("Clear Fetch List");
        clearFetchListBtn.action = () -> {
            FetchRegistry.INSTANCE.clear();
            Logger.info("Fetch List cleared!");
        };
        hlist2.add(clearFetchListBtn);

        WButton printEventQueueBtn = theme.button("Print Event Queue");
        printEventQueueBtn.action = () -> {
            StringBuilder sb = new StringBuilder();
            EventRegistry.INSTANCE.getAll().forEach(event -> {
                sb.append(String.format("Event Type: %s, Wait on Path: %s\n", event.type, event.bWaitOnPath));
            });

            Logger.info("Event Queue:\n%s", sb.toString());
        };
        hlist2.add(printEventQueueBtn);

        WButton clearEventQueueBtn = theme.button("Clear Event Queue");
        clearEventQueueBtn.action = () -> {
            EventRegistry.INSTANCE.clear();
            Logger.info("Event Queue cleared!");
        };
        hlist2.add(clearEventQueueBtn);

        return list;
    }

    @EventHandler
    public void onServerConnectEnd(ServerConnectEndEvent event) {
        if (!isActive()) return;

        StorageRegistry.INSTANCE.load();
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!isActive()) return;

        if (!EventRegistry.INSTANCE.isEmpty() && currentEvent == null) {
            currentEvent = EventRegistry.INSTANCE.next();
        }

        if (currentEvent != null) {
            if (debugMode.get()) {
                Logger.info("Executing event: %s", currentEvent.type.toString());
            }
            if (currentEvent.bWaitOnPath && baritone.getPathingBehavior().hasPath() || baritone.getPathingBehavior().isPathing()) {
                // Wait for Baritone to finish pathing
                return;
            }

            currentEvent.callback.run();
            currentEvent = null;
        }
        // Home shit
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || mc.world == null || !highlightLinkedStorages.get()) return;

        if (!invertHighlight.get()) {
            StorageRegistry.INSTANCE.getAll().forEach(storage -> {
                if (storage.blockPos == null || !mc.world.isPosLoaded(storage.blockPos)) return;

                event.renderer.box(storage.blockPos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            });
        } else {
            for (BlockEntity blockEntity : Utils.blockEntities()) {
                if (!(blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity))
                    continue;

                BlockPos pos = blockEntity.getPos();
                if (!mc.world.isPosLoaded(pos) || StorageRegistry.INSTANCE.find(pos) != null) return;

                event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
            }
        }
    }

    @EventHandler
    private void onBlockInteract(InteractBlockEvent event) {
        if (!isActive() || mc.world == null) return;

        BlockEntity blockEntity = mc.world.getBlockEntity(event.result.getBlockPos());
        if (!(blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity)) {
            if (debugMode.get()) Logger.error("Block entity is not a valid storage type!");
            lastBlockInteractPos = null;
            return;
        }

        lastBlockInteractPos = event.result.getBlockPos();

        if (debugMode.get()) {
            Logger.info("Interacted with block at %s", lastBlockInteractPos);
        }
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String msg = event.getMessage().getString();
        if (msg == null || msg.isEmpty()) return;
        msg = msg.toLowerCase().trim();

        if (!msg.contains("[baritone]") || msg.contains("omegaware")) return;
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

            if (item == null) {
                if (debugMode.get()) Logger.error("Item not found: %s%s", Formatting.WHITE, blockName);
                return;
            }

            if (FetchRegistry.INSTANCE.hasItem(item)) return;

            StorageRegistry.Storage storage = StorageRegistry.INSTANCE.findItem(item);
            if (storage == null) {
                Logger.error("No linked storage contains the item: %s%s", Formatting.WHITE, item.getName().getString());

                if (disconnectOnError.get()) {
                    AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                    if (autoReconnect.isActive()) {
                        autoReconnect.toggle();
                    }

                    String prefix = Logger.PREFIX.getString();
                    MutableText text = Text.literal(String.format("%s%s%s%s %s", Formatting.GRAY, Formatting.BLUE, prefix.substring(0, prefix.length() - 1), Formatting.GRAY, Formatting.RED) + String.format("No linked storage contains the item: %s\n", item.getName().getString()));

                    disconnectOnError.set(false); // Disable the setting to prevent infinite disconnects

                    ClientPlayNetworkHandler networkHandler = mc.getNetworkHandler();
                    if (networkHandler != null) {
                        networkHandler.getConnection().disconnect(text);
                    }
                }
                return;
            }

            FetchRegistry.INSTANCE.add(new FetchRegistry.Material(item, stacks + extraStacks.get()));
            EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.FetchItems, true, () -> StorageRegistry.INSTANCE.findItemAndPath(item)));
            return;
        }

        if (msg.contains("done building")) {
            if (debugMode.get()) {
                Logger.info("Baritone has finished building!");
            }

            if (disconnectOnDone.get()) {
                AutoReconnect autoReconnect = Modules.get().get(AutoReconnect.class);
                if (autoReconnect.isActive()) {
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

        msg = msg.substring(2).trim(); // Remove the "> " part
        if (msg.startsWith("build") || msg.startsWith("litematica")) {
            buildCommand = msg;
            if (debugMode.get()) {
                Logger.info("Build command captured: %s%s", Formatting.WHITE, buildCommand);
            }
            return;
        }

        if (msg.startsWith("stop") || msg.startsWith("cancel")) {
            buildCommand = "";
            currentEvent = null;
            EventRegistry.INSTANCE.clear();
            FetchRegistry.INSTANCE.clear();

            Logger.info("Stop received.");
        }
    }

    @EventHandler
    private void onInventory(InventoryEvent event) {
        if (!isActive() || mc.player == null || mc.world == null || mc.currentScreen == null || lastBlockInteractPos == null) return;

        if (StorageRegistry.INSTANCE.find(lastBlockInteractPos) != null) {
            StorageRegistry.Storage storage = StorageRegistry.INSTANCE.indexStorage(mc.player.currentScreenHandler, lastBlockInteractPos);
            StorageRegistry.INSTANCE.updateStorage(lastBlockInteractPos, storage.inventory);
            StorageRegistry.INSTANCE.update();
            StorageRegistry.INSTANCE.save();
        }

        if (!FetchRegistry.INSTANCE.isEmpty()) {
            if (!EventRegistry.INSTANCE.eventExists(EventRegistry.Event.EventType.FetchItems)) {
                EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.FetchItems, true, () -> StorageRegistry.INSTANCE.findItemAndPath(FetchRegistry.INSTANCE.get().getFirst().item)));
            }

            StorageRegistry.Storage interactionStorage = StorageRegistry.INSTANCE.find(lastBlockInteractPos);
            if (interactionStorage == null) return;

            FetchRegistry.INSTANCE.get().forEach(material -> {
                if (material.item == null || material.stacks <= 0) return;
                if (!interactionStorage.hasItem(material.item)) return;
                ScreenHandler handler = mc.player.currentScreenHandler;
                if (handler == null) return;

                MeteorExecutor.execute(() -> {
                    boolean initial = true;
                    int count = 0;

                    int max = 27; // Default size for most chests, shulker boxes, etc.
                    if (handler.getType() == ScreenHandlerType.GENERIC_9X6) max = 27 * 2;

                    for (int i = 0; i < max; i++) {
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
                            OmegawareAddons.LOG.error("Interrupted while sleeping in item fetch: {}", e.getMessage());
                        }

                        // Exit if user closes screen or exit world
                        if (mc.currentScreen == null || !Utils.canUpdate()) break;

                        Item item = handler.getSlot(i).getStack().getItem();
                        if (item != material.item) continue;

                        count++;
                        InvUtils.shiftClick().slotId(i);

                        if (count >= material.stacks) {
                            break;
                        }
                    }

                    FetchRegistry.Material updatedMaterial = FetchRegistry.INSTANCE.updateMaterial(material, material.stacks - count);
                    if (debugMode.get()) {
                        Logger.info("Fetched %d stacks of %s%s, %d stacks remaining", count, Formatting.WHITE, material.item.getName().getString(), updatedMaterial != null ? updatedMaterial.stacks : 0);
                    }

                    FetchRegistry.INSTANCE.update();

                    if (FetchRegistry.INSTANCE.isEmpty()) {
                        EventRegistry.INSTANCE.push(new EventRegistry.Event(EventRegistry.Event.EventType.Resume, true, () -> baritone.getCommandManager().execute(buildCommand)));
                    }
                });
            });
        }

        if (!storageLinkMode.get() || StorageRegistry.INSTANCE.find(lastBlockInteractPos) != null) return;

        StorageRegistry.Storage storage = StorageRegistry.INSTANCE.indexStorage(mc.player.currentScreenHandler, lastBlockInteractPos);
        if (storage == null) {
            if (debugMode.get()) Logger.error("Storage is null for block at %s", lastBlockInteractPos);
            return;
        }


        if (storage.inventory.isEmpty()) {
            if (debugMode.get()) Logger.warn("Storage at %s is empty!", lastBlockInteractPos);
            return;
        }

        if (debugMode.get()) {
            Logger.info("Indexed storage at %s with %d items", lastBlockInteractPos, storage.inventory.size());
        }

        StorageRegistry.INSTANCE.add(storage);
        StorageRegistry.INSTANCE.save();

        Logger.info("Storage at %s has been linked!", lastBlockInteractPos);
    }

    public void pathToPos(BlockPos blockPos) {
        if (mc.player == null || mc.world == null) return;

        if (debugMode.get()) Logger.info("%sNavigating to:%s X=%s, Y=%s, Z=%s", Formatting.GREEN, Formatting.WHITE, blockPos.getX(), blockPos.getY(), blockPos.getZ());


        if (!ignoreY.get()) {
            baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos));
        } else baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos.withY(mc.player.getBlockY())));
    }
}
