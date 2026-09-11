package com.strutton.dynamicmagic.client;

import net.minecraft.client.Minecraft;
import com.strutton.dynamicmagic.magic.CraftedSpell;
import com.strutton.dynamicmagic.network.OpenSpellbookPayload;
import com.strutton.dynamicmagic.skill.MagicSkill;

public final class ClientSpellbook {
    private static OpenSpellbookPayload data;
    private ClientSpellbook() {}
    public static void open(OpenSpellbookPayload payload) {
        data = payload;
        if (payload.editingSpell() != null || !payload.heldSpellbook()) openCraft(payload.editingSpell(), payload.editingSpell() != null, false);
        else openLoadout();
    }
    public static OpenSpellbookPayload data() { return data; }
    public static boolean knowsSkill(MagicSkill skill) {
        return data != null && (data.skills() & (1L << skill.ordinal())) != 0;
    }
    public static void openLoadout() {
        if (data != null) Minecraft.getInstance().setScreen(new SpellbookLoadoutScreen(data));
    }
    public static void openKnowledge() {
        if (data != null) Minecraft.getInstance().setScreen(new MagicKnowledgeScreen(data));
    }
    public static void openCraft(CraftedSpell editing) { openCraft(editing, false, editing != null); }
    private static void openCraft(CraftedSpell editing, boolean editHeld, boolean memoryOnly) {
        if (data != null) Minecraft.getInstance().setScreen(new SpellcraftScreen(data.snapshot(),
                new com.strutton.dynamicmagic.magic.CasterStats(data.control(), data.efficiency()),
                data.savedSpells(), editing, data.forceCaps(), editHeld, memoryOnly));
    }
}
