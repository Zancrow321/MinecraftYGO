package io.github.zancrow321.jadm.tournament;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.zancrow321.jadm.engine.tournament.Bracket;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * {@code /jadm tournament ...}: anyone can open the window, join, leave, say they're ready and open their draft or
 * deck building window ({@code deck}); hosts (operators, or
 * any player with {@code playersCanHost}) create, set up, start and call off; operators manage the arenas.
 */
public final class TournamentCommands {
    private TournamentCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tournament")
                .executes(ctx -> {
                    manager(ctx).open(ctx.getSource().getPlayerOrException(), "default");
                    return 1;
                })
                .then(tab("bracket"))
                .then(tab("table"))
                .then(tab("matches"))
                .then(tab("rules"))
                .then(Commands.literal("create")
                        .executes(ctx -> host(ctx, m -> m.create(player(ctx), null, null)))
                        .then(Commands.argument("format", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(java.util.stream.Stream
                                        .concat(Arrays.stream(Bracket.Format.values()).map(f -> f.id),
                                                java.util.stream.Stream.of("sealed", "draft")), b))
                                .executes(ctx -> host(ctx, m -> m.create(player(ctx),
                                        StringArgumentType.getString(ctx, "format"), null)))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> host(ctx, m -> m.create(player(ctx),
                                                StringArgumentType.getString(ctx, "format"),
                                                StringArgumentType.getString(ctx, "name")))))))
                .then(Commands.literal("join").executes(ctx -> anyone(ctx, m -> m.join(player(ctx)))))
                .then(Commands.literal("leave").executes(ctx -> anyone(ctx, m -> m.leave(player(ctx)))))
                .then(Commands.literal("ready").executes(ctx -> anyone(ctx, m -> m.ready(player(ctx)))))
                .then(Commands.literal("deck").executes(ctx -> anyone(ctx, m -> m.openLimited(player(ctx)))))
                .then(Commands.literal("status").executes(TournamentCommands::status))
                .then(Commands.literal("settings").executes(TournamentCommands::settings))
                .then(Commands.literal("start").executes(ctx -> host(ctx, m -> m.start(player(ctx)))))
                .then(Commands.literal("cancel").executes(ctx -> host(ctx, TournamentManager::cancel)))
                .then(Commands.literal("set")
                        .then(Commands.argument("setting", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(TournamentOptions.all()
                                        .stream().filter(TournamentOptions.Option::perTournament)
                                        .map(TournamentOptions.Option::key), b))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                                values(StringArgumentType.getString(ctx, "setting")), b))
                                        .executes(ctx -> host(ctx, m -> {
                                            String key = StringArgumentType.getString(ctx, "setting");
                                            String error = m.set(key, StringArgumentType.getString(ctx, "value"));
                                            if (error == null) {
                                                ctx.getSource().sendSuccess(() -> Component.literal(
                                                        TournamentOptions.option(key).key() + " = "
                                                                + m.current().setting(TournamentOptions.option(key)
                                                                .key())), false);
                                            }
                                            return error;
                                        })))))
                .then(Commands.literal("addnpc")
                        .executes(ctx -> host(ctx, m -> m.addNpcs(1)))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> host(ctx, m -> m.addNpcs(IntegerArgumentType.getInteger(ctx,
                                        "count"))))))
                .then(Commands.literal("kick")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(entrantNames(ctx), b))
                                .executes(ctx -> host(ctx, m -> m.kick(StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("award")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(entrantNames(ctx), b))
                                .executes(ctx -> anyone(ctx, m -> m.award(StringArgumentType.getString(ctx,
                                        "name"))))))
                .then(Commands.literal("arena")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("add").executes(ctx -> anyone(ctx, m -> m.addArena(player(ctx)))))
                        .then(Commands.literal("remove")
                                .executes(ctx -> anyone(ctx, m -> m.removeArena(player(ctx), 0)))
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(ctx -> anyone(ctx, m -> m.removeArena(player(ctx),
                                                IntegerArgumentType.getInteger(ctx, "number"))))))
                        .then(Commands.literal("list").executes(ctx -> {
                            List<String> lines = manager(ctx).arenaLines();
                            ctx.getSource().sendSuccess(() -> Component.literal(lines.isEmpty()
                                    ? "No arena belongs to tournaments. Stand on a Duel Arena and run "
                                    + "/jadm tournament arena add." : String.join("\n", lines)), false);
                            return lines.size();
                        })));
    }

    /** {@code /jadm tournament table}: the window at that tab. */
    private static LiteralArgumentBuilder<CommandSourceStack> tab(String name) {
        return Commands.literal(name).executes(ctx -> {
            manager(ctx).open(ctx.getSource().getPlayerOrException(), name);
            return 1;
        });
    }

    /** Suggestions for a setting's value. */
    private static List<String> values(String key) {
        TournamentOptions.Option o = TournamentOptions.option(key);
        if (o == null) {
            return List.of();
        }
        return switch (o.key()) {
            case "format" -> Arrays.stream(Bracket.Format.values()).map(f -> f.id).toList();
            case "npcFill" -> List.of("none", "min", "bracket");
            case "npcMatches" -> List.of("play", "coinflip");
            case "deckMode" -> List.of("constructed", "sealed", "draft");
            case "limitedPacks" -> List.of("pack 5", "pack:LOB 5", "pack:LOB 3; pack:MRD 2");
            case "ruleset" -> List.of("server", "auto", "mr1", "goat", "mr2", "mr3", "mr4", "modern");
            case "banlist" -> List.of("server", "auto", "none");
            case "entryFeeCurrency" -> List.of("currency", "points", "minecraft:emerald");
            case "bestOf" -> List.of("1", "3", "5");
            case "topCut" -> List.of("0", "4", "8", "16");
            default -> switch (o.kind()) {
                case BOOL -> List.of("true", "false");
                case LIST -> List.of(TournamentOptions.configured(o).isEmpty() ? "none"
                        : TournamentOptions.configured(o));
                default -> List.of(TournamentOptions.configured(o));
            };
        };
    }

    private static List<String> entrantNames(CommandContext<CommandSourceStack> ctx) {
        Tournament t = TournamentManager.get(ctx.getSource().getServer()).current();
        return t == null ? List.of() : t.entrants.stream().map(e -> e.name).toList();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        TournamentManager m = manager(ctx);
        Tournament t = m.current();
        if (t == null) {
            ctx.getSource().sendSuccess(() -> Component.literal("There is no tournament yet."), false);
            return 0;
        }
        TournamentView v = TournamentView.of(t, m);
        StringBuilder out = new StringBuilder("\"" + t.name + "\" (" + v.formatName + "): " + v.status);
        if (t.state.equals(Tournament.OPEN)) {
            out.append("\nDuelists: ").append(String.join(", ", v.entrants));
        } else if (t.bracket != null) {
            for (Bracket.Match match : t.bracket.matches) {
                String live = v.live.get(match.id);
                if (match.playable() || live != null) {
                    out.append("\n").append(t.bracket.title(match)).append(": ").append(t.entrants.get(match.a).name)
                            .append(" vs ").append(t.entrants.get(match.b).name)
                            .append(t.bracket.bestOf > 1 ? " (" + match.winsA + "-" + match.winsB + ")" : "")
                            .append(live != null ? ", " + live.toLowerCase(java.util.Locale.ROOT) : "");
                }
            }
            if (t.state.equals(Tournament.DONE)) {
                t.entrants.stream().filter(e -> e.place > 0 && e.place <= 8)
                        .sorted(java.util.Comparator.comparingInt(e -> e.place))
                        .forEach(e -> out.append("\n").append(TournamentManager.ordinal(e.place)).append(" ")
                                .append(e.name));
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static int settings(CommandContext<CommandSourceStack> ctx) {
        Tournament t = manager(ctx).current();
        boolean live = t != null && t.state.equals(Tournament.OPEN);
        StringBuilder out = new StringBuilder(live ? "Settings of \"" + t.name + "\" (change with /jadm tournament "
                + "set <setting> <value>):" : "Tournament defaults from the server config:");
        for (TournamentOptions.Option o : TournamentOptions.all()) {
            if (o.perTournament() || !live) {
                out.append("\n").append(o.key()).append(" = ")
                        .append(live ? t.setting(o.key()) : TournamentOptions.configured(o));
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return 1;
    }

    private static TournamentManager manager(CommandContext<CommandSourceStack> ctx) {
        return TournamentManager.get(ctx.getSource().getServer());
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Runs a command anyone may use; a returned text is the reason it failed. */
    private static int anyone(CommandContext<CommandSourceStack> ctx, Function<TournamentManager, String> action)
            throws CommandSyntaxException {
        ctx.getSource().getPlayerOrException();
        String error = action.apply(manager(ctx));
        if (error != null) {
            ctx.getSource().sendFailure(Component.literal(error));
            return 0;
        }
        return 1;
    }

    /** Runs a host command: for operators, the tournament's host, or anyone with {@code playersCanHost}. */
    private static int host(CommandContext<CommandSourceStack> ctx, Function<TournamentManager, String> action)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!manager(ctx).canHost(player)) {
            ctx.getSource().sendFailure(Component.literal("Only operators (or the tournament's host) can do that.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return anyone(ctx, action);
    }
}
