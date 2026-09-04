package com.strutton.dynamicmagic.client;

import net.minecraft.client.Minecraft;
import com.strutton.dynamicmagic.knowledge.KnowledgeSnapshot;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import java.util.List;

public final class ClientSpellbook {
    private ClientSpellbook() {}
    public static void open(KnowledgeSnapshot knowledge, com.strutton.dynamicmagic.magic.CasterStats stats,
                            List<CraftedSpell> saved, CraftedSpell editing, List<Double> forceCaps) {
        Minecraft.getInstance().setScreen(new SpellcraftScreen(knowledge, stats, saved, editing, forceCaps));
    }
}
