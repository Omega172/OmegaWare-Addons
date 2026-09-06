package xyz.omegaware.addon.modules;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;
import xyz.omegaware.addon.OmegawareAddons;
import xyz.omegaware.addon.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static xyz.omegaware.addon.utils.ServerCheck.isNot6B6T;

// Shamelessly taken from https://github.com/kybe236/rusher-auto-item-frame-dupe/
public class ItemFrameDupeModule extends Module {
    public ItemFrameDupeModule() {
        super(OmegawareAddons.CATEGORY, "6B6T-item-frame-dupe", "automates the 6b6t item frame dupe");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> rotationCount = sgGeneral.add(new IntSetting.Builder()
        .name("Rotation-Count")
        .defaultValue(1)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> placeToInsertDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Place-to-Insert-Delay")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> insertToRotateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Insert-to-Rotate-Delay")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> rotateToRotateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Rotate-to-Rotate-Delay")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> rotateToBreakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Rotate-to-Break-Delay")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Integer> breakToPlaceDelay = sgGeneral.add(new IntSetting.Builder()
        .name("Break-to-Place-Delay")
        .defaultValue(0)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> dropShulkers = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-shulkers")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxShulkersOfTypeInInventory = sgGeneral.add(new IntSetting.Builder()
        .name("max-shulkers-of-type-in-inventory")
        .description("Maximum number of shulker boxes of the same type allowed in the inventory.")
        .defaultValue(5)
        .min(1)
        .sliderMax(27)
        .build()
    );

    private final Setting<Boolean> smartShulkerQueue = sgGeneral.add(new BoolSetting.Builder()
        .name("smart-shulker-queue")
        .description("Uses a queue to cycle through shulker boxes.")
        .defaultValue(false)
        .build()
    );

    public static ArrayList<ItemStack> shulkerQueue = new ArrayList<>();
    int shulkerQueueIndex = 0;
    int currentRotationCount = 0;
    int forceDelay = 0;

