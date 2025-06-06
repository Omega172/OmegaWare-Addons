package xyz.omegaware.addon.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.command.argument.ICommandArgument;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.command.argument.ArgConsumer;
import baritone.command.argument.CommandArguments;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.packets.InventoryEvent;
import meteordevelopment.meteorclient.events.world.ServerConnectEndEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
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

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestModule extends Module {
    public TestModule() {
        super(OmegawareAddons.CATEGORY, "Test Module", "This is a test module for OmegaWare Addons.");
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("If enabled, debug messages will be printed to the chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> storageLinkMode = sgGeneral.add(new BoolSetting.Builder()
        .name("storage-link-mode")
        .description("If enabled, all storage blocks you interact with will be linked to this module.")
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

    private class StroageItem {
        public Item item;
        public LinkedStorage linkedStorage;

        public StroageItem(Item item, LinkedStorage linkedStorage) {
            this.item = item;
            this.linkedStorage = linkedStorage;
        }
    }
    private final List<StroageItem> itemsToFetch = new ArrayList<>();

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

        loadLinkedStorages();
    }

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

        WHorizontalList hList2 = list.add(theme.horizontalList()).expandX().widget();

        WTextBox itemTextBox = theme.textBox("");
        itemTextBox.minWidth = 100;
        WButton findItemBtn = theme.button("Find Item in Linked Storages");
        findItemBtn.action = () -> {
            if (mc.player == null || mc.world == null) return;

            Identifier identifier = Identifier.of(itemTextBox.get());
            Item item = Registries.ITEM.get(identifier).asItem();

            if (item == null) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Item not found: ").formatted(Formatting.RED))
                    .append(Text.literal(itemTextBox.get()).formatted(Formatting.WHITE)));
                return;
            }

            pathToItemLocation(item);
        };
        hList2.add(findItemBtn);
        hList2.add(itemTextBox);

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
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (!isActive()) return;
        String message = event.getMessage().getString();

        if (!message.toLowerCase().startsWith("[baritone]")) return;

        String msg = message.substring(13).trim();
        if (msg.toLowerCase().startsWith("build")) {
            buildCommand = msg;
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("Build command captured: ").formatted(Formatting.GREEN))
                .append(Text.literal(buildCommand).formatted(Formatting.WHITE)));
            event.cancel();
            return;
        }

        msg = message.substring(10).trim();
        if (msg.matches("^(\\d+)x Block\\{minecraft:([a-z0-9_]+)}(\\[axis=[xy]])?$")) { // Missing item message
            String[] parts = msg.split(" ");

            String blockMessage = parts[1];
            String blockName = blockMessage.substring(blockMessage.indexOf(':') + 1);

            int endIndex = blockName.indexOf('}');
            if (endIndex != -1) {
                blockName = blockName.substring(0, endIndex);
            }

            Identifier identifier = Identifier.of(blockName);
            Item item = Registries.ITEM.get(identifier).asItem();

            if (item == null) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Item not found: ").formatted(Formatting.RED))
                    .append(Text.literal(blockName).formatted(Formatting.WHITE)));
                return;
            }

            pathToItemLocation(item);
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
            MeteorExecutor.execute(() -> moveSlots(mc.player.currentScreenHandler, SlotUtils.indexToId(SlotUtils.MAIN_START)));
        }

        if (lastBlockInteractPos == null) return;

        if (storageLinkMode.get()) {
            BlockEntity blockEntity = mc.world.getBlockEntity(lastBlockInteractPos);
            if (blockEntity == null) return;

            if (blockEntity instanceof ShulkerBoxBlockEntity || blockEntity instanceof ChestBlockEntity || blockEntity instanceof BarrelBlockEntity || blockEntity instanceof EnderChestBlockEntity) {
                for (LinkedStorage linkedStorage : linkedStorages) {
                    if (linkedStorage.blockPos.equals(lastBlockInteractPos)) {
                        lastBlockInteractPos = null;
                        linkedStorage.inventory = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos()).inventory;
                        return;
                    }
                }
                lastBlockInteractPos = null;

                LinkedStorage linkedStorage = indexStorage(mc.player.currentScreenHandler, blockEntity.getPos());
                if (linkedStorage == null) return;

                linkedStorages.add(linkedStorage);
                saveLinkedStorages();

                MutableText msg = OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Test Module: ").formatted(Formatting.GREEN))
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
            if (!mc.world.isPosLoaded(storage.blockPos)) return false;
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

        ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
            .append(Text.literal("Navigating to: ").formatted(Formatting.GREEN))
            .append(Text.literal(String.format("X=%s, Y=%s, Z=%s", blockPos.getX(), blockPos.getY(), blockPos.getZ())).formatted(Formatting.WHITE)));

        baritone.getCustomGoalProcess().setGoalAndPath(new GoalGetToBlock(blockPos));
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
    private FindResult itemsToFetchHas(Item item) {
        for (StroageItem storageItem : itemsToFetch) {
            if (storageItem.item == item) {
                return new FindResult(true, storageItem.linkedStorage);
            }
        }

        return new FindResult(false, null);
    }

    private void pathToItemLocation(Item item) {
        if (mc.player == null || mc.interactionManager == null) return;

        LinkedStorage linkedStorage = findItem(item);
        if (linkedStorage == null) {
            ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                .append(Text.literal("No linked storage contains the item: ").formatted(Formatting.RED))
                .append(Text.literal(item.getName().getString()).formatted(Formatting.WHITE)));
            return;
        }

        ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
            .append(Text.literal("Navigating to storage containing: ").formatted(Formatting.GREEN))
            .append(Text.literal(item.getName().getString()).formatted(Formatting.WHITE)));

        itemsToFetch.add(new StroageItem(item, linkedStorage));

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

    private void moveSlots(ScreenHandler handler, int end) {
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

            FindResult findResult = itemsToFetchHas(item);
            if (!findResult.found) continue;

            grabbedItems.add(item);

            count++;
            InvUtils.shiftClick().slotId(i);

            findResult.linkedStorage.inventory.remove(handler.getSlot(i).getStack());

            if (count >= 3) {
                break;
            }
        }

        itemsToFetch.removeIf(stroageItem -> grabbedItems.contains(stroageItem.item));

        // Remove empty storages
        linkedStorages.removeIf(linkedStorage -> {
            if (linkedStorage.inventory.isEmpty()) {
                ChatUtils.sendMsg(OmegawareAddons.PREFIX.copy()
                    .append(Text.literal("Removed empty storage at: ").formatted(Formatting.RED))
                    .append(Text.literal(String.format("X=%s, Y=%s, Z=%s", linkedStorage.blockPos.getX(), linkedStorage.blockPos.getY(), linkedStorage.blockPos.getZ())).formatted(Formatting.WHITE)));
                return true;
            }
            return false;
        });

        if (!buildCommand.isEmpty()) {
            eventQueue.add(new Event(true, () -> {
                baritone.getCommandManager().execute(buildCommand);
            }));
        }
    }
}
