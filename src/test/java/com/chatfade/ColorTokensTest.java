package com.chatfade;

import java.awt.Color;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the {@code @name@} colour token handling added for issue #21, where tokens
 * introduced by the Mad Angel / Combat Achievement update rendered as literal text.
 */
public class ColorTokensTest
{
	// ── Stripping real tokens ───────────────────────────────

	@Test
	public void stripsNewNamedTokens()
	{
		assertEquals("You have been frozen!", ColorTokens.strip("@mes_hl_red@You have been frozen!"));
		assertEquals("Congratulations!", ColorTokens.strip("@mes_hl_pur@Congratulations!"));
	}

	@Test
	public void stripsClassicThreeLetterCodes()
	{
		assertEquals("Hello", ColorTokens.strip("@red@Hello"));
		assertEquals("ab", ColorTokens.strip("@gre@a@blu@b"));
	}

	@Test
	public void stripsUnknownButStructuredTokens()
	{
		// A token we have no colour for must still be removed rather than shown raw,
		// so a future Jagex palette entry degrades gracefully.
		assertEquals("text", ColorTokens.strip("@some_future_name@text"));
	}

	// ── Leaving player text alone ───────────────────────────

	@Test
	public void leavesPlayerTypedAtTextAlone()
	{
		assertEquals("@bob@ hello", ColorTokens.strip("@bob@ hello"));
		assertEquals("email me @ work", ColorTokens.strip("email me @ work"));
		assertEquals("@@", ColorTokens.strip("@@"));
	}

	@Test
	public void leavesMixedCaseNamesAlone()
	{
		// Real tokens are always lowercase; this keeps names like @Bob_Smith@ intact.
		assertEquals("@Bob_Smith@", ColorTokens.strip("@Bob_Smith@"));
	}

	@Test
	public void handlesNullAndTokenFreeText()
	{
		assertNull(ColorTokens.strip(null));
		assertEquals("plain message", ColorTokens.strip("plain message"));
	}

	// ── Detection ───────────────────────────────────────────

	@Test
	public void detectsTokensOnly()
	{
		assertTrue(ColorTokens.containsToken("@mes_hl_red@x"));
		assertTrue(ColorTokens.containsToken("@red@x"));
		assertFalse(ColorTokens.containsToken("@bob@"));
		assertFalse(ColorTokens.containsToken("plain message"));
		assertFalse(ColorTokens.containsToken(null));
	}

	@Test
	public void resolvesKnownColours()
	{
		assertEquals(new Color(0xFF0000), ColorTokens.colorFor("red"));
		assertEquals(new Color(0xFFFFFF), ColorTokens.colorFor("whi"));
		assertNull(ColorTokens.colorFor("some_future_name"));
		assertNull(ColorTokens.colorFor("bob"));
	}
}
