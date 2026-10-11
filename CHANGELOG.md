# Changelog

All notable changes to this project are documented in this file.

## [Unreleased]

## [2.1.0] - 2026-10-11
### Added
- Stack Tile tracking: logs ENTER/LEAVE/DESPAWN events, flag-clear windows, and knight excursions to CSV files for analyzing how the Knight of Ardougne escapes the pickpocket stack
- Live flag-clear and knight-move rate display in the timer overlay (opt-in)
- Optional tracking of a named player (e.g. an alt) as a "blocking re-setter" test subject, surfaced in the flag-clear window data

### Changed
- Renamed the "Knight Tile 1"/"Knight Tile 2" markers to "Knight Tile" and "Stack Tile" to match their actual roles; existing saved tiles and colors are migrated automatically
