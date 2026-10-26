import groovy.transform.Field
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Field static final String VERSION = "1.6.1"
@Field static final String DEFAULT_FEED = "https://www.pwcs.edu/events/_data/academic-calendar.ics"
@Field static final String DRIVER_NAME = "PWCS School Today Switch"
@Field static final String CHILD_LABEL = "School Today"

definition(name:"School Status Manager", namespace:"pierowman", author:"Pierowman",
    description:"School status from an ICS feed, last-good cache, summer boundaries, and manual overrides.",
    category:"Convenience", singleInstance:true)
preferences { page(name:"mainPage") }

def mainPage() {
    dynamicPage(name:"mainPage", title:"School Status Manager", install:true, uninstall:true) {
        section("Calendar") {
            input "feedUrl", "text", title:"Academic calendar ICS URL", required:true, defaultValue:DEFAULT_FEED
            input "noSchoolPhrases", "text", title:"No-school phrases", required:true, defaultValue:"No School"
            input "firstDayPhrases", "text", title:"First-day phrases", required:true, defaultValue:"First Day of School"
            input "lastDayPhrases", "text", title:"Last-day phrases", required:true, defaultValue:"Last Day of School for Students"
        }
        section("Schedule") {
            input "evaluationTime", "time", title:"Daily calendar evaluation", required:true, defaultValue:"00:05"
            input "morningRecheckEnabled", "bool", title:"Enable morning recheck", required:true, defaultValue:true, submitOnChange:true
            if (settings.morningRecheckEnabled != false)
                input "morningRecheckTime", "time", title:"Morning recheck", required:true, defaultValue:"05:05"
            input "autoResetOverride", "bool", title:"Automatically clear manual override", required:true, defaultValue:true, submitOnChange:true
            if (settings.autoResetOverride != false)
                input "overrideResetTime", "time", title:"Override reset time", required:true, defaultValue:"00:05"
        }
        section("Failure handling") {
            input "retryCount", "number", title:"Retries", range:"0..10", required:true, defaultValue:3
            input "retryDelayMinutes", "number", title:"Minutes between retries", range:"1..180", required:true, defaultValue:15
            input "requestTimeoutSeconds", "number", title:"HTTP timeout seconds", range:"5..60", required:true, defaultValue:20
        }
        section("Reliability notifications") {
            input "notifyOnFailures", "bool", title:"Notify after repeated live-feed failures", required:true, defaultValue:false, submitOnChange:true
            if (settings.notifyOnFailures == true) {
                input "notificationDevices", "capability.notification", title:"Notification device(s)", multiple:true, required:true
                input "failureNotifyThreshold", "number", title:"Notify after this many consecutive failures", range:"1..20", required:true, defaultValue:3
            }
        }
        section("Test-date evaluation (does not change real school status)") {
            input "testDate", "date", title:"Date to evaluate using the cached calendar", required:false
            input "runTestDate", "button", title:"Evaluate test date"
            paragraph "Test result: ${getChildDevice(childDni())?.currentValue('testResult') ?: 'not run'}"
        }
        section("Delay settings") {
            input "defaultDelayHours", "number", title:"Default delay hours", range:"1..6", required:true, defaultValue:2
        }
        section("Manual controls") {
            input "refreshNow", "button", title:"Refresh calendar now"
            input "normalNow", "button", title:"Manual: Normal School"
            input "closedNow", "button", title:"Manual: No School"
            input "delayNow", "button", title:"Manual: Delayed"
            input "automaticNow", "button", title:"Return to Automatic"
        }
        section("Logging") { input "debugLogging", "bool", title:"Enable debug logging", required:true, defaultValue:false }
        section("Current state") {
            def d=getChildDevice(childDni())
            paragraph "Version: ${VERSION}"
            paragraph "Status: ${d?.currentValue('schoolStatus') ?: 'UNKNOWN'}"
            paragraph "Delay: ${d?.currentValue('delayHours') ?: 0} hour(s)"
            paragraph "Reason: ${d?.currentValue('reason') ?: 'Awaiting evaluation'}"
            paragraph "Source: ${d?.currentValue('source') ?: 'unknown'}"
            paragraph "Feed health: ${d?.currentValue('feedHealth') ?: 'unknown'}"
            paragraph "Cache age: ${d?.currentValue('calendarCacheAgeDays') != null ? d.currentValue('calendarCacheAgeDays') : -1} day(s)"
            paragraph "Tomorrow: ${d?.currentValue('tomorrowStatus') ?: 'UNKNOWN'} - ${d?.currentValue('tomorrowReason') ?: 'not evaluated'}"
            paragraph "Next school day: ${d?.currentValue('nextSchoolDay') ?: 'unknown'}"
            paragraph "Next relevant event: ${d?.currentValue('nextRelevantEvent') ?: 'none known'}"
            paragraph "Cache coverage: ${d?.currentValue('cachedEventCount') ?: 0} events, ${d?.currentValue('cacheFirstDate') ?: 'unknown'} through ${d?.currentValue('cacheLastDate') ?: 'unknown'}"
            paragraph "Boundary markers: first=${d?.currentValue('firstDayMarkerFound') ?: 'false'}, last=${d?.currentValue('lastDayMarkerFound') ?: 'false'}"
            paragraph "Calendar changed on last successful pull: ${d?.currentValue('calendarChanged') ?: 'unknown'}"
            paragraph "Consecutive live failures: ${d?.currentValue('consecutiveFailures') ?: 0}"
            paragraph "Override: ${d?.currentValue('override') ?: 'AUTO'}"
            paragraph "Override set at: ${d?.currentValue('overrideSetAt') ?: 'never'}"
        }
    }
}

