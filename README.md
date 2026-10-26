# School Status Manager v1.6.0

School Status Manager determines the current school-day status from a configurable ICS academic-calendar feed. It supports planned no-school days, weekends, summer break, a last-successful calendar cache, and manual operational overrides.

The app does **not** communicate with Alexa directly. Hubitat Rule Machine translates the app's `schoolStatus` attribute into pulses on Alexa-visible virtual contact sensors. Alexa routines then use those contact events to create the appropriate alarms.

## Package contents

- `School_Status_Manager_App_v1.6.0.groovy` — parent Hubitat app
- `School_Status_Device_Driver_v1.6.0.groovy` — child-device driver
- `README_School_Status_Manager_v1.6.0.md` — this guide

## Hubitat installation

1. In **Drivers Code**, add or replace the driver with `School_Status_Device_Driver_v1.6.0.groovy`, then save it.
2. In **Apps Code**, add or replace the app with `School_Status_Manager_App_v1.6.0.groovy`, then save it.
3. Open the installed **School Status Manager** app and press **Done** so Hubitat updates its schedules and settings.
4. Select **Refresh calendar now**.
5. Open **Devices > School Today** and verify the current attributes.

When upgrading an existing installation, update the driver first and the app second.

## Primary status values

Rule Machine should use the `schoolStatus` attribute on the **School Today** device.

- `NORMAL` — school is operating on the normal schedule
- `DELAYED` — school is operating with a delay
- `NO_SCHOOL` — no school today
- `UNKNOWN` — the app does not have enough trustworthy information to determine the status

The device also publishes `delayHours`:

- the configured delay-hour value when `schoolStatus` is `DELAYED`
- `0` otherwise

## Device-page controls

The **School Today** device page provides these commands:

- **Set Normal School**
- **Set No School**
- **Set Delayed**
- **Set Automatic**
- **Refresh**

Manual overrides can either persist until cleared or reset automatically at the configured time, depending on the app's **Automatically clear manual override** setting.

The generic Switch capability and ON/OFF commands are intentionally not used. A simple ON state cannot distinguish a normal school day from a delayed school day.

## Device diagnostics

The **School Today** device exposes:

- `appVersion`
- `schoolStatus`
- `delayHours`
- `reason`
- `source`
- `feedHealth`
- `override`
- `autoResetOverride`
- `calendarCacheAgeDays`
- `lastChecked`
- `lastSuccessfulUpdate`

## Calendar and cache behavior

- The feed URL and event-matching phrases are configurable.
- Events are evaluated by their `DTSTART` calendar date. Event duration is not used to determine whether the entire school day is affected.
- Weekends are treated as `NO_SCHOOL`.
- A matching no-school event overrides a normal weekday.
- The first day and last day of school are treated as school days.
- Summer starts the day after the last-day marker and ends on the next first-day marker.
- If the first-day marker is missing and there is no positive evidence of summer, the fail-safe assumption is that school is in session.
- If the last-day marker is missing but a future first-day marker exists and today is before it, the app assumes summer break.
- Every successful feed download and parse completely replaces the parsed calendar cache. If an event disappeared from the successful new feed, it is removed from the cache.
- A failed download or parse does not replace the cache. The app uses the last successful parsed calendar when available.

## Alexa dependency and architecture

This automation depends on:

1. Hubitat's Alexa integration exposing compatible virtual contact sensors to Alexa.
2. Alexa routines that trigger when those virtual contact sensors open.
3. Separate Alexa routines for normal-time alarms and delayed alarms because the alarm times are hardcoded inside the Alexa routines.
4. Hubitat Rule Machine deciding which virtual contact sensor to pulse based on `School Today: schoolStatus`.

The current setup uses custom Alexa Plus routines to dynamically create or set the desired Alexa alarms before their scheduled alarm times. The Hubitat contact sensor does not itself contain the alarm time. It selects which Alexa routine runs, and that routine contains the appropriate alarm configuration.

No contact pulse is sent for `NO_SCHOOL` or `UNKNOWN`, so no school alarm is created in those cases.

## Recommended virtual devices and Alexa routines

For each alarm or person that needs separate handling, create a pair of Alexa-compatible virtual contact sensors:

- Normal-alarm contact
- Two-hour-delay-alarm contact

Create corresponding Alexa routines:

- When the normal contact opens, run the custom Alexa Plus routine that creates the normal-time alarm.
- When the delay contact opens, run the custom Alexa Plus routine that creates the delayed alarm.

If several Alexa alarms must be created, Rule Machine can pulse multiple normal contacts or multiple delay contacts in the appropriate branch.

## Rule Machine setup

Schedule the Rule Machine rule to run after School Status Manager's calendar refresh. For example, if the morning recheck runs at 5:05 AM, schedule the Rule Machine rule several minutes later.

### Trigger event

Use a scheduled time that occurs after the School Status Manager refresh and before the Alexa alarms need to be created.

