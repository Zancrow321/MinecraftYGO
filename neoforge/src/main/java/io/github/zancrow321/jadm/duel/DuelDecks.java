package io.github.zancrow321.jadm.duel;

import com.mojang.serialization.Codec;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.item.JadmComponents;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The deck box each player picked last before a duel, remembered (through death too) so the next duel offers it
 * first, and plays it without asking when the player turned the question off.
 */
public final class DuelDecks {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Jadm.MOD_ID);
    private static final Codec<Optional<UUID>> OPTIONAL_UUID = UUIDUtil.CODEC.optionalFieldOf("deck").codec();
    private static final Supplier<AttachmentType<Optional<UUID>>> CHOSEN = ATTACHMENTS.register("duel_deck",
            () -> AttachmentType.<Optional<UUID>>builder(Optional::empty).serialize(OPTIONAL_UUID).copyOnDeath()
                    .build());

    private DuelDecks() {
    }

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    /** The id of the deck box the player picked last, if any. */
    public static UUID chosen(ServerPlayer player) {
        return player.getData(CHOSEN).orElse(null);
    }

    public static void choose(ServerPlayer player, UUID box) {
        player.setData(CHOSEN, Optional.ofNullable(box));
    }

    /** Whether {@code box} is the one the player picked last. */
    public static boolean isChosen(ServerPlayer player, ItemStack box) {
        UUID chosen = chosen(player);
        return chosen != null && chosen.equals(box.get(JadmComponents.DECK_ID.get()));
    }

    /** The box's id, given to it now if it has none yet. */
    public static UUID id(ItemStack box) {
        UUID id = box.get(JadmComponents.DECK_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            box.set(JadmComponents.DECK_ID.get(), id);
        }
        return id;
    }
}
