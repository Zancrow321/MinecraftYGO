package io.github.zancrow321.jadm.clan;

import com.google.gson.Gson;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.JadmServerConfig;
import io.github.zancrow321.jadm.network.ClanPayload;
import io.github.zancrow321.jadm.points.Points;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * {@code /jadm clan}: founding, joining and running a clan, its crest and treasury, clan chat and clan wars; for
 * operators {@code /jadm clan admin}.
 */
public final class ClanCommands {
    private static final Gson GSON = new Gson();
    private static final Pattern TAG = Pattern.compile("[A-Za-z0-9]{2,5}");
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} '&.!-]{3,24}");
    private static final int MOTTO = 60;
    private static final long INVITE_TICKS = 20 * 60 * 5;
    private static final int TOP = 10;

    private ClanCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("clan")
                .executes(ctx -> open(ctx.getSource().getPlayerOrException()))
                .then(Commands.literal("create")
                        .then(Commands.argument("tag", StringArgumentType.word())
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ClanCommands::create))))
                .then(Commands.literal("info").executes(ctx -> info(ctx, null))
                        .then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(ClanCommands::tags)
                                .executes(ctx -> info(ctx, StringArgumentType.getString(ctx, "clan")))))
                .then(Commands.literal("top").executes(ClanCommands::top))
                .then(Commands.literal("invite")
                        .then(Commands.argument("player", EntityArgument.player()).executes(ClanCommands::invite)))
                .then(Commands.literal("join")
                        .then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(ClanCommands::tags)
                                .executes(ClanCommands::join)))
                .then(Commands.literal("leave").executes(ClanCommands::leave))
                .then(Commands.literal("kick")
                        .then(Commands.argument("member", StringArgumentType.word()).suggests(ClanCommands::members)
                                .executes(ClanCommands::kick)))
                .then(Commands.literal("promote")
                        .then(Commands.argument("member", StringArgumentType.word()).suggests(ClanCommands::members)
                                .executes(ctx -> setRole(ctx, Clans.Role.OFFICER))))
                .then(Commands.literal("demote")
                        .then(Commands.argument("member", StringArgumentType.word()).suggests(ClanCommands::members)
                                .executes(ctx -> setRole(ctx, Clans.Role.MEMBER))))
                .then(Commands.literal("leader")
                        .then(Commands.argument("member", StringArgumentType.word()).suggests(ClanCommands::members)
                                .executes(ClanCommands::transfer)))
                .then(Commands.literal("disband")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("This ends the clan for good; its "
                                    + "treasury goes to you. To disband it, run ")
                                    .append(Component.literal("/jadm clan disband confirm")
                                            .withStyle(ChatFormatting.YELLOW)), false);
                            return 0;
                        })
                        .then(Commands.literal("confirm").executes(ClanCommands::disband)))
                .then(Commands.literal("crest").executes(ClanCommands::crest))
                .then(Commands.literal("banner").executes(ClanCommands::banner))
                .then(Commands.literal("color")
                        .then(Commands.argument("color", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ClanText.COLORS, b))
                                .executes(ClanCommands::color)))
                .then(Commands.literal("motto").executes(ctx -> motto(ctx, ""))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> motto(ctx, StringArgumentType.getString(ctx, "text")))))
                .then(Commands.literal("rename")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(ClanCommands::rename)))
                .then(Commands.literal("tag")
                        .then(Commands.argument("tag", StringArgumentType.word()).executes(ClanCommands::retag)))
                .then(Commands.literal("open")
                        .then(Commands.argument("open", BoolArgumentType.bool()).executes(ClanCommands::setOpen)))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(ClanCommands::deposit)))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                .executes(ctx -> pay(ctx, null))))
                .then(Commands.literal("pay")
                        .then(Commands.argument("member", StringArgumentType.word()).suggests(ClanCommands::members)
                                .then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(ctx -> pay(ctx, StringArgumentType.getString(ctx, "member"))))))
                .then(Commands.literal("chat")
                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                .executes(ClanCommands::chat)))
                .then(Commands.literal("war").executes(ClanCommands::warStatus)
                        .then(Commands.literal("declare")
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(ClanCommands::tags)
                                        .executes(ctx -> declare(ctx, 0, Clans.War.RACE))
                                        .then(Commands.argument("stake", LongArgumentType.longArg(0))
                                                .executes(ctx -> declare(ctx, LongArgumentType.getLong(ctx, "stake"),
                                                        Clans.War.RACE)))
                                        .then(Commands.literal(Clans.War.RACE)
                                                .executes(ctx -> declare(ctx, 0, Clans.War.RACE))
                                                .then(Commands.argument("stake", LongArgumentType.longArg(0))
                                                        .executes(ctx -> declare(ctx,
                                                                LongArgumentType.getLong(ctx, "stake"),
                                                                Clans.War.RACE))))
                                        .then(Commands.literal(Clans.War.BATTLE)
                                                .executes(ctx -> declare(ctx, 0, Clans.War.BATTLE))
                                                .then(Commands.argument("stake", LongArgumentType.longArg(0))
                                                        .executes(ctx -> declare(ctx,
                                                                LongArgumentType.getLong(ctx, "stake"),
                                                                Clans.War.BATTLE))))))
                        .then(Commands.literal("lineup").executes(ClanCommands::showLineup)
                                .then(Commands.argument("members", StringArgumentType.greedyString())
                                        .suggests(ClanCommands::members).executes(ClanCommands::setLineup)))
                        .then(Commands.literal("join").executes(ctx -> signUp(ctx, true)))
                        .then(Commands.literal("leave").executes(ctx -> signUp(ctx, false)))
                        .then(Commands.literal("ready").executes(ClanCommands::ready))
                        .then(Commands.literal("accept").executes(ctx -> answer(ctx, null, true))
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(ClanCommands::tags)
                                        .executes(ctx -> answer(ctx, StringArgumentType.getString(ctx, "clan"),
                                                true))))
                        .then(Commands.literal("deny").executes(ctx -> answer(ctx, null, false))
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(ClanCommands::tags)
                                        .executes(ctx -> answer(ctx, StringArgumentType.getString(ctx, "clan"),
                                                false))))
                        .then(Commands.literal("cancel").executes(ClanCommands::cancel))
                        .then(Commands.literal("surrender")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("Surrendering gives the war "
                                            + "and both stakes to the other clan. To surrender, run ")
                                            .append(Component.literal("/jadm clan war surrender confirm")
                                                    .withStyle(ChatFormatting.YELLOW)), false);
                                    return 0;
                                })
                                .then(Commands.literal("confirm").executes(ClanCommands::surrender))))
                .then(Commands.literal("admin").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("disband")
                                .then(Commands.argument("clan", StringArgumentType.greedyString())
                                        .suggests(ClanCommands::tags).executes(ClanCommands::adminDisband)))
                        .then(Commands.literal("rating")
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(ClanCommands::tags)
                                        .then(Commands.argument("rating", IntegerArgumentType.integer(0, 100_000))
                                                .executes(ClanCommands::adminRating))))
                        .then(Commands.literal("treasury")
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(ClanCommands::tags)
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ClanCommands::adminTreasury))))
                        .then(Commands.literal("endwar")
                                .then(Commands.argument("clan", StringArgumentType.word()).suggests(ClanCommands::tags)
                                        .executes(ClanCommands::adminEndWar)))
                        .then(Commands.literal("season")
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal("This puts every clan back "
                                            + "to the start rating and clears the war records. To start a new clan "
                                            + "season, run ").append(Component.literal("/jadm clan admin season "
                                            + "confirm").withStyle(ChatFormatting.YELLOW)), false);
                                    return 0;
                                })
                                .then(Commands.literal("confirm").executes(ClanCommands::adminSeason))));
    }

    // ---- The window ----

    /** Opens the clan window for {@code player}. */
    public static int open(ServerPlayer player) {
        sync(player, true);
        return 1;
    }

    /** Sends {@code player} the clan window's contents; it opens only if {@code open}. */
    public static void sync(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(player, new ClanPayload(open,
                GSON.toJson(ClanView.of(player.server, player.getUUID()))));
    }

    /** After a change: names in chat and Tab, and open clan windows of the clan's online members. */
    static void refresh(MinecraftServer server, Clans.Clan clan) {
        if (clan == null) {
            return;
        }
        ClanTeams.sync(server, clan);
        for (UUID id : clan.members.keySet()) {
            refresh(server, id);
        }
    }

    static void refresh(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            online.refreshDisplayName();
            online.refreshTabListName();
            sync(online, false);
        }
    }

    // ---- Founding and membership ----

    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans clans = clans(ctx);
        if (!JadmServerConfig.CLANS.enabled.get()) {
            return fail(ctx, "Clans are turned off on this server.");
        }
        if (clans.of(player.getUUID()) != null) {
            return fail(ctx, "You are already in a clan. Leave it first.");
        }
        String tag = StringArgumentType.getString(ctx, "tag");
        String name = StringArgumentType.getString(ctx, "name").trim();
        String why = checkTag(clans, tag, null);
        if (why == null) {
            why = checkName(clans, name, null);
        }
        if (why != null) {
            return fail(ctx, why);
        }
        long price = Points.active() ? JadmServerConfig.CLANS.createPrice.get() : 0;
        if (price > 0 && !Points.get(player.server).take(player.server, player.getUUID(), price)) {
            return fail(ctx, "Founding a clan costs " + Points.format(price) + ".");
        }
        Clans.Clan clan = clans.create(player.getUUID(), player.getScoreboardName(), tag.toUpperCase(), name);
        player.server.getPlayerList().broadcastSystemMessage(Component.literal(player.getScoreboardName()
                + " founded the clan ").withStyle(ChatFormatting.GRAY).append(ClanText.named(clan)).append(
                Component.literal("!").withStyle(ChatFormatting.GRAY)), false);
        player.sendSystemMessage(Component.literal((price > 0 ? "Paid " + Points.format(price) + ". " : "")
                + "Next: hold a banner from the loom and run /jadm clan crest for your crest, and invite duelists with "
                + "/jadm clan invite <player>.").withStyle(ChatFormatting.GRAY));
        refresh(player.server, clan);
        return 1;
    }

    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Clans clans = clans(ctx);
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        if (clans.of(target.getUUID()) != null) {
            return fail(ctx, target.getScoreboardName() + " is already in a clan.");
        }
        if (full(clan)) {
            return fail(ctx, "Your clan is full (" + JadmServerConfig.CLANS.maxMembers.get() + " members).");
        }
        clans.invites.computeIfAbsent(target.getUUID(), k -> new HashMap<>())
                .put(clan.id, player.server.getTickCount() + INVITE_TICKS);
        target.sendSystemMessage(Component.literal(player.getScoreboardName() + " invites you to join the clan ")
                .append(ClanText.named(clan)).append(". ")
                .append(ClanText.button("Join", ChatFormatting.GREEN, "/jadm clan join " + clan.tag)));
        ClanText.tellManagers(player.server, clan, Component.literal(player.getScoreboardName() + " invited "
                + target.getScoreboardName() + " to the clan.").withStyle(ChatFormatting.GRAY));
        sync(target, false);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans clans = clans(ctx);
        Clans.Clan clan = clans.find(StringArgumentType.getString(ctx, "clan").trim());
        if (clan == null) {
            return fail(ctx, "There is no such clan. /jadm clan top lists them.");
        }
        if (!JadmServerConfig.CLANS.enabled.get()) {
            return fail(ctx, "Clans are turned off on this server.");
        }
        Clans.Clan own = clans.of(player.getUUID());
        if (own != null) {
            return fail(ctx, own == clan ? "You are already in that clan." : "You are already in a clan. Leave it "
                    + "first.");
        }
        Long until = clans.invites.getOrDefault(player.getUUID(), Map.of()).get(clan.id);
        boolean invited = until != null && until >= player.server.getTickCount();
        if (!invited && !clan.open) {
            return fail(ctx, "[" + clan.tag + "] only takes duelists its leader or an officer invited.");
        }
        if (full(clan)) {
            return fail(ctx, "[" + clan.tag + "] is full.");
        }
        clans.join(clan, player.getUUID(), player.getScoreboardName());
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " joined the clan ")
                .withStyle(ChatFormatting.GREEN).append(ClanText.tag(clan)).append(
                        Component.literal("!").withStyle(ChatFormatting.GREEN)));
        refresh(player.server, clan);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = member(ctx, player);
        if (clan == null) {
            return 0;
        }
        if (clan.role(player.getUUID()) == Clans.Role.LEADER) {
            return fail(ctx, clan.members.size() > 1
                    ? "You lead the clan: hand that over first with /jadm clan leader <member>, or disband it."
                    : "You are the clan's last member: /jadm clan disband ends it.");
        }
        ClanWars.dropFromLineups(clans(ctx), clan, player.getUUID());
        clans(ctx).leave(clan, player.getUUID());
        ClanTeams.leave(player.server, player.getScoreboardName());
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " left the clan.")
                .withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("You left ").withStyle(ChatFormatting.GRAY)
                .append(ClanText.named(clan)).append("."));
        refresh(player.server, clan);
        refresh(player.server, player.getUUID());
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        UUID target = memberNamed(clan, StringArgumentType.getString(ctx, "member"));
        if (target == null) {
            return fail(ctx, "No member of your clan is called that.");
        }
        if (target.equals(player.getUUID())) {
            return fail(ctx, "Use /jadm clan leave to leave.");
        }
        Clans.Role mine = clan.role(player.getUUID());
        Clans.Role theirs = clan.role(target);
        if (theirs.ordinal() >= mine.ordinal()) {
            return fail(ctx, "Only the leader can remove an officer.");
        }
        String name = clan.members.get(target).name;
        ClanWars.dropFromLineups(clans(ctx), clan, target);
        clans(ctx).leave(clan, target);
        ClanTeams.leave(player.server, name);
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " removed " + name
                + " from the clan.").withStyle(ChatFormatting.GRAY));
        ServerPlayer kicked = player.server.getPlayerList().getPlayer(target);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.literal(player.getScoreboardName() + " removed you from ")
                    .withStyle(ChatFormatting.GRAY).append(ClanText.named(clan)).append("."));
        }
        refresh(player.server, clan);
        refresh(player.server, target);
        return 1;
    }

    private static int setRole(CommandContext<CommandSourceStack> ctx, Clans.Role role) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        UUID target = memberNamed(clan, StringArgumentType.getString(ctx, "member"));
        if (target == null) {
            return fail(ctx, "No member of your clan is called that.");
        }
        Clans.Member m = clan.members.get(target);
        if (m.role == Clans.Role.LEADER) {
            return fail(ctx, "Hand over the lead with /jadm clan leader <member>.");
        }
        if (m.role == role) {
            return fail(ctx, m.name + " already is " + (role == Clans.Role.OFFICER ? "an officer." : "a member."));
        }
        m.role = role;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(m.name + (role == Clans.Role.OFFICER
                ? " is now an officer of the clan." : " is a member again.")).withStyle(ChatFormatting.AQUA));
        refresh(player.server, clan);
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        UUID target = memberNamed(clan, StringArgumentType.getString(ctx, "member"));
        if (target == null || target.equals(player.getUUID())) {
            return fail(ctx, "Name another member of your clan.");
        }
        clan.members.get(player.getUUID()).role = Clans.Role.OFFICER;
        clan.members.get(target).role = Clans.Role.LEADER;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(clan.members.get(target).name
                + " now leads the clan.").withStyle(ChatFormatting.GOLD));
        refresh(player.server, clan);
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        long treasury = disband(player.server, clan);
        if (treasury > 0) {
            Points.get(player.server).add(player.server, player.getUUID(), treasury);
            player.sendSystemMessage(Component.literal("The clan treasury, " + Points.format(treasury)
                    + ", goes to you.").withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    /**
     * Ends a clan: settles its wars (the other side wins a running one), tells everyone and removes it.
     *
     * @return what was in its treasury
     */
    static long disband(MinecraftServer server, Clans.Clan clan) {
        Clans clans = Clans.get(server);
        for (Clans.War war : List.copyOf(clans.warsOf(clan.id))) {
            if (war.running()) {
                ClanWars.finish(server, war, war.other(clan.id), "disband");
            } else {
                ClanWars.callOff(server, war, "[" + clan.tag + "] disbanded.");
            }
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal("The clan ").withStyle(ChatFormatting.GRAY)
                .append(ClanText.named(clan)).append(Component.literal(" has disbanded.")
                        .withStyle(ChatFormatting.GRAY)), false);
        List<UUID> members = List.copyOf(clan.members.keySet());
        long treasury = clan.treasury;
        clans.disband(clan);
        ClanTeams.remove(server, clan);
        members.forEach(id -> refresh(server, id));
        return treasury;
    }

    // ---- The clan's look ----

    private static int crest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        Clans.Crest crest = ClanCrests.of(player.getMainHandItem());
        if (crest == null) {
            crest = ClanCrests.of(player.getOffhandItem());
        }
        if (crest == null) {
            return fail(ctx, "Hold a banner: its colors and patterns become the clan's crest. Make one on a loom.");
        }
        clan.crest = crest;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " gave the clan a new "
                + "crest. /jadm clan banner gets you a copy.").withStyle(ChatFormatting.AQUA));
        refresh(player.server, clan);
        return 1;
    }

    private static int banner(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = member(ctx, player);
        if (clan == null) {
            return 0;
        }
        if (!clan.crest.isSet()) {
            return fail(ctx, "Your clan has no crest yet: the leader or an officer holds a banner and runs /jadm "
                    + "clan crest.");
        }
        long price = Points.active() ? JadmServerConfig.CLANS.bannerPrice.get() : 0;
        if (price > 0 && !Points.get(player.server).take(player.server, player.getUUID(), price)) {
            return fail(ctx, "A clan banner costs " + Points.format(price) + ".");
        }
        ItemStack banner = ClanCrests.banner(player.server.registryAccess(), clan);
        if (!player.getInventory().add(banner)) {
            player.drop(banner, false);
        }
        player.sendSystemMessage(Component.literal("Here is your clan banner" + (price > 0 ? " (" + Points.format(price)
                + ")" : "") + ".").withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int color(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        String color = StringArgumentType.getString(ctx, "color").toLowerCase();
        if (!ClanText.COLORS.contains(color)) {
            return fail(ctx, "Pick one of: " + String.join(", ", ClanText.COLORS) + ".");
        }
        clan.color = color;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal("The clan's color is now ").withStyle(ChatFormatting.GRAY)
                .append(ClanText.tag(clan)).append("."));
        refresh(player.server, clan);
        return 1;
    }

    private static int motto(CommandContext<CommandSourceStack> ctx, String text) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        String motto = text.trim();
        if (motto.length() > MOTTO) {
            return fail(ctx, "A motto has " + MOTTO + " characters at most.");
        }
        clan.motto = motto;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(motto.isEmpty() ? "The clan motto was cleared."
                : "New clan motto: \"" + motto + "\"").withStyle(ChatFormatting.GRAY));
        refresh(player.server, clan);
        return 1;
    }

    private static int rename(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name").trim();
        String why = checkName(clans(ctx), name, clan);
        if (why != null) {
            return fail(ctx, why);
        }
        clan.name = name;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal("The clan is now called ").withStyle(ChatFormatting.GRAY)
                .append(ClanText.named(clan)).append("."));
        refresh(player.server, clan);
        return 1;
    }

    private static int retag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        String tag = StringArgumentType.getString(ctx, "tag");
        String why = checkTag(clans(ctx), tag, clan);
        if (why != null) {
            return fail(ctx, why);
        }
        clan.tag = tag.toUpperCase();
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal("The clan's tag is now ").withStyle(ChatFormatting.GRAY)
                .append(ClanText.tag(clan)).append("."));
        refresh(player.server, clan);
        return 1;
    }

    private static int setOpen(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        clan.open = BoolArgumentType.getBool(ctx, "open");
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(clan.open
                ? "Anyone can join the clan now with /jadm clan join " + clan.tag + "."
                : "The clan takes only invited duelists now.").withStyle(ChatFormatting.GRAY));
        refresh(player.server, clan);
        return 1;
    }

    // ---- Treasury ----

    private static int deposit(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = member(ctx, player);
        if (clan == null) {
            return 0;
        }
        if (!Points.active()) {
            return fail(ctx, "The clan treasury holds Duel Points, which this server doesn't use.");
        }
        long amount = LongArgumentType.getLong(ctx, "amount");
        if (!Points.get(player.server).take(player.server, player.getUUID(), amount)) {
            return fail(ctx, "You don't have " + Points.format(amount) + ".");
        }
        clan.treasury += amount;
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " paid "
                + Points.format(amount) + " into the clan treasury (now " + Points.format(clan.treasury) + ").")
                .withStyle(ChatFormatting.GRAY));
        refresh(player.server, clan);
        return 1;
    }

    /** The leader pays out of the treasury, to {@code memberName} or to themselves if {@code null}. */
    private static int pay(CommandContext<CommandSourceStack> ctx, String memberName) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        if (!Points.active()) {
            return fail(ctx, "The clan treasury holds Duel Points, which this server doesn't use.");
        }
        UUID to = memberName == null ? player.getUUID() : memberNamed(clan, memberName);
        if (to == null) {
            return fail(ctx, "No member of your clan is called that.");
        }
        long amount = LongArgumentType.getLong(ctx, "amount");
        if (amount > clan.treasury) {
            return fail(ctx, "The clan treasury holds only " + Points.format(clan.treasury) + ".");
        }
        clan.treasury -= amount;
        Points.get(player.server).add(player.server, to, amount);
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " paid "
                + Points.format(amount) + " from the clan treasury to " + clan.members.get(to).name + " (left: "
                + Points.format(clan.treasury) + ").").withStyle(ChatFormatting.GRAY));
        refresh(player.server, clan);
        return 1;
    }

    // ---- Talking ----

    private static int chat(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = member(ctx, player);
        if (clan == null) {
            return 0;
        }
        String message = StringArgumentType.getString(ctx, "message");
        ClanText.tell(player.server, clan, ClanText.tag(clan).append(Component.literal(" " + player.getScoreboardName()
                + ": ").withStyle(ChatFormatting.GRAY)).append(Component.literal(message)
                .withStyle(ChatFormatting.WHITE)));
        Jadm.LOGGER.info("[Clan {}] {}: {}", clan.tag, player.getScoreboardName(), message);
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> ctx, String which) {
        CommandSourceStack source = ctx.getSource();
        Clans clans = clans(ctx);
        Clans.Clan clan;
        if (which == null) {
            ServerPlayer player = source.getPlayer();
            clan = player == null ? null : clans.of(player.getUUID());
            if (clan == null) {
                return fail(ctx, "You aren't in a clan. /jadm clan info <clan> shows another one.");
            }
        } else {
            clan = clans.find(which.trim());
            if (clan == null) {
                return fail(ctx, "There is no such clan.");
            }
        }
        Clans.Clan shown = clan;
        source.sendSuccess(() -> ClanText.named(shown).withStyle(ChatFormatting.BOLD), false);
        if (!shown.motto.isEmpty()) {
            source.sendSuccess(() -> Component.literal("\"" + shown.motto + "\"").withStyle(ChatFormatting.ITALIC,
                    ChatFormatting.GRAY), false);
        }
        UUID leader = shown.leader();
        source.sendSuccess(() -> Component.literal("Rating " + shown.rating + ", " + ordinal(clans.place(shown))
                + " place. Wars: " + shown.warsWon + " won, " + shown.warsLost + " lost, " + shown.warsDrawn
                + " drawn.").withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(shown.members.size() + " members, led by "
                + (leader == null ? "nobody" : shown.members.get(leader).name) + (shown.open ? "; anyone can join."
                : "; by invitation.")).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(String.join(", ", shown.members.values().stream()
                .map(m -> m.name + (m.role == Clans.Role.MEMBER ? "" : " (" + m.role.title + ")")).toList()))
                .withStyle(ChatFormatting.WHITE), false);
        for (Clans.War w : clans.warsOf(shown.id)) {
            if (w.running()) {
                Clans.Clan other = clans.byId(w.other(shown.id));
                source.sendSuccess(() -> Component.literal("At war: ").withStyle(ChatFormatting.RED)
                        .append(ClanText.score(shown, w.score(shown.id), other, w.score(other.id))), false);
            }
        }
        return shown.rating;
    }

    private static int top(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        List<Clans.Clan> standings = clans(ctx).standings();
        if (standings.isEmpty()) {
            source.sendSuccess(() -> Component.literal("There are no clans yet. Found one with /jadm clan create "
                    + "<tag> <name>."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Clan ranking, season " + clans(ctx).season())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < Math.min(TOP, standings.size()); i++) {
            Clans.Clan c = standings.get(i);
            int place = i + 1;
            source.sendSuccess(() -> Component.literal(place + ". ").append(ClanText.named(c))
                    .append(Component.literal(": " + c.rating + " (" + c.warsWon + "-" + c.warsLost + "-"
                            + c.warsDrawn + "), " + c.members.size() + " members").withStyle(ChatFormatting.GRAY)),
                    false);
        }
        return Math.min(TOP, standings.size());
    }

    // ---- Wars ----

    private static int warStatus(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans clans = clans(ctx);
        Clans.Clan clan = member(ctx, player);
        if (clan == null) {
            return 0;
        }
        List<Clans.War> wars = clans.warsOf(clan.id);
        if (wars.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Your clan isn't at war. The leader or an officer "
                    + "declares one with /jadm clan war declare <clan> [stake]."), false);
            return 0;
        }
        long now = System.currentTimeMillis();
        for (Clans.War w : wars) {
            Clans.Clan other = clans.byId(w.other(clan.id));
            if (other == null) {
                continue;
            }
            MutableComponent line;
            if (w.running()) {
                line = Component.literal("At war: ").withStyle(ChatFormatting.RED)
                        .append(ClanText.score(clan, w.score(clan.id), other, w.score(other.id)))
                        .append(Component.literal(", " + ClanText.duration(w.endsAt - now) + " left"
                                + (w.target > 0 ? ", first to " + w.target : "")
                                + (w.stake > 0 ? ", " + Points.format(2 * w.stake) + " at stake" : "") + ".")
                                .withStyle(ChatFormatting.GRAY));
            } else if (w.defender.equals(clan.id)) {
                line = ClanText.named(other).append(Component.literal(" declared war on you"
                        + (w.stake > 0 ? " for " + Points.format(w.stake) + " each" : "") + ". ")
                        .withStyle(ChatFormatting.GOLD))
                        .append(ClanText.button("Accept", ChatFormatting.GREEN, "/jadm clan war accept " + other.tag))
                        .append(" ")
                        .append(ClanText.button("Deny", ChatFormatting.RED, "/jadm clan war deny " + other.tag));
            } else {
                line = Component.literal("You declared war on ").withStyle(ChatFormatting.GRAY)
                        .append(ClanText.named(other)).append(Component.literal("; waiting for them to accept.")
                                .withStyle(ChatFormatting.GRAY));
            }
            MutableComponent shown = line;
            ctx.getSource().sendSuccess(() -> shown, false);
        }
        return wars.size();
    }

    private static int declare(CommandContext<CommandSourceStack> ctx, long stake, String format)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        Clans.Clan other = clans(ctx).find(StringArgumentType.getString(ctx, "clan"));
        if (other == null) {
            return fail(ctx, "There is no such clan. /jadm clan top lists them.");
        }
        String why = ClanWars.declare(player.server, player, clan, other, stake, format);
        if (why != null) {
            return fail(ctx, why);
        }
        refresh(player.server, clan);
        refresh(player.server, other);
        return 1;
    }

    private static int answer(CommandContext<CommandSourceStack> ctx, String from, boolean accept)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans clans = clans(ctx);
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        Clans.Clan attacker = from == null ? null : clans.find(from);
        Clans.War war = null;
        for (Clans.War w : clans.warsOf(clan.id)) {
            if (!w.running() && w.defender.equals(clan.id) && (attacker == null || w.attacker.equals(attacker.id))) {
                war = w;
                break;
            }
        }
        if (war == null) {
            return fail(ctx, "No clan has declared war on yours" + (from == null ? "." : " under that name."));
        }
        Clans.Clan other = clans.byId(war.attacker);
        if (accept) {
            String why = ClanWars.accept(player.server, clan, war);
            if (why != null) {
                return fail(ctx, why);
            }
        } else {
            ClanWars.callOff(player.server, war, "[" + clan.tag + "] turned it down.");
        }
        refresh(player.server, clan);
        refresh(player.server, other);
        return 1;
    }

    /** The clan's arena battle that hasn't begun, or {@code null} after saying there is none. */
    private static Clans.War battle(CommandContext<CommandSourceStack> ctx, Clans.Clan clan) {
        for (Clans.War w : clans(ctx).warsOf(clan.id)) {
            if (w.running() && w.battle()) {
                if (w.bout >= 0) {
                    fail(ctx, "The arena battle has already begun.");
                    return null;
                }
                return w;
            }
        }
        fail(ctx, "Your clan has no arena battle coming up.");
        return null;
    }

    private static int showLineup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = member(ctx, player);
        Clans.War war = clan == null ? null : battle(ctx, clan);
        if (war == null) {
            return 0;
        }
        List<UUID> lineup = war.lineup(clan.id);
        ctx.getSource().sendSuccess(() -> Component.literal("Your lineup (" + lineup.size() + " of "
                + ClanBattles.size() + "): " + (lineup.isEmpty() ? "nobody yet" : String.join(", ",
                lineup.stream().map(id -> clan.members.get(id).name).toList()))
                + (war.ready(clan.id) ? ". Ready." : ".")).withStyle(ChatFormatting.GOLD), false);
        return lineup.size();
    }

    private static int setLineup(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        Clans.War war = clan == null ? null : battle(ctx, clan);
        if (war == null) {
            return 0;
        }
        List<UUID> picked = new java.util.ArrayList<>();
        for (String name : StringArgumentType.getString(ctx, "members").trim().split("[\\s,]+")) {
            UUID id = memberNamed(clan, name);
            if (id == null) {
                return fail(ctx, name + " isn't in your clan.");
            }
            if (picked.contains(id)) {
                return fail(ctx, name + " can only fight once.");
            }
            picked.add(id);
        }
        if (picked.size() > ClanBattles.size()) {
            return fail(ctx, "An arena battle has " + ClanBattles.size() + " duelists a side.");
        }
        List<UUID> lineup = war.lineup(clan.id);
        lineup.clear();
        lineup.addAll(picked);
        war.setReady(clan.id, false);
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + " set the battle lineup: "
                + String.join(", ", picked.stream().map(id -> clan.members.get(id).name).toList()) + ".")
                .withStyle(ChatFormatting.GOLD));
        refresh(player.server, clan);
        return picked.size();
    }

    private static int signUp(CommandContext<CommandSourceStack> ctx, boolean join) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = member(ctx, player);
        Clans.War war = clan == null ? null : battle(ctx, clan);
        if (war == null) {
            return 0;
        }
        List<UUID> lineup = war.lineup(clan.id);
        if (join) {
            if (lineup.contains(player.getUUID())) {
                return fail(ctx, "You are already in the lineup.");
            }
            if (lineup.size() >= ClanBattles.size()) {
                return fail(ctx, "The lineup is full.");
            }
            lineup.add(player.getUUID());
        } else if (!lineup.remove(player.getUUID())) {
            return fail(ctx, "You aren't in the lineup.");
        }
        war.setReady(clan.id, false);
        clans(ctx).changed();
        ClanText.tell(player.server, clan, Component.literal(player.getScoreboardName() + (join
                ? " will fight in the arena battle (" : " left the battle lineup (") + lineup.size() + " of "
                + ClanBattles.size() + ").").withStyle(ChatFormatting.GOLD));
        refresh(player.server, clan);
        return 1;
    }

    private static int ready(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans.Clan clan = managed(ctx, player);
        Clans.War war = clan == null ? null : battle(ctx, clan);
        if (war == null) {
            return 0;
        }
        if (war.lineup(clan.id).size() < ClanBattles.size()) {
            return fail(ctx, "The lineup needs " + ClanBattles.size() + " duelists first.");
        }
        if (war.ready(clan.id)) {
            return fail(ctx, "Your clan is ready already; changing the lineup takes that back.");
        }
        boolean ready = true;
        war.setReady(clan.id, true);
        clans(ctx).changed();
        Clans.Clan other = clans(ctx).byId(war.other(clan.id));
        Component text = Component.literal("").append(ClanText.tag(clan)).append(Component.literal(ready
                ? " is ready for the arena battle." + (war.ready(other.id) ? " It begins now!"
                : " Waiting for [" + other.tag + "].") : "").withStyle(ChatFormatting.GOLD));
        ClanText.tell(player.server, clan, text);
        ClanText.tell(player.server, other, text);
        if (ready && !war.ready(other.id)) {
            ClanText.tellManagers(player.server, other, Component.literal("When your lineup stands: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(ClanText.button("Ready", ChatFormatting.GREEN, "/jadm clan war ready")));
        }
        refresh(player.server, clan);
        refresh(player.server, other);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans clans = clans(ctx);
        Clans.Clan clan = managed(ctx, player);
        if (clan == null) {
            return 0;
        }
        for (Clans.War w : clans.warsOf(clan.id)) {
            if (!w.running() && w.attacker.equals(clan.id)) {
                Clans.Clan other = clans.byId(w.defender);
                ClanWars.callOff(player.server, w, "[" + clan.tag + "] took the declaration back.");
                refresh(player.server, clan);
                refresh(player.server, other);
                return 1;
            }
        }
        return fail(ctx, "Your clan hasn't declared a war that waits for an answer.");
    }

    private static int surrender(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Clans clans = clans(ctx);
        Clans.Clan clan = led(ctx, player);
        if (clan == null) {
            return 0;
        }
        for (Clans.War w : clans.warsOf(clan.id)) {
            if (w.running()) {
                Clans.Clan other = clans.byId(w.other(clan.id));
                ClanWars.finish(player.server, w, w.other(clan.id), "surrender");
                refresh(player.server, clan);
                refresh(player.server, other);
                return 1;
            }
        }
        return fail(ctx, "Your clan isn't at war.");
    }

    // ---- Operators ----

    private static int adminDisband(CommandContext<CommandSourceStack> ctx) {
        Clans.Clan clan = clans(ctx).find(StringArgumentType.getString(ctx, "clan").trim());
        if (clan == null) {
            return fail(ctx, "There is no such clan.");
        }
        String tag = clan.tag;
        disband(ctx.getSource().getServer(), clan);
        ctx.getSource().sendSuccess(() -> Component.literal("Disbanded [" + tag + "]; its treasury is gone."), true);
        return 1;
    }

    private static int adminRating(CommandContext<CommandSourceStack> ctx) {
        Clans.Clan clan = clans(ctx).find(StringArgumentType.getString(ctx, "clan"));
        if (clan == null) {
            return fail(ctx, "There is no such clan.");
        }
        clan.rating = IntegerArgumentType.getInteger(ctx, "rating");
        clan.peak = Math.max(clan.peak, clan.rating);
        clans(ctx).changed();
        ctx.getSource().sendSuccess(() -> Component.literal("Set [" + clan.tag + "]'s rating to " + clan.rating
                + "."), true);
        return 1;
    }

    private static int adminTreasury(CommandContext<CommandSourceStack> ctx) {
        Clans.Clan clan = clans(ctx).find(StringArgumentType.getString(ctx, "clan"));
        if (clan == null) {
            return fail(ctx, "There is no such clan.");
        }
        clan.treasury = LongArgumentType.getLong(ctx, "amount");
        clans(ctx).changed();
        ctx.getSource().sendSuccess(() -> Component.literal("Set [" + clan.tag + "]'s treasury to "
                + Points.format(clan.treasury) + "."), true);
        refresh(ctx.getSource().getServer(), clan);
        return 1;
    }

    private static int adminEndWar(CommandContext<CommandSourceStack> ctx) {
        Clans clans = clans(ctx);
        Clans.Clan clan = clans.find(StringArgumentType.getString(ctx, "clan"));
        if (clan == null) {
            return fail(ctx, "There is no such clan.");
        }
        for (Clans.War w : clans.warsOf(clan.id)) {
            if (w.running()) {
                UUID winner = w.attackerScore > w.defenderScore ? w.attacker
                        : w.defenderScore > w.attackerScore ? w.defender : null;
                ClanWars.finish(ctx.getSource().getServer(), w, winner, "time");
                ctx.getSource().sendSuccess(() -> Component.literal("Ended [" + clan.tag + "]'s war on the "
                        + "current score."), true);
                return 1;
            }
        }
        return fail(ctx, "[" + clan.tag + "] isn't at war.");
    }

    private static int adminSeason(CommandContext<CommandSourceStack> ctx) {
        int season = clans(ctx).newSeason();
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("Clan season " + season
                + " has begun! Every clan starts again at " + JadmServerConfig.CLANS.startRating.get() + ".")
                .withStyle(ChatFormatting.GOLD), false);
        return season;
    }

    // ---- Events ----

    /** Keeps the member's name current and tells leaders and officers about wars waiting for an answer. */
    public static void onLogin(ServerPlayer player) {
        Clans clans = Clans.get(player.server);
        Clans.Clan clan = clans.of(player.getUUID());
        if (clan == null) {
            ClanTeams.leave(player.server, player.getScoreboardName());
            return;
        }
        Clans.Member m = clan.members.get(player.getUUID());
        if (!m.name.equals(player.getScoreboardName())) {
            ClanTeams.leave(player.server, m.name);
            m.name = player.getScoreboardName();
            clans.changed();
        }
        ClanTeams.sync(player.server, clan);
        long now = System.currentTimeMillis();
        for (Clans.War w : clans.warsOf(clan.id)) {
            Clans.Clan other = clans.byId(w.other(clan.id));
            if (other == null) {
                continue;
            }
            if (w.running()) {
                player.sendSystemMessage(Component.literal("Your clan is at war: ").withStyle(ChatFormatting.GOLD)
                        .append(ClanText.score(clan, w.score(clan.id), other, w.score(other.id)))
                        .append(Component.literal(", " + ClanText.duration(w.endsAt - now) + " left.")
                                .withStyle(ChatFormatting.GRAY)));
            } else if (w.defender.equals(clan.id) && m.role.manages()) {
                player.sendSystemMessage(ClanText.named(other).append(Component.literal(" declared war on your "
                        + "clan. ").withStyle(ChatFormatting.GOLD))
                        .append(ClanText.button("Accept", ChatFormatting.GREEN, "/jadm clan war accept " + other.tag))
                        .append(" ")
                        .append(ClanText.button("Deny", ChatFormatting.RED, "/jadm clan war deny " + other.tag)));
            }
        }
    }

    // ---- Helpers ----

    private static String checkTag(Clans clans, String tag, Clans.Clan except) {
        if (!TAG.matcher(tag).matches()) {
            return "A clan tag is 2 to 5 letters or digits, like KC.";
        }
        Clans.Clan taken = clans.find(tag);
        if (taken != null && taken != except && taken.tag.equalsIgnoreCase(tag)) {
            return "The tag [" + tag.toUpperCase() + "] is taken.";
        }
        return null;
    }

    private static String checkName(Clans clans, String name, Clans.Clan except) {
        if (!NAME.matcher(name).matches()) {
            return "A clan name is 3 to 24 letters, digits, spaces and ' & . ! -";
        }
        for (Clans.Clan c : clans.all()) {
            if (c != except && c.name.equalsIgnoreCase(name)) {
                return "There already is a clan called " + c.name + ".";
            }
        }
        return null;
    }

    private static boolean full(Clans.Clan clan) {
        int max = JadmServerConfig.CLANS.maxMembers.get();
        return max > 0 && clan.members.size() >= max;
    }

    private static UUID memberNamed(Clans.Clan clan, String name) {
        return clan.members.entrySet().stream().filter(e -> e.getValue().name.equalsIgnoreCase(name))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }

    /** The player's clan, or {@code null} after telling them they have none. */
    private static Clans.Clan member(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Clans.Clan clan = clans(ctx).of(player.getUUID());
        if (clan == null) {
            fail(ctx, "You aren't in a clan. Found one with /jadm clan create <tag> <name>, or join one.");
        }
        return clan;
    }

    /** The clan the player leads or is an officer of, or {@code null} after telling them why not. */
    private static Clans.Clan managed(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Clans.Clan clan = member(ctx, player);
        if (clan != null && !clan.role(player.getUUID()).manages()) {
            fail(ctx, "Only the clan's leader and officers can do that.");
            return null;
        }
        return clan;
    }

    /** The clan the player leads, or {@code null} after telling them why not. */
    private static Clans.Clan led(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        Clans.Clan clan = member(ctx, player);
        if (clan != null && clan.role(player.getUUID()) != Clans.Role.LEADER) {
            fail(ctx, "Only the clan's leader can do that.");
            return null;
        }
        return clan;
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String why) {
        ctx.getSource().sendFailure(Component.literal(why));
        return 0;
    }

    private static CompletableFuture<Suggestions> tags(CommandContext<CommandSourceStack> ctx,
                                                       SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(clans(ctx).all().stream().map(c -> c.tag), builder);
    }

    private static CompletableFuture<Suggestions> members(CommandContext<CommandSourceStack> ctx,
                                                          SuggestionsBuilder builder) {
        ServerPlayer player = ctx.getSource().getPlayer();
        Clans.Clan clan = player == null ? null : clans(ctx).of(player.getUUID());
        return clan == null ? builder.buildFuture()
                : SharedSuggestionProvider.suggest(clan.members.values().stream().map(m -> m.name), builder);
    }

    private static Clans clans(CommandContext<CommandSourceStack> ctx) {
        return Clans.get(ctx.getSource().getServer());
    }

    static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = mod100 >= 11 && mod100 <= 13 ? "th"
                : switch (n % 10) {
                    case 1 -> "st";
                    case 2 -> "nd";
                    case 3 -> "rd";
                    default -> "th";
                };
        return n + suffix;
    }
}
