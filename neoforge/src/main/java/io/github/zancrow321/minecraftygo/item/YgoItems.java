package io.github.zancrow321.minecraftygo.item;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class YgoItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftYgo.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MinecraftYgo.MOD_ID);

    public static final DeferredItem<DuelDiskItem> DUEL_DISK = ITEMS.registerItem("duel_disk", DuelDiskItem::new,
            new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.minecraftygo"))
                    .icon(() -> DUEL_DISK.get().getDefaultInstance())
                    .displayItems((parameters, output) -> output.accept(DUEL_DISK.get()))
                    .build());

    private YgoItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
