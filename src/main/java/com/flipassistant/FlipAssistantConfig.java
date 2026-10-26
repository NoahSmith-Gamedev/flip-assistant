package com.flipassistant;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(FlipAssistantConfig.GROUP)
public interface FlipAssistantConfig extends Config
{
	String GROUP = "flipassistant";

	@ConfigItem(
		keyName = "cashStack",
		name = "Available gold",
		description = "How much gold you have for flipping. Suggested quantities are sized to fit this.",
		position = 1
	)
	default int cashStack()
	{
		return 10_000_000;
	}

	@ConfigItem(
		keyName = "maxPerItem",
		name = "Max gold per item",
		description = "Never suggest putting more than this into a single item. Spreading gold across items lowers risk.",
		position = 2
	)
	default int maxPerItem()
	{
		return 5_000_000;
	}

	@Range(min = 0)
	@ConfigItem(
		keyName = "minHourlyVolume",
		name = "Min trades per hour",
		description = "Skip items that traded fewer times than this in the last hour. Higher means offers fill faster.",
		position = 3
	)
	default int minHourlyVolume()
	{
		return 200;
	}

	@Range(min = 1, max = 120)
	@ConfigItem(
		keyName = "maxPriceAgeMinutes",
		name = "Max price age (minutes)",
		description = "Skip items whose latest buy or sell is older than this. Old prices are unreliable.",
		position = 4
	)
	default int maxPriceAgeMinutes()
	{
		return 10;
	}

	@Range(min = 1, max = 100)
	@ConfigItem(
		keyName = "maxRoiPercent",
		name = "Max margin %",
		description = "Skip flips whose margin is above this % of the buy price. Huge margins are often manipulated or barely-traded items.",
		position = 5
	)
	default int maxRoiPercent()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "includeMembers",
		name = "Include members items",
		description = "Turn off if you're playing free-to-play.",
		position = 6
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
		position = 7
	)
	default int maxSuggestions()
	{
		return 15;
	}

	@Range(min = 0, max = 10)
	@ConfigItem(
		keyName = "taxPercent",
		name = "GE tax %",
		description = "Tax Jagex takes on each sale (capped at 5M per item). Only change this if Jagex changes the rate.",
		position = 8
	)
	default int taxPercent()
	{
		return 2;
	}
}
