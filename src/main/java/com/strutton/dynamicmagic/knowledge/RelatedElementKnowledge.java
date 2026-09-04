package com.strutton.dynamicmagic.knowledge;

import com.strutton.dynamicmagic.magic.Element;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Elemental synthesis discovered by repeatedly casting the required elements in one ordered spell. */
public final class RelatedElementKnowledge {
    private static final String KEY = "DynamicMagicRelatedElementStudy";
    private static final List<Relation> RELATIONS = List.of(
            new Relation(Element.ICE, 192, Element.WATER, Element.AIR),
            new Relation(Element.SAND, 320, Element.EARTH),
            new Relation(Element.SCORCH, 384, Element.FIRE, Element.AIR),
            new Relation(Element.LAVA, 768, Element.FIRE),
            new Relation(Element.QUICK, 512, Element.AIR, Element.LIGHTNING),
            new Relation(Element.METAL, 512, Element.EARTH, Element.LIGHTNING),
            new Relation(Element.GLASS, 640, Element.FIRE, Element.SAND),
            new Relation(Element.STORM, 768, Element.WATER, Element.AIR, Element.LIGHTNING),
            new Relation(Element.PLASMA, 1024, Element.FIRE, Element.LIGHTNING));
    private RelatedElementKnowledge() {}

    public static void practice(ServerPlayer player, Set<Element> used) {
        for (Relation relation : RELATIONS) {
            if (ElementKnowledge.knows(player, relation.result) || !used.containsAll(relation.ingredients)) continue;
            CompoundTag root = player.getPersistentData();
            CompoundTag progress = root.getCompound(KEY).copy();
            double total = progress.getDouble(relation.result.name()) + 1;
            progress.putDouble(relation.result.name(), total);
            root.put(KEY, progress);
            if (total < relation.requirement) continue;
            ElementKnowledge.learn(player, relation.result);
            player.displayClientMessage(Component.literal("Elemental synthesis breakthrough: "
                    + relation.result.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    public static void copy(ServerPlayer from, ServerPlayer to) {
        if (from.getPersistentData().contains(KEY))
            to.getPersistentData().put(KEY, from.getPersistentData().get(KEY).copy());
    }
    public static void reset(ServerPlayer player) { player.getPersistentData().remove(KEY); }

    private record Relation(Element result, double requirement, Set<Element> ingredients) {
        private Relation(Element result, double requirement, Element... ingredients) {
            this(result, requirement, EnumSet.copyOf(List.of(ingredients)));
        }
    }
}