def installed(){ ensureChild(); initialize(); runIn(3,"startupRefresh",[overwrite:true]) }
def updated(){ unschedule(); ensureChild(); initialize(); runIn(3,"startupRefresh",[overwrite:true]) }
def uninstalled(){ unschedule(); def d=getChildDevice(childDni()); if(d) deleteChildDevice(d.deviceNetworkId) }

private void initialize(){
    schedule(cronFor(settings.evaluationTime,0,5),"dailyEvaluation")
    if(settings.morningRecheckEnabled!=false) schedule(cronFor(settings.morningRecheckTime,5,5),"morningRecheck")
    if(settings.autoResetOverride!=false) schedule(cronFor(settings.overrideResetTime,0,5),"resetManualOverride")
}
def dailyEvaluation(){ refreshCalendar("daily",0) }
def morningRecheck(){ refreshCalendar("morning",0) }
def startupRefresh(){ refreshCalendar("startup",0) }
def resetManualOverride(){ if(state.manualOverride){ state.remove("manualOverride"); state.remove("overrideSetAt"); refreshCalendar("override-auto-reset",0) } }
def retryRefresh(Map data=[:]){ refreshCalendar("retry", safeInt(data.attempt,1)) }

def appButtonHandler(String b){
    if(b=="refreshNow") refreshCalendar("manual",0)
    else if(b=="normalNow") setManual("NORMAL","app")
    else if(b=="closedNow") setManual("NO_SCHOOL","app")
    else if(b=="delayNow") setManual("DELAYED","app")
    else if(b=="automaticNow") clearManual("app")
    else if(b=="runTestDate") runTestEvaluation()
}
def childRefresh(String dni){ if(dni==childDni()) refreshCalendar("child",0) }
def childSetStatus(String dni,String status){ if(dni==childDni()) setManual(status,"child") }
def childClearOverride(String dni){ if(dni==childDni()) clearManual("child") }

