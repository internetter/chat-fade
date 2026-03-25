package com.chatfade;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.SpriteOverride;

@RequiredArgsConstructor
public enum FixedHideChatSprites implements SpriteOverride
{
	FIXED_HIDE_CHAT_LEFT_BORDER(-206, "/-206.png"),
	FIXED_HIDE_CHAT_RIGHT_BORDER(-207, "/-207.png");

	@Getter
	private final int spriteId;

	@Getter
	private final String fileName;
}
