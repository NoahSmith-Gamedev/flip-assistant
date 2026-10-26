package com.flipassistant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Turns wiki price data into a ranked list of flip suggestions.
 *
 * This class has no RuneLite or network code in it, so the math can be tested on its own.
 */
public final class FlipCalculator
{
	/** Jagex caps the GE tax at 5M per item. */
	static final long TAX_CAP = 5_000_000L;

	/**
	 * How far (as a fraction) the latest price may drift from the 1-hour average before
	 * we treat it as a spike and skip the item. 0.15 means 15%.
	 */
	static final double MAX_DRIFT_FROM_AVERAGE = 0.15;

	/** Normal mode skips items that fell more than this % over the last day (when "skip falling" is on). */
	static final double FALLING_THRESHOLD = -3.0;
	/** Overnight mode is stricter: skip anything that fell more than this %. */
	static final double OVERNIGHT_FALLING_THRESHOLD = -1.5;

	/** The user's settings, copied out of the RuneLite config. */
	public static final class Settings
	{
		long cash;
		long maxPerItem;
		/** Minimum trades per hour on EACH side (instant buys and instant sells). */
		long minFillsPerHour;
		long maxPriceAgeSeconds;
		boolean includeMembers;
		int taxPercent;
		int maxRoiPercent;
		int maxResults;

		/** Fraction-of-market assumption: % of the market's trades your offer is expected to win. */
		int marketSharePercent = 20;
		/** Normal mode: aim for each offer to fill within this many minutes. */
		int maxFillMinutes = 30;
		/** Normal mode: give up this % of the spread on each side so offers fill faster. */
		int priceBoostPercent = 0;
		StabilityFilter stabilityFilter = StabilityFilter.MODERATE_OR_BETTER;
		boolean skipFalling = true;

		boolean overnight = false;
		int overnightHours = 8;
	}

	private FlipCalculator()
	{
	}

	/** GE tax on selling one item at this price, rounded down and capped. */
	static long taxFor(long sellPrice, int taxPercent)
	{
		long tax = sellPrice * taxPercent / 100;
		return Math.min(tax, TAX_CAP);
	}

	/** Extra gp to bid above the latest sell price (and ask below the latest buy price). */
	static long boostFor(long spread, int boostPercent)
	{
		if (spread <= 2 || boostPercent <= 0)
		{
			return 0;
		}
		return Math.round(spread * boostPercent / 100.0);
	}