private void refreshCalendar(String source,Integer attempt){
    ensureChild(); String checked=timestamp(); state.lastChecked=checked
    try {
        Map parsed=parseCalendar(downloadCalendar())
        String signature=calendarSignature(parsed.events)
        Boolean changed=state.calendarSignature==null ? false : state.calendarSignature.toString()!=signature
        state.calendarSignature=signature
        state.calendarChanged=changed
        state.calendarCache=[cachedAt:checked, events:parsed.events, firstDays:parsed.firstDays, lastDays:parsed.lastDays, eventCount:parsed.eventCount]
        state.observedFirstDays=mergeDates(state.observedFirstDays,parsed.firstDays)
        state.observedLastDays=mergeDates(state.observedLastDays,parsed.lastDays)
        state.lastSuccessfulUpdate=checked
        state.consecutiveFailures=0
        state.failureNotificationSent=false
        unschedule("retryRefresh")
        publishDecision(decide(localToday(),parsed),checked,"HEALTHY","LIVE_CALENDAR")
        debug "Successful live pull replaced cache with ${parsed.eventCount} events; changed=${changed}"
    } catch(Exception e){ handleFailure(source,attempt,checked,e) }
}

private String downloadCalendar(){
    String body=null; String uri=(settings.feedUrl?:DEFAULT_FEED).toString().trim()
    if(!(uri==~/(?i)^https?:\/.+/)) throw new IllegalArgumentException("Feed URL must start with http:// or https://")
    httpGet([uri:uri,contentType:"text/plain",timeout:safeInt(settings.requestTimeoutSeconds,20),
             headers:[Accept:"text/calendar, text/plain;q=0.9, */*;q=0.5","User-Agent":"Hubitat-School-Status/${VERSION}"]]){ resp ->
        if((resp?.status as Integer)!=200) throw new RuntimeException("HTTP ${resp?.status}")
        // Fix 1: Hubitat forbids payload.metaClass introspection.
        def payload=resp?.data
        if(payload==null) throw new RuntimeException("Response contained no data")
        body=payload.toString()
    }
    if(!body || body.trim().size()<20) throw new RuntimeException("Response empty or too short")
    return body
}

private Map parseCalendar(String body){
    String text=body.replace("\r\n","\n").replace('\r','\n')
    if(!text.toUpperCase().contains("BEGIN:VCALENDAR") || !text.toUpperCase().contains("END:VCALENDAR")) throw new RuntimeException("Not a complete VCALENDAR")
    List<String> lines=[]
    text.split("\n",-1).each{ line -> if((line.startsWith(" ")||line.startsWith("\t"))&&!lines.isEmpty()) lines[-1]=lines[-1]+line.substring(1); else lines<<line }
    List<Map> raw=[]; Map current=null
    lines.each{ line ->
        String u=line.toUpperCase()
        if(u=="BEGIN:VEVENT") current=[:]
        else if(u=="END:VEVENT"){ if(current!=null) raw<<current; current=null }
        else if(current!=null){ int c=line.indexOf(':'); if(c>0){ String p=line.substring(0,c).split(';',2)[0].toUpperCase(); if(p in ["DTSTART","SUMMARY","STATUS"]) current[p]=line.substring(c+1) } }
    }
    if(raw.isEmpty()) throw new RuntimeException("No VEVENT records")
    Map<String,List<String>> byDate=[:]; List<String> compact=[]; List<String> first=[]; List<String> last=[]; int count=0
    raw.each{ ev ->
        if(ev["STATUS"]?.toString()?.equalsIgnoreCase("CANCELLED")) return
        String ds=ev["DTSTART"]?.toString()?.trim(); String summary=decode(ev["SUMMARY"]?.toString()?:"")
        // Fix 2: simple date extraction avoids unsupported Matcher/BASIC_ISO_DATE behavior.
        if(!ds || ds.size()<8) return
        String d=ds.substring(0,8); if(!(d==~/\d{8}/)) return
        String iso="${d.substring(0,4)}-${d.substring(4,6)}-${d.substring(6,8)}"
        byDate[iso]=(byDate[iso]?:[])+[summary]; compact<<"${iso}|${summary}"; count++
        if(matches(summary,settings.firstDayPhrases,"First Day of School")) first<<iso
        if(matches(summary,settings.lastDayPhrases,"Last Day of School for Students")) last<<iso
    }
    if(count==0) throw new RuntimeException("VCALENDAR contained no VEVENT with a valid DTSTART date")
    [eventsByDate:byDate,events:compact.unique().sort(),firstDays:first.unique().sort(),lastDays:last.unique().sort(),eventCount:count]
}

