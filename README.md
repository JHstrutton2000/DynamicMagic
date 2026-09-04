# Dynamic Spellcraft 0.11.0

A NeoForge 1.21.1 mod for learned spell grammar, programmable magic, spatial storage, time manipulation, and expandable mana.

## Dynamic spell instructions

Spells are no longer a single hard-coded impact with a power slider. Every recipe has a shared carrier—source, form, and delivery—and an ordered chain of up to eight operations. Each operation independently chooses:

- A learned element and compatible impact
- Force, direction, and Aim/Self target
- Casting range and effect radius
- Effect duration and one to eight repetitions
- A low-level physics operation when Physics is selected

Operations execute in their displayed order and the cost model consumes the same instruction list. Added range, radius, duration, repetitions, elements, and operations therefore change both behavior and mana/control requirements. Existing spell items and saved recipes automatically migrate to a one-operation chain when read.

Elemental state makes ordering meaningful. Water leaves targets wet for the configured duration. A following Lightning operation consumes Wet for 50% more force, Ice consumes it for doubled freezing duration, and Fire striking Wet or Frozen produces damaging steam. Beam forms affect every entity along their path, while each operation's radius controls its endpoint area.

The editor's **Add Step**, **Duplicate**, **Remove**, and operation arrows construct and navigate the chain. Two included examples demonstrate composition: **Storm Lance** combines Water with Lightning, and **Gravitic Snare** combines an Earth root with an Air stop-motion operation.

## Keybind workflow

- Press **G** to open spell creation from anywhere.
- Hold a crafted spell and press **H** to load it into the editor; **Save Changes** updates that exact held item.
- Both bindings are configurable under Controls → Dynamic Spellcraft.

The old spellbook remains registered so existing saves do not lose it, but it is no longer craftable or shown in the creative tab.

## Discoverable skills

Hostile creatures have a 2.5% chance to drop one skill tome the killer has not learned:

- **Hunger Ward:** prevents hunger and saturation from decreasing.
- **Mana Well:** increases mana regeneration by 75%.
- **Divine Vitality:** restores one health every three seconds in addition to ordinary healing.

Mana regeneration scales linearly with hunger and maximum pool size: zero hunger gives no regeneration, half hunger restores 0.5% of maximum mana per second, and full hunger restores 1% per second. Mana Well multiplies that result by 1.75.

## Element mastery and force

Every element has independent experience. Successfully casting an element trains it and raises that element's maximum force from a base of 3 toward an absolute maximum of 10. The spell editor displays the current selected element's force ceiling, and the server independently rejects forged or edited recipes above it. `/magic mastery` reports control, efficiency, and every learned element's maximum force.

Successful spells also grant one point of enlightenment to each distinct element actually used by that spell. Discoveries are element-scoped: practicing Air can reveal Air movement and physics techniques, but can never reveal Divine healing. The first natural breakthrough takes 256 successful relevant casts, and each later breakthrough for that element costs another 256 points more than the previous one. Learning a new element grants only its fundamental technique; the rest require practice and study.

An additional grimoire for an element the caster already knows is consumed as focused study and supplies exactly enough enlightenment to force that element's next available breakthrough. `/magic mastery` displays each known element's current enlightenment and next threshold.

## Program targets

Programs preserve the nearest entity found by Entity Nearby, Hostile Nearby, or Entity In Sight. Their `THEN` selector can retain caster aim, fire in the direction of the detected entity, or apply the spell directly to that entity. Runtime sensing upkeep and conditional release costs still apply separately.

### Independent IF branches

A program contains up to eight independent branches, and each branch contains up to eight ordered actions. Every branch has its own condition, polling interval, sensor range, and target routing. Use **+ Branch**, the branch arrows, and **Add Action** in the editor. All matching branches may execute during the same tick; each pays its own action cost, while the whole toggled program pays shared background upkeep.

Additional self-state conditions include health below 25%/50%, falling, poisoned or withered, burning, low mana, low hunger, standing on ground, and being in water. The included **Adaptive Survival** program demonstrates three branches: Air cushioning while falling, Divine healing below half health, and Divine cleansing while poisoned.

Programmed spells are toggles. Clicking one pays formation and activates a server-side runtime that continues even when the item is put away or the hotbar slot changes. Clicking that same spell again releases it. Different programs may run together and pay upkeep independently. A program automatically releases when upkeep or a triggered cast drains the pool. **Hasten Time Toggle** is a premade always-running program that accelerates the complete server tick rate under these rules.

