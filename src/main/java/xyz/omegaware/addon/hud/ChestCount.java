package xyz.omegaware.addon.hud;

import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import xyz.omegaware.addon.OmegawareAddons;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class ChestCount extends HudElement {
    public static final HudElementInfo<ChestCount> INFO = new HudElementInfo<>(OmegawareAddons.HUD_GROUP, "Dubs Count", "Displays how many dubs are in render distance", ChestCount::new);

    public ChestCount() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (mc.player == null || mc.level == null) return;

        int count = 0;
        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof ChestBlockEntity chestBlock)) continue;

            BlockState blockState = chestBlock.getBlockState();
            ChestType chestType = blockState.getValue(ChestBlock.TYPE);

            // Only count left chests to avoid double counting
            if (chestType.equals(ChestType.SINGLE) || chestType.equals(ChestType.RIGHT)) continue;

            count++;
        }

        renderer.text("Dubs: ", x, y, Color.WHITE, true);
        renderer.text("" + count, x + renderer.textWidth("Dubs: ", true), y, Color.LIGHT_GRAY, true);

        setSize(renderer.textWidth("Dubs: " + count, true), renderer.textHeight(true) + 1);
    }
}
