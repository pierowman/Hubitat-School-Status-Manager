# School Status Manager

School Status Manager is a Hubitat app that determines whether school is operating normally, delayed, or closed by combining an academic-calendar ICS feed with school-year boundary detection, a last-known-good calendar cache, and manual overrides.

The resulting **School Today** device provides a single school-status source that can be consumed by Rule Machine or other Hubitat automations.

## Features

- Configurable ICS academic-calendar feed
- Automatic recognition of scheduled no-school days
- Weekend handling
- First-day and last-day school-year boundary detection
- Automatic summer-break detection
- Last-successful calendar caching for feed outages
- Delayed-opening support with configurable delay hours
- Manual Normal, Delayed, and No School overrides
- Optional automatic reset of manual overrides
- Morning calendar recheck
- Feed-failure retry handling
- Optional failure notifications
- Tomorrow's school status
- Next school day
- Next relevant calendar event
- Calendar-feed diagnostics
- Test-date evaluation
- Hubitat Package Manager support
- Rule Machine and Alexa integration

---

# Installation

## Recommended: Hubitat Package Manager

Install **Hubitat Package Manager (HPM)** on your Hubitat hub if it is not already installed.

In HPM:

1. Select **Install**.
2. Select **From a URL**.
3. Enter:

`https://raw.githubusercontent.com/pierowman/Hubitat-School-Status-Manager/master/manifests/packageManifest.json`

4. Complete the HPM installation.
5. Go to **Apps > Add User App**.
6. Select **School Status Manager**.
7. Configure the calendar feed and desired options.
8. Select **Done**.

The app automatically performs an initial calendar refresh after installation and creates the **School Today** child device.

## Updating

Use **Hubitat Package Manager > Update** when a new package version is available.

Normal code updates begin running the next time the app executes. The app version displayed on the **School Today** device will update the next time status is published.

If a release specifically changes scheduling behavior, open **School Status Manager** and select **Done** after updating so Hubitat recreates the app's scheduled jobs.

## Manual Installation

HPM is recommended, but manual installation is also supported.

1. Add `drivers/SchoolStatusDevice.groovy` under **Drivers Code**.
2. Add `apps/SchoolStatusManager.groovy` under **Apps Code**.
3. Go to **Apps > Add User App**.
4. Select **School Status Manager**.
5. Configure the app and select **Done**.

---

# Configuration

## Calendar Feed

Enter the URL of the school's ICS academic-calendar feed.

The default configuration is designed around the Prince William County Public Schools academic calendar, but the feed URL and event-matching phrases are configurable.

Configure phrases for:

- No School
- First Day of School
- Last Day of School

Matching is case-insensitive.

## Calendar Refresh Schedule

Configure:

- Daily evaluation time
- Optional morning recheck
- Retry count
- Retry delay
- HTTP timeout

The morning recheck provides a second opportunity to retrieve an updated calendar before morning automations run.

## Delayed Opening

Configure the default number of delay hours.

The default is:

`2 hours`

When a manual delayed opening is selected:

`schoolStatus = DELAYED`

and:

`delayHours = configured delay`

---

# School Status

The **School Today** device exposes four primary status values.

## NORMAL

School operates on its normal schedule.

## DELAYED

School operates on a delayed schedule.

The `delayHours` attribute specifies the delay duration.

## NO_SCHOOL

Students do not attend school.

## UNKNOWN

The app does not have enough trustworthy information to determine school status.

Downstream alarm automation should normally treat `UNKNOWN` as a fail-safe condition and avoid creating an alarm.

---

# Calendar Decision Logic

The app evaluates the school calendar by **calendar date**, not by the duration or time of an ICS event.

This is intentional because some school-calendar feeds represent an event with a limited time even when the event applies to the entire school day.

The decision hierarchy is:

1. Manual override
2. Weekend
3. Explicit No School calendar event
4. First Day of School
5. Last Day of School
6. Summer-break determination
7. Normal weekday
8. Fail-safe handling when insufficient information is available

The first and last days themselves are considered school days.

Summer begins on the day following the last day of school and ends on the next first day of school.

If a first-day marker is missing and there is no positive evidence of summer, the fail-safe assumption is that school is in session.

If the previous last-day marker is missing but a future first-day marker is available and the current date is before it, the app treats the date as summer break.

---

# Calendar Cache

Every successful calendar download is parsed and stored as the current last-known-good calendar.

A successful download **completely replaces the previous cache**.

This means that if the school removes or changes an event, the next successful calendar refresh becomes authoritative.

If a download or parse fails:

- The existing cache is retained.
- The cached calendar is used for the school-status decision when possible.
- Feed health indicates that live data is stale or unavailable.
- Configured retries are attempted.

The cache does not expire automatically simply because it becomes old.

---

# Manual Overrides

The **School Today** device provides:

- **Set Normal School**
- **Set Delayed**
- **Set No School**
- **Set Automatic**
- **Refresh**

