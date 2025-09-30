package xyz.omegaware.addon.modules;

// ... (imports unchanged)

class StorageRegistry {
    public static final StorageRegistry INSTANCE = new StorageRegistry();
    // ... (rest unchanged)
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

                if (result.isAccepted()) {
                    // Retry swing if no blocks are added to inventory
                    // Guess the item to check as the first one in storage, fallback to null
                    Item expectedItem = null;
                    if (storage.inventory != null && !storage.inventory.isEmpty()) {
                        expectedItem = storage.inventory.get(0).getItem();
                    }
                    int maxRetries = 3;
                    int retryDelay = 1000; // ms (updated to 1000ms)
                    int beforeCount = expectedItem != null ? InvUtils.findAll(expectedItem).size() : -1;
                    boolean blockAdded = false;
                    for (int attempt = 0; attempt < maxRetries; attempt++) {
                        mc.player.swingHand(Hand.MAIN_HAND);
                        try {
                            Thread.sleep(retryDelay);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (expectedItem != null) {
                            int afterCount = InvUtils.findAll(expectedItem).size();
                            if (afterCount > beforeCount) {
                                blockAdded = true;
                                break;
                            }
                        } else {
                            blockAdded = true;
                            break;
                        }
                    }
                    if (!blockAdded && OmegawareAddons.BETTER_BARITONE_BUILD.debugMode.get()) {
                        Logger.warn("No blocks were added to inventory after interaction, even after %d retries.", maxRetries);
                    }
                }
            }));
        }
    }
// ... (rest unchanged)
// ... (rest of file unchanged)