package com.flipassistant;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Launches a RuneLite development client with Flip Assistant loaded.
 * Run this from IntelliJ with "-ea" in the VM options.
 */
public class FlipAssistantPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FlipAssistantPlugin.class);
		RuneLite.main(args);
	}
}
