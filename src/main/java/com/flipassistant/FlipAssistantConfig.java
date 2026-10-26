package com.flipassistant;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(FlipAssistantConfig.GROUP)
public interface FlipAssistantConfig extends Config
{
	String GROUP = "flipassistant";

	@ConfigSection(name = "Strategy", description = "How you plan to flip", position = 0)
	String strategySection = "strategy";

	@ConfigSection(name = "Budget", description = "How much gold to use", position = 1)
	String budgetSection = "budget";

	@ConfigSection(name = "Filters", description = "Which items are allowed", position = 2)
	String filtersSection = "filters";

	@ConfigSection(name = "Advanced", description = "Assumptions behind the estimates", position = 3, closedByDefault = true)
	String advancedSection = "advanced";

	// ---- Strategy ----

	@ConfigItem(
		keyName = "overnightMode",
		name = "Overnight mode",
		description = "I'm setting buy offers before bed and selling in the morning. Uses patient prices, "
			+ "only stable items that aren't falling, and ranks by total profit.",
		position = 1,
		section = strategySection
	)
	default boolean overnightMode()
	{
		return false;
	}

	@Range(min = 2, max = 16)
	@ConfigItem(
		keyName = "overnightHours",
		name = "Hours away (overnight)",
		description = "How long you'll be away in overnight mode. Offers are sized to fill in this time.",
		position = 2,
		section = strategySection
	)
	default int overnightHours()
	{
		return 8;
	}

	@Range(min = 5, max = 240)
	@ConfigItem(
		keyName = "maxFillMinutes",
		name = "Target fill time (minutes)",
		description = "Normal mode: size each offer so the buy and the sell should each finish within this time.",
		position = 3,
		section = strategySection
	)
	default int maxFillMinutes()
	{
		return 30;
	}

	@Range(min = 0, max = 50)
	@ConfigItem(
		keyName = "priceBoostPercent",
		name = "Fill faster: give up % of spread",
		description = "Normal mode: bid a bit higher and sell a bit lower than the market so offers fill sooner. "
			+ "10 means give up 10% of the gap between buy and sell price on each side. Lowers profit per item.",
		position = 4,
		section = strategySection
	)
	default int priceBoostPercent()
	{
		return 0;
	}

	// ---- Budget ----

	@ConfigItem(
		keyName = "cashStack",
		name = "Available gold",
		description = "How much gold you have for flipping. Suggested quantities are sized to fit this.",
		position = 1,
		section = budgetSection
	)
	default int cashStack()
	{
		return 10_000_000;
	}

	@ConfigItem(
		keyName = "maxPerItem",
		name = "Max gold per item",
		description = "Never suggest putting more than this into a single item. Spreading gold across items lowers risk.",
		position = 2,
		section = budgetSection
	)
	default int maxPerItem()
	{
		return 5_000_000;
	}

	// ---- Filters ----

	@ConfigItem(
		keyName = "stabilityFilter",
		name = "Stability",
		description = "Hide items whose price has been jumping around. Items with no history yet show as '?'.",
		position = 1,
		section = filtersSection
	)
	default StabilityFilter stabilityFilter()
	{
		return StabilityFilter.MODERATE_OR_BETTER;
	}

	@ConfigItem(
		keyName = "skipFalling",
		name = "Skip falling items",
		description = "Hide items whose price dropped more than 3% over the last day.",
		position = 2,
		section = filtersSection
	)
	default boolean skipFalling()
	{
		return true;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "minHourlyVolume",
		name = "Min trades per hour (each side)",
		description = "Skip items with fewer instant buys OR instant sells per hour than this.",
		position = 3,
		section = filtersSection
	)
	default int minHourlyVolume()
	{
		return 100;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "maxRoiPercent",
		name = "Max margin %",
		description = "Skip flips whose margin is above this % of the buy price. Huge margins are often manipulated or barely-traded items.",
		position = 4,
		section = filtersSection
	)
	default int maxRoiPercent()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "includeMembers",
		name = "Include members items",
		description = "Turn off if you're playing free-to-play.",
		position = 5,
		section = filtersSection
	)
	default boolean includeMembers()
	{
		return true;
	}

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "maxSuggestions",
		name = "Number of suggestions",
		description = "How many flips to show in the panel.",
		position = 6,
		section = filtersSection
	)
	default int maxSuggestions()
	{
		return 15;
	}

	// ---- Advanced ----

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "marketSharePercent",
		name = "Your share of trades %",
		description = "Other flippers compete for the same trades, so you won't win them all. The plugin assumes your "
			+ "offer gets this % of the market's trades when estimating fill times and quantities.",
		position = 1,
		section = advancedSection
	)
	default int marketSharePercent()
	{
		return 20;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "maxPriceAgeMinutes",
		name = "Max price age (minutes)",
		description = "Skip items whose latest buy or sell is older than this. Old prices are unreliable.",
		position = 2,
		section = advancedSection
	)
	default int maxPriceAgeMinutes()
	{
		return 10;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "taxPercent",
		name = "GE tax %",
		description = "Tax Jagex takes on each sale (capped at 5M per item). Only change this if Jagex changes the rate.",
		position = 3,
		section = advancedSection
	)
	default int taxPercent()
	{
		return 2;
	}
}
