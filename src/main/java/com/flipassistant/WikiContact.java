package com.flipassistant;

/**
 * How this plugin identifies itself to the OSRS Wiki price API.
 *
 * The wiki asks every tool to send a descriptive User-Agent, ideally with contact info,
 * so they can reach you if something goes wrong. Put your Discord username in place of
 * YOUR_DISCORD_NAME, or remove the " - contact: ..." part entirely.
 *
 * Future Flip Assistant updates will not overwrite this file.
 */
final class WikiContact
{
	static final String USER_AGENT = "Flip Assistant RuneLite plugin - contact: YOUR_DISCORD_NAME on Discord";

	private WikiContact()
	{
	}
}
