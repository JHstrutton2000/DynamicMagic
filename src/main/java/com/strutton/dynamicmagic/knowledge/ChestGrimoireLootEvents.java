package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.DynamicMagic;
import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** Adds discoverable element grimoires to generated chest loot, never to entity drops. */
public final class ChestGrimoireLootEvents {
    private static final String POOL_NAME = "dynamicmagic_grimoire_discovery";
    private static final float BOOK_CHANCE = .05f;
    private static final Element[] BASIC = {
            Element.WATER, Element.EARTH, Element.LIGHTNING, Element.AIR, Element.FIRE
    };
    private static final Element[] COMBINED = {
            Element.ICE, Element.METAL, Element.GLASS, Element.PLASMA,
            Element.QUICK, Element.SCORCH, Element.LAVA, Element.STORM
    };
    private static final Element[] ESOTERIC = {
            Element.LIGHT, Element.SHADOW, Element.ARCANE, Element.SPACE,
            Element.TIME, Element.DIVINE, Element.SPIRIT, Element.UNDEAD
    };

    private ChestGrimoireLootEvents() {}

    @SubscribeEvent
    public static void addGrimoires(LootTableLoadEvent event) {
        if (!event.getName().getPath().startsWith("chests/") || event.getTable().getPool(POOL_NAME) != null) return;

        LootPool.Builder pool = LootPool.lootPool()
                .name(POOL_NAME)
                .setRolls(ConstantValue.exactly(1))
                .when(LootItemRandomChanceCondition.randomChance(BOOK_CHANCE));
        addTier(pool, BASIC, 80);
        addTier(pool, COMBINED, 11);
        addTier(pool, ESOTERIC, 1);
        event.getTable().addPool(pool.build());
    }

    private static void addTier(LootPool.Builder pool, Element[] elements, int weight) {
        for (Element element : elements)
            pool.add(LootItem.lootTableItem(DynamicMagic.ELEMENT_GRIMOIRES.get(element).get()).setWeight(weight));
    }
}
