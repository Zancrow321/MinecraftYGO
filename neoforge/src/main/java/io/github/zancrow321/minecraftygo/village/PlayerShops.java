package io.github.zancrow321.minecraftygo.village;

import io.github.zancrow321.minecraftygo.MinecraftYgo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Shop Stands, the shops players run: their block, block entity and menu, and who owns how many. */
public final class PlayerShops {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MinecraftYgo.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MinecraftYgo.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MinecraftYgo.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MinecraftYgo.MOD_ID);

    public static final DeferredBlock<ShopStand> STAND = BLOCKS.registerBlock("shop_stand", ShopStand::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(SoundType.WOOD));
    public static final DeferredItem<BlockItem> STAND_ITEM = ITEMS.registerSimpleBlockItem(STAND);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShopStandBlockEntity>> STAND_ENTITY =
            BLOCK_ENTITIES.register("shop_stand",
                    () -> BlockEntityType.Builder.of(ShopStandBlockEntity::new, STAND.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ShopStandMenu>> STAND_MENU = MENUS.register(
            "shop_stand", () -> IMenuTypeExtension.create((id, inventory, data) -> new ShopStandMenu(id, inventory)));

    private PlayerShops() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    /** Where each player's stands are, for {@code maxPerPlayer}. */
    static final class Owners extends SavedData {
        private static final String NAME = "minecraftygo_shop_stands";

        private final Map<UUID, Set<String>> stands = new HashMap<>();

        static Owners get(MinecraftServer server) {
            return server.overworld().getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Owners::new, Owners::load, null), NAME);
        }

        int count(UUID owner) {
            return stands.getOrDefault(owner, Set.of()).size();
        }

        void add(UUID owner, Level level, BlockPos pos) {
            stands.computeIfAbsent(owner, o -> new HashSet<>()).add(key(level, pos));
            setDirty();
        }

        void remove(UUID owner, Level level, BlockPos pos) {
            Set<String> set = stands.get(owner);
            if (set != null && set.remove(key(level, pos))) {
                setDirty();
            }
        }

        private static String key(Level level, BlockPos pos) {
            return level.dimension().location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }

        private static Owners load(CompoundTag tag, HolderLookup.Provider registries) {
            Owners owners = new Owners();
            for (String uuid : tag.getAllKeys()) {
                Set<String> set = new HashSet<>();
                for (Tag stand : tag.getList(uuid, Tag.TAG_STRING)) {
                    set.add(stand.getAsString());
                }
                owners.stands.put(UUID.fromString(uuid), set);
            }
            return owners;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            stands.forEach((uuid, set) -> {
                ListTag list = new ListTag();
                set.forEach(stand -> list.add(StringTag.valueOf(stand)));
                tag.put(uuid.toString(), list);
            });
            return tag;
        }
    }
}
