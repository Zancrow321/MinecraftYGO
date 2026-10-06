package io.github.zancrow321.jadm.cosmetics;

import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.duel.DuelDisks;
import io.github.zancrow321.jadm.item.JadmComponents;
import io.github.zancrow321.jadm.item.JadmItems;
import io.github.zancrow321.jadm.network.CosmeticsPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Server side: counts progress, announces unlocks and applies a player's picks. */
public final class PlayerCosmetics {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Jadm.MOD_ID);
    private static final Supplier<AttachmentType<CosmeticsData>> DATA = ATTACHMENTS.register("cosmetics",
            () -> AttachmentType.builder(() -> CosmeticsData.NEW).serialize(CosmeticsData.CODEC).copyOnDeath().build());

    private PlayerCosmetics() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static CosmeticsData get(ServerPlayer player) {
        return player.getData(DATA);
    }

    /** The sleeve a player's cards wear in duels. */
    public static String sleeve(ServerPlayer player) {
        return Cosmetics.sleeve(get(player).sleeve()).id();
    }

    /** @return what the win unlocked, e.g. "Gold card sleeve" */
    public static List<String> wonDuel(ServerPlayer player, boolean againstNpc) {
        return update(player, d -> new CosmeticsData(d.wins() + 1, d.npcWins() + (againstNpc ? 1 : 0), d.packs(),
                d.sleeve()));
    }

    public static void openedPack(ServerPlayer player) {
        update(player, d -> new CosmeticsData(d.wins(), d.npcWins(), d.packs() + 1, d.sleeve()));
    }

    private static List<String> update(ServerPlayer player, UnaryOperator<CosmeticsData> change) {
        CosmeticsData before = get(player);
        CosmeticsData after = change.apply(before);
        player.setData(DATA, after);
        List<String> unlocked = new ArrayList<>();
        announce(player, before, after, Cosmetics.SKINS, "disk skin", unlocked);
        announce(player, before, after, Cosmetics.SLEEVES, "card sleeve", unlocked);
        return unlocked;
    }

    private static void announce(ServerPlayer player, CosmeticsData before, CosmeticsData after,
                                 List<Cosmetics.Cosmetic> cosmetics, String kind, List<String> unlocked) {
        for (Cosmetics.Cosmetic c : cosmetics) {
            if (!c.unlocked(before) && c.unlocked(after)) {
                player.sendSystemMessage(Component.literal("Unlocked the " + c.name() + " " + kind
                        + "! Pick it with /jadm cosmetics.").withStyle(ChatFormatting.GOLD));
                unlocked.add(c.name() + " " + kind);
            }
        }
    }

    /** Opens the cosmetics screen on the player's client. */
    public static void open(ServerPlayer player) {
        ItemStack disk = disk(player);
        String skin = disk.isEmpty() ? Cosmetics.DEFAULT_SKIN : skin(disk);
        PacketDistributor.sendToPlayer(player, new CosmeticsPayload(get(player), skin, !disk.isEmpty()));
    }

    /** Puts a skin on the player's disk or picks their sleeve, if they have unlocked it. */
    public static void select(ServerPlayer player, boolean isSkin, String id) {
        CosmeticsData data = get(player);
        Cosmetics.Cosmetic cosmetic = isSkin ? Cosmetics.skin(id) : Cosmetics.sleeve(id);
        if (!cosmetic.id().equals(id) || !cosmetic.unlocked(data)) {
            return;
        }
        if (isSkin) {
            ItemStack disk = disk(player);
            if (disk.isEmpty()) {
                player.sendSystemMessage(Component.literal("Wear or hold your Duel Disk to change its skin."));
                return;
            }
            disk.set(JadmComponents.DISK_SKIN.get(), id);
        } else {
            player.setData(DATA, data.withSleeve(id));
        }
        open(player);
    }

    public static String skin(ItemStack disk) {
        return disk.getOrDefault(JadmComponents.DISK_SKIN.get(), Cosmetics.DEFAULT_SKIN);
    }

    /** The worn disk, else one in either hand. */
    private static ItemStack disk(ServerPlayer player) {
        ItemStack worn = DuelDisks.worn(player);
        if (!worn.isEmpty()) {
            return worn;
        }
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            if (stack.is(JadmItems.DUEL_DISK.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
