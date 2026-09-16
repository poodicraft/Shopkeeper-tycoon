# Balance notes

The tuning the game ships with, and why. It lives in `game/ProductType.java`,
`game/Upgrade.java`, `game/GameState.java` and `game/Shop.java`.

## The day

One in-game day is **180 seconds**. The shop trades between 6% and 92% of it;
outside that, footfall is a trickle. `GameState.trafficCurve()` layers a morning
bump, a lunchtime peak and an evening rush over a flat base, so a day has a shape
you can feel without being punishing.

## Products

| Product | Unlocks | Cost | Base price | Margin | Demand |
|---|---|---|---|---|---|
| Bread | 1 | $2 | $5 | 2.5x | 1.35 |
| Milk | 1 | $3 | $8 | 2.7x | 1.20 |
| Coffee | 2 | $7 | $17 | 2.4x | 1.10 |
| Cheese | 3 | $12 | $28 | 2.3x | 0.95 |
| Chocolate | 4 | $20 | $46 | 2.3x | 0.90 |
| Wine | 5 | $34 | $78 | 2.3x | 0.75 |
| Sushi | 6 | $58 | $130 | 2.2x | 0.65 |
| Caviar | 7 | $105 | $240 | 2.3x | 0.50 |

Margins hold near 2.3x across the range, so higher tiers are worth more per sale
without making the early game pointless. Demand falls as value rises, which is what
stops a shop of pure caviar from being strictly correct.

## Demand

Each shopper rolls a personal ceiling:

```
willingness = priceTolerance * (0.86 .. 1.20)
ceiling     = product.basePrice * willingness
```

`priceTolerance` starts near 1.04 and rises with Decor and with satisfaction. A
shelf priced above a shopper's ceiling is skipped entirely.

Shopping lists are weighted 3.2x toward products actually on a shelf right now and
0.55x toward everything else. Without that bias the opening shop — which can only
stock one or two lines — turns away most of its customers, which tested as
unwinnable rather than difficult.

## The till

Scanning takes `0.52s * 0.82^scannerLevel` **per item**, and a basket holds one to
six. That is the core tension: a big basket pins you behind the counter while
shelves empty and the queue grows. A hired cashier works at 1.25x that time, so
they are slower than an attentive owner but they never leave the till.

Arrivals throttle once three people are waiting, dropping to 12% of the base rate
by the time the line is long. People who can see a queue through the window keep
walking, which is both more believable and much kinder to the satisfaction score
than letting them come in and storm out.

## Carrying

The player carries `10 + 4 * stockerLevel` units at a time and moves at 74% speed
while loaded. Filling a shelf takes `0.14s` per unit. A full shelf is
`12 + 6 * shelvingLevel` units, so restocking a maxed shelf is several trips —
which is what makes hiring a stocker feel like a promotion rather than a discount.

## Costs

- Rent: `$18 + $7 per shelf` per day
- Wages: `$55 per cashier + $40 per stocker` per day
- Shelving: `$180 * 1.48^n` for the nth of twelve slots

## Reference results

`tools/run-tests.sh` plays five days with an autopilot shopkeeper that orders,
carries, stocks and serves, but never optimises prices. It reaches level 4 with
roughly $950 banked, serving 15-18 customers a day and losing three to eight. A
player who actually watches the queue and prices deliberately does considerably
better, which is the intended shape.
