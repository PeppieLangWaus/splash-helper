package xyz.peppie.splashhelper;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SplashHelperPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SplashHelperPlugin.class);
		RuneLite.main(args);
	}
}