private Map cachedParsed(){
    Map c=state.calendarCache instanceof Map?state.calendarCache:null
    if(!c || !(c.events instanceof Collection)) return null
    Map<String,List<String>> by=[:]
    c.events.each{ v -> String x=v.toString(); int i=x.indexOf('|'); if(i==10){ String d=x.substring(0,10); by[d]=(by[d]?:[])+[x.substring(11)] } }
    [eventsByDate:by,events:c.events,firstDays:dateList(c.firstDays),lastDays:dateList(c.lastDays),eventCount:safeInt(c.eventCount,by.size())]
}

private Map decide(LocalDate today,Map parsed){
    String key=today.toString(); List<String> summaries=(parsed.eventsByDate[key]?:[]) as List<String>
    List<String> first=mergeDates(state.observedFirstDays,parsed.firstDays); List<String> last=mergeDates(state.observedLastDays,parsed.lastDays)
    if(today.dayOfWeek in [DayOfWeek.SATURDAY,DayOfWeek.SUNDAY]) return [status:"NO_SCHOOL",reason:"Weekend"]
    String closed=summaries.find{ matches(it,settings.noSchoolPhrases,"No School") }
    if(closed) return [status:"NO_SCHOOL",reason:"Calendar: ${closed}"]
    if(first.contains(key)) return [status:"NORMAL",reason:"First day of school"]
    if(last.contains(key)) return [status:"NORMAL",reason:"Last day of school; summer starts tomorrow"]
    Map summer=summerEvidence(today,first,last)
    if(summer.yes) return [status:"NO_SCHOOL",reason:summer.reason]
    [status:"NORMAL",reason:"Weekday; no no-school event or positive summer evidence"]
}

private Map summerEvidence(LocalDate today,List<String> fs,List<String> ls){
    List<LocalDate> first=parseDates(fs), last=parseDates(ls); List<Map> past=[]
    first.findAll{it.isBefore(today)}.each{past<<[d:it,t:"first"]}; last.findAll{it.isBefore(today)}.each{past<<[d:it,t:"last"]}
    Map latest=past?past.sort{a,b->a.d<=>b.d}.last():null
    if(latest?.t=="last"){ LocalDate next=first.findAll{it.isAfter(latest.d)}.sort().find{true}; if(next==null||today.isBefore(next)) return [yes:true,reason:"Summer break after ${latest.d}"+(next?" until ${next}":"")] }
    if(latest?.t=="first") return [yes:false]
    LocalDate next=first.findAll{it.isAfter(today)}.sort().find{true}
    if(last.isEmpty()&&next) return [yes:true,reason:"Summer before future first day ${next}; last-day marker missing"]
    [yes:false]
}

private void handleFailure(String source,Integer attempt,String checked,Exception e){
    state.consecutiveFailures=safeInt(state.consecutiveFailures,0)+1
    Map cached=cachedParsed(); String health=state.lastSuccessfulUpdate?"STALE":"ERROR"; String err="${e.class.simpleName}: ${e.message}"
    if(cached){ Map decision=decide(localToday(),cached); publishDecision(decision,checked,health,"CACHED_CALENDAR"); updateReason("Cached calendar used after live failure (${err}); ${decision.reason}") }
    else retainUnknown("Live calendar failure (${err}); no successful cache yet",checked,health,"FAIL_SAFE")
    maybeNotifyFailure(err)
    int max=safeInt(settings.retryCount,3)
    if(attempt<max) runIn(safeInt(settings.retryDelayMinutes,15)*60,"retryRefresh",[overwrite:true,data:[attempt:attempt+1]])
}

