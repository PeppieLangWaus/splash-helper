package xyz.peppie.splashhelper.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SessionStatField
{
	SPELL("Spell"),
	CASTS("Casts"),
	XP_GAINED("XP Gained"),
	XP_PER_HOUR("XP/Hour"),
	RUNE_COST("Rune Cost"),
	NEARBY_PLAYERS("Nearby Players"),
	HIGHEST_PLAYERS("Highest Players"),
	PLAYER_DEATHS("Deaths");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
