package com.strutton.dynamicmagic.client;
import com.strutton.dynamicmagic.network.ConfigureSpellNodePayload;
import com.strutton.dynamicmagic.network.OpenSpellNodePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
public final class SpellNodeScreen extends Screen {
    private final OpenSpellNodePayload data; private int left,top;
    public SpellNodeScreen(OpenSpellNodePayload data){super(Component.literal("Spell Automation Block"));this.data=data;}
    @Override protected void init(){left=(width-520)/2;top=Math.max(20,(height-320)/2);int i=0;for(String spell:data.spells()){if(i>=20)break;int row=i++;addRenderableWidget(Button.builder(Component.literal(spell),b->PacketDistributor.sendToServer(new ConfigureSpellNodePayload(data.pos(),spell,-1))).bounds(left+12+(row%2)*160,top+48+(row/2)*23,154,20).build());}for(int slot=0;slot<4;slot++){final int target=slot;String label=slot<data.crystals().size()?data.crystals().get(slot):"Empty";addRenderableWidget(Button.builder(Component.literal((slot+1)+": "+label),b->PacketDistributor.sendToServer(new ConfigureSpellNodePayload(data.pos(),"",target))).bounds(left+350,top+48+slot*46,154,32).build());}}
    @Override public void render(GuiGraphics g,int mx,int my,float pt){g.fill(0,0,width,height,0xE90B1019);g.fill(left,top,left+520,top+300,0xF21A2332);g.drawCenteredString(font,title,width/2,top+12,0xFFFFFF);g.drawString(font,"Selected: "+(data.selected().isBlank()?"None":data.selected()),left+12,top+34,0xFFE08A,false);g.drawString(font,"Remembered spells",left+12,top+260,0xA9EFFF,false);g.drawString(font,"Four crystal slots",left+350,top+34,0xC7A8FF,false);g.drawString(font,"Right-click the block with a crystal to insert it.",left+350,top+240,0x91A2B4,false);super.render(g,mx,my,pt);}
    @Override public boolean isPauseScreen(){return false;}
}
