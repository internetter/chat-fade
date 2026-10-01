package com.chatfade;

/** What the built-in message filter does with a message it matches. */
public enum MessageFilterType
{
	CENSOR_WORDS("Censor words"),
	CENSOR_MESSAGE("Censor message"),
	REMOVE_MESSAGE("Remove message");

	private final String name;

	MessageFilterType(String name)
	{
		this.name = name;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
