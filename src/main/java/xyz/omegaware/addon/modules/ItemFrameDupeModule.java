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
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import xyz.omegaware.addon.OmegawareAddons;

import java.util.ArrayList;
import java.util.List;

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

    private final Setting<Boolean> dropInvIfFull = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-inventory-if-full")
        .defaultValue(true)
        .build()
    );

    public static ArrayList<ItemStack> shulkerQueue = new ArrayList<>();
    int shulkerQueueIndex = 0;
    int currentRotationCount = 0;
    int forceDelay = 0;

    @EventHandler
    private void onTickPost(TickEvent.Post event) {
        if (!isActive()) return;

        if (forceDelay != 0) {
            forceDelay--;
            return;
        }

        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.world.getBlockState(mc.player.getBlockPos().up(3)).isAir()) return;
        if (mc.interactionManager.getCurrentGameMode() != GameMode.SURVIVAL) return;

        Box blockAbovePlayer = new Box(mc.player.getBlockPos().up(2));
        List<Entity> entitiesAbovePlayer = mc.world.getOtherEntities(null, blockAbovePlayer);
        entitiesAbovePlayer.removeIf(entity -> !(entity instanceof ItemFrameEntity));

        if (entitiesAbovePlayer.isEmpty()) {
            if (!getItemFrame()) return;
            placeItemFrame();
            forceDelay = placeToInsertDelay.get();
            return;
        }

        if (dropInvIfFull.get()) {
            FindItemResult res = InvUtils.findEmpty();
            if (!res.found()) {
                mc.player.setPitch(90.0f);
                for (int i = 9; i < mc.player.getInventory().size(); i++) {
                    InvUtils.drop().slot(i);
                }
            }
        }

        ItemFrameEntity frame = (ItemFrameEntity)entitiesAbovePlayer.getFirst();
        if (frame.getHeldItemStack().isEmpty()) {
            ItemStack itemStack = mc.player.getMainHandStack();
            if (!itemStack.isEmpty()) {
                interactItemFrame(frame);
                forceDelay = insertToRotateDelay.get();
            }
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

        int selectedSlot = mc.player.getInventory().selectedSlot;
        if (mc.player.getInventory().getStack(selectedSlot).getItem() == Items.ITEM_FRAME) return true;

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
        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;

        BlockPos targetPos = mc.player.getBlockPos().up(3);

        if (mc.world.getBlockState(targetPos).isAir()) return;

        Direction face = Direction.DOWN;

        Vec3d hitPos = Vec3d.ofCenter(targetPos);
        BlockHitResult hit = new BlockHitResult(hitPos, face, targetPos, false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public void interactItemFrame(ItemFrameEntity frame) {
        if (mc.player == null || mc.interactionManager == null) return;

        mc.interactionManager.interactEntity(mc.player, frame, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    public void attackItemFrame(ItemFrameEntity frame) {
        if (mc.player == null || mc.interactionManager == null) return;

        mc.interactionManager.attackEntity(mc.player, frame);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