Selecting Normal, Delayed, or No School switches the status source to a manual override.

Example:

`schoolStatus = DELAYED`  
`override = DELAYED`  
`source = MANUAL_OVERRIDE`

Selecting **Set Automatic** clears the manual override and returns control to the automatic calendar logic.

## Automatic Override Reset

Manual overrides can optionally reset automatically at a configured time.

When automatic reset is disabled, the selected override remains active until **Set Automatic** is selected.

---

# Device Status and Diagnostics

The **School Today** device is the primary place to view the app's current state.

Useful attributes include:

- `appVersion`
- `schoolStatus`
- `delayHours`
- `reason`
- `source`
- `feedHealth`
- `lastChecked`
- `lastSuccessfulUpdate`
- `override`
- `overrideSetAt`
- `autoResetOverride`
- `calendarCacheAgeDays`
- `calendarChanged`
- `consecutiveFailures`
- `tomorrowStatus`
- `tomorrowReason`
- `nextSchoolDay`
- `nextRelevantEvent`
- `cachedEventCount`
- `cacheFirstDate`
- `cacheLastDate`
- `firstDayMarkerFound`
- `lastDayMarkerFound`
- `testResult`

## Status Source

The `source` attribute identifies how the current status was determined:

- `LIVE_CALENDAR`
- `CACHED_CALENDAR`
- `MANUAL_OVERRIDE`
- `FAIL_SAFE`

---

# Test-Date Evaluation

The app can evaluate a selected date using the current cached calendar without changing the real school status.

Use this to test dates such as:

- Normal weekday
- Scheduled No School day
- First day of school
- Last day of school
- Summer date
- Weekend

The result is published as `testResult`.

Test-date evaluation does **not** change `schoolStatus` and does not intentionally trigger downstream Rule Machine or Alexa automation.

---

# Alexa Alarm Integration

School Status Manager does not communicate with Alexa directly.

The intended architecture is:

**School Status Manager**  
↓  
**School Today.schoolStatus**  
↓  
**Hubitat Rule Machine**  
↓  
**Alexa-compatible virtual contact sensors**  
↓  
**Alexa routines**  
↓  
**Alexa alarm configuration**

The current implementation uses custom Alexa Plus routines that dynamically configure Alexa alarms.

Because the alarm times are defined inside the Alexa routines, normal and delayed schedules use separate Alexa routines.

---

# Rule Machine Example

Rule Machine evaluates the **School Today** device before Alexa alarms need to be created.

Example:

```text
IF School Today schoolStatus = NORMAL THEN

    Open Normal Alarm Contact(s)
    Wait 5 seconds
    Close Normal Alarm Contact(s)

ELSE-IF School Today schoolStatus = DELAYED
    AND School Today delayHours = 2 THEN

    Open 2-Hour Delay Alarm Contact(s)
    Wait 5 seconds
    Close 2-Hour Delay Alarm Contact(s)

ELSE-IF School Today schoolStatus = NO_SCHOOL THEN

    Do nothing

ELSE-IF School Today schoolStatus = UNKNOWN THEN

    Do nothing

END-IF
```

The contact remains open for five seconds and then closes so it is ready to trigger again on another school day.

No contact pulse means no corresponding Alexa alarm routine is triggered.

---

# Failure Handling

When the live calendar cannot be retrieved:

1. The existing cache is preserved.
2. The app attempts to determine school status from the cached calendar.
3. `source` changes to `CACHED_CALENDAR` when the cache is used.
4. `feedHealth` indicates the degraded feed state.
5. The consecutive-failure counter increments.
6. The app retries according to its configured retry settings.

Optional Hubitat notifications can be configured after a specified number of consecutive failures.

A successful live refresh resets the failure sequence.

---

# Recommended Validation

After installation, verify:

### Calendar

- Feed health is healthy.
- Cached event count is populated.
- Next relevant event looks correct.
- First/last-day markers reflect the available feed.
- Test-date evaluation correctly identifies known No School and summer dates.

### Manual Statuses

Test:

- Normal
- Delayed
- No School
- Return to Automatic

### Rule Machine / Alexa

Confirm:

- NORMAL pulses only the normal alarm contact.
- DELAYED with the expected delay hours pulses only the corresponding delayed-alarm contact.
- NO_SCHOOL produces no alarm trigger.
- UNKNOWN produces no alarm trigger.
- Each contact opens, remains open for five seconds, and closes.

---

# Repository Structure

```text
Hubitat-School-Status-Manager/
├── apps/
│   └── SchoolStatusManager.groovy
├── drivers/
│   └── SchoolStatusDevice.groovy
├── manifests/
│   ├── packageManifest.json
│   └── repository.json
├── README.md
├── INSTALL-HPM.md
└── LICENSE
```

---

# Release Information

Current package version and release notes are maintained in:

`manifests/packageManifest.json`

The currently running application version is also available through the `appVersion` attribute on the **School Today** device.

This README intentionally avoids embedding a specific release number so it remains valid across routine package updates.