	public static List<FlipSuggestion> calculate(
		Map<Integer, ItemInfo> items,
		Map<Integer, LatestPrice> latest,
		Map<Integer, HourPrice> lastHour,
		Map<Integer, ItemStats> stats,
		long nowSeconds,
		Settings s)
	{
		List<FlipSuggestion> results = new ArrayList<>();
		long budget = Math.min(s.cash, s.maxPerItem);
		double share = s.marketSharePercent / 100.0;
		double windowHours = s.overnight ? s.overnightHours : s.maxFillMinutes / 60.0;
		if (budget <= 0 || share <= 0 || windowHours <= 0)
		{
			return results;
		}
		if (stats == null)
		{
			stats = Collections.emptyMap();
		}

		for (ItemInfo item : items.values())
		{
			if (!s.includeMembers && item.members)
			{
				continue;
			}

			LatestPrice lp = latest.get(item.id);
			HourPrice hp = lastHour.get(item.id);
			if (lp == null || hp == null
				|| lp.high == null || lp.low == null || lp.highTime == null || lp.lowTime == null
				|| hp.avgHighPrice == null || hp.avgLowPrice == null)
			{
				continue;
			}

			// Both sides must have traded recently, or the prices are stale.
			if (nowSeconds - lp.highTime > s.maxPriceAgeSeconds || nowSeconds - lp.lowTime > s.maxPriceAgeSeconds)
			{
				continue;
			}

			// Skip items whose latest prices are far from their hourly average (likely a spike or manipulation).
			if (drift(lp.low, hp.avgLowPrice) > MAX_DRIFT_FROM_AVERAGE || drift(lp.high, hp.avgHighPrice) > MAX_DRIFT_FROM_AVERAGE)
			{
				continue;
			}

			ItemStats st = stats.get(item.id);
			Stability stability = st == null ? Stability.UNKNOWN : st.stability;
			Double trend = st == null ? null : st.trendPercent;

			long buy;
			long sell;
			if (s.overnight)
			{
				// Overnight offers sit for hours, so only steady, non-falling items with a known day range qualify.
				if (st == null || stability != Stability.STABLE || trend == null || trend < OVERNIGHT_FALLING_THRESHOLD
					|| st.dayLowPrice == null || st.dayHighPrice == null)
				{
					continue;
				}
				// Patient prices: bid toward the low end of the day's range, ask toward the high end.
				buy = Math.min(lp.low + 1, st.dayLowPrice);
				sell = Math.max(lp.high - 1, st.dayHighPrice);
			}
			else
			{
				if (!s.stabilityFilter.allows(stability))
				{
					continue;
				}
				if (s.skipFalling && trend != null && trend < FALLING_THRESHOLD)
				{
					continue;
				}
				// Bid just above the latest instant-sell price and ask just below the latest instant-buy price.
				// The optional boost gives up part of the spread so offers fill faster.
				long boost = boostFor(lp.high - lp.low, s.priceBoostPercent);
				buy = lp.low + 1 + boost;
				sell = lp.high - 1 - boost;
			}
			if (buy <= 0 || sell <= buy)
			{
				continue;
			}

			long tax = taxFor(sell, s.taxPercent);
			long margin = sell - tax - buy;
			if (margin <= 0)
			{
				continue;
			}

			// Very large margins are usually traps: thin markets or manipulated prices.
			if ((double) margin / buy * 100.0 > s.maxRoiPercent)
			{
				continue;
			}

			// Your buy offer fills from other players' instant sells; your sell offer fills from their instant buys.
			// Use the slower of the last hour and the day's average, to stay on the safe side.
			double buyFills = hp.lowPriceVolume;
			double sellFills = hp.highPriceVolume;
			if (st != null && st.hoursWithPrice >= StatsCalculator.MIN_HOURS)
			{
				buyFills = Math.min(buyFills, st.avgBuyFillsPerHour);
				sellFills = Math.min(sellFills, st.avgSellFillsPerHour);
			}
			if (buyFills <= 0 || sellFills <= 0 || Math.min(buyFills, sellFills) < s.minFillsPerHour)
			{
				continue;
			}

			// You won't win every trade: other flippers compete. Assume you get your market share,
			// and keep the order small enough that both the buy and the sell finish within the window.
			// This also limits how much your own order pushes the price.
			double myBuyRate = buyFills * share;
			double mySellRate = sellFills * share;
			long qty = budget / buy;
			if (item.limit != null)
			{
				qty = Math.min(qty, item.limit);
			}
			qty = Math.min(qty, (long) Math.floor(myBuyRate * windowHours));
			qty = Math.min(qty, (long) Math.floor(mySellRate * windowHours));
			if (qty < 1)
			{
				continue;
			}

			double buyMinutes = qty / myBuyRate * 60.0;
			double sellMinutes = qty / mySellRate * 60.0;
			long total = margin * qty;
			double hoursWaiting = Math.max((buyMinutes + sellMinutes) / 60.0, 1.0 / 60.0);
			long perHour = Math.round(total / hoursWaiting);
			double impact = qty / buyFills * 100.0;

			results.add(new FlipSuggestion(item.id, item.name, buy, sell, tax, margin, (int) qty, total, perHour,
				buyMinutes, sellMinutes, impact, buyFills, sellFills, item.limit, stability, trend));
		}

		if (s.overnight)
		{
			// Overnight: you're waiting anyway, so rank by total profit.
			results.sort((a, b) -> Long.compare(b.totalProfit, a.totalProfit));
		}
		else
		{
			// Active flipping: rank by profit per hour so fast, reliable flips beat slow ones.
			results.sort((a, b) -> Long.compare(b.profitPerHour, a.profitPerHour));
		}
		if (results.size() > s.maxResults)
		{
			return new ArrayList<>(results.subList(0, s.maxResults));
		}
		return results;
	}

	/** How far a price is from its average, as a fraction (0.1 = 10%). */
	private static double drift(long price, long average)
	{
		if (average <= 0)
		{
			return Double.MAX_VALUE;
		}
		return Math.abs(price - average) / (double) average;
	}
}