    @Override
    public void onActivate() {
        if (isNot6B6T()) {
            Logger.error("%s is only intended for use on 6b6t.", name.replace("-", " "));
            toggle();
        }
    }

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!isActive()) return;

        // if interacting with an item skip the tick
        if (mc.player != null && (mc.player.isUsingItem())) return;

        if (forceDelay != 0) {
            forceDelay--;
            return;
        }

        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.level.getBlockState(mc.player.blockPosition().above(3)).isAir()) return;
        if (mc.gameMode.getPlayerMode() != GameType.SURVIVAL) return;

        AABB blockAbovePlayer = new AABB(mc.player.blockPosition().above(2));
        List<Entity> entitiesAbovePlayer = mc.level.getEntities(null, blockAbovePlayer);
        entitiesAbovePlayer.removeIf(entity -> !(entity instanceof ItemFrame));

        if (entitiesAbovePlayer.isEmpty()) {
            if (!getItemFrame()) return;
            placeItemFrame();
            forceDelay = placeToInsertDelay.get();
            return;
        }

        if (dropShulkers.get()) {
            if (smartShulkerQueue.get() && !shulkerQueue.isEmpty()) {
                for (ItemStack queuedShulker : shulkerQueue) {
                    int count = 0;
                    List<Integer> indices = new ArrayList<>();
                    for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
                        ItemStack invStack = mc.player.getInventory().getItem(i);
                        if (!invStack.isEmpty() &&
                            invStack.getDisplayName().equals(queuedShulker.getDisplayName()) &&
                            invStack.getItem() == queuedShulker.getItem() &&
                            isShulkerBox(invStack.getItem())) {
                            count++;
                            indices.add(i);
                        }
                    }


                    // If count exceeds the maximum allowed, drop the extras.
                    if (count > maxShulkersOfTypeInInventory.get()) {
                        int dropCount = count - maxShulkersOfTypeInInventory.get();
                        for (int index : indices) {
                            if (dropCount <= 0) break;
                            int containerIndex = (index < 9) ? index + 36 : index;
                            if (!isShulkerBox(mc.player.getInventory().getItem(containerIndex).getItem())) continue;

                            InvUtils.drop().slot(containerIndex);
                            dropCount--;
                        }
                    }
                }
            }
        }

        ItemFrame frame = (ItemFrame)entitiesAbovePlayer.getFirst();
        if (frame.getItem().isEmpty()) {
            if (!getShulker()) return;
            interactItemFrame(frame);
            forceDelay = insertToRotateDelay.get();
        } else if (currentRotationCount >= rotationCount.get()) {
            attackItemFrame(frame);
            forceDelay = breakToPlaceDelay.get();
            currentRotationCount = 0;
        } else {
            interactItemFrame(frame);
            currentRotationCount++;
            if (currentRotationCount >= rotationCount.get()) {
                forceDelay = rotateToBreakDelay.get();
            } else {
                forceDelay = rotateToRotateDelay.get();
            }
        }
    }

    private boolean getItemFrame() {
        if (mc.player == null) return false;

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (mc.player.getInventory().getItem(selectedSlot).getItem() == Items.ITEM_FRAME) return true;

        FindItemResult res = InvUtils.findInHotbar(Items.ITEM_FRAME);
        FindItemResult resInv = InvUtils.find(Items.ITEM_FRAME);
        if (res.count() <= 0 && resInv.count() <= 0) return false;

        if (res.count() > 0) {
            mc.player.getInventory().setSelectedSlot(res.slot());
            return true;
        }

        InvUtils.move().fromId(resInv.slot()).to(selectedSlot);
        return true;
    }

    public void placeItemFrame() {
        if (mc.level == null || mc.player == null || mc.gameMode == null) return;

        BlockPos targetPos = mc.player.blockPosition().above(3);

        if (mc.level.getBlockState(targetPos).isAir()) return;

        Direction face = Direction.DOWN;

        Vec3 hitPos = Vec3.atCenterOf(targetPos);
        BlockHitResult hit = new BlockHitResult(hitPos, face, targetPos, false);

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    public void interactItemFrame(ItemFrame frame) {
        if (mc.player == null || mc.gameMode == null) return;

        mc.gameMode.interact(mc.player, frame, new EntityHitResult(frame), InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    public void attackItemFrame(ItemFrame frame) {
        if (mc.player == null || mc.gameMode == null) return;

        mc.gameMode.attack(mc.player, frame);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean getShulker() {
        if (mc.player == null) return false;

        if (smartShulkerQueue.get()) {
            if (shulkerQueue.isEmpty()) return false;
            if (shulkerQueueIndex >= shulkerQueue.size()) {
                shulkerQueueIndex = 0;
            }

            ItemStack shulkerStack = shulkerQueue.get(shulkerQueueIndex);
            shulkerQueueIndex++;
            if (shulkerStack.isEmpty()) return false;

            FindItemResult hotbarRes = InvUtils.findInHotbar(itemStack -> {
                Item item = itemStack.getItem();
                return isShulkerBox(item) && itemStack.getCount() > 0 && itemStack.is(shulkerStack.getItem());
            });

            if (hotbarRes.count() > 0) {
                ItemStack itemStack = mc.player.getInventory().getItem(hotbarRes.slot());
                if (itemStack.isEmpty()) return false;

                if (itemStack.getDisplayName().equals(shulkerStack.getDisplayName()) && itemStack.getItem() == shulkerStack.getItem()) {
                    mc.player.getInventory().setSelectedSlot(hotbarRes.slot());
                    return true;
                }
                return false;
            }

            FindItemResult invRes = InvUtils.find(itemStack -> {
                Item item = itemStack.getItem();
                return isShulkerBox(item) && itemStack.getCount() > 0 && itemStack.is(shulkerStack.getItem());
            });

            if (invRes.count() > 0) {
                for (int i = 0; i < invRes.count(); i++) {
                    ItemStack itemStack = mc.player.getInventory().getItem(invRes.slot());
                    if (itemStack.getDisplayName().equals(shulkerStack.getDisplayName()) && itemStack.getItem() == shulkerStack.getItem()) {
                        InvUtils.move().fromId(invRes.slot()).to(mc.player.getInventory().getSelectedSlot());
                        return true;
                    }
                }
            }

            return false;
        }

        FindItemResult invRes = InvUtils.find(itemStack -> {
            Item item = itemStack.getItem();
            return isShulkerBox(item) && itemStack.getCount() > 0;
        });

        FindItemResult hotbarRes = InvUtils.findInHotbar(itemStack -> {
            Item item = itemStack.getItem();
            return isShulkerBox(item) && itemStack.getCount() > 0;
        });

        if (invRes.count() <= 0 && hotbarRes.count() <= 0) return false;

        if (hotbarRes.count() > 0) {
            mc.player.getInventory().setSelectedSlot(hotbarRes.slot());
            return true;
        }

        InvUtils.move().fromId(invRes.slot()).to(mc.player.getInventory().getSelectedSlot());
        return true;
    }

    private boolean isShulkerBox(Item item) {
        return item.builtInRegistryHolder().is(ItemTags.SHULKER_BOXES);
    }
}
