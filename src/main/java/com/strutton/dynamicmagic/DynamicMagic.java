package com.strutton.dynamicmagic;

import com.mojang.logging.LogUtils;
import com.strutton.dynamicmagic.item.SpellFocusItem;
import com.strutton.dynamicmagic.item.SpellbookItem;
import com.strutton.dynamicmagic.item.CraftedSpellItem;
import com.strutton.dynamicmagic.item.ElementGrimoireItem;
import com.strutton.dynamicmagic.command.MagicCommands;
import com.strutton.dynamicmagic.knowledge.KnowledgeEvents;
import com.strutton.dynamicmagic.knowledge.ChestGrimoireLootEvents;
import com.strutton.dynamicmagic.magic.Element;
import com.strutton.dynamicmagic.time.TimeMagicController;
import com.strutton.dynamicmagic.mana.ManaEvents;
import com.strutton.dynamicmagic.network.SpellNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import com.strutton.dynamicmagic.skill.MagicSkill;
import com.strutton.dynamicmagic.skill.SkillEvents;
import com.strutton.dynamicmagic.item.SkillTomeItem;
import com.strutton.dynamicmagic.magic.ProgramSpellController;

@Mod(DynamicMagic.MOD_ID)
public final class DynamicMagic {
    public static final String MOD_ID = "dynamicmagic";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredRegister.Blocks BLOCKS = net.neoforged.neoforge.registries.DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<net.minecraft.world.item.alchemy.Potion> POTIONS =
            DeferredRegister.create(Registries.POTION, MOD_ID);
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MANA_POTION =
            POTIONS.register("mana", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> VAMPIRE_CURE_POTION =
            POTIONS.register("vampire_cure", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> EXPANSION_RESET_POTION =
            POTIONS.register("expansion_reset", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MANA_EXPANSION_1 =
            POTIONS.register("mana_expansion_1", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MANA_EXPANSION_5 =
            POTIONS.register("mana_expansion_5", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MANA_EXPANSION_10 =
            POTIONS.register("mana_expansion_10", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MANA_EXPANSION_25 =
            POTIONS.register("mana_expansion_25", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<net.minecraft.world.item.alchemy.Potion, net.minecraft.world.item.alchemy.Potion> MANA_EXPANSION_50 =
            POTIONS.register("mana_expansion_50", () -> new net.minecraft.world.item.alchemy.Potion());
    public static final DeferredHolder<EntityType<?>, EntityType<com.strutton.dynamicmagic.entity.CreeperVillager>> CREEPER_VILLAGER =
            ENTITY_TYPES.register("creeper_villager", () -> EntityType.Builder
                    .of(com.strutton.dynamicmagic.entity.CreeperVillager::new, MobCategory.CREATURE)
                    .sized(.6f, 1.95f).clientTrackingRange(10).build("creeper_villager"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.strutton.dynamicmagic.entity.EndermanVillager>> ENDERMAN_VILLAGER =
            ENTITY_TYPES.register("enderman_villager", () -> EntityType.Builder
                    .of(com.strutton.dynamicmagic.entity.EndermanVillager::new, MobCategory.CREATURE)
                    .sized(.6f, 1.95f).clientTrackingRange(10).build("enderman_villager"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.strutton.dynamicmagic.entity.AlexVillager>> ALEX_VILLAGER =
            ENTITY_TYPES.register("alex_villager", () -> EntityType.Builder
                    .of(com.strutton.dynamicmagic.entity.AlexVillager::new, MobCategory.CREATURE)
                    .sized(.6f, 1.95f).clientTrackingRange(10).build("alex_villager"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.strutton.dynamicmagic.entity.SkeletonMageVillager>> SKELETON_MAGE_VILLAGER =
            ENTITY_TYPES.register("skeleton_mage_villager", () -> EntityType.Builder
                    .of(com.strutton.dynamicmagic.entity.SkeletonMageVillager::new, MobCategory.CREATURE)
                    .sized(.6f, 1.95f).clientTrackingRange(10).build("skeleton_mage_villager"));
    public static final DeferredItem<Item> SPELL_FOCUS = ITEMS.register("spell_focus",
            () -> new SpellFocusItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SPELLBOOK = ITEMS.register("spellbook",
            () -> new SpellbookItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CRAFTED_SPELL = ITEMS.register("crafted_spell",
            () -> new CraftedSpellItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MANA_CRYSTAL_LESSER = ITEMS.register("lesser_mana_crystal", () -> new com.strutton.dynamicmagic.item.ManaCrystalItem(100, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MANA_CRYSTAL_COMMON = ITEMS.register("mana_crystal", () -> new com.strutton.dynamicmagic.item.ManaCrystalItem(500, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MANA_CRYSTAL_GREATER = ITEMS.register("greater_mana_crystal", () -> new com.strutton.dynamicmagic.item.ManaCrystalItem(2000, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> MANA_CRYSTAL_PRISTINE = ITEMS.register("pristine_mana_crystal", () -> new com.strutton.dynamicmagic.item.ManaCrystalItem(10000, new Item.Properties().stacksTo(1)));
    public static final net.neoforged.neoforge.registries.DeferredBlock<net.minecraft.world.level.block.Block> SPELL_AUTOMATION_BLOCK = BLOCKS.register("spell_automation_block", () -> new com.strutton.dynamicmagic.block.SpellAutomationBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties.of().strength(3.5f).requiresCorrectToolForDrops()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> SPELL_AUTOMATION_BLOCK_ITEM = ITEMS.registerSimpleBlockItem("spell_automation_block", SPELL_AUTOMATION_BLOCK);
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<com.strutton.dynamicmagic.block.SpellAutomationBlockEntity>> SPELL_AUTOMATION_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("spell_automation_block", () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(com.strutton.dynamicmagic.block.SpellAutomationBlockEntity::new, SPELL_AUTOMATION_BLOCK.get()).build(null));
    public static final DeferredItem<Item> VAMPIRE_CURE = ITEMS.register("vampire_cure",
            () -> new com.strutton.dynamicmagic.item.VampireCureItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> VILLAGE_MAGE_SPAWN_EGG = ITEMS.register("village_mage_spawn_egg",
            () -> new com.strutton.dynamicmagic.item.VillageMageSpawnEggItem(new Item.Properties()));
    public static final DeferredItem<Item> EXPLOSION_TOME = ITEMS.register("explosion_tome",
            () -> new com.strutton.dynamicmagic.item.ExplosionTomeItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> TELEPORT_TOME = ITEMS.register("teleport_tome",
            () -> new com.strutton.dynamicmagic.item.TechniqueTomeItem(
                    com.strutton.dynamicmagic.magic.ImpactType.TELEPORT, 42, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> FERTILITY_TOME = ITEMS.register("fertility_tome",
            () -> new com.strutton.dynamicmagic.item.TechniqueTomeItem(
                    com.strutton.dynamicmagic.magic.ImpactType.FERTILITY, 34, new Item.Properties().stacksTo(1)));
    public static final Map<Element, DeferredItem<Item>> ELEMENT_GRIMOIRES = new EnumMap<>(Element.class);
    public static final Map<MagicSkill, DeferredItem<Item>> SKILL_TOMES = new EnumMap<>(MagicSkill.class);
    static {
        for (Element element : Element.values()) {
            ELEMENT_GRIMOIRES.put(element, ITEMS.register(element.name().toLowerCase(java.util.Locale.ROOT) + "_grimoire",
                    () -> new ElementGrimoireItem(element, new Item.Properties().stacksTo(1))));
        }
        for (MagicSkill skill : MagicSkill.values()) {
            SKILL_TOMES.put(skill, ITEMS.register(skill.name().toLowerCase(java.util.Locale.ROOT) + "_skill_tome",
                    () -> new SkillTomeItem(skill, new Item.Properties().stacksTo(1))));
        }
    }
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAGIC_TAB = TABS.register("magic",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dynamicmagic"))
                    .icon(() -> SPELL_FOCUS.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(SPELL_FOCUS.get());
                        output.accept(SPELLBOOK.get());
                        output.accept(MANA_CRYSTAL_LESSER.get()); output.accept(MANA_CRYSTAL_COMMON.get());
                        output.accept(MANA_CRYSTAL_GREATER.get()); output.accept(MANA_CRYSTAL_PRISTINE.get());
                        output.accept(SPELL_AUTOMATION_BLOCK_ITEM.get());
                        output.accept(VAMPIRE_CURE.get());
                        output.accept(VILLAGE_MAGE_SPAWN_EGG.get());
                        output.accept(EXPLOSION_TOME.get());
                        output.accept(TELEPORT_TOME.get());
                        output.accept(FERTILITY_TOME.get());
                        ELEMENT_GRIMOIRES.values().forEach(holder -> output.accept(holder.get()));
                        SKILL_TOMES.values().forEach(holder -> output.accept(holder.get()));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(MANA_POTION));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(VAMPIRE_CURE_POTION));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(EXPANSION_RESET_POTION));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(MANA_EXPANSION_1));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(MANA_EXPANSION_5));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(MANA_EXPANSION_10));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(MANA_EXPANSION_25));
                        output.accept(com.strutton.dynamicmagic.mana.ManaBrewing.potionStack(MANA_EXPANSION_50));
                    })
                    .build());

    public DynamicMagic(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        TABS.register(modBus);
        ENTITY_TYPES.register(modBus);
        POTIONS.register(modBus);
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent event) ->
        {
            var attributes = net.minecraft.world.entity.npc.Villager.createAttributes().build();
            event.put(CREEPER_VILLAGER.get(), attributes);
            event.put(ENDERMAN_VILLAGER.get(), attributes);
            event.put(ALEX_VILLAGER.get(), attributes);
            event.put(SKELETON_MAGE_VILLAGER.get(), attributes);
        });
        modBus.addListener(SpellNetworking::register);
        NeoForge.EVENT_BUS.register(ManaEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.mana.ManaMendingEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.mana.ManaBrewing.class);
        NeoForge.EVENT_BUS.register(KnowledgeEvents.class);
        NeoForge.EVENT_BUS.register(ChestGrimoireLootEvents.class);
        NeoForge.EVENT_BUS.register(MagicCommands.class);
        NeoForge.EVENT_BUS.register(TimeMagicController.class);
        NeoForge.EVENT_BUS.register(SkillEvents.class);
        NeoForge.EVENT_BUS.register(ProgramSpellController.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.magic.RuneMagicController.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.magic.DimensionDoorController.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.vampire.VampireEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.mage.VillageMageEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.mage.MorphMageEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.dragon.DragonEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.werewolf.WerewolfEvents.class);
        NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.skill.ExplosionProgression.class);
        if (net.neoforged.fml.ModList.get().isLoaded("aoa3"))
            NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.compat.AdventOfAscensionIntegration.class);
        if (net.neoforged.fml.ModList.get().isLoaded("apothic_spawners"))
            NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.compat.ApotheosisIntegration.class);
        if (net.neoforged.fml.ModList.get().isLoaded("irons_spellbooks"))
            com.strutton.dynamicmagic.compat.IronSpellsIntegration.register(NeoForge.EVENT_BUS);
        if (net.neoforged.fml.ModList.get().isLoaded("sololeveling")) {
            NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.compat.SoloLevelingIntegration.class);
            com.strutton.dynamicmagic.compat.SoloLevelingIntegration.register(NeoForge.EVENT_BUS);
        }
        if (net.neoforged.fml.ModList.get().isLoaded("morph")) {
            NeoForge.EVENT_BUS.register(com.strutton.dynamicmagic.morph.MorphV2Integration.class);
            com.strutton.dynamicmagic.morph.MorphV2Integration.registerMorphEvent(NeoForge.EVENT_BUS);
        }
    }
}
