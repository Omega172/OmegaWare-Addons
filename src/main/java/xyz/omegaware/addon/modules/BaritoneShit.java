package xyz.omegaware.addon.modules;

// So far this is just a copy of the original Baritone Build command
// Planned features:
// - Somehow add a way to set "resupply" containers
// - when the build process gets stuck or runs out of items, it should automatically go to the nearest container and resupply
// - if the container contains shulker boxes, it should open the shulkers and take the items out
// -  it should only take 1-3 stacks of a required item at a time
// - if its inventory becomes full, it should somehow store the items. (maybe in a shulker that it should carry? probably with a specific name)
// - If the shulker still has items needed for the build, it should attempt to hold on to the shulker in its inventory
// - if the container contains items that are not in the schematic, said items should be ignored
// - once the resupply is done, it should return to the build process and continue building
// - once the build is done it should attempt to return any unused materials to where they came from

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.IBaritoneProvider;
import baritone.api.command.Command;
import baritone.api.command.argument.IArgConsumer;
import baritone.api.command.datatypes.RelativeBlockPos;
import baritone.api.command.datatypes.RelativeFile;
import baritone.api.utils.BetterBlockPos;

import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import xyz.omegaware.addon.OmegawareAddons;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

class BetterBuildCommand extends Command {
    private final File schematicsDir;

    public BetterBuildCommand(IBaritone baritone) {
        super(baritone, "better_build");
        this.schematicsDir = new File(baritone.getPlayerContext().minecraft().runDirectory, "schematics");
    }

    @Override
    public void execute(String label, IArgConsumer args) {
        final File file = args.getDatatypePost(RelativeFile.INSTANCE, schematicsDir).getAbsoluteFile();
        BetterBlockPos origin = ctx.playerFeet();
        BetterBlockPos buildOrigin;
        if (args.hasAny()) {
            args.requireMax(3);
            buildOrigin = args.getDatatypePost(RelativeBlockPos.INSTANCE, origin);
        } else {
            args.requireMax(0);
            buildOrigin = origin;
        }
        boolean success = baritone.getBuilderProcess().build(file.getName(), file, buildOrigin);
        logDirect(String.format("Successfully loaded schematic for building\nOrigin: %s", buildOrigin));

        if (baritone.getBuilderProcess().isPaused() || baritone.getBuilderProcess().isTemporary()) {
            // Do shit, based on the build task not being a discarded task
            // this is temporary flag defines if a task is "resumable"
        }
    }

    @Override
    public Stream<String> tabComplete(String label, IArgConsumer args) {
        if (args.hasExactlyOne()) {
            return RelativeFile.tabComplete(args, schematicsDir);
        } else if (args.has(2)) {
            args.get();
            return args.tabCompleteDatatype(RelativeBlockPos.INSTANCE);
        }
        return Stream.empty();
    }

    @Override
    public String getShortDesc() {
        return "Build a schematic but baritone is not stupid";
    }

    @Override
    public List<String> getLongDesc() {
        return Arrays.asList(
            "Build a schematic from a file.",
            "",
            "Usage:",
            "> better_build <filename> - Loads and builds '<filename>.schematic'",
            "> better_build <filename> <x> <y> <z> - Custom position"
        );
    }
}

public class BaritoneShit extends Module {
    public BaritoneShit() {
        super(OmegawareAddons.CATEGORY, "Baritone-Shit", "This module adds some shit to Baritone");
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private void Shit() {
        IBaritoneProvider baritoneProvider  = BaritoneAPI.getProvider();
        baritoneProvider.getPrimaryBaritone().getCommandManager().getRegistry().register(new BetterBuildCommand(baritoneProvider.getPrimaryBaritone()));
    }
}
