package `fun`.dogon.note

import android.app.*
import android.appwidget.*
import android.content.*
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class NoteWidget:AppWidgetProvider() {
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray) { Repo.init(c);val p=goAsync();Repo.scope.launch { try { ids.forEach { update(c,it) } } finally { p.finish() } } }
    override fun onDeleted(c:Context,ids:IntArray) { ids.forEach { c.getSharedPreferences("widgets",0).edit().remove(it.toString()).apply() } }
    override fun onReceive(c:Context,i:Intent) {
        super.onReceive(c,i);if(i.action!="fun.dogon.note.TOGGLE_WIDGET_TASK")return;Repo.init(c);val pending=goAsync();Repo.scope.launch { try { val noteId=i.getStringExtra("note")?:return@launch;val blockId=i.getStringExtra("block")?:return@launch;val n=Repo.dao.get(noteId)?:return@launch;if(n.mode!="none")return@launch;val content=n.content();val b=content.blocks.find { it.id==blockId&&it.kind=="check" }?:return@launch;val updated=content.copy(blocks=content.blocks.map { if(it.id==blockId)b.copy(checked=!b.checked) else it });Repo.save(n.copy(payload=updated.json(),updated=System.currentTimeMillis())) } finally { pending.finish() } }
    }
    companion object {
        suspend fun refresh(c:Context) { AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c,NoteWidget::class.java)).forEach { update(c,it) } }
        private fun selectedIds(c:Context,id:Int):List<String> { val p=c.getSharedPreferences("widgets",0);val set=runCatching { p.getStringSet(id.toString(),null) }.getOrNull();if(set!=null)return set.toList();return runCatching { p.getString(id.toString(),null) }.getOrNull()?.let { listOf(it) }?:emptyList() }
        suspend fun update(c:Context,id:Int) {
            val ids=selectedIds(c,id);val notes=ids.mapNotNull { Repo.dao.get(it) }.filter { !it.archived }
            val v=RemoteViews(c.packageName,R.layout.note_widget);v.removeAllViews(R.id.widget_items)
            val first=notes.firstOrNull();v.setTextViewText(R.id.widget_title,when { notes.isEmpty()->"Not seçilmedi";notes.size==1->first!!.content().title.ifBlank { "Başlıksız not" };else->"${notes.size} not" })
            if(first!=null&&first.icon.isNotBlank()){v.setViewVisibility(R.id.widget_icon,View.VISIBLE);v.setImageViewBitmap(R.id.widget_icon,iconBitmap(first.icon,"#C7C7DB"))}else v.setViewVisibility(R.id.widget_icon,View.GONE)
            if(notes.isEmpty()) { val row=RemoteViews(c.packageName,R.layout.widget_text_row);row.setTextViewText(R.id.item_text,"Widget'ı yeniden yapılandırıp not seçin.");v.addView(R.id.widget_items,row) }
            var count=0
            notes.forEach { n ->
                if(count>=10)return@forEach
                val header=RemoteViews(c.packageName,R.layout.widget_note_header);header.setTextViewText(R.id.item_title,n.content().title.ifBlank { "Başlıksız not" });header.setImageViewBitmap(R.id.item_icon,iconBitmap(n.icon.ifBlank { "note" },"#C7C7DB"));v.addView(R.id.widget_items,header);count++
                if(n.mode!="none") { val row=RemoteViews(c.packageName,R.layout.widget_text_row);row.setTextViewText(R.id.item_text,"Kilitli · Açmak için dokunun");v.addView(R.id.widget_items,row);count++ }
                else {
                    val content=n.content();val blocks=content.blocks.filter { it.kind!="image"&&android.text.Html.fromHtml(it.html,android.text.Html.FROM_HTML_MODE_COMPACT).toString().isNotBlank() }
                    if(blocks.isEmpty()){val row=RemoteViews(c.packageName,R.layout.widget_text_row);row.setTextViewText(R.id.item_text,"Henüz içerik yok");v.addView(R.id.widget_items,row);count++}
                    blocks.forEach blockLoop@ { b -> if(count>=10)return@blockLoop;val text=android.text.Html.fromHtml(b.html,android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim();val row=RemoteViews(c.packageName,R.layout.widget_text_row);row.setTextViewText(R.id.item_text,if(b.kind=="check")"${if(b.checked)"☑" else "☐"} $text" else text);if(b.kind=="check"){val toggle=Intent(c,NoteWidget::class.java).setAction("fun.dogon.note.TOGGLE_WIDGET_TASK").setData(Uri.parse("donote://toggle/${n.id}/${b.id}")).putExtra("note",n.id).putExtra("block",b.id);row.setOnClickPendingIntent(R.id.item_text,PendingIntent.getBroadcast(c,(n.id+b.id).hashCode(),toggle,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))};v.addView(R.id.widget_items,row);count++ }
                }
            }
            val target=Intent(c,MainActivity::class.java).setData(Uri.parse("donote://widget/$id"));if(notes.size==1)target.putExtra("note",notes.first().id);v.setOnClickPendingIntent(R.id.widget_root,PendingIntent.getActivity(c,id,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE));AppWidgetManager.getInstance(c).updateAppWidget(id,v)
        }
    }
}
class WidgetConfigActivity:ComponentActivity() {
    override fun attachBaseContext(base:android.content.Context) { val config=android.content.res.Configuration(base.resources.configuration);config.setLocale(java.util.Locale("tr","TR"));super.attachBaseContext(base.createConfigurationContext(config)) }
    override fun onCreate(state:android.os.Bundle?) { super.onCreate(state);Repo.init(this);setResult(RESULT_CANCELED);val widgetId=intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID);if(widgetId==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return}
        setContent { NoteTheme { val notes by Repo.dao.observe().collectAsState(initial=emptyList());val scope=rememberCoroutineScope();var selected by remember { val p=getSharedPreferences("widgets",0);val initial=runCatching { p.getStringSet(widgetId.toString(),emptySet()) }.getOrNull()?:runCatching { p.getString(widgetId.toString(),null)?.let { setOf(it) } }.getOrNull()?:emptySet();mutableStateOf(initial) }
            Surface(Modifier.fillMaxSize()) { Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp)) { Text("Widget için notları seçin",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(6.dp));Text("Tek not veya birden fazla not seçebilirsiniz.",color=Muted);Spacer(Modifier.height(16.dp));LazyColumn(Modifier.weight(1f)) { items(notes.filter { !it.archived }) { n -> ListItem(headlineContent={Text(n.content().title.ifBlank { "Başlıksız not" })},leadingContent={Checkbox(n.id in selected,{checked->selected=if(checked)selected+n.id else selected-n.id})},trailingContent={if(n.icon.isNotBlank())Icon(noteIcon(n.icon),null,tint=hexColor(n.color))},modifier=Modifier.clickable { selected=if(n.id in selected)selected-n.id else selected+n.id }) } };Button(enabled=selected.isNotEmpty(),onClick={scope.launch { getSharedPreferences("widgets",0).edit().remove(widgetId.toString()).putStringSet(widgetId.toString(),selected).apply();NoteWidget.update(this@WidgetConfigActivity,widgetId);setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId));finish() }},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Check,null);Spacer(Modifier.width(8.dp));Text("Kaydet")}
            } }
        } }
    }
}
