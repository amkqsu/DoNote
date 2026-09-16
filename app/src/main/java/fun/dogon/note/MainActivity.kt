package `fun`.dogon.note

import android.app.Activity
import android.content.*
import android.os.Bundle
import android.text.Html
import android.text.TextUtils
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EditorState(initial:Note,var key:ByteArray?=null,initialContent:Content=initial.content()) {
    private val originalNote=initial
    private val originalContent=initialContent
    var note by mutableStateOf(initial)
    var content by mutableStateOf(initialContent)
    var version by mutableIntStateOf(0)
    var saved by mutableStateOf(true)
    var savingError by mutableStateOf("")
    var externalOperation=false
    var active by mutableStateOf(initialContent.blocks.firstOrNull()?.id?:"")
    val mutex=Mutex()
    val undo=mutableStateListOf<Content>()
    val redo=mutableStateListOf<Content>()
    var lastHistory=0L
    fun installKey(value:ByteArray) { key?.fill(0);key=value }
    fun change(c:Content,history:Boolean=true) { if(c==content)return;if(history) { val now=System.currentTimeMillis();if(now-lastHistory>700) { undo.add(content);if(undo.size>40)undo.removeAt(0);lastHistory=now };redo.clear() };content=c;version++;saved=false }
    fun block(b:Block) { change(content.copy(blocks=content.blocks.map { if(it.id==b.id)b else it })) }
    fun add(kind:String) { val b=Block(kind=kind);change(content.copy(blocks=content.blocks+b));active=b.id }
    fun meta(n:Note) { note=n;version++;saved=false }
    fun back() { if(undo.isNotEmpty()) { redo.add(content);change(undo.removeAt(undo.lastIndex),false) } }
    fun forward() { if(redo.isNotEmpty()) { undo.add(content);change(redo.removeAt(redo.lastIndex),false) } }
    suspend fun persist() = mutex.withLock {
        val currentVersion=version;val c=content;val n=note
        try {
            val exists=withContext(Dispatchers.IO){Repo.dao.get(note.id)!=null}
            val empty=c.title.isBlank()&&c.blocks.all { it.kind!="image"&&Html.fromHtml(it.html,Html.FROM_HTML_MODE_COMPACT).toString().isBlank() }
            val metadataChanged=n.category!=originalNote.category||n.icon!=originalNote.icon||n.color!=originalNote.color||n.pinned!=originalNote.pinned||n.reminder!=originalNote.reminder||n.mode!=originalNote.mode
            if(!exists&&empty&&!metadataChanged){saved=true;savingError="";return@withLock}
            if(saved&&exists)return@withLock
            withContext(Dispatchers.IO) { val payload=if(n.mode=="none")c.json() else Vault.encrypt(c.json().toByteArray(),requireNotNull(key));Repo.save(n.copy(payload=payload,updated=System.currentTimeMillis())) }
            if(version==currentVersion)saved=true;savingError=""
        } catch(e:Exception) { savingError="Kaydedilemedi: ${e.localizedMessage?:"depolama hatası"}";saved=false }
    }
}
class MainModel:ViewModel() {
    var screen by mutableStateOf("home")
    var editor by mutableStateOf<EditorState?>(null)
    var incoming by mutableStateOf<String?>(null)
    var shareText by mutableStateOf<Pair<String,String>?>(null)
    override fun onCleared() { editor?.key?.fill(0) }
}
class MainActivity:ComponentActivity() {
    override fun attachBaseContext(base:android.content.Context) { val config=android.content.res.Configuration(base.resources.configuration);config.setLocale(java.util.Locale("tr","TR"));super.attachBaseContext(base.createConfigurationContext(config)) }
    lateinit var model:MainModel
    override fun onCreate(state:Bundle?) { super.onCreate(state);Repo.init(this);enableEdgeToEdge();model=ViewModelProvider(this)[MainModel::class.java];if(state==null)handleIntent(intent);setContent { AppRoot(this,model) } }
    override fun onNewIntent(i:Intent) { super.onNewIntent(i);setIntent(i);handleIntent(i) }
    override fun onResume() { super.onResume();requestHighRefresh() }
    private fun handleIntent(i:Intent) { model.incoming=i.getStringExtra("note");if(i.action==Intent.ACTION_SEND&&i.type?.startsWith("text/")==true){val text=i.getStringExtra(Intent.EXTRA_TEXT).orEmpty();val subject=i.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty();if(text.isNotBlank()||subject.isNotBlank())model.shareText=subject to text} }
    private fun requestHighRefresh() { if(android.os.Build.VERSION.SDK_INT>=23){val display=display?:return;val current=display.mode;val mode=display.supportedModes.filter { it.physicalWidth==current.physicalWidth&&it.physicalHeight==current.physicalHeight }.maxByOrNull { it.refreshRate }?:return;window.attributes=window.attributes.apply { preferredDisplayModeId=mode.modeId }} }
    override fun onPause() { super.onPause();if(::model.isInitialized)model.editor?.let { e -> lifecycleScope.launch { e.persist() } } }
    override fun onStop() { super.onStop();if(::model.isInitialized) { val e=model.editor;if(e!=null&&e.note.mode!="none"&&!e.externalOperation)lifecycleScope.launch { e.persist();if(e.saved) { model.editor=null;e.key?.fill(0) } } } }
}
@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable fun AppRoot(activity:MainActivity,model:MainModel) {
    val notes by Repo.dao.observe().collectAsState(initial=emptyList());val cats by Repo.dao.categories().collectAsState(initial=emptyList());val prefsVersion by Repo.revision.collectAsState()
    val accent=remember(prefsVersion){Repo.str("accent","#c7c7db")};val animated=remember(prefsVersion){Repo.bool("animations",true)};val speed=remember(prefsVersion){Repo.str("animationSpeed","1.0").toFloatOrNull()?.coerceIn(0.5f,2f)?:1f}
    val scope=rememberCoroutineScope();val snackbar=remember { SnackbarHostState() };var unlock by remember { mutableStateOf<Note?>(null) };var action by remember { mutableStateOf<Note?>(null) };var copyAfter by remember { mutableStateOf(false) }
    fun message(s:String) { scope.launch { snackbar.showSnackbar(s) } }
    fun copy(n:Note,c:Content=n.content()) { val clipboard=activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;val clip=ClipData.newPlainText("DoNote",c.copyText());if(n.mode!="none"&&android.os.Build.VERSION.SDK_INT>=33)clip.description.extras=android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE",true) };clipboard.setPrimaryClip(clip);message("Not kopyalandı") }
    fun open(n:Note,copyOnly:Boolean=false) { if(n.mode!="none") { unlock=n;copyAfter=copyOnly } else if(copyOnly)copy(n) else { model.editor=EditorState(n);scope.launch { Repo.dao.used(n.category) } } }
    fun remove(n:Note) { scope.launch { Repo.remove(n);if(snackbar.showSnackbar("Not silindi","Geri al",duration=SnackbarDuration.Long)==SnackbarResult.ActionPerformed)Repo.restore(n) } }
    LaunchedEffect(model.shareText) { model.shareText?.let { (subject,text) -> val previous=model.editor;if(previous!=null){previous.persist();if(!previous.saved){message(previous.savingError);return@LaunchedEffect};previous.key?.fill(0)};val html=TextUtils.htmlEncode(text).replace("\n","<br>");val body=if(text.isBlank())listOf(Block()) else listOf(Block(html=html));model.editor=EditorState(Note(payload=Content(subject,body).json()),initialContent=Content(subject,body));model.shareText=null } }
    LaunchedEffect(model.incoming) { val id=model.incoming;if(id!=null) { val n=withContext(Dispatchers.IO){Repo.dao.get(id)};if(n!=null) { val previous=model.editor;if(previous!=null){previous.persist();if(!previous.saved){message(previous.savingError);return@LaunchedEffect};previous.key?.fill(0);model.editor=null};model.incoming=null;open(n) }else model.incoming=null } }
    val e=model.editor
    SideEffect { if(e?.note?.mode!="none"&&e!=null || unlock!=null)activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    NoteTheme(accent) {
        Scaffold(snackbarHost={SnackbarHost(snackbar)},containerColor=Bg,contentWindowInsets=WindowInsets(0.dp,0.dp,0.dp,0.dp)) { insets ->
            Box(Modifier.fillMaxSize().padding(insets).imePadding().clipToBounds()) {
                AnimatedContent(targetState=if(e!=null)"editor" else model.screen,transitionSpec={ if(animated)(fadeIn(animationSpec=androidx.compose.animation.core.tween((170/speed).toInt()))+slideInHorizontally(animationSpec=androidx.compose.animation.core.tween((190/speed).toInt())) { it/16 }) togetherWith fadeOut(animationSpec=androidx.compose.animation.core.tween((140/speed).toInt())) else EnterTransition.None togetherWith ExitTransition.None },label="ekran") { screen ->
                    when(screen) {
                        "editor" -> if(e!=null)EditorScreen(e,cats,activity,onClose={scope.launch { e.persist();if(e.saved) { model.editor=null;e.key?.fill(0) }else message(e.savingError) }},onMessage=::message,onCopy={copy(e.note,e.content)})
                        "settings" -> SettingsScreen(activity,cats,onBack={model.screen="home"},onArchive={model.screen="archive"},onMessage=::message)
                        "archive" -> { BackHandler { model.screen="settings" };Column { TopAppBar(title={Text("Arşiv")},navigationIcon={IconButton(onClick={model.screen="settings"}){Icon(Icons.Outlined.ArrowBack,"Geri")}});if(notes.none { it.archived })EmptyState("Arşiv boş","Arşivlediğiniz notlar burada görünür.",Icons.Outlined.Inventory2) else LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) { items(notes.filter { it.archived },key={it.id}) { n -> NoteCard(n,cats.find{it.id==n.category},onOpen={open(n)},onCopy={open(n,true)},onHold={action=n},trailing={IconButton(onClick={scope.launch { Repo.save(n.copy(archived=false)) };message("Not arşivden çıkarıldı")}){Icon(Icons.Outlined.Unarchive,"Arşivden çıkar")}}) } } } }
                        else -> HomeScreen(notes.filter { !it.archived },cats,prefsVersion,onOpen={open(it)},onCopy={open(it,true)},onHold={action=it},onSettings={model.screen="settings"},onNew={model.editor=EditorState(Note(category=cats.find { it.id=="personal" }?.id?:cats.firstOrNull()?.id?:"other",position=(notes.maxOfOrNull { it.position }?:0L)+1L))})
                    }
                }
            }
        }
        if(action!=null) { val n=action!!;ModalBottomSheet(onDismissRequest={action=null}) { Column(Modifier.padding(horizontal=20.dp).navigationBarsPadding()) {
            Text(n.content().title.ifBlank { "Başlıksız not" },style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
            ActionRow(if(n.pinned)"Sabitlemeyi kaldır" else "Sabitle",Icons.Outlined.PushPin) { action=null;scope.launch { Repo.save(n.copy(pinned=!n.pinned)) } }
            ActionRow(if(n.archived)"Arşivden çıkar" else "Arşivle",Icons.Outlined.Inventory2) { action=null;scope.launch { Repo.save(n.copy(archived=!n.archived)) } }
            if(!n.archived)ActionRow("Sıralamayı değiştir",Icons.Outlined.DragIndicator) { action=null;model.screen="home";message("Kartın sağındaki tutamacı basılı tutup sürükleyin.") }
            if(!n.archived)Text("Sıralamak için kartın sağındaki tutamacı basılı tutup sürükleyin. Sabitlenmiş notlar kendi aralarında sıralanır.",color=Muted,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(vertical=12.dp))
            ActionRow("Sil",Icons.Outlined.DeleteOutline) { action=null;remove(n) };Spacer(Modifier.height(20.dp))
        } } }
        if(unlock!=null) UnlockDialog(activity,unlock!!,onDismiss={unlock=null},onUnlocked={c,key -> val n=unlock!!;unlock=null;if(copyAfter){copy(n,c);key.fill(0)}else {model.editor=EditorState(n,key,c);scope.launch { Repo.dao.used(n.category) }}})
    }
}
@Composable fun ActionRow(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,action:()->Unit) { ListItem(headlineContent={Text(label)},leadingContent={Icon(icon,null)},modifier=Modifier.clickable(onClick=action),colors=ListItemDefaults.colors(containerColor=Color.Transparent)) }
@Composable fun EmptyState(title:String,body:String,icon:androidx.compose.ui.graphics.vector.ImageVector) { Column(Modifier.fillMaxSize().padding(36.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.height(20.dp));Text(title,style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(10.dp));Text(body,color=Muted,style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center) } }
@OptIn(ExperimentalMaterial3Api::class,ExperimentalFoundationApi::class)
@Composable fun HomeScreen(notes:List<Note>,cats:List<Category>,prefsVersion:Int,onOpen:(Note)->Unit,onCopy:(Note)->Unit,onHold:(Note)->Unit,onSettings:()->Unit,onNew:()->Unit) {
    var query by remember { mutableStateOf("") };var selected by remember { mutableStateOf("") };var filter by remember { mutableStateOf("all") };val listState=rememberLazyListState();val scope=rememberCoroutineScope()
    val featured=remember(prefsVersion){Repo.str("featured")};val auto=remember(prefsVersion){Repo.bool("autoOrder")};val counter=remember(prefsVersion){Repo.bool("counter",true)};val filters=remember(prefsVersion){Repo.bool("filters",true)};val animations=remember(prefsVersion){Repo.bool("animations",true)}
    val ordered=cats.sortedWith(compareByDescending<Category> { it.id==featured }.thenByDescending { if(auto)it.uses else 0 }.thenBy { it.position })
    val visible=remember(notes,selected,filter,filters,query) { notes.filter { n -> (selected.isEmpty()||n.category==selected) && (!filters||filter=="all"||filter=="pinned"&&n.pinned||filter=="locked"&&n.mode!="none"||filter=="reminder"&&n.reminder.isNotBlank()) && (query.isBlank()||n.mode=="none"&&(n.content().copyText().contains(query,true))) } }
    var local by remember { mutableStateOf(visible) };var dragging by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) { if(dragging==null)local=visible }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start=22.dp,end=10.dp,top=14.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("DoNote",style=MaterialTheme.typography.headlineLarge);if(counter)Text("${notes.size} not",color=Muted,style=MaterialTheme.typography.labelSmall) };IconButton(onClick=onSettings){Icon(Icons.Outlined.Settings,"Ayarlar")} }
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=18.dp),placeholder={Text("Notlarında ara")},leadingIcon={Icon(Icons.Outlined.Search,null)},trailingIcon={if(query.isNotEmpty())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"Aramayı temizle")}},singleLine=true,shape=RoundedCornerShape(16.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Line,focusedBorderColor=MaterialTheme.colorScheme.primary))
        LazyRow(contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { item { FilterChip(selected.isEmpty(),{selected=""},label={Text("Tümü")}) };items(ordered,key={it.id}) { c -> FilterChip(selected==c.id,{selected=c.id},label={Text(c.name)},leadingIcon={Icon(noteIcon(c.icon),null,Modifier.size(17.dp),tint=hexColor(c.color))}) } }
        if(filters)LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)) { items(listOf("all" to "Hepsi","pinned" to "Sabitlenen","locked" to "Kilitli","reminder" to "Hatırlatmalı")) { (id,label)->SuggestionChip({filter=id},label={Text(label,color=if(filter==id)MaterialTheme.colorScheme.primary else Muted)},border=BorderStroke(1.dp,if(filter==id)MaterialTheme.colorScheme.primary else Line)) } }
        Box(Modifier.weight(1f)) {
            if(visible.isEmpty())EmptyState(if(notes.isEmpty())"İlk notunu oluştur" else "Not bulunamadı",if(notes.isEmpty())"Bir fikir, bir liste, bir hatırlatma.\nBaşlamak için + düğmesine dokun." else "Aramayı veya seçili filtreyi değiştirebilirsin.",Icons.Outlined.EditNote)
            LazyColumn(state=listState,contentPadding=PaddingValues(start=18.dp,end=18.dp,top=10.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                items(local,key={it.id}) { n ->
                    var delta by remember { mutableFloatStateOf(0f) };val current by rememberUpdatedState(local)
                    NoteCard(n,cats.find { it.id==n.category },onOpen={onOpen(n)},onCopy={onCopy(n)},onHold={onHold(n)},modifier=(if(animations)Modifier.animateItem()else Modifier).scale(if(dragging==n.id)1.02f else 1f).graphicsLayer { translationY=if(dragging==n.id)delta else 0f },trailing={
                        Icon(Icons.Outlined.DragIndicator,"Sıralamak için basılı tutup sürükle",tint=Muted,modifier=Modifier.size(42.dp).padding(9.dp).pointerInput(n.id,selected,query,filter) {
                            detectDragGesturesAfterLongPress(onDragStart={dragging=n.id;delta=0f},onDragEnd={dragging=null;val saved=current;scope.launch { val orderedIds=saved.map { it.id }.toSet();val slots=notes.filter { it.id in orderedIds }.map { it.position }.sorted();Repo.dao.putAll(saved.mapIndexed { index,item -> item.copy(position=slots[index]) }) }},onDragCancel={dragging=null;local=visible},onDrag={change,amount ->
                                change.consume();delta+=amount.y;val idx=current.indexOfFirst { it.id==n.id };val item=listState.layoutInfo.visibleItemsInfo.find { it.key==n.id };val threshold=(item?.size?:120)*0.6f
                                if(kotlin.math.abs(delta)>threshold) { val to=(idx+if(delta>0)1 else -1).coerceIn(0,current.lastIndex);if(to!=idx&&current[to].pinned==n.pinned) { local=current.toMutableList().apply { add(to,removeAt(idx)) };delta=0f;scope.launch { if(to>=listState.firstVisibleItemIndex+2||to<=listState.firstVisibleItemIndex)listState.scrollBy(if(amount.y>0)110f else -110f) } } }
                            })
                        })
                    })
                }
            }
            val interaction=remember { MutableInteractionSource() };val pressed by interaction.collectIsPressedAsState();val fabScale by animateFloatAsState(if(pressed&&animations)0.93f else 1f,spring(dampingRatio=0.7f,stiffness=550f),label="ekleme")
            FloatingActionButton(onClick=onNew,interactionSource=interaction,modifier=Modifier.align(Alignment.BottomEnd).padding(22.dp).scale(fabScale),containerColor=MaterialTheme.colorScheme.primary,contentColor=MaterialTheme.colorScheme.onPrimary,shape=RoundedCornerShape(20.dp)) { Icon(Icons.Outlined.Add,"Yeni not",Modifier.size(28.dp)) }
        }
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun NoteCard(n:Note,cat:Category?,onOpen:()->Unit,onCopy:()->Unit,onHold:()->Unit,modifier:Modifier=Modifier,trailing:@Composable ()->Unit={}) {
    val c=remember(n.payload,n.mode){n.content()};var now by remember { mutableLongStateOf(System.currentTimeMillis()) };LaunchedEffect(n.updated){while(true){now=System.currentTimeMillis();delay(60000)}}
    Surface(modifier.fillMaxWidth(),color=SurfaceColor,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,Line)) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).combinedClickable(onClick=onOpen,onLongClick=onHold).padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { if(n.icon.isNotBlank()) { Icon(noteIcon(n.icon),null,tint=hexColor(n.color),modifier=Modifier.size(23.dp));Spacer(Modifier.width(10.dp)) };Text(c.title.ifBlank { "Başlıksız not" },style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis);if(n.pinned)Icon(Icons.Outlined.PushPin,"Sabitlenmiş",Modifier.size(16.dp),tint=Muted);if(n.mode!="none")Icon(Icons.Outlined.Lock,"Kilitli",Modifier.size(16.dp),tint=Muted) }
            Spacer(Modifier.height(9.dp));Text(if(n.mode!="none")"İçeriği görmek için kilidi açın" else c.plain().ifBlank { "Henüz içerik yok" },color=Muted,maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(9.dp));Row(verticalAlignment=Alignment.CenterVertically) {
                if(cat!=null)Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.background(hexColor(cat.color).copy(alpha=0.1f),RoundedCornerShape(6.dp)).padding(horizontal=8.dp,vertical=4.dp)){Icon(noteIcon(cat.icon),null,Modifier.size(14.dp),tint=hexColor(cat.color));Spacer(Modifier.width(5.dp));Text(cat.name,color=hexColor(cat.color),style=MaterialTheme.typography.labelSmall)}
                Spacer(Modifier.width(8.dp));Text(relativeTime(n.updated,now),style=MaterialTheme.typography.labelSmall,color=Muted,modifier=Modifier.weight(1f));if(n.reminder.isNotBlank())Icon(Icons.Outlined.NotificationsNone,"Hatırlatma",Modifier.size(16.dp),tint=Muted);IconButton(onClick=onCopy,modifier=Modifier.size(44.dp)){Icon(Icons.Outlined.ContentCopy,"Notu kopyala",Modifier.size(19.dp))}
            }
        }
        Box(Modifier.padding(end=6.dp)){trailing()}
    } }
}
fun relativeTime(time:Long,now:Long=System.currentTimeMillis()):String { val minutes=((now-time).coerceAtLeast(0)/60000);return when {minutes<1->"Az önce";minutes<60->"$minutes dk önce";minutes<1440->"${minutes/60} saat önce";else->"${minutes/1440} gün önce"} }
@Composable fun UnlockDialog(activity:Activity,n:Note,onDismiss:()->Unit,onUnlocked:(Content,ByteArray)->Unit) {
    var pin by remember { mutableStateOf("") };var error by remember { mutableStateOf("") };var busy by remember { mutableStateOf(false) };val scope=rememberCoroutineScope()
    AlertDialog(onDismissRequest={if(!busy)onDismiss()},title={Text("Notun kilidini aç")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) { Text("Bu notun PIN'ini girin.",color=Muted);OutlinedTextField(pin,{if(it.length<=12&&it.all(Char::isDigit))pin=it},label={Text("PIN")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=KeyboardType.NumberPassword));if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}},confirmButton={TextButton(enabled=pin.length>=6&&!busy,onClick={busy=true;scope.launch {
        val wait=Repo.prefs.getLong("lockWait.${n.id}",0)-System.currentTimeMillis();if(wait>0){error="${(wait/1000)+1} saniye sonra tekrar deneyin.";busy=false;return@launch}
        val result=withContext(Dispatchers.IO){runCatching { Vault.unlock(n,pin) }}
        result.onSuccess { Repo.prefs.edit().remove("fail.${n.id}").remove("lockWait.${n.id}").apply();onUnlocked(it.first,it.second) }.onFailure { val count=Repo.prefs.getInt("fail.${n.id}",0)+1;Repo.prefs.edit().putInt("fail.${n.id}",count).putLong("lockWait.${n.id}",if(count>=5)System.currentTimeMillis()+30000 else 0).apply();error="PIN yanlış veya not verisi okunamıyor." };busy=false
    }}){Text("Kilidi aç")}},dismissButton={TextButton(enabled=!busy,onClick=onDismiss){Text("Vazgeç")}})
}