private void publishDecision(Map decision,String checked,String health,String source){
    String automatic=normalizeStatus(decision.status); String finalStatus=state.manualOverride?normalizeStatus(state.manualOverride):automatic
    String reason=state.manualOverride?"Manual ${friendly(finalStatus)} override; automatic result: ${decision.reason}":decision.reason
    publish(finalStatus,reason,checked,health,source,state.manualOverride?:"AUTO")
}
private void setManual(String status,String source){
    status=normalizeStatus(status); if(!(status in ["NORMAL","NO_SCHOOL","DELAYED"])) return
    state.manualOverride=status; state.overrideSetAt=timestamp()
    publish(status,"Manual ${friendly(status)} override"+(settings.autoResetOverride!=false?"; auto-reset enabled":"; persistent until cleared"),state.lastChecked?:"never",currentHealth(),"MANUAL_OVERRIDE",status)
}
private void clearManual(String source){ state.remove("manualOverride"); state.remove("overrideSetAt"); refreshCalendar("automatic",0) }

private void publish(String status,String reason,String checked,String health,String source,String override){
    def d=ensureChild(); if(!d)return; status=normalizeStatus(status); boolean school=status in ["NORMAL","DELAYED"]; int delay=status=="DELAYED"?safeInt(settings.defaultDelayHours,2):0
    d.sendEvent(name:"schoolStatus",value:status); d.sendEvent(name:"delayHours",value:delay,unit:"h"); d.sendEvent(name:"appVersion",value:VERSION)
    d.sendEvent(name:"reason",value:limit(reason)); d.sendEvent(name:"lastChecked",value:checked); d.sendEvent(name:"lastSuccessfulUpdate",value:state.lastSuccessfulUpdate?:"never")
    d.sendEvent(name:"feedHealth",value:health); d.sendEvent(name:"source",value:source); d.sendEvent(name:"override",value:override); d.sendEvent(name:"overrideSetAt",value:state.overrideSetAt?:"never"); d.sendEvent(name:"autoResetOverride",value:(settings.autoResetOverride!=false).toString()); d.sendEvent(name:"calendarCacheAgeDays",value:cacheAge())
    d.sendEvent(name:"nextRelevantEvent",value:nextRelevantEvent())
    d.sendEvent(name:"calendarChanged",value:(state.calendarChanged==true).toString())
    d.sendEvent(name:"consecutiveFailures",value:safeInt(state.consecutiveFailures,0))
    publishCalendarDiagnostics(d)
}
private void updateReason(String reason){ def d=ensureChild(); if(d)d.sendEvent(name:"reason",value:limit(reason)) }
private void retainUnknown(String reason,String checked,String health,String source){ def d=ensureChild(); if(!d)return; if(d.currentValue("schoolStatus")==null)d.sendEvent(name:"schoolStatus",value:"UNKNOWN"); d.sendEvent(name:"appVersion",value:VERSION); d.sendEvent(name:"reason",value:limit(reason));d.sendEvent(name:"lastChecked",value:checked);d.sendEvent(name:"feedHealth",value:health);d.sendEvent(name:"source",value:source);d.sendEvent(name:"calendarCacheAgeDays",value:cacheAge());d.sendEvent(name:"consecutiveFailures",value:safeInt(state.consecutiveFailures,0));d.sendEvent(name:"nextRelevantEvent",value:nextRelevantEvent());d.sendEvent(name:"calendarChanged",value:(state.calendarChanged==true).toString()); publishCalendarDiagnostics(d) }

