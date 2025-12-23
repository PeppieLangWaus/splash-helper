package xyz.peppie.splashhelper.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum TargetNpc
{
	KNIGHT_OF_ARDOUGNE("Knight of Ardougne"),
	RAT("Rat"),
	GUARD("Guard");

	private final String npcName;

	@Override
	public String toString()
	{
		return npcName;
	}
}
