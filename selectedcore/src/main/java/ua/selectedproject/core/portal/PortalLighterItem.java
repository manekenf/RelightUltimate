package ua.selectedproject.core.portal;

import ua.selectedproject.core.SelectedCore;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class PortalLighterItem {
    public static Item PORTAL_LIGHTER;

    public static void register() {
        // Intentionally indestructible: the lighter is an admin/civic item, not a
        // consumable — we want a single instance per shop/clan that can't be ground
        // away by wear. If we ever want it to behave like flint-and-steel, switch
        // maxDamage(...) here and add take-damage in PortalLighter.useOnBlock.
        PORTAL_LIGHTER = Registry.register(
                Registries.ITEM,
                Identifier.of(SelectedCore.MOD_ID, "portal_lighter"),
                new Item(new Item.Settings().maxCount(1))
        );

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(PORTAL_LIGHTER);
        });

        SelectedCore.LOGGER.info("Portal Lighter item registered");
    }
}
