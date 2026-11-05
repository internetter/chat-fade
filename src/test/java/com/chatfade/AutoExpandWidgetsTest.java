package com.chatfade;

import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertTrue;

/**
 * Which interfaces count as "something is using the chat area".
 *
 * <p>Two features read this one list: Fixed Mode puts the chatbox back when one of these is up,
 * and the overlay gets out of its way (#38). An interface missing from it is therefore invisible
 * twice over — the chatbox stays shut and the quest step that is waiting on a click looks like a
 * quest doing nothing.
 *
 * <p>The IDs are asserted as plain numbers on purpose. The list previously mixed
 * {@code widgets.InterfaceID} and {@code gameval.InterfaceID} names for identical values —
 * DIALOG_NPC and CHAT_LEFT are both 231 — which is exactly how the plain message box came to be
 * missing without anyone noticing. Numbers cannot hide behind a synonym.
 */
public class AutoExpandWidgetsTest
{
	private static boolean covers(int group)
	{
		for (Map.Entry<Integer, Integer> entry : ChatFadePlugin.AUTO_EXPAND_WIDGETS)
		{
			if (entry.getKey() == group)
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void coversThePlainMessageBox()
	{
		// 229, the "Click here to continue" box. Reported by @internetter mid-quest: Mudfoot
		// hands over the items, the game waits for a click, and nothing appears to happen.
		assertTrue("MESSAGEBOX (229) must bring the chatbox back", covers(229));
	}

	@Test
	public void coversBothItemBoxes()
	{
		assertTrue("OBJECTBOX (193), one item and text", covers(193));
		assertTrue("OBJECTBOX_DOUBLE (11), two items and text", covers(11));
	}

	@Test
	public void coversTheOtherMessageBoxVariants()
	{
		assertTrue("MESSAGEBOX_TITLED (923)", covers(923));
		assertTrue("MESSAGEBOX_URL (917)", covers(917));
	}

	@Test
	public void coversSomebodyTalking()
	{
		assertTrue("CHAT_LEFT (231), an NPC speaking", covers(231));
		assertTrue("CHAT_RIGHT (217), you speaking", covers(217));
		assertTrue("CHATMENU (219), choose an option", covers(219));
	}

	@Test
	public void coversThePromptsThatTakeInput()
	{
		assertTrue("SKILLMULTI (270), the 'how many?' prompt", covers(270));
		assertTrue("CHATBOX (162), which holds the search prompts", covers(162));
	}

	@Test
	public void everyEntryNamesARealComponent()
	{
		for (Map.Entry<Integer, Integer> entry : ChatFadePlugin.AUTO_EXPAND_WIDGETS)
		{
			assertTrue("interface id should be positive: " + entry.getKey(), entry.getKey() > 0);
			assertTrue("component index should not be negative: " + entry.getValue(),
				entry.getValue() >= 0);
		}
	}
}
