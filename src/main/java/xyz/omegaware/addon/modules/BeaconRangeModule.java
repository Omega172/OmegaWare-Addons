package xyz.omegaware.addon.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import xyz.omegaware.addon.OmegawareAddons;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;

public class BeaconRangeModule extends Module {
    public BeaconRangeModule() {
        super(OmegawareAddons.CATEGORY, "beacon-range", "A module that renders a box to show the range of powered beacons.");
    }

    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> cullOverlapping = sgRender.add(new BoolSetting.Builder()
        .name("cull-overlapping")
        .description("Cull overlapping boxes.")
        .defaultValue(true)
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

    private static class Rect2D {
        double x1, y1, x2, y2;
        Rect2D(double x1, double y1, double x2, double y2) {
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
        }
        boolean intersects(Rect2D o) {
            return x1 < o.x2 && x2 > o.x1 && y1 < o.y2 && y2 > o.y1;
        }
        List<Rect2D> subtract(Rect2D o) {
            List<Rect2D> out = new ArrayList<>();
            if (!intersects(o)) {
                out.add(this);
                return out;
            }
            // Top
            if (y1 < o.y1) out.add(new Rect2D(x1, y1, x2, o.y1));
            // Bottom
            if (y2 > o.y2) out.add(new Rect2D(x1, o.y2, x2, y2));
            // Left
            if (x1 < o.x1) out.add(new Rect2D(x1, Math.max(y1, o.y1), o.x1, Math.min(y2, o.y2)));
            // Right
            if (x2 > o.x2) out.add(new Rect2D(o.x2, Math.max(y1, o.y1), x2, Math.min(y2, o.y2)));
            return out;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        List<Box> renderedBoxes = new ArrayList<>();
        double epsilon = 0.001;

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof BeaconBlockEntity)) continue;

            BlockPos pos = blockEntity.getPos();
            int level = getBeaconLevel(blockEntity.getWorld(), pos);
            if (level < 1) continue;

            int range = 10 + (level * 10);

            double x1 = pos.getX() - range;
            double y1 = pos.getY() - range;
            double z1 = pos.getZ() - range;
            double x2 = pos.getX() + range;
            double y2 = pos.getY() + range;
            double z2 = pos.getZ() + range;

            List<Box> toRender = new ArrayList<>();
            toRender.add(new Box(x1, y1, z1, x2, y2, z2));

            if (cullOverlapping.get()) {
                for (Box prev : renderedBoxes) {
                    List<Box> next = new ArrayList<>();
                    for (Box box : toRender) {
                        next.addAll(subtractBox(box, prev));
                    }
                    toRender = next;
                    if (toRender.isEmpty()) break;
                }
            }

            renderedBoxes.add(new Box(x1, y1, z1, x2, y2, z2));

            // Per-face, per-section rendering
            for (Box box : toRender) {
                for (int axis = 0; axis < 3; axis++) {
                    for (boolean isMin : new boolean[]{true, false}) {
                        Rect2D faceRect;
                        double fx, fxE;
                        switch (axis) {
                            case 0: // X
                                fx = isMin ? box.minX : box.maxX;
                                faceRect = new Rect2D(box.minZ, box.minY, box.maxZ, box.maxY);
                                break;
                            case 1: // Y
                                fx = isMin ? box.minY : box.maxY;
                                faceRect = new Rect2D(box.minX, box.minZ, box.maxX, box.maxZ);
                                break;
                            case 2: // Z
                                fx = isMin ? box.minZ : box.maxZ;
                                faceRect = new Rect2D(box.minX, box.minY, box.maxX, box.maxY);
                                break;
                            default: continue;
                        }
                        List<Rect2D> visible = new ArrayList<>();
                        visible.add(faceRect);
                        for (Box other : renderedBoxes) {
                            if (other == box) break; // Only check previous boxes
                            boolean sharesFace = false;
                            Rect2D overlap = null;
                            switch (axis) {
                                case 0:
                                    sharesFace = Math.abs((isMin ? other.maxX : other.minX) - fx) < 1e-6 &&
                                        other.minY < box.maxY && other.maxY > box.minY &&
                                        other.minZ < box.maxZ && other.maxZ > box.minZ;
                                    if (sharesFace) overlap = new Rect2D(
                                        Math.max(box.minZ, other.minZ),
                                        Math.max(box.minY, other.minY),
                                        Math.min(box.maxZ, other.maxZ),
                                        Math.min(box.maxY, other.maxY)
                                    );
                                    break;
                                case 1:
                                    sharesFace = Math.abs((isMin ? other.maxY : other.minY) - fx) < 1e-6 &&
                                        other.minX < box.maxX && other.maxX > box.minX &&
                                        other.minZ < box.maxZ && other.maxZ > box.minZ;
                                    if (sharesFace) overlap = new Rect2D(
                                        Math.max(box.minX, other.minX),
                                        Math.max(box.minZ, other.minZ),
                                        Math.min(box.maxX, other.maxX),
                                        Math.min(box.maxZ, other.maxZ)
                                    );
                                    break;
                                case 2:
                                    sharesFace = Math.abs((isMin ? other.maxZ : other.minZ) - fx) < 1e-6 &&
                                        other.minX < box.maxX && other.maxX > box.minX &&
                                        other.minY < box.maxY && other.maxY > box.minY;
                                    if (sharesFace) overlap = new Rect2D(
                                        Math.max(box.minX, other.minX),
                                        Math.max(box.minY, other.minY),
                                        Math.min(box.maxX, other.maxX),
                                        Math.min(box.maxY, other.maxY)
                                    );
                                    break;
                            }
                            if (sharesFace) {
                                List<Rect2D> next = new ArrayList<>();
                                for (Rect2D r : visible) next.addAll(r.subtract(overlap));
                                visible = next;
                                if (visible.isEmpty()) break;
                            }
                        }
                        for (Rect2D r : visible) {
                            switch (axis) {
                                case 0: // X
                                    fx = isMin ? box.minX : box.maxX;
                                    fxE = isMin ? fx + epsilon : fx - epsilon;
                                    event.renderer.box(fx, r.y1, r.x1, fxE, r.y2, r.x2, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                                    break;
                                case 1: // Y
                                    fx = isMin ? box.minY : box.maxY;
                                    fxE = isMin ? fx + epsilon : fx - epsilon;
                                    event.renderer.box(r.x1, fx, r.y1, r.x2, fxE, r.y2, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                                    break;
                                case 2: // Z
                                    fx = isMin ? box.minZ : box.maxZ;
                                    fxE = isMin ? fx + epsilon : fx - epsilon;
                                    event.renderer.box(r.x1, r.y1, fx, r.x2, r.y2, fxE, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    private List<Box> subtractBox(Box box, Box subtract) {
        List<Box> result = new ArrayList<>();
        if (!box.intersects(subtract)) {
            result.add(box);
            return result;
        }

        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;

        double sx1 = subtract.minX, sy1 = subtract.minY, sz1 = subtract.minZ;
        double sx2 = subtract.maxX, sy2 = subtract.maxY, sz2 = subtract.maxZ;

        // Left
        if (x1 < sx1)
            result.add(new Box(x1, y1, z1, sx1, y2, z2));
        // Right
        if (x2 > sx2)
            result.add(new Box(sx2, y1, z1, x2, y2, z2));
        // Bottom
        if (y1 < sy1)
            result.add(new Box(Math.max(x1, sx1), y1, z1, Math.min(x2, sx2), sy1, z2));
        // Top
        if (y2 > sy2)
            result.add(new Box(Math.max(x1, sx1), sy2, z1, Math.min(x2, sx2), y2, z2));
        // Front
        if (z1 < sz1)
            result.add(new Box(Math.max(x1, sx1), Math.max(y1, sy1), z1, Math.min(x2, sx2), Math.min(y2, sy2), sz1));
        // Back
        if (z2 > sz2)
            result.add(new Box(Math.max(x1, sx1), Math.max(y1, sy1), sz2, Math.min(x2, sx2), Math.min(y2, sy2), z2));

        return result;
    }

    public static int getBeaconLevel(World world, BlockPos beaconPos) {
        int level = 0;

        for (int y = 1; y <= 4; y++) {
            int layerY = beaconPos.getY() - y;
            if (layerY < world.getBottomY()) break;

            boolean validLayer = true;

            for (int x = -y; x <= y; x++) {
                for (int z = -y; z <= y; z++) {
                    BlockPos checkPos = beaconPos.add(x, -y, z);
                    if (!isValidBeaconBase(world, checkPos)) {
                        validLayer = false;
                        break;
                    }
                }
                if (!validLayer) break;
            }

            if (validLayer) {
                level++;
            } else {
                break;
            }
        }

        return level;
    }

    private static boolean isValidBeaconBase(World world, BlockPos pos) {
        return world.getBlockState(pos).isOf(Blocks.IRON_BLOCK)
            || world.getBlockState(pos).isOf(Blocks.GOLD_BLOCK)
            || world.getBlockState(pos).isOf(Blocks.EMERALD_BLOCK)
            || world.getBlockState(pos).isOf(Blocks.DIAMOND_BLOCK)
            || world.getBlockState(pos).isOf(Blocks.NETHERITE_BLOCK);
    }
    // End of BeaconCache
}