private void publishCalendarDiagnostics(def d){
    Map parsed=cachedParsed()
    if(!parsed){
        d.sendEvent(name:"tomorrowStatus",value:"UNKNOWN"); d.sendEvent(name:"tomorrowReason",value:"No successful cache")
        d.sendEvent(name:"nextSchoolDay",value:"unknown"); d.sendEvent(name:"cachedEventCount",value:0)
        d.sendEvent(name:"cacheFirstDate",value:"unknown"); d.sendEvent(name:"cacheLastDate",value:"unknown")
        d.sendEvent(name:"firstDayMarkerFound",value:"false"); d.sendEvent(name:"lastDayMarkerFound",value:"false"); return
    }
    Map tomorrow=decide(localToday().plusDays(1),parsed)
    d.sendEvent(name:"tomorrowStatus",value:tomorrow.status); d.sendEvent(name:"tomorrowReason",value:limit(tomorrow.reason?.toString()))
    d.sendEvent(name:"nextSchoolDay",value:findNextSchoolDay(parsed))
    List<String> dates=(parsed.eventsByDate?.keySet()?:[]).collect{it.toString()}.sort()
    d.sendEvent(name:"cachedEventCount",value:safeInt(parsed.eventCount,0))
    d.sendEvent(name:"cacheFirstDate",value:dates?dates.first():"unknown"); d.sendEvent(name:"cacheLastDate",value:dates?dates.last():"unknown")
    d.sendEvent(name:"firstDayMarkerFound",value:((parsed.firstDays?:[]).size()>0).toString()); d.sendEvent(name:"lastDayMarkerFound",value:((parsed.lastDays?:[]).size()>0).toString())
}
private String findNextSchoolDay(Map parsed){
    LocalDate d=localToday().plusDays(1)
    // Bound the diagnostic search to about a year; it does not affect actual decisions.
    for(int i=0;i<370;i++){
        Map result=decide(d,parsed)
        if(result.status=="NORMAL") return d.toString()
        d=d.plusDays(1)
    }
    return "not found in search window"
}
private void runTestEvaluation(){
    def d=ensureChild(); if(!d)return
    if(!settings.testDate){ d.sendEvent(name:"testResult",value:"Select a test date first"); return }
    Map parsed=cachedParsed(); if(!parsed){ d.sendEvent(name:"testResult",value:"No successful cached calendar available"); return }
    try {
        LocalDate date=LocalDate.parse(settings.testDate.toString().substring(0,10))
        Map result=decide(date,parsed)
        d.sendEvent(name:"testResult",value:"${date}: ${result.status} - ${result.reason}")
    } catch(Exception e){ d.sendEvent(name:"testResult",value:"Invalid test date: ${e.message}") }
}
private String calendarSignature(def events){
    String joined=(events?:[]).collect{it.toString()}.sort().join("\n")
    return Integer.toHexString(joined.hashCode())+":"+joined.length()
}
private String nextRelevantEvent(){
    Map parsed=cachedParsed(); if(!parsed)return "none known"
    LocalDate today=localToday(); List<Map> relevant=[]
    parsed.events.each{ item ->
        String x=item.toString(); int i=x.indexOf('|')
        if(i==10){
            try {
                LocalDate d=LocalDate.parse(x.substring(0,10)); String summary=x.substring(11)
                if(!d.isBefore(today) && (matches(summary,settings.noSchoolPhrases,"No School") || matches(summary,settings.firstDayPhrases,"First Day of School") || matches(summary,settings.lastDayPhrases,"Last Day of School for Students"))) relevant<<[date:d,summary:summary]
            } catch(Exception ignored){}
        }
    }
    if(!relevant)return "none known"
    Map next=relevant.sort{a,b->a.date<=>b.date}.first()
    return "${next.date}: ${next.summary}"
}
private void maybeNotifyFailure(String error){
    if(settings.notifyOnFailures!=true)return
    int threshold=safeInt(settings.failureNotifyThreshold,3)
    int failures=safeInt(state.consecutiveFailures,0)
    if(failures>=threshold && state.failureNotificationSent!=true){
        String message="School Status Manager: ${failures} consecutive live calendar failures. Cached/fail-safe status remains active. Latest: ${error}"
        settings.notificationDevices?.each{ device -> try{device.deviceNotification(message)}catch(Exception ignored){} }
        state.failureNotificationSent=true
    }
}
private def ensureChild(){ def d=getChildDevice(childDni()); if(d)return d; try{return addChildDevice("pierowman",DRIVER_NAME,childDni(),[name:CHILD_LABEL,label:CHILD_LABEL,isComponent:true])}catch(Exception e){log.error "Install '${DRIVER_NAME}' driver first: ${e.message}";return null} }
private String childDni(){"pwcs-school-today-${app.id}"}
private String normalizeStatus(def s){String v=(s?:"UNKNOWN").toString().toUpperCase();v in ["NORMAL","NO_SCHOOL","DELAYED","UNKNOWN"]?v:"UNKNOWN"}
private String friendly(String s){[NORMAL:"Normal School",NO_SCHOOL:"No School",DELAYED:"Delayed",UNKNOWN:"Unknown"][normalizeStatus(s)]}
private Boolean matches(String summary,def raw,String fallback){String h=norm(summary);phrases(raw,fallback).any{h.contains(norm(it))}}
private List<String> phrases(def raw,String fallback){(raw?.toString()?.trim()?:fallback).split(/[,;\n\r]+/).collect{it.trim()}.findAll{it}}
private String norm(String x){(x?:"").toLowerCase().replaceAll(/[\u2010-\u2015]/,"-").replaceAll(/\s+/," ").trim()}
private String decode(String x){(x?:"").replace("\\n"," ").replace("\\N"," ").replace("\\,",",").replace("\\;",";").replace("\\\\","\\").replaceAll(/\s+/," ").trim()}
private List<String> mergeDates(def a,def b){List<String> x=dateList(a);x.addAll(dateList(b));x.unique().sort()}
private List<String> dateList(def x){if(x==null)return[];Collection c=x instanceof Collection?x:[x];c.collect{it.toString()}.findAll{it==~/\d{4}-\d{2}-\d{2}/}.unique().sort()}
private List<LocalDate> parseDates(List<String> x){x.collect{try{LocalDate.parse(it)}catch(Exception ignored){null}}.findAll{it}.unique().sort()}
private int safeInt(def x,int f){try{x==null?f:x.toString().toInteger()}catch(Exception ignored){f}}
private LocalDate localToday(){TimeZone tz=location?.timeZone?:TimeZone.getTimeZone("America/New_York");Instant.ofEpochMilli(now()).atZone(tz.toZoneId()).toLocalDate()}
private String timestamp(){TimeZone tz=location?.timeZone?:TimeZone.getTimeZone("America/New_York");new Date().format("yyyy-MM-dd'T'HH:mm:ssXXX",tz)}
private String cronFor(def raw,int h,int m){List<Integer> p=hourMinute(raw,h,m);"0 ${p[1]} ${p[0]} * * ?"}
private List<Integer> hourMinute(def raw,int h,int m){def x=(raw?.toString()?:"")=~/(?:T|^)(\d{1,2}):(\d{2})/;x.find()?[x.group(1).toInteger(),x.group(2).toInteger()]:[h,m]}
private int cacheAge(){Map c=state.calendarCache instanceof Map?state.calendarCache:null;if(!c?.cachedAt)return -1;try{Math.max(0L,java.time.Duration.between(OffsetDateTime.parse(c.cachedAt.toString()).toInstant(),Instant.now()).toDays()) as int}catch(Exception ignored){-1}}
private String currentHealth(){getChildDevice(childDni())?.currentValue("feedHealth")?.toString()?:"unknown"}
private String limit(String x){String v=x?:"";v.size()<=1000?v:v.substring(0,997)+"..."}
private void debug(String x){if(settings.debugLogging==true)log.debug x}
