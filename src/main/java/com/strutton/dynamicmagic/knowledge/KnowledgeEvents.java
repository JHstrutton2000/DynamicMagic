package com.strutton.dynamicmagic.knowledge;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import com.strutton.dynamicmagic.storage.MagicStorage;
import com.strutton.dynamicmagic.mana.Mana;
import com.strutton.dynamicmagic.magic.CasterMastery;
import com.strutton.dynamicmagic.storage.MagicContracts;
import com.strutton.dynamicmagic.magic.SavedSpellLibrary;

public final class KnowledgeEvents {
    private KnowledgeEvents() {}

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        ElementKnowledge.copy(event.getOriginal(), event.getEntity());
        ComponentKnowledge.copy(event.getOriginal(), event.getEntity());
        if (event.getOriginal() instanceof ServerPlayer original && event.getEntity() instanceof ServerPlayer replacement)
        {
            MagicStorage.copy(original, replacement);
            Mana.copy(original, replacement);
            CasterMastery.copy(original, replacement);
            MagicContracts.copy(original, replacement);
            SavedSpellLibrary.copy(original, replacement);
            com.strutton.dynamicmagic.magic.TeleportLocations.copy(original, replacement);
            com.strutton.dynamicmagic.skill.SkillKnowledge.copy(original, replacement);
            com.strutton.dynamicmagic.magic.ElementMastery.copy(original, replacement);
            com.strutton.dynamicmagic.magic.ProjectileAccuracy.copy(original, replacement);
            RelatedElementKnowledge.copy(original, replacement);
            ElementAffinity.copy(original, replacement);
            com.strutton.dynamicmagic.vampire.Vampirism.copy(original, replacement);
            StudyKnowledge.copy(original, replacement);
            com.strutton.dynamicmagic.dragon.DragonProgression.copy(original, replacement);
            com.strutton.dynamicmagic.skill.ExplosionProgression.copy(original, replacement);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        ComponentKnowledge.ensureStarter(event.getEntity());
    }
}
