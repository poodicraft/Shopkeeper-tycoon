# Balance notes

The numbers below are the tuning the game ships with, and why. They live in
`game/ProductType.java`, `game/Upgrade.java`, `game/GameState.java` and
`game/Shop.java`.

## Day length

One in-game day is **180 seconds**. The shop trades between 6% and 92% of the
day; outside that, footfall drops to a trickle. `GameState.trafficCurve()` layers
a morning bump, a lunchtime peak and an evening rush over a flat base, so the
rhythm of a day is visible without being punishing.

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

Margins stay near 2.3x across the range, so higher tiers are worth more per sale
without making the early game pointless. Demand weight falls as value rises,
which is what stops a shop of pure caviar from being strictly correct.

## Demand

Each shopper rolls a personal willingness to pay:

```
willingness = priceTolerance * (0.86 .. 1.20)
ceiling     = product.basePrice * willingness
```

`priceTolerance` starts at about 1.04 and rises with the Decor upgrade and with
satisfaction. A shelf priced above a shopper's ceiling is skipped entirely.

Shopping lists are weighted 3.2x toward products actually on a shelf right now,
and 0.55x toward everything else. Without that bias the early shop — which can
only stock one or two lines — turns away most of its customers, which tested
badly: the first few days were unwinnable.

## Throughput

Checkout takes `3.0s * 0.79^registerLevel`. With no cashier it runs at half speed
and takes 1.35x as long, so serving by hand is worth roughly 2.7x the throughput
of ignoring the queue — enough to make tapping matter without making idle play
pointless.

Arrivals throttle once three people are waiting, falling to 12% of the base rate
by the time the line is long. People who would have walked out simply never come
in, which is both more realistic and much kinder to the satisfaction score than
letting them enter and storm out.

## Costs

- Rent: `$18 + $7 per shelf` per day
- Wages: `$55 per cashier + $40 per stocker` per day
- Shelves: `$140 * 1.52^n` for the nth slot, fifteen in total

## Reference results

`tools/run-tests.sh` plays six days with an autopilot shopkeeper that never taps
the till. It reaches level 4 with roughly $1,150 in the bank, serving 13-17
customers a day and losing fewer than 5. A player who actually serves the queue
does considerably better, which is the intended shape.
