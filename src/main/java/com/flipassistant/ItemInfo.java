package com.flipassistant;

/**
 * Basic facts about one tradeable item, from the wiki's /mapping endpoint.
 */
public final class ItemInfo
{
	final int id;
	final String name;
	/** GE buy limit per 4 hours, or null if the wiki doesn't know it. */
	final Integer limit;
	final boolean members;

	public ItemInfo(int id, String name, Integer limit, boolean members)
	{
		this.id = id;
		this.name = name;
		this.limit = limit;
		this.members = members;
	}
}
