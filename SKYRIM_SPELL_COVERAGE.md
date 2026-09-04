# Skyrim spell coverage

Dynamic Spellcraft represents Skyrim's named spells as combinations instead of hard-coded one-off spells.

| Skyrim family | Dynamic Spellcraft construction |
|---|---|
| Fire, frost, and shock bolts/streams/runes/cloaks/walls | Fire/Ice/Lightning + Damage, Ignite, Freeze, Chain or Apply Effect + Bolt/Beam/Rune/Cloak/Wall |
| Armor, wards, and elemental resistance | Earth/Metal/Glass/Arcane + Protect or the matching resistance potion effect + Shield/Cloak |
| Healing, fast healing, regeneration, cure disease/poison | Divine + Heal/Cleanse/Apply Effect; duration and power reproduce tiers |
| Turn/repel undead, sun damage, undead circles | Divine + Turn Undead/Damage + Bolt/Burst/Rune/Cloak |
| Detect life/dead, candlelight, magelight | Spirit/Undead/Light + Detect Life/Detect Undead/Illuminate |
| Paralysis, waterbreathing, telekinesis, transmutation | Ice Apply Slowness, Water Apply Water Breathing, Physics, and Transmute Block |
| Calm, courage, fear, frenzy, rally, pacify, rout, mayhem | Spirit/Light/Shadow + Apply Effect/Mind Calm/Mind Fear/Mind Frenzy; Burst and radius reproduce group versions |
| Invisibility, muffle, clairvoyance | Shadow Apply Invisibility, Shadow Apply Speed, Spirit detection/programmed direction |
| Bound weapons | Summon source + Weapon form + Conjure Item |
| Familiar/atronach/daedra/ash/seeker summons | Summon Creature + the desired element; power, duration, and element select the tier |
| Raise zombie/revenant/thrall and heal undead | Undead + Reanimate, or Blood + Heal; duration selects temporary versus thrall-like upkeep |
| Soul trap | Spirit + Soul Trap + Bolt/Touch/Weapon |
| Banish/command daedra | Divine/Arcane + Turn Undead, Mind Calm, Mind Frenzy, or Contract Entity |
| Equilibrium and vampiric drain | Blood + Equilibrium converts health to mana without killing the caster; Blood + Drain Life steals health |
| Whirlwind cloak, ash spells, poison rune, vampire sight | Air Cloak Physics, Sand Apply Effect, Earth Rune Apply Poison, Shadow Apply Night Vision |

Every vanilla potion effect is exposed through `Apply Effect`. The effect itself chooses its required element, while power controls amplifier, duration controls lifetime, and the spell cost model charges ongoing mana maintenance.
