package io.github.zancrow321.minecraftygo;

import com.mojang.brigadier.context.CommandContext;
import io.github.zancrow321.minecraftygo.engine.OcgCore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The {@code /ygo} command tree.
 */
final class YgoCommands {
    private YgoCommands() {
    }

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ygo")
                .then(Commands.literal("version").executes(YgoCommands::version)));
    }

    private static int version(CommandContext<CommandSourceStack> context) {
        try {
            OcgCore.Version version = OcgCore.get().version();
            context.getSource().sendSuccess(() -> Component.translatable("commands.minecraftygo.version",
                    version.toString()), false);
            return 1;
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            context.getSource().sendFailure(Component.translatable("commands.minecraftygo.version.unavailable",
                    e.getMessage()));
            return 0;
        }
    }
}
