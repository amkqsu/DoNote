package `fun`.dogon.note

import android.app.*
import android.content.*
import android.os.Build
import android.net.Uri
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit

val repeats=linkedMapOf("once" to "Tek sefer","hourly" to "Saatlik","daily" to "Günlük","weekly" to "Haftalık","monthly" to "Aylık","yearly" to "Yıllık","weekdays" to "Seçili günler")
data class Reminder(val anchor:String,val repeat:String="once",val interval:Int=1,val weekdays:List<Int> = emptyList(),val enabled:Boolean=true,val message:String="") {
    fun json()=JSONObject().put("anchor",anchor).put("repeat",repeat).put("interval",interval).put("weekdays",JSONArray(weekdays)).put("enabled",enabled).put("message",message).toString()
    fun next(after:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):Long? {
        if(!enabled)return null
        val start=LocalDateTime.parse(anchor);val now=Instant.ofEpochMilli(after).atZone(zone).toLocalDateTime()
        fun epoch(d:LocalDateTime)=d.atZone(zone).toInstant().toEpochMilli()
        if(repeat=="once")return epoch(start).takeIf { it>after }
        if(repeat=="weekdays") {
            var day=if(now.toLocalDate().isBefore(start.toLocalDate())) start.toLocalDate() else now.toLocalDate()
            repeat(15) { val d=day.atTime(start.toLocalTime());if(day.dayOfWeek.value in weekdays && !d.isBefore(start) && epoch(d)>after)return epoch(d);day=day.plusDays(1) };return null
        }
        val units=when(repeat) { "hourly"->ChronoUnit.HOURS;"daily"->ChronoUnit.DAYS;"weekly"->ChronoUnit.WEEKS;"monthly"->ChronoUnit.MONTHS;else->ChronoUnit.YEARS }
        var index=(units.between(start,now)/interval).coerceAtLeast(0)
        repeat(4) { val candidate=start.plus(index*interval,units);if(epoch(candidate)>after)return epoch(candidate);index++ }
        return null
    }
    companion object { fun parse(s:String):Reminder { val j=JSONObject(s);val anchor=j.getString("anchor");LocalDateTime.parse(anchor);val repeat=j.getString("repeat");require(repeat in repeats);val interval=j.optInt("interval",1);require(interval in 1..99);val a=j.optJSONArray("weekdays")?:JSONArray();val days=List(a.length()) { a.getInt(it) };require(days.all { it in 1..7 } && (repeat!="weekdays"||days.isNotEmpty()));return Reminder(anchor,repeat,interval,days,j.optBoolean("enabled",true),j.optString("message").take(200)) } }
}
object ReminderEngine {
    fun allowed(c:Context)=Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    fun pending(c:Context,id:String)=PendingIntent.getBroadcast(c,0,Intent(c,ReminderReceiver::class.java).setData(Uri.parse("donote://reminder/$id")).putExtra("note",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel(c:Context,id:String) { c.getSystemService(AlarmManager::class.java).cancel(pending(c,id));c.getSystemService(NotificationManager::class.java).cancel(id,1) }
    fun schedule(c:Context,n:Note,after:Long=System.currentTimeMillis()) {
        cancel(c,n.id);if(n.archived || n.reminder.isBlank())return
        val time=runCatching { Reminder.parse(n.reminder).next(after) }.getOrNull()?:return
        val manager=c.getSystemService(AlarmManager::class.java)
        try { if(allowed(c))manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pending(c,n.id)) else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pending(c,n.id)) } catch(_:SecurityException) { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,time,pending(c,n.id)) }
    }
    suspend fun restore(c:Context) { Repo.dao.all().forEach { schedule(c,it) } }
    fun notify(c:Context,n:Note) {
        val manager=c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("notes","Not hatırlatmaları",NotificationManager.IMPORTANCE_HIGH))
        val click=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).setData(Uri.parse("donote://note/${n.id}")).putExtra("note",n.id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content=n.content();val reminder=runCatching { Reminder.parse(n.reminder) }.getOrNull();val title=if(n.mode!="none")"Kilitli not" else content.title.ifBlank { "Başlıksız not" };val defaultBody=if(n.mode!="none")"Görüntülemek için kilidi açın." else content.plain().lineSequence().firstOrNull { it.isNotBlank() }?.take(220)?:"Notunuzu kontrol etmeyi unutmayın.";val body=reminder?.message?.takeIf { it.isNotBlank() }?:defaultBody
        val builder=NotificationCompat.Builder(c,"notes").setSmallIcon(R.drawable.ic_note).setContentTitle(title).setContentText(body).setStyle(NotificationCompat.BigTextStyle().setBigContentTitle(title).bigText(body)).setContentIntent(click).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_REMINDER).setColor(android.graphics.Color.parseColor(n.color))
        if(n.icon.isNotEmpty())builder.setLargeIcon(iconBitmap(n.icon,n.color))
        runCatching { manager.notify(n.id,1,builder.build()) }
    }
}
class ReminderReceiver:BroadcastReceiver() {
    override fun onReceive(c:Context,intent:Intent) { Repo.init(c);val pending=goAsync();Repo.scope.launch { try { val id=intent.getStringExtra("note")?:return@launch;val n=Repo.dao.get(id)?:return@launch;if(n.archived||n.reminder.isBlank())return@launch;val r=Reminder.parse(n.reminder);if(!r.enabled)return@launch
        if(r.repeat=="once")Repo.dao.put(n.copy(reminder=r.copy(enabled=false).json())) else ReminderEngine.schedule(c,n,System.currentTimeMillis()+1000)
        ReminderEngine.notify(c,n)
    } finally { pending.finish() } } }
}
class RestoreReceiver:BroadcastReceiver() { override fun onReceive(c:Context,i:Intent) { if(i.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))return;Repo.init(c);val pending=goAsync();Repo.scope.launch { try { ReminderEngine.restore(c);NoteWidget.refresh(c) } finally { pending.finish() } } } }
