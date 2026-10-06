package io.github.zancrow321.minecraftygo.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zancrow321.minecraftygo.MinecraftYgo;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Data stored on the mod's items: which card a card item is, a pack's set, a binder's collection, a deck box's deck.
 */
public final class YgoComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MinecraftYgo.MOD_ID);

    /** A single card and the rarity it was pulled at. */
    public record CardStack(int code, String rarity) {
        public static final Codec<CardStack> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("code").forGetter(CardStack::code),
                Codec.STRING.optionalFieldOf("rarity", "common").forGetter(CardStack::rarity)
        ).apply(i, CardStack::new));
        public static final StreamCodec<ByteBuf, CardStack> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, CardStack::code, ByteBufCodecs.STRING_UTF8, CardStack::rarity, CardStack::new);
    }

    /** The cards in a binder: passcode to number of copies. */
    public record CardCollection(Map<Integer, Integer> counts) {
        public static final CardCollection EMPTY = new CardCollection(Map.of());
        public static final Codec<CardCollection> CODEC = Codec.unboundedMap(
                        Codec.STRING.xmap(Integer::parseInt, String::valueOf), Codec.INT)
                .xmap(CardCollection::new, CardCollection::counts);
        public static final StreamCodec<ByteBuf, CardCollection> STREAM_CODEC = ByteBufCodecs.<ByteBuf, Integer,
                        Integer, Map<Integer, Integer>>map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.VAR_INT)
                .map(CardCollection::new, CardCollection::counts);

        public CardCollection {
            Map<Integer, Integer> sorted = new TreeMap<>();
            counts.forEach((code, n) -> {
                if (n > 0) {
                    sorted.put(code, n);
                }
            });
            counts = Collections.unmodifiableMap(sorted);
        }

        public int count(int code) {
            return counts.getOrDefault(code, 0);
        }

        public int total() {
            return counts.values().stream().mapToInt(Integer::intValue).sum();
        }

        public CardCollection add(int code, int n) {
            Map<Integer, Integer> next = new HashMap<>(counts);
            next.merge(code, n, Integer::sum);
            return new CardCollection(next);
        }
    }

    /**
     * The foil copies in a binder, keyed {@code "<passcode>:<rarity>"}. They are counted in its {@link CardCollection}
     * too; the copies of a card that aren't listed here are commons. See {@link BinderItem#copies}.
     */
    public record CardFoils(Map<String, Integer> copies) {
        public static final CardFoils EMPTY = new CardFoils(Map.of());
        public static final Codec<CardFoils> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT)
                .xmap(CardFoils::new, CardFoils::copies);
        public static final StreamCodec<ByteBuf, CardFoils> STREAM_CODEC = ByteBufCodecs.<ByteBuf, String, Integer,
                        Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT)
                .map(CardFoils::new, CardFoils::copies);

        public CardFoils {
            Map<String, Integer> sorted = new TreeMap<>();
            copies.forEach((key, n) -> {
                if (n > 0) {
                    sorted.put(key, n);
                }
            });
            copies = Collections.unmodifiableMap(sorted);
        }
    }

    /** A deck in a deck box. */
    public record DeckList(List<Integer> main, List<Integer> extra) {
        public static final DeckList EMPTY = new DeckList(List.of(), List.of());
        public static final Codec<DeckList> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.listOf().optionalFieldOf("main", List.of()).forGetter(DeckList::main),
                Codec.INT.listOf().optionalFieldOf("extra", List.of()).forGetter(DeckList::extra)
        ).apply(i, DeckList::new));
        public static final StreamCodec<ByteBuf, DeckList> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DeckList::main,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), DeckList::extra,
                DeckList::new);

        public DeckList {
            main = List.copyOf(main);
            extra = List.copyOf(extra);
        }

        public int size() {
            return main.size() + extra.size();
        }
    }

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardStack>> CARD =
            COMPONENTS.registerComponentType("card", b -> b.persistent(CardStack.CODEC)
                    .networkSynchronized(CardStack.STREAM_CODEC));
    /** The set a booster pack is from; packs without one are from a random era set. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PACK_SET =
            COMPONENTS.registerComponentType("pack_set", b -> b.persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardCollection>> COLLECTION =
            COMPONENTS.registerComponentType("collection", b -> b.persistent(CardCollection.CODEC)
                    .networkSynchronized(CardCollection.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CardFoils>> FOILS =
            COMPONENTS.registerComponentType("foils", b -> b.persistent(CardFoils.CODEC)
                    .networkSynchronized(CardFoils.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DeckList>> DECK =
            COMPONENTS.registerComponentType("deck", b -> b.persistent(DeckList.CODEC)
                    .networkSynchronized(DeckList.STREAM_CODEC));

    /** The skin a duel disk wears; see {@code Cosmetics.SKINS}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> DISK_SKIN =
            COMPONENTS.registerComponentType("disk_skin", b -> b.persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private YgoComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
