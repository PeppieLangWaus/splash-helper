package xyz.peppie.splashhelper.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OverallStatField
{
	TOTAL_CASTS("Total Casts"),
	TOTAL_XP("Total XP"),
	TOTAL_COST("Total Cost"),
	REMAINING_CASTS("Remaining"),
	HOURS_REMAINING("Hours Remaining"),
	POTENTIAL_XP("Potential XP"),
	GP_PER_HOUR("GP/Hour"),
	CURRENT_PLAYERS("Current Players"),
	HIGHEST_PLAYERS("Highest Players"),
	TOTAL_DEATHS("Total Deaths");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