## General physics magic

The dedicated Knockback impact has been removed. Existing Knockback spell NBT migrates to **Physics**, which exposes the underlying movement operations instead:

- Add velocity in the selected direction
- Set directional velocity
- Multiply or reverse current velocity
- Stop motion
- Reset fall distance
- Disable or enable gravity

Physics can use Aim/Self and program-detected targets. Wind Lift/Float is a continuous self-targeted upward-velocity spell: it applies lift every tick while held, stops applying lift immediately on release, and does not add a final release impulse. Legacy Wind Lift items whose old recoil encoding pointed Down are migrated to Up when loaded.

## Divine magic and targeting

Divine is a learnable, non-starter element whose fundamental law is restoration. Divine energy heals living targets and removes poison/wither rather than causing damage. Every crafted spell now has an independent **Aim/Self** target choice, so attacks, protection, healing, movement, and utility can deliberately target the caster or the aimed location/entity. Learn Divine with a Divine Grimoire or `/magic learn divine`.

## Element laws

- Fire ignites; Water extinguishes fire and thawing; Air pushes and provides directional lift.
- Earth roots movement; Ice damages and freezes; Lightning chains through nearby living targets.
- Light illuminates targets with a glow; Shadow blinds; Arcane dispels active effects.
- Space teleports, stores, contracts, and summons; Time stops, slows, or accelerates every server tick.
- Divine heals and cleanses. Every ordinary element can also learn to conjure a temporary imitation of a previously held item.

### World reactions and elemental ratios

The **Shape matter** impact applies elemental force directly to blocks. Water force 2 or higher turns lava into obsidian; Water force 3 or higher forms a water source at the struck replaceable space or adjacent face. Fire force 2.5 or higher turns water into cobblestone, while Ice force 2 or higher freezes water. Water's low-cost Extinguish operation removes fire blocks as well as fire on living targets, and Fire/Scorch/Lava/Plasma Ignite spells readily light combustible blocks.

Ordered Shape matter operations are resolved together. Water plus Ice immediately creates ice once both contribute at least force 2. Water plus Fire needs total force 4: a Water-to-Fire ratio of at least 1.25 creates obsidian, while a hotter mixture creates cobblestone. This ratio is independent of operation order.

### Weather, lightning, and living magic

Water can learn **Call rain**, while the synthesized Storm element can learn **Call storm**. Both require Continuous delivery, consume ongoing mana, and restore the prior weather when released or depleted. Lightning and Storm can learn **Summon lightning**, which creates a real lightning bolt at the targeted block once force reaches 2.

Divine magic damages all undead rather than healing them. The advanced force-4 **Resurrect** operation restores zombie villagers to villagers and zombified piglins to pigs. Spirit magic can outline living creatures, place animals into breeding readiness without food, and perform a mana-maintained Spirit Projection into spectator mode; ending the spell restores the caster's previous game mode. Undead magic can outline undead, suppress their aggression toward the caster for the configured duration, and—with force 3—corrupt villagers into zombie villagers.

Metal's **Detect ores** operation highlights ore blocks with caster-only particles. Its scan radius grows with spell force and Metal mastery.

### Study, related elements, and wild grimoires

Every new caster knows the premade **Study Element** spell. Studying a block or creature trains the observed element a little; 80 points teaches a basic element, with the requirement multiplied for rarer and more powerful elements. Water, lava, ice, glass, ores/metals, fire, soul/sculk matter, lightning rods, living creatures, and undead each reveal their corresponding element. Studying a known element contributes to its enlightenment instead.

Using related elements together also accumulates synthesis knowledge. The implemented relationships are Water+Wind→Ice (192 casts), Fire+Wind→Scorch (320), Fire+Earth→Lava (384), Wind+Lightning→Quick (512), Earth+Lightning→Metal (512), Fire+Earth+Wind→Glass (640), Water+Wind+Lightning→Storm (768), and Fire+Lightning→Plasma (1024).

Hostile creatures can drop unknown grimoires: basic Water/Earth/Lightning/Wind/Fire books are uncommon, compound-element books are much rarer, and esoteric books such as Space, Time, Divine, Spirit, and Undead are rarest. Additional copies still act as focused breakthrough study.

### Projectile accuracy

