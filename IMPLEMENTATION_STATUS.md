# Implementation status — v0.11.0

Implemented:

- Up to eight independent IF branches per toggled spell program, each with its own condition, interval, sensor, target routing, and eight-action chain
- Simultaneous true branches execute in authoring order and pay independent action costs in addition to shared program upkeep
- Falling, health below 25%/50%, poisoned/withered, burning, low-mana, and low-hunger self-state conditions
- Adaptive Survival example: falling cushion, low-health heal, and poison cleanse in one spell item
- Branch-aware NBT migration, server validation, learned-knowledge filtering, editing, tooltips, and cost calculation
- JUnit tests for cost scaling and branch persistence, plus NeoForge GameTests for actual entity healing, cleansing, movement, fall state, and simultaneous condition matching

- Ordered recipes containing up to eight independently configured runtime operations
- Per-operation element, impact, force, direction, target, range, radius, duration, repetition, and physics controls
- Server-authoritative full-recipe NBT networking and validation
- Backward migration of legacy spell items and saved recipes into one-operation chains
- Water/Wet interactions with Lightning amplification, Ice duration, and Fire steam reactions
- True beam volumes that affect entities throughout the configured path
- Multi-element Storm Lance and Gravitic Snare demonstration recipes
- `/magic mana set <maximum>`, `/magic mana set unlimited`, and `/magic mana refill`
- Persistent current, maximum, unlimited, and exhaustion mana state
- Synchronized mana HUD above the hunger bar
- Nausea, Slowness II, and controlled maximum-mana growth on true exhaustion
- Nonlinear magnitude/complexity spell math, combination tax, and per-element multipliers
- Dedicated Stop/Accelerate/Slow temporal upkeep loads
- Client ticking-state broadcast for visibly accelerated/slowed entities
- Remote 27-slot item chest
- Custom paginated stored-entity menu and selected entity summoning
- Opaque, non-blurred spellbook and entity-storage backdrops
- Existing v0.4 knowledge, conditional program, direction, wind lift, time, and storage systems
- Divine element, healing/cleansing law, Divine grimoire, and Aim/Self selection
- Persistent caster control and efficiency progression
- Instability dissipation, backlash, and self-collapse outcomes
- Persistent personal spell recipes shown in the spellbook
- Entity contracts with strength/aggression/size resistance and maintained summons
- Previously-seen item memory and temporary elemental item constructs
- Distinct Water, Earth, Lightning, Light, Shadow, Arcane, Space, Time, and Divine effects
- Rebindable G spellcraft and H held-spell editor keys with server-authoritative updates
- Hunger-scaled mana regeneration
- Discoverable Hunger Ward, Mana Well, and Divine Vitality skill tomes
- Independent per-element mastery and server-enforced force ceilings
- Program targeting through caster aim, direction-to-detected, or direct detected entity
- Exact 1% compounding maximum-mana growth per genuine exhaustion
- Persistent 12,000-tick (10 in-game minute) cooldown between maximum-mana increases
- Vanilla 4.0 exhaustion cost for every successful mana expansion, including a one-loss Hunger Ward bypass
- Generic entity-physics impact replacing Knockback: add/set/multiply/reverse/stop velocity, gravity control, and fall reset
- Overdraw failure empties mana and applies exhaustion penalties; a shortfall of at most 50 mana grants the exact 1% maximum-mana increase, while a larger shortfall does not
- Server-side toggle runtimes for multiple simultaneous programmed spells
- Program release by clicking the same spell or exhausting mana
- Premade Hasten Time Toggle program
- Linear hunger-scaled regeneration from 0% to 1% of maximum mana per second
- Held Wind Lift/Float applies stable upward velocity every tick, stops on release, and migrates legacy downward-recoil spell data
- Element-scoped enlightenment and increasingly difficult natural breakthroughs (256, 512, 768, ... relevant successful casts)
- Repeat element grimoires act as focused study items that force only the next technique relevant to that element
- Newly learned elements grant only one fundamental technique instead of their entire advanced technique set
- `/magic mastery` reports per-element enlightenment progress and next breakthrough thresholds
- Regression GameTests for held upward Float, legacy lift migration, slow breakthroughs, and prevention of cross-element Heal discovery
- Block-targeted elemental transmutation: Water/lava obsidian, Water creation, Fire/water cobblestone, Ice freezing, low-cost fire removal, and combustible ignition
- Order-independent Fire/Water and Water/Ice combined-spell resolution using explicit force thresholds and ratios
- Mana-maintained Rain, Storm, and Spirit Projection with restoration of prior weather/game mode on release or depletion
- Real lightning-bolt summoning, mastery-scaled Metal ore highlighting, Spirit life detection/fertility, and Undead detection/pacification/corruption
- Divine adverse damage against undead and force-gated zombie-villager/zombified-piglin resurrection
- Starter Study Element spell with block/entity observation, mastery training, rarity-scaled element acquisition, and known-element enlightenment
- Persistent synthesis progression for Ice, Scorch, Lava, Quick, Metal, Glass, Storm, and Plasma
- Tiered wild grimoire drops for basic, combined, and esoteric elements, including models and names for all new books
- Persistent projectile accuracy progression and real projectile ray spread
- Expanded integration suite covering elemental block laws, ratio outcomes, holy/Spirit entity laws, study/synthesis thresholds, projection restoration, and accuracy growth

Validation:

- `gradlew test` passes on JDK 21.
- `gradlew runGameTestServer` passes all eleven required NeoForge GameTests.
- `gradlew build` passes on JDK 21.
