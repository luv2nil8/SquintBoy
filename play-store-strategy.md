# Squint Boy Advance — Monetization Reference

## Model
Freemium + single IAP ("Squint Boy Pro") via Google Play Billing on the phone companion app.
Purchase is synced to the watch via Wearable MessageClient (push + request/response).

## Free Tier (Demo)

| Feature | Limit |
|---------|-------|
| Platforms | GB, GBC only (GBA transfers rejected) |
| ROM slots | 3 max |
| Session length | 15 minutes (soft-lock, never erases progress) |
| Color palettes | 3 of 24 |
| Save states | Locked |
| Fast forward | Locked |
| Display scaling | Locked |
| Bilinear filter | Locked |
| Frameskip | Locked |
| Save backup/export (phone) | Locked |

Session timer only ticks while the emulator is in RUNNING state.
On expiry the game pauses with an upgrade prompt overlay — the user can exit and re-enter for another 15 minutes.

## Pro Tier (Squint Boy Pro)

All demo restrictions removed:
- GBA support
- Unlimited ROMs and session time
- All 24 color palettes
- Save states
- Fast forward (variable speed, long-press to select)
- Display scaling + bilinear filter + frameskip
- Save backup and export from companion app

## Implementation Details

### Constants
- `DemoLimits.MAX_ROMS = 3`
- `DemoLimits.SESSION_TIME_MS = 15 * 60 * 1000L`
- `DemoLimits.PALETTE_COUNT = 3`

### Entitlement Flow
1. Phone: `MobileBillingManager` checks Google Play purchases, exposes `isPro` StateFlow
2. Phone pushes entitlement to watch via `PATH_ENTITLEMENT_PUSH`
3. Watch: `EntitlementRepository` caches `isPro` in SharedPreferences
4. On watch boot, `EntitlementRepository` requests entitlement from phone (3 retries with backoff)

### Gating Points (Wear)
- `RomReceiverService`: rejects GBA transfers + enforces ROM cap in demo
- `EmulatorViewModel`: runs session timer, soft-locks on expiry
- `PauseOverlay`: save/FF/scale/palette controls show locked state with upgrade nudge
- `RomLibraryScreen`: "Unlock Everything" chip, "Upgrade for more" when at ROM cap

### Gating Points (Mobile)
- `RomsTab`: shows ROM count remaining, locks "Add ROM" at cap
- `RomManagementScreen`: scaling, filter, frameskip, backup, palette controls locked
