package ua.selectedproject.core.economy;

import ua.selectedproject.core.SelectedCore;
import ua.selectedproject.core.config.CoreConfig;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public class CoinItems {
    public static Item COIN_TIER_1; // Copper/Bronze
    public static Item COIN_TIER_2; // Silver
    public static Item COIN_TIER_3; // Gold

    public static void register() {
        COIN_TIER_1 = Registry.register(
                Registries.ITEM,
                Identifier.of(SelectedCore.MOD_ID, "coin_tier_1"),
                new Item(new Item.Settings().maxCount(64))
        );

        COIN_TIER_2 = Registry.register(
                Registries.ITEM,
                Identifier.of(SelectedCore.MOD_ID, "coin_tier_2"),
                new Item(new Item.Settings().maxCount(64))
        );

        COIN_TIER_3 = Registry.register(
                Registries.ITEM,
                Identifier.of(SelectedCore.MOD_ID, "coin_tier_3"),
                new Item(new Item.Settings().maxCount(64))
        );

        // Add to creative menu
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(COIN_TIER_1);
            content.add(COIN_TIER_2);
            content.add(COIN_TIER_3);
        });

        SelectedCore.LOGGER.info("Coin items registered");
    }

    /**
     * Calculate the total coin value from a given set of coin counts.
     */
    public static long calculateValue(int tier1Count, int tier2Count, int tier3Count) {
        CoreConfig config = CoreConfig.getInstance();
        return (long) tier1Count * config.coinTier1Value
             + (long) tier2Count * config.coinTier2Value
             + (long) tier3Count * config.coinTier3Value;
    }

    /**
     * Count coins in a player's inventory and ender chest.
     * Returns int[]{tier1, tier2, tier3}.
     */
    public static int[] countPlayerCoins(ServerPlayerEntity player) {
        int t1 = 0, t2 = 0, t3 = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(COIN_TIER_1)) t1 += stack.getCount();
            else if (stack.isOf(COIN_TIER_2)) t2 += stack.getCount();
            else if (stack.isOf(COIN_TIER_3)) t3 += stack.getCount();
        }
        for (int i = 0; i < player.getEnderChestInventory().size(); i++) {
            ItemStack stack = player.getEnderChestInventory().getStack(i);
            if (stack.isOf(COIN_TIER_1)) t1 += stack.getCount();
            else if (stack.isOf(COIN_TIER_2)) t2 += stack.getCount();
            else if (stack.isOf(COIN_TIER_3)) t3 += stack.getCount();
        }
        return new int[]{t1, t2, t3};
    }

    /**
     * Count total coin wealth stored in a player's ender chest.
     */
    public static long countEnderChestWealth(ServerPlayerEntity player) {
        int t1 = 0, t2 = 0, t3 = 0;
        for (int i = 0; i < player.getEnderChestInventory().size(); i++) {
            ItemStack stack = player.getEnderChestInventory().getStack(i);
            if (stack.isOf(COIN_TIER_1)) t1 += stack.getCount();
            else if (stack.isOf(COIN_TIER_2)) t2 += stack.getCount();
            else if (stack.isOf(COIN_TIER_3)) t3 += stack.getCount();
        }
        return calculateValue(t1, t2, t3);
    }
}
