package `fun`.dogon.note

import android.app.*
import android.appwidget.*
import android.content.*
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class NoteWidget:AppWidgetProvider() {
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray) { Repo.init(c);val p=goAsync();Repo.scope.launch { try { ids.forEach { update(c,it) } } finally { p.finish() } } }
    override fun onDeleted(c:Context,ids:IntArray) { ids.forEach { c.getSharedPreferences("widgets",0).edit().remove(it.toString()).apply() } }
    companion object {
        suspend fun refresh(c:Context) { AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,NoteWidget::class.java)).forEach { update(c,it) } }
        suspend fun update(c:Context,id:Int) {
            val selected=c.getSharedPreferences("widgets",0).getString(id.toString(),null)
            val n=selected?.let { Repo.dao.get(it) };val content=n?.content()
            val v=RemoteViews(c.packageName,R.layout.note_widget)
            v.setTextViewText(R.id.widget_title,if(n==null)"Not bulunamadı" else if(n.archived)"Arşivlenmiş not" else content!!.title.ifBlank { "Başlıksız not" })
            v.setTextViewText(R.id.widget_body,if(n==null)"Yeni not seçmek için widget'ı yeniden yapılandırın." else if(n.archived)"Arşive Ayarlar'dan ulaşabilirsiniz." else if(n.mode!="none")"Kilitli · Açmak için dokunun" else content!!.plain().take(1000))
            if(n!=null&&n.icon.isNotEmpty()) { v.setViewVisibility(R.id.widget_icon,View.VISIBLE);v.setImageViewBitmap(R.id.widget_icon,iconBitmap(n.icon,n.color)) } else v.setViewVisibility(R.id.widget_icon,View.GONE)
            val target=Intent(c,MainActivity::class.java).setData(Uri.parse("donote://widget/$id"));if(n!=null&&!n.archived)target.putExtra("note",n.id)
            v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,id,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            AppWidgetManager.getInstance(c).updateAppWidget(id,v)
        }
    }
}
class WidgetConfigActivity:ComponentActivity() {
    override fun attachBaseContext(base:android.content.Context) { val config=android.content.res.Configuration(base.resources.configuration);config.setLocale(java.util.Locale("tr","TR"));super.attachBaseContext(base.createConfigurationContext(config)) }
    override fun onCreate(state:Bundle?) { super.onCreate(state);Repo.init(this);setResult(RESULT_CANCELED)
        val widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return}
        setContent { NoteTheme { val notes by Repo.dao.observe().collectAsState(initial=emptyList());val scope=rememberCoroutineScope()
            Surface(Modifier.fillMaxSize()) { Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
                Text("Widget için not seçin",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(16.dp))
                if(notes.none { !it.archived })Text("Önce DoNote'ta bir not oluşturun.")
                LazyColumn { items(notes.filter { !it.archived }) { n -> ListItem(headlineContent={Text(n.content().title.ifBlank { "Başlıksız not" })},leadingContent={if(n.icon.isNotBlank())Icon(noteIcon(n.icon),null,tint=hexColor(n.color))},modifier=Modifier.clickable { scope.launch { getSharedPreferences("widgets",0).edit().putString(widgetId.toString(),n.id).apply();NoteWidget.update(this@WidgetConfigActivity,widgetId);setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish() } }) } }
            } }
        } }
    }
}
