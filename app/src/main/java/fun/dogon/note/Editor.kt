package `fun`.dogon.note

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.*
import android.text.style.*
import android.view.Gravity
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditorScreen(e:EditorState,cats:List<Category>,activity:Activity,onClose:()->Unit,onMessage:(String)->Unit,onCopy:()->Unit) {
    val scope=rememberCoroutineScope();var editView by remember { mutableStateOf<EditText?>(null) };var iconDialog by remember { mutableStateOf(false) };var categoryMenu by remember { mutableStateOf(false) };var reminderDialog by remember { mutableStateOf(false) };var lockDialog by remember { mutableStateOf(false) };var linkDialog by remember { mutableStateOf(false) };var linksDialog by remember { mutableStateOf(false) };var options by remember { mutableStateOf(false) };var busy by remember { mutableStateOf(false) };var focusNext by remember { mutableStateOf("") }
    val listState=rememberLazyListState()
    BackHandler(onBack=onClose)
    LaunchedEffect(e.version){if(!e.saved){delay(400);e.persist()}}
    LaunchedEffect(e.savingError){if(e.savingError.isNotBlank())onMessage(e.savingError)}
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> e.externalOperation=false;if(uri!=null) { busy=true;scope.launch { val encoded=withContext(Dispatchers.IO){runCatching { readImage(activity,uri) }};encoded.onSuccess { if(e.content.blocks.count{it.kind=="image"}>=12)onMessage("Bir nota en fazla 12 resim eklenebilir.") else if(e.content.json().length+it.length>24*1024*1024)onMessage("Notun resim kapasitesi doldu (24 MB).") else { val block=Block(kind="image",image=it);e.change(e.content.copy(blocks=e.content.blocks+block));e.active=block.id } }.onFailure { onMessage(it.localizedMessage?:"Resim okunamadı") };busy=false } } }
    fun add(kind:String) { if(e.content.blocks.size>=500){onMessage("Bir not en fazla 500 bölüm içerebilir.");return};e.add(kind);focusNext=e.active;scope.launch { delay(60);listState.animateScrollToItem(e.content.blocks.size+1) } }
    fun format(action:String) {
        val v=editView;if(v==null||e.content.blocks.none { it.id==e.active&&it.kind!="image" }){onMessage("Önce biçimlendireceğiniz metni seçin.");return}
        val start=v.selectionStart.coerceAtLeast(0);val end=v.selectionEnd.coerceAtLeast(0);if(start==end){onMessage("Önce biçimlendireceğiniz metni seçin.");return}
        val text=v.text
        when(action) {
            "bold","italic" -> { val style=if(action=="bold")Typeface.BOLD else Typeface.ITALIC;val spans=text.getSpans(start,end,StyleSpan::class.java).filter { it.style==style };if(spans.isNotEmpty())spans.forEach { text.removeSpan(it) }else text.setSpan(StyleSpan(style),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            "underline" -> { val spans=text.getSpans(start,end,UnderlineSpan::class.java);if(spans.isNotEmpty())spans.forEach{text.removeSpan(it)}else text.setSpan(UnderlineSpan(),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            "strike" -> { val spans=text.getSpans(start,end,StrikethroughSpan::class.java);if(spans.isNotEmpty())spans.forEach{text.removeSpan(it)}else text.setSpan(StrikethroughSpan(),start,end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        }
        e.content.blocks.find { it.id==e.active }?.let { e.block(it.copy(html=Html.toHtml(text,Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL))) }
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title={Column { Text("Not",style=MaterialTheme.typography.titleMedium);Text(if(busy)"Resim hazırlanıyor…" else if(e.saved)"Kaydedildi" else "Kaydediliyor…",style=MaterialTheme.typography.labelSmall,color=Muted) }},navigationIcon={IconButton(onClick=onClose){Icon(Icons.Outlined.ArrowBack,"Kaydet ve geri dön")}},actions={IconButton(onClick=onCopy){Icon(Icons.Outlined.ContentCopy,"Notu kopyala")};IconButton(onClick={options=true}){Icon(Icons.Outlined.MoreVert,"Not seçenekleri")};DropdownMenu(options,{options=false}) { DropdownMenuItem(text={Text("İkon ve renk")},onClick={options=false;iconDialog=true});DropdownMenuItem(text={Text("Hatırlatma")},onClick={options=false;reminderDialog=true});DropdownMenuItem(text={Text(if(e.note.mode=="none")"Notu kilitle" else "Kilit ayarları")},onClick={options=false;lockDialog=true});DropdownMenuItem(text={Text(if(e.note.pinned)"Sabitlemeyi kaldır" else "Sabitle")},onClick={options=false;e.meta(e.note.copy(pinned=!e.note.pinned))});DropdownMenuItem(text={Text("Bağlantıları aç")},onClick={options=false;linksDialog=true}) } })
        Row(Modifier.padding(horizontal=18.dp),verticalAlignment=Alignment.CenterVertically) {
            if(e.note.icon.isNotBlank())IconButton(onClick={iconDialog=true}){Icon(noteIcon(e.note.icon),"Not ikonu",tint=hexColor(e.note.color))} else TextButton(onClick={iconDialog=true}){Text("İkon ekle")}
            Box { AssistChip(onClick={categoryMenu=true},label={Text(cats.find { it.id==e.note.category }?.name?:"Kategori")},trailingIcon={Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp))});DropdownMenu(categoryMenu,{categoryMenu=false}){cats.forEach { c -> DropdownMenuItem(text={Text(c.name)},leadingIcon={Box(Modifier.size(9.dp).background(hexColor(c.color),RoundedCornerShape(3.dp)))},onClick={e.meta(e.note.copy(category=c.id));categoryMenu=false}) }} }
            Spacer(Modifier.weight(1f));IconButton(onClick={reminderDialog=true}){Icon(Icons.Outlined.NotificationsNone,"Hatırlatma",tint=if(e.note.reminder.isNotBlank())MaterialTheme.colorScheme.primary else Muted)};IconButton(onClick={lockDialog=true}){Icon(if(e.note.mode=="none")Icons.Outlined.LockOpen else Icons.Outlined.Lock,"Kilit ayarları")}
        }
        if(e.note.reminder.isNotBlank())Text(runCatching { reminderLabel(Reminder.parse(e.note.reminder)) }.getOrDefault(""),style=MaterialTheme.typography.labelSmall,color=Muted,modifier=Modifier.padding(horizontal=24.dp,vertical=3.dp))
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(state=listState,modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            item { OutlinedTextField(e.content.title,{if(it.length<=500)e.change(e.content.copy(title=it))},modifier=Modifier.fillMaxWidth(),placeholder={Text("Başlık",style=MaterialTheme.typography.headlineSmall)},textStyle=MaterialTheme.typography.headlineSmall,colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=androidx.compose.ui.graphics.Color.Transparent,unfocusedBorderColor=androidx.compose.ui.graphics.Color.Transparent),maxLines=4) }
            items(e.content.blocks,key={it.id}) { block ->
                Column {
                    Row(verticalAlignment=Alignment.Top,modifier=Modifier.fillMaxWidth().background(if(block.kind=="quote")Raised else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(8.dp))) {
                        when(block.kind) { "check"->Checkbox(block.checked,{e.block(block.copy(checked=it))},Modifier.padding(top=2.dp));"bullet"->Text("•",Modifier.padding(start=8.dp,top=13.dp,end=6.dp));"quote"->Text("❝",Modifier.padding(start=8.dp,top=13.dp,end=6.dp),color=MaterialTheme.colorScheme.primary) }
                        if(block.kind=="image") {
                            val bitmap=remember(block.image){runCatching { val bytes=Vault.bytes(block.image);BitmapFactory.decodeByteArray(bytes,0,bytes.size) }.getOrNull()}
                            if(bitmap!=null)Image(bitmap.asImageBitmap(),"Nota eklenen resim",Modifier.weight(1f).heightIn(max=320.dp).clickable { e.active=block.id },contentScale=androidx.compose.ui.layout.ContentScale.Fit) else Text("Resim okunamadı",Modifier.weight(1f))
                        } else {
                            val current by rememberUpdatedState(block)
                            AndroidView(modifier=Modifier.weight(1f).heightIn(min=52.dp),factory={ctx ->
                                EditText(ctx).apply {
                                    setTextColor(android.graphics.Color.parseColor("#F5F5F7"));setHintTextColor(android.graphics.Color.parseColor("#77777F"));setBackgroundColor(android.graphics.Color.TRANSPARENT);setPadding(8,14,8,14);gravity=Gravity.TOP;inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES;setHorizontallyScrolling(false);minLines=1;hint=if(block.kind=="check")"Yapılacak…" else "Yazmaya başla…";importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                                    setText(Html.fromHtml(block.html,Html.FROM_HTML_MODE_COMPACT).trimEnd());tag=block.html
                                    setOnFocusChangeListener { _,focused -> if(focused){e.active=block.id;editView=this} }
                                    addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){};override fun afterTextChanged(s:Editable?) { if(s!=null&&hasFocus()&&tag!="__updating__") { val html=Html.toHtml(s,Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL);tag=html;e.block(current.copy(html=html)) } } })
                                }
                            },update={v ->
                                v.textSize=when(block.kind){"h1"->25f;"h2"->20f;else->16f};v.setTypeface(null,if(block.kind in listOf("h1","h2"))Typeface.BOLD else Typeface.NORMAL);v.alpha=if(block.checked)0.5f else 1f
                                if(v.tag!=block.html) { val selection=v.selectionStart;v.tag="__updating__";v.setText(Html.fromHtml(block.html,Html.FROM_HTML_MODE_COMPACT).trimEnd());v.tag=block.html;if(v.hasFocus())v.setSelection(selection.coerceIn(0,v.length())) }
                                if(focusNext==block.id){v.requestFocus();focusNext="";editView=v;e.active=block.id}
                            })
                        }
                        IconButton(onClick={e.active=block.id;if(block.kind=="image")editView=null; e.change(e.content.copy(blocks=e.content.blocks.filter { it.id!=block.id }))},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Close,"Bölümü sil",Modifier.size(15.dp),tint=Muted)}
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) { TextButton(onClick={add("text")}){Icon(Icons.Outlined.Add,null,Modifier.size(16.dp));Text("Metin")};TextButton(onClick={add("check")}){Icon(Icons.Outlined.CheckBox,null,Modifier.size(16.dp));Text("Görev")} } }
            item { if(Repo.bool("counter",true))Text("${e.content.plain().split(Regex("\\s+")).count { it.isNotBlank() }} kelime · ${e.content.blocks.count{it.kind=="check"&&it.checked}}/${e.content.blocks.count{it.kind=="check"}} görev",style=MaterialTheme.typography.labelSmall,color=Muted,modifier=Modifier.padding(vertical=12.dp)) }
        }
        HorizontalDivider(color=Line)
        LazyRow(Modifier.fillMaxWidth().background(SurfaceColor),contentPadding=PaddingValues(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            item { Tool(Icons.Outlined.Undo,"Geri al",e.undo.isNotEmpty()){e.back()} };item{Tool(Icons.Outlined.Redo,"Yinele",e.redo.isNotEmpty()){e.forward()}}
            item { Tool(Icons.Outlined.FormatBold,"Kalın"){format("bold")} };item { Tool(Icons.Outlined.FormatItalic,"İtalik"){format("italic")} };item{Tool(Icons.Outlined.FormatUnderlined,"Altı çizili"){format("underline")}};item{Tool(Icons.Outlined.StrikethroughS,"Üstü çizili"){format("strike")}}
            item { Tool(Icons.Outlined.CheckBox,"Yapılacak ekle"){add("check")} };item { Tool(Icons.Outlined.Image,"Resim ekle",!busy){e.externalOperation=true;imagePicker.launch("image/*")} };item { Tool(Icons.Outlined.Link,"Bağlantı ekle"){linkDialog=true} }
            item { Tool(Icons.Outlined.Title,"Büyük başlık"){e.content.blocks.find{it.id==e.active&&it.kind!="image"}?.let{e.block(it.copy(kind=if(it.kind=="h1")"text"else"h1"))}?:add("h1")} };item { Tool(Icons.Outlined.TextFields,"Alt başlık"){e.content.blocks.find{it.id==e.active&&it.kind!="image"}?.let{e.block(it.copy(kind=if(it.kind=="h2")"text"else"h2"))}?:add("h2")} };item { Tool(Icons.Outlined.FormatListBulleted,"Madde işareti"){add("bullet")} };item { Tool(Icons.Outlined.FormatQuote,"Alıntı"){add("quote")} }
        }
    }
    if(iconDialog)IconDialog(e.note.icon,e.note.color,{iconDialog=false}) { icon,color -> e.meta(e.note.copy(icon=icon,color=color));iconDialog=false }
    if(reminderDialog)ReminderDialog(activity,e.note.reminder,{reminderDialog=false}) { e.meta(e.note.copy(reminder=it));reminderDialog=false }
    if(lockDialog)LockDialog(activity,e,{lockDialog=false},onMessage)
    if(linkDialog)LinkDialog(editView,{linkDialog=false}) { label,url ->
        val v=editView;if(v!=null&&v.hasFocus()) { val start=v.selectionStart.coerceAtLeast(0);val end=v.selectionEnd.coerceAtLeast(start);v.text.replace(start,end,label);v.text.setSpan(URLSpan(url),start,start+label.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);e.content.blocks.find { it.id==e.active }?.let { e.block(it.copy(html=Html.toHtml(v.text,Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL))) } } else e.change(e.content.copy(blocks=e.content.blocks+Block(html="<a href=\"${TextUtils.htmlEncode(url)}\">${TextUtils.htmlEncode(label)}</a>")))
        linkDialog=false
    }
    if(linksDialog) { val links=e.content.blocks.flatMap { val s=Html.fromHtml(it.html,Html.FROM_HTML_MODE_COMPACT);s.getSpans(0,s.length,URLSpan::class.java).map { it.url } }.distinct();AlertDialog(onDismissRequest={linksDialog=false},title={Text("Bağlantılar")},text={Column(Modifier.verticalScroll(rememberScrollState())) { if(links.isEmpty())Text("Bu notta bağlantı yok.");links.forEach { url -> TextButton(onClick={if(safeUrl(url))runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }.onFailure { onMessage("Bağlantıyı açabilecek uygulama yok.") }else onMessage("Yalnızca https, http ve mailto bağlantıları açılabilir.")}){Text(url)} } }},confirmButton={TextButton(onClick={linksDialog=false}){Text("Kapat")}}) }
}
@Composable fun Tool(icon:androidx.compose.ui.graphics.vector.ImageVector,label:String,enabled:Boolean=true,click:()->Unit) { IconButton(onClick=click,enabled=enabled){Icon(icon,label,Modifier.size(22.dp))} }
fun readImage(c:Context,uri:Uri):String {
    val opts=BitmapFactory.Options().apply { inJustDecodeBounds=true };c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,opts) };require(opts.outWidth>0&&opts.outHeight>0){"Bu dosya desteklenen bir resim değil."};require(opts.outWidth.toLong()*opts.outHeight<=150000000) { "Resim çözünürlüğü çok yüksek." }
    var sample=1;while(opts.outWidth/sample>1600||opts.outHeight/sample>1600)sample*=2
    val bitmap=c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) }?:error("Resim açılamadı.")
    val orientation=c.contentResolver.openInputStream(uri)?.use { runCatching { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1) }.getOrDefault(1) }?:1
    val matrix=android.graphics.Matrix();when(orientation) { 2->matrix.setScale(-1f,1f);3->matrix.setRotate(180f);4->matrix.setScale(1f,-1f);5->{matrix.setRotate(90f);matrix.postScale(-1f,1f)};6->matrix.setRotate(90f);7->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)};8->matrix.setRotate(-90f) }
    val oriented=if(matrix.isIdentity)bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
    val output=ByteArrayOutputStream();oriented.compress(Bitmap.CompressFormat.JPEG,88,output);if(oriented!==bitmap)oriented.recycle();bitmap.recycle();require(output.size()<=3*1024*1024){"Resim çok büyük. Daha küçük bir resim seçin."};return Vault.b64(output.toByteArray())
}
fun safeUrl(url:String):Boolean = runCatching { val u=Uri.parse(url);u.scheme in listOf("https","http","mailto") && if(u.scheme=="mailto")u.schemeSpecificPart.contains("@") else !u.host.isNullOrBlank() }.getOrDefault(false)
@Composable fun LinkDialog(v:EditText?,dismiss:()->Unit,save:(String,String)->Unit) { var label by remember { mutableStateOf(if(v!=null&&v.selectionStart>=0&&v.selectionEnd>v.selectionStart)v.text.substring(v.selectionStart,v.selectionEnd) else "") };var url by remember { mutableStateOf("") };AlertDialog(onDismissRequest=dismiss,title={Text("Bağlantı ekle")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){OutlinedTextField(label,{label=it},label={Text("Görünen metin")});OutlinedTextField(url,{url=it.trim()},label={Text("https:// veya mailto:")},singleLine=true,keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.Uri))}},confirmButton={TextButton(enabled=safeUrl(url),onClick={save(label.ifBlank { url },url)}){Text("Ekle")}},dismissButton={TextButton(onClick=dismiss){Text("Vazgeç")}}) }
@Composable fun IconDialog(initial:String,initialColor:String,dismiss:()->Unit,save:(String,String)->Unit) {
    var icon by remember { mutableStateOf(initial) }
    var color by remember { mutableStateOf(initialColor) }
    AlertDialog(onDismissRequest=dismiss,title={Text("Not ikonu")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FilterChip(icon.isEmpty(),{icon=""},label={Text("İkonsuz")})
            iconKeys.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    row.forEach { key ->
                        IconButton(onClick={icon=key},modifier=Modifier.background(if(icon==key)Raised else androidx.compose.ui.graphics.Color.Transparent,RoundedCornerShape(12.dp))) {
                            Icon(noteIcon(key),iconLabels[iconKeys.indexOf(key)],tint=hexColor(color))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp));Text("İkon rengi");ColorPicker(color){color=it}
        }
    },confirmButton={TextButton(onClick={save(icon,color)}){Text("Uygula")}},dismissButton={TextButton(onClick=dismiss){Text("Vazgeç")}})
}
@Composable fun ColorPicker(selected:String,change:(String)->Unit) { Column { palette.chunked(5).forEach { row->Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween) { row.forEach { color->Box(Modifier.size(40.dp).background(hexColor(color),RoundedCornerShape(12.dp)).border(if(selected.equals(color,true))3.dp else 0.dp,MaterialTheme.colorScheme.onSurface,RoundedCornerShape(12.dp)).clickable { change(color) },contentAlignment=Alignment.Center) { if(selected.equals(color,true))Icon(Icons.Outlined.Check,null,tint=contrast(hexColor(color)),modifier=Modifier.size(18.dp)) } } } } } }
fun reminderLabel(r:Reminder):String { val date=LocalDateTime.parse(r.anchor).format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));return if(!r.enabled)"Tamamlandı · $date" else "${repeats[r.repeat]}${if(r.interval>1)" (${r.interval})"else""} · $date" }
@Composable fun ReminderDialog(activity:Activity,existing:String,dismiss:()->Unit,save:(String)->Unit) {
    val initial=remember { runCatching { Reminder.parse(existing) }.getOrNull() };var dateTime by remember { mutableStateOf(initial?.let { LocalDateTime.parse(it.anchor) }?:LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)) };var repeat by remember { mutableStateOf(initial?.repeat?:"once") };var interval by remember { mutableStateOf((initial?.interval?:1).toString()) };var days by remember { mutableStateOf(initial?.weekdays?:listOf(1,2,3,4,5)) };var exact by remember { mutableStateOf(ReminderEngine.allowed(activity)) }
    val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    AlertDialog(onDismissRequest=dismiss,title={Text("Hatırlatma")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick={DatePickerDialog(activity,{_,y,m,d->dateTime=dateTime.withDayOfMonth(1).withYear(y).withMonth(m+1).withDayOfMonth(d)},dateTime.year,dateTime.monthValue-1,dateTime.dayOfMonth).show()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.CalendarMonth,null);Spacer(Modifier.width(8.dp));Text(dateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))}
        OutlinedButton(onClick={TimePickerDialog(activity,{_,h,m->dateTime=dateTime.withHour(h).withMinute(m)},dateTime.hour,dateTime.minute,true).show()},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Schedule,null);Spacer(Modifier.width(8.dp));Text(dateTime.format(DateTimeFormatter.ofPattern("HH:mm")))}
        repeats.entries.chunked(2).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { row.forEach{(k,v)->FilterChip(repeat==k,{repeat=k},label={Text(v)})} } }
        if(repeat!="once"&&repeat!="weekdays")OutlinedTextField(interval,{if(it.length<=2&&it.all(Char::isDigit))interval=it},label={Text("Tekrar aralığı (1–99)")},keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
        if(repeat=="weekdays")listOf("Pzt","Sal","Çar","Per","Cum","Cmt","Paz").mapIndexed { i,s->i+1 to s }.chunked(4).forEach { row->Row {row.forEach { (n,label)->FilterChip(n in days,{days=if(n in days)days-n else days+n},label={Text(label)})}} }
        if(repeat=="monthly")Text("Seçilen gün o ay yoksa ayın son günü kullanılır.",color=Muted,style=MaterialTheme.typography.bodySmall)
        if(!exact) { Text("Tam zamanında hatırlatma için alarm iznini açın. İzin verilmezse Android bildirimi geciktirebilir.",color=Muted,style=MaterialTheme.typography.bodySmall);TextButton(onClick={if(Build.VERSION.SDK_INT>=31)runCatching { activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${activity.packageName}"))) };exact=ReminderEngine.allowed(activity)}){Text("Alarm iznini aç")} }
        if(repeat=="once"&&dateTime.isBefore(LocalDateTime.now()))Text("Gelecekte bir tarih ve saat seçin.",color=MaterialTheme.colorScheme.error)
    }},confirmButton={TextButton(enabled=(interval.toIntOrNull()?:0) in 1..99 && (repeat!="weekdays"||days.isNotEmpty())&&(repeat!="once"||dateTime.isAfter(LocalDateTime.now())),onClick={if(Build.VERSION.SDK_INT>=33)notifications.launch(android.Manifest.permission.POST_NOTIFICATIONS);save(Reminder(dateTime.toString(),repeat,interval.toInt(),days).json())}){Text("Kaydet")}},dismissButton={Row { if(existing.isNotBlank())TextButton(onClick={save("")}){Text("Kaldır")};TextButton(onClick=dismiss){Text("Vazgeç")} }})
}
@Composable fun LockDialog(activity:Activity,e:EditorState,dismiss:()->Unit,message:(String)->Unit) {
    var pin by remember { mutableStateOf("") };var confirm by remember { mutableStateOf("") };var bio by remember { mutableStateOf(false) };var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf("") };var signal by remember { mutableStateOf<android.os.CancellationSignal?>(null) };val scope=rememberCoroutineScope()
    DisposableEffect(Unit) { activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);onDispose { signal?.cancel();if(e.note.mode=="none")activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) } }
    fun enableBio(key:ByteArray) { try { val cipher=Vault.bioCipher(e.note.id);signal=Vault.biometric(activity,cipher,{ c -> e.meta(e.note.copy(mode="biometric",bio=Vault.b64(c.iv+c.doFinal(key))));busy=false;dismiss();message("Biyometri ve kurtarma PIN'i etkin") },{error=it;busy=false}) }catch(_:Exception){error="Biyometri kullanılamıyor. PIN kilidi etkin kaldı.";busy=false} }
    AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text(if(e.note.mode=="none")"Notu kilitle" else "Kilit ayarları")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        if(e.note.mode=="none") {
            Text("6–12 rakamlı bir PIN belirleyin. Unutulan PIN sıfırlanamaz. JSON yedeğindeki kilitli not da bu PIN ile açılır.",color=Muted)
            OutlinedTextField(pin,{if(it.length<=12&&it.all(Char::isDigit))pin=it},label={Text("PIN")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true)
            OutlinedTextField(confirm,{if(it.length<=12&&it.all(Char::isDigit))confirm=it},label={Text("PIN tekrar")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true)
            if(Vault.bioAvailable(activity))Row(verticalAlignment=Alignment.CenterVertically){Text("Biyometriyi de kullan",Modifier.weight(1f));Switch(bio,{bio=it})}else Text("Bu cihazda kullanılabilir güçlü biyometri yok; PIN kullanılabilir.",color=Muted,style=MaterialTheme.typography.bodySmall)
        } else {
            Text("İçerik şifreli. Bildirimler ve widget içeriği göstermez.",color=Muted)
            if(e.note.bio.isBlank()&&Vault.bioAvailable(activity))OutlinedButton(enabled=!busy,onClick={busy=true;enableBio(requireNotNull(e.key))}){Text("Biyometriyi etkinleştir")}
            if(e.note.bio.isNotBlank())OutlinedButton(onClick={e.meta(e.note.copy(mode="pin",bio=""));dismiss()}){Text("Yalnızca PIN kullan")}
            OutlinedButton(enabled=!busy,onClick={e.meta(e.note.copy(mode="none",salt="",wrapped="",bio="",payload=e.content.json()));dismiss();message("Notun kilidi kaldırıldı")}){Text("Kilidi kaldır")}
        }
        if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
    }},confirmButton={if(e.note.mode=="none")TextButton(enabled=pin.length>=6&&pin==confirm&&!busy,onClick={busy=true;scope.launch {
        val result=withContext(Dispatchers.IO){runCatching { Vault.lock(e.note,e.content,pin) }}
        result.onSuccess { (n,key)->
            e.installKey(key);e.meta(n);e.persist();if(e.saved){if(bio)enableBio(key)else{busy=false;dismiss();message("PIN kilidi etkin")}}else{error=e.savingError;busy=false}
        }.onFailure { error=it.localizedMessage?:"Kilit oluşturulamadı";busy=false }
    }}){Text("Kilitle")}else TextButton(enabled=!busy,onClick=dismiss){Text("Tamam")}},dismissButton={if(e.note.mode=="none")TextButton(enabled=!busy,onClick=dismiss){Text("Vazgeç")}})
}
