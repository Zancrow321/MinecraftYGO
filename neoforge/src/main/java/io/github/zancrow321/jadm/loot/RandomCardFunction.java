package io.github.zancrow321.jadm.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.zancrow321.jadm.Jadm;
import io.github.zancrow321.jadm.engine.data.BoosterSets;
import io.github.zancrow321.jadm.item.BoosterPackItem;
import io.github.zancrow321.jadm.item.JadmComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Loot function {@code jadm:random_card}: turns a card item into a random card from a random booster set,
 * of the given rarity (common by default).
 */
public final class RandomCardFunction extends LootItemConditionalFunction {
    private static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Jadm.MOD_ID);

    public static final MapCodec<RandomCardFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i)
            .and(com.mojang.serialization.Codec.STRING.optionalFieldOf("rarity", "common").forGetter(f -> f.rarity))
            .apply(i, RandomCardFunction::new));

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomCardFunction>> TYPE =
            FUNCTIONS.register("random_card", () -> new LootItemFunctionType<>(CODEC));

    private final String rarity;

    private RandomCardFunction(List<LootItemCondition> conditions, String rarity) {
        super(conditions);
        this.rarity = rarity;
    }

    public static void register(IEventBus modBus) {
        FUNCTIONS.register(modBus);
    }

    @Override
    public LootItemFunctionType<RandomCardFunction> getType() {
        return TYPE.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        BoosterSets.BoosterSet set = BoosterPackItem.randomSet(context.getRandom());
        if (set == null) {
            return ItemStack.EMPTY;
        }
        BoosterSets.Rarity wanted = BoosterSets.Rarity.parse(rarity);
        List<BoosterSets.Card> cards = set.of(wanted).isEmpty() ? set.cards() : set.of(wanted);
        BoosterSets.Card card = cards.get(context.getRandom().nextInt(cards.size()));
        stack.set(JadmComponents.CARD.get(), new JadmComponents.CardStack(card.code(), card.rarity().id()));
        return stack;
    }
}