Projectile spells begin with imperfect accuracy. Successful projectile practice persistently reduces ray spread, with longer-range practice providing more experience. Accuracy approaches—but never reaches—100%, and `/magic mastery` reports the current value.

## Mastery, instability, and constructs

Control and efficiency are persistent learned caster statistics rather than constants. Difficult successful casts train them. Low control raises cost and now has physical consequences: dissipation, backlash damage, or collapse onto the caster. `/magic mastery` displays both values.

The **Conjure seen item** impact remembers mundane item types the player has held, creates an elemental imitation of the most recently learned shape, and dissolves that construct after its power-scaled lifetime.

## Contracts

**Contract entity** tests the caster's control and spell power against the target's health, armor, aggression, and physical size. Rejection can make a mob hostile. Successful contracts are persistent reusable summon templates. **Summon contract** manifests the latest pact; its strength-derived mana tether drains every second and the summon dissolves when the caster cannot sustain it.

## Personal spell recipes

Creating a custom spell now saves or updates that recipe by name. Up to 64 personal recipes persist with the player and appear as diamond-marked buttons in the spellbook alongside built-in favorites. Clicking one produces another spell item.

## Mana commands

```mcfunction
/magic mana set 500
/magic mana set unlimited
/magic mana refill
```

Setting a finite maximum also fills the pool. Unlimited mana bypasses every formation, upkeep, program-runtime, and conditional-cast payment. The synchronized blue mana bar is rendered immediately above the hunger bar.

When an affordable spell payment lands exactly at zero, the player receives 10 seconds of nausea and 8 seconds of Slowness II, and maximum mana grows by exactly 1% of its current maximum. An unaffordable spell still fails and empties the remaining pool, but if its cost was no more than 50 mana above the amount remaining, that near-exhaustion can also grow maximum mana by exactly 1%. A shortfall greater than 50 applies the penalties without growth. Every expansion starts a persistent 12,000-tick cooldown—10 in-game minutes—and another growth event also requires the pool to regenerate above 10%. A successful expansion adds 4.0 vanilla exhaustion, consuming one saturation point first or one food point when saturation is empty. Hunger Ward blocks ordinary hunger loss but deliberately permits this single expansion cost.

## Spell-cost model

Costs now use nonlinear magnitude, squared complexity, effect-combination tax, control instability, and element scarcity. Air/Earth/Water are baseline; Fire/Ice, Light/Shadow, Lightning, Arcane, Space, and Time become progressively more expensive. Sustained time magic adds a dedicated temporal-maintenance load, with Stop Time substantially above acceleration, slowing, and ordinary elemental attacks.

## Time acceleration

Stop, slow, and accelerate spells still control the server's global tick-rate manager. v0.5 additionally broadcasts each changed ticking state to every client. This keeps client interpolation synchronized so mobs, AI, projectiles, physics, block entities, and other entity movement visibly follow accelerated or slowed time.

## Spatial storage

- **Item Storage** opens a remotely accessible 27-slot chest. It uses the player's persistent Ender Chest inventory, so items survive death and world reloads.
- **Store Entity** captures a targeted non-player entity into the 32-entry magical entity vault with complete NBT.
- **Entity Storage** opens a crisp custom list. Clicking **Summon** restores the chosen stored entity in front of the caster and removes that entry from the vault.

## Readable interfaces

The spellbook and entity-storage screens use an opaque custom backdrop. The vanilla world-blur pass is not invoked, preventing the title, learned-favorites label, cost preview, and other text from being blurred beneath the button layer.

## Knowledge and programming

New players start with one randomized basic element and only Create from Mana, Bolt, Projectile, Damage, Look Direction, and Always. Dimensional Summoning requires Space. Every element, form, delivery, impact, direction, condition, favorite, and program option is filtered against server-synchronized knowledge.

```mcfunction
/magic learn all
/magic known
/magic storage
/magic mastery
/magic skills
/magic forget_all
```

`/magic forget_all` is an operator-only reset intended for test characters, including saves that learned unrelated techniques under the earlier global-discovery system.

Programmed spells continuously pay runtime mana, test their sensor condition, and pay a separate casting cost only when their branch executes.

## Development

Use JDK 21:

```powershell
.\gradlew build
.\gradlew runClient
.\gradlew test
.\gradlew runGameTestServer
```

`test` runs deterministic recipe, persistence, and mana-cost checks. `runGameTestServer` runs headless integration tests against real Minecraft entity health, effects, velocity, fall distance, and live condition state.
