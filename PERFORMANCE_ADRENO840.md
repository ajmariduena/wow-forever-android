# WoW Forever / Adreno 840 experiments (10 November 2026)

## Evidence and verdict

- [GameNative #2132](https://github.com/utkarshdalal/GameNative/pull/2132) introduced Gen8 V37 explicitly for a840/a830/a829/a825/a810 and native ARM64 DXVK 2.4.1 wowfix. [#2157](https://github.com/utkarshdalal/GameNative/pull/2157) automatically selects that DXVK for the native WoW executable, only on bionic. It does not select a GPU driver or TU_DEBUG flags.
- [GameNative #2343](https://github.com/utkarshdalal/GameNative/pull/2343), merged 4 November, adds Gen8 V38. The current upstream manifest still lists V38 as its newest Gen8 build. The separate v27 R1 entry is for a6xx/a7xx, not this phone.
- [StevenMXZ V39](https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/tag/v39), published 7 November, is newer. The release offers normal and extra-patched Gen8 builds, recommending normal if the patched build has problems. We bundle normal Gen8 V39, Vulkan 1.4.367, pinned to GitHub's SHA256 `572e415c11b636ea2a4c4968c60a9b24e459302549c8ea38e814dbd07e40654d`. It is the newest candidate, not a verified WoW performance winner.
- [The existing a840 WoW port](https://github.com/arusiasotto/wow-forever-a840) documents reaching Ironforge on Adreno 840 with **V32 plus RP6 scheduler patch and sysmem**. Other tested drivers produced shader pipeline assertions or striped output. Its notes specifically recommend sysmem because GMEM was glitchy. This is strong evidence to keep the current default; it is not proof that newer V39 still requires sysmem, nor that it contains the same shader fix.
- No primary evidence found that either V39 mode is stable or faster with this exact game/phone. Both are marked experimental. If shaders fail or the image corrupts, return to V32 + sysmem. No promises of an FPS increase before measuring.

## DXVK, pacing and affinity

Keep the bundled native aarch64 DXVK `2.4.1-wow-aarch64-test`; a newer general DXVK package may contain x64/ARM64EC DLLs that the pure ARM64 client cannot load. No async flag or latency tweak is enabled: `DXVKHelper` only enables async if explicitly configured; this launcher config contains only the version. Async requires a matching patched DXVK and is not a generic throughput switch.

[DXVK configuration](https://github.com/doitsujin/dxvk/blob/v2.4.1/dxvk.conf) documents sync interval and frame-latency overrides. VSync can interact with presentation pacing, but no evidence establishes a fixed 30-FPS halving on this device. The app already defaults its limiter to 60 and removes DXVK_FRAME_RATE when its own limiter is active. Preserve existing VSync values; new Config.wtf files default to 0. Test an existing install with in-game VSync off separately after comparing drivers. Do not attribute that change to the driver.

Leave mailbox, Samsung UBWC hint, WINEESYNC=0 and other environment values as before for a controlled driver experiment. Zink settings affect the OpenGL translation path, not the selected D3D11/DXVK path; PULSE_LATENCY_MSEC affects sound. No evidence supports changing these to fix GPU throughput here.

Read-only adb `/sys/devices/system/cpu/cpufreq/policy*` on the SM-F971B showed cores 0–5 at max 3628800 kHz and 6–7 at 4742400 kHz. All eight are performance/prime cores. The current WinHandler affinity path cannot help native WoW when its helper is not started. Upstream's TaskAffinity adds a native library and process/thread management; that is outside a low-risk launcher change. Do not constrain the game to fewer cores without measurements.

## Launcher changes and reproduction

The Adreno 8xx-only “Driver gráfico” menu persists four choices and applies both the driver ID and TU_DEBUG at next launch. Default/unknown preference is V32 + sysmem. V39 is bundled through the same `tools/fetch_components.sh`, checksum list and asset extraction path as existing drivers. Run the fetch script before a clean build; no rebuild is needed to switch choices on the phone. Fail extraction visibly rather than launching a silently missing driver. System Qualcomm is omitted to keep this experiment to the proven Wrapper/Turnip path.

900p means a 1600×900 pixel budget reshaped to the current display aspect. Start with 720p. Every launch caps farclip, horizonClip and their RAID equivalents at 1200 if missing or higher; lower user values are preserved. This fixes imported farclip=3000 without raising lower distances or resetting other quality settings. FPS in the quick-menu header enables the existing FPS-only HUD preset, regardless of the selected menu tab.

## Phone protocol (manual, no installation performed)

1. Choose 720p, keep the same fold state, 60-FPS app limiter, location and camera. Let initial shader compilation settle. Do not clear shader caches between runs.
2. Try V32 + sysmem (baseline), V32 without sysmem, V39 + sysmem, then V39 without sysmem. Fully exit and relaunch between choices. If a variant fails, record the failure and restore baseline.
3. Open the in-game menu and tap FPS in its header. Record FPS range for 60 seconds standing still and 60 seconds along the same walking route. Repeat baseline last to detect warming/thermal drift.
4. Report each variant's standing/walking FPS ranges, stutters, stripes/black textures/crashes, and approximate phone temperature. Wait for comparable temperature between runs. A screenshot with the HUD helps identify the actual render budget.
5. On the best stable driver, optionally compare 900p and in-game VSync off independently. The launcher preserves existing VSync=1 because the halving hypothesis remains unverified.
