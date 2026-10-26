metadata {
    definition(name: "PWCS School Today Switch", namespace: "pierowman", author: "Pierowman") {
        capability "Actuator"
        capability "Sensor"
        capability "Refresh"

        attribute "schoolStatus", "enum", ["NORMAL", "NO_SCHOOL", "DELAYED", "UNKNOWN"]
        attribute "appVersion", "string"
        attribute "nextRelevantEvent", "string"
        attribute "calendarChanged", "string"
        attribute "overrideSetAt", "string"
        attribute "consecutiveFailures", "number"
        attribute "testResult", "string"
        attribute "delayHours", "number"
        attribute "tomorrowStatus", "string"
        attribute "tomorrowReason", "string"
        attribute "nextSchoolDay", "string"
        attribute "cachedEventCount", "number"
        attribute "cacheFirstDate", "string"
        attribute "cacheLastDate", "string"
        attribute "firstDayMarkerFound", "string"
        attribute "lastDayMarkerFound", "string"
        attribute "reason", "string"
        attribute "feedHealth", "enum", ["healthy", "stale", "error", "unknown"]
        attribute "source", "string"
        attribute "override", "enum", ["AUTO", "NORMAL", "NO_SCHOOL", "DELAYED"]
        attribute "autoResetOverride", "string"
        attribute "calendarCacheAgeDays", "number"
        attribute "lastChecked", "string"
        attribute "lastSuccessfulUpdate", "string"

        command "setNormalSchool"
        command "setNoSchool"
        command "setDelayed"
        command "setAutomatic"
    }
}

def installed() { initializeAttributes() }
def updated() { initializeAttributes() }

def refresh() { parent?.childRefresh(device.deviceNetworkId) }
def setNormalSchool() { parent?.childSetStatus(device.deviceNetworkId, "NORMAL") }
def setNoSchool() { parent?.childSetStatus(device.deviceNetworkId, "NO_SCHOOL") }
def setDelayed() { parent?.childSetStatus(device.deviceNetworkId, "DELAYED") }
def setAutomatic() { parent?.childClearOverride(device.deviceNetworkId) }

private void initializeAttributes() {
    if (device.currentValue("schoolStatus") == null) sendEvent(name:"schoolStatus", value:"UNKNOWN")
    if (device.currentValue("delayHours") == null) sendEvent(name:"delayHours", value:0, unit:"h")
    if (device.currentValue("tomorrowStatus") == null) sendEvent(name:"tomorrowStatus", value:"UNKNOWN")
    if (device.currentValue("tomorrowReason") == null) sendEvent(name:"tomorrowReason", value:"not evaluated")
    if (device.currentValue("nextSchoolDay") == null) sendEvent(name:"nextSchoolDay", value:"unknown")
    if (device.currentValue("cachedEventCount") == null) sendEvent(name:"cachedEventCount", value:0)
    if (device.currentValue("cacheFirstDate") == null) sendEvent(name:"cacheFirstDate", value:"unknown")
    if (device.currentValue("cacheLastDate") == null) sendEvent(name:"cacheLastDate", value:"unknown")
    if (device.currentValue("firstDayMarkerFound") == null) sendEvent(name:"firstDayMarkerFound", value:"false")
    if (device.currentValue("lastDayMarkerFound") == null) sendEvent(name:"lastDayMarkerFound", value:"false")
    if (device.currentValue("feedHealth") == null) sendEvent(name:"feedHealth", value:"unknown")
    if (device.currentValue("override") == null) sendEvent(name:"override", value:"AUTO")
    if (device.currentValue("calendarCacheAgeDays") == null) sendEvent(name:"calendarCacheAgeDays", value:-1)
    if (device.currentValue("nextRelevantEvent") == null) sendEvent(name:"nextRelevantEvent", value:"none known")
    if (device.currentValue("calendarChanged") == null) sendEvent(name:"calendarChanged", value:"unknown")
    if (device.currentValue("overrideSetAt") == null) sendEvent(name:"overrideSetAt", value:"never")
    if (device.currentValue("consecutiveFailures") == null) sendEvent(name:"consecutiveFailures", value:0)
    if (device.currentValue("testResult") == null) sendEvent(name:"testResult", value:"not run")
}