### Required expression or device attribute

Select the custom attribute `schoolStatus` from the **School Today** device.

### Actions to run

```text
IF School Today schoolStatus = NORMAL THEN
    Open Normal Alarm Contact(s)
    Wait 5 seconds
    Close Normal Alarm Contact(s)

ELSE-IF School Today schoolStatus = DELAYED THEN
    Open Delayed Alarm Contact(s)
    Wait 5 seconds
    Close Delayed Alarm Contact(s)

ELSE-IF School Today schoolStatus = NO_SCHOOL THEN
    Do nothing

ELSE-IF School Today schoolStatus = UNKNOWN THEN
    Do nothing
END-IF
```

The five-second open/close sequence creates a contact transition that Alexa can use as a routine trigger and resets the virtual contact so it is ready for the next school day.

## End-to-end flow

```text
Academic-calendar ICS feed
        |
        v
School Status Manager
(calendar, cache, summer rules, manual override)
        |
        v
School Today.schoolStatus
        |
        v
Hubitat Rule Machine
        |
        +-- NORMAL ----------> Pulse normal virtual contact(s)
        |
        +-- DELAYED --> Pulse delay virtual contact(s)
        |
        +-- NO_SCHOOL -------> No pulse
        |
        +-- UNKNOWN ---------> No pulse
        |
        v
Hubitat Alexa integration
        |
        v
Alexa contact-triggered routine
        |
        v
Custom Alexa Plus routine creates the configured alarm
```

## Testing checklist

1. Set **Normal School** on the device page and run the Rule Machine rule manually. Verify that only the normal Alexa contact opens for five seconds and that the normal Alexa routine runs.
2. Set **Delayed** and repeat. Verify that only the delay contact opens and that the delayed Alexa routine runs.
3. Set **No School** and repeat. Verify that no contact opens and no alarm is created.
4. Select **Automatic**, refresh the calendar, and verify that the device returns to the calendar-derived status.
5. If auto-reset is enabled, test that a manual override clears at the configured reset time.

## Reliability and diagnostic enhancements in v1.6.0

- `nextRelevantEvent` shows the next recognized no-school, first-day, or last-day event in the successful cache.
- `source` uses standardized values: `LIVE_CALENDAR`, `CACHED_CALENDAR`, `MANUAL_OVERRIDE`, or `FAIL_SAFE`.
- `calendarChanged` indicates whether the most recent successful parsed calendar differs from the previous successful parsed calendar.
- `overrideSetAt` records when the active manual override was selected.
- `consecutiveFailures` counts consecutive live-feed failures and resets to zero after a successful live pull.
- Optional failure notifications can be sent through selected Hubitat notification devices after a configurable number of consecutive failures. Only one notification is sent per failure sequence; a successful pull resets notification eligibility.
- Test-date evaluation uses the last successful cached calendar and does not alter the real `schoolStatus`, manual override, Alexa contacts, or Rule Machine behavior. Select a date in the app, choose **Evaluate test date**, and read `testResult` on the device or app page.

The cache does not expire automatically. Its age is exposed for visibility, while the last successful parsed calendar remains available during feed outages.

## Additional functionality in v1.6.0

### Generic delayed status

The status model is now `NORMAL`, `DELAYED`, `NO_SCHOOL`, and `UNKNOWN`. `DELAYED` uses the `delayHours` attribute. The default is 2 hours and can be changed in the app. This keeps the school-status model generic while retaining a two-hour default for the current PWCS workflow.

For the current Alexa setup, Rule Machine should check both `schoolStatus = DELAYED` and `delayHours = 2` before pulsing the Alexa contact associated with the two-hour-delay routine.

### Tomorrow and next-school-day diagnostics

The device publishes `tomorrowStatus`, `tomorrowReason`, and `nextSchoolDay`. These are informational and do not trigger Alexa by themselves.

### Calendar-cache diagnostics

The device additionally publishes `cachedEventCount`, `cacheFirstDate`, `cacheLastDate`, `firstDayMarkerFound`, and `lastDayMarkerFound` so the rolling calendar feed can be sanity-checked from the device page.

## Hubitat Package Manager support

Version 1.6.0 is packaged for Hubitat Package Manager (HPM). The package manifest is `manifests/packageManifest.json`, and the author's repository manifest is `manifests/repository.json`.

The source uses `Pierowman` as the displayed author and `pierowman` as the Hubitat namespace. Because versions through 1.5.0 used the old namespace, upgrading an existing manually installed copy to the HPM package should be treated as a namespace migration. Remove the old installed app and old user code after recording your settings, then install the Pierowman package and configure it again. The calendar cache will repopulate on the first successful refresh.

The manifest URLs assume this GitHub repository path:

`https://github.com/Pierowman/Hubitat-School-Status-Manager`

If the final GitHub owner or repository name differs, update all URLs in both JSON manifests before installing through HPM.
