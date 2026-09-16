package `fun`.dogon.note

import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import androidx.room.withTransaction
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(activity:Activity,cats:List<Category>,onBack:()->Unit,onArchive:()->Unit,onMessage:(String)->Unit) {
    BackHandler(onBack=onBack)
    val revision by Repo.revision.collectAsState();val scope=rememberCoroutineScope();var editCategory by remember { mutableStateOf<Category?>(null) };var deleteCategory by remember { mutableStateOf<Category?>(null) };var featuredMenu by remember { mutableStateOf(false) };var busy by remember { mutableStateOf(false) };var imported by remember { mutableStateOf<String?>(null) };var summary by remember { mutableStateOf("") };var exportConfirm by remember { mutableStateOf(false) };var colorDialog by remember { mutableStateOf(false) }
    val featured=remember(revision){Repo.str("featured")}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) { busy=true;scope.launch { val result=withContext(Dispatchers.IO){runCatching { val json=Repo.export();activity.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }?:error("Dosya açılamadı") }};onMessage(if(result.isSuccess)"JSON yedeği kaydedildi" else "Yedek kaydedilemedi: ${result.exceptionOrNull()?.localizedMessage}");busy=false } } }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { busy=true;scope.launch {
        val result=withContext(Dispatchers.IO){runCatching { val bytes=activity.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(64*1024*1024) }?:error("Dosya okunamadı");val raw=bytes.toString(Charsets.UTF_8);val backup=Backup.parse(raw);raw to backup }}
        result.onSuccess { (raw,backup)->imported=raw;summary="${backup.notes.size} not ve ${backup.categories.size} kategori bulundu. Mevcut notlar korunur; aynı kimlikli notlar atlanır. Tercihler yedekten alınır. Kilitli notların PIN'i değişmez; biyometriyi bu cihazda yeniden etkinleştirebilirsiniz." }.onFailure { onMessage("İçe aktarılamadı: ${it.localizedMessage?:"Geçersiz dosya"}") };busy=false
    } } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title={Text("Ayarlar")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Geri")}})
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item { Panel { Text("Kategoriler",style=MaterialTheme.typography.titleMedium);cats.forEachIndexed { index,c->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(11.dp).background(hexColor(c.color),RoundedCornerShape(4.dp)));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f).clickable{editCategory=c}){Text(c.name);if(c.id==featured)Text("Öne çıkan",style=MaterialTheme.typography.labelSmall,color=Muted)}
                    IconButton(enabled=index>0,onClick={scope.launch { Repo.db.withTransaction { Repo.dao.cat(c.copy(position=cats[index-1].position));Repo.dao.cat(cats[index-1].copy(position=c.position)) } }},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.KeyboardArrowUp,"Kategoriyi yukarı taşı")}
                    IconButton(onClick={editCategory=c},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Edit,"Kategoriyi düzenle",Modifier.size(19.dp))}
                    if(c.id!="other")IconButton(onClick={deleteCategory=c},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.DeleteOutline,"Kategoriyi sil",Modifier.size(19.dp))}
                }
            };OutlinedButton(enabled=cats.size<200,onClick={editCategory=Category(name="",position=(cats.maxOfOrNull {it.position}?:0)+1)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text("Yeni kategori")}
                Box { OutlinedButton(onClick={featuredMenu=true}){Text("Öne çıkan: ${cats.find{it.id==featured}?.name?:"Yok"}");Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(featuredMenu,{featuredMenu=false}) { DropdownMenuItem(text={Text("Yok")},onClick={Repo.setting("featured","");featuredMenu=false});cats.forEach{c->DropdownMenuItem(text={Text(c.name)},onClick={Repo.setting("featured",c.id);featuredMenu=false})} } }
                SettingToggle("Kullanıma göre sırala","Öne çıkan kategori ilk kalır; diğerleri açılma ve oluşturulma sıklığına göre sıralanır.","autoOrder",false,revision)
            } }
            item { Panel { Text("Görünüm",style=MaterialTheme.typography.titleMedium);SettingToggle("Not sayacı","Ana ekranda not sayısını göster.","counter",true,revision);SettingToggle("Hızlı filtreler","Sabitlenen, kilitli ve hatırlatmalı notlar.","filters",true,revision);SettingToggle("Hafif animasyonlar","Ekran ve liste geçişleri.","animations",true,revision);OutlinedButton(onClick={colorDialog=true}){Text("Vurgu rengini seç")};Text("Varsayılan görünüm DoFit temasıyla aynıdır.",color=Muted,style=MaterialTheme.typography.bodySmall) } }
            item { Panel { Text("Notlar ve yedekleme",style=MaterialTheme.typography.titleMedium);ActionRow("Arşiv",Icons.Outlined.Inventory2,onArchive);ActionRow("JSON dışa aktar",Icons.Outlined.UploadFile){if(!busy)exportConfirm=true};ActionRow("JSON içe aktar",Icons.Outlined.Download){if(!busy)importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))};Text("Notlar, resimler, kategoriler ve tercihler yedeklenir. Hazır ikon kütüphanesi uygulamada kalır; notun ikon seçimi korunur.",style=MaterialTheme.typography.bodySmall,color=Muted) } }
            item { Panel { Text("Hatırlatma izinleri",style=MaterialTheme.typography.titleMedium);ActionRow("Bildirim ayarları",Icons.Outlined.NotificationsNone){runCatching { activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)) }};if(Build.VERSION.SDK_INT>=31)ActionRow("Alarm ve hatırlatma izni",Icons.Outlined.Alarm){runCatching { activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${activity.packageName}"))) }};Text("Uygulamayı zorla durdurursanız hatırlatmalar, DoNote'u yeniden açana kadar çalışmaz. Arşivlenmiş notlarda hatırlatmalar duraklar.",color=Muted,style=MaterialTheme.typography.bodySmall) } }
            item { Panel { Text("Gizlilik",style=MaterialTheme.typography.titleMedium);Text("Veriler yalnızca bu telefonda saklanır. DoNote internet izni istemez. Kilitli notların başlığı, metni ve resimleri şifrelenir; bildirimlerde ve widget'ta içerik gizlenir. Kilitli notlar uygulama arka plana geçtiğinde yeniden kilitlenir.",style=MaterialTheme.typography.bodyMedium,color=Muted);Text("PIN'inizi ve JSON yedeklerinizi güvenle saklayın. Uygulamayı kaldırmak yerel verileri siler.",style=MaterialTheme.typography.bodySmall,color=Muted) } }
            item { Column(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text("DoNote",style=MaterialTheme.typography.titleMedium);Text("Sürüm 4.00 · fun.dogon.note",color=Muted,style=MaterialTheme.typography.labelSmall) } }
        }
    }
    if(editCategory!=null) { val c=editCategory!!;CategoryDialog(c,cats,{editCategory=null}){new->scope.launch { Repo.dao.cat(new) };editCategory=null} }
    if(deleteCategory!=null) { val c=deleteCategory!!;AlertDialog(onDismissRequest={deleteCategory=null},title={Text("Kategoriyi sil?")},text={Text("${c.name} kategorisindeki notlar ${cats.find { it.id=="other" }?.name?:"Diğer"} kategorisine taşınacak. Notlar silinmez.")},confirmButton={TextButton(onClick={scope.launch { Repo.deleteCategory(c);if(featured==c.id)Repo.setting("featured","") };deleteCategory=null}){Text("Kategoriyi sil")}},dismissButton={TextButton(onClick={deleteCategory=null}){Text("Vazgeç")}}) }
    if(imported!=null)AlertDialog(onDismissRequest={if(!busy)imported=null},title={Text("Yedeği içe aktar")},text={Text(summary)},confirmButton={TextButton(enabled=!busy,onClick={val raw=imported!!;busy=true;scope.launch { val result=withContext(Dispatchers.IO){runCatching { Repo.import(raw) }};result.onSuccess{onMessage("${it.first} not eklendi, ${it.second} mevcut not atlandı")}.onFailure{onMessage("İçe aktarma başarısız: ${it.localizedMessage}")};busy=false;imported=null }}){Text("İçe aktar")}},dismissButton={TextButton(enabled=!busy,onClick={imported=null}){Text("Vazgeç")}})
    if(exportConfirm)AlertDialog(onDismissRequest={exportConfirm=false},title={Text("JSON yedeği oluştur")},text={Text("Kilitli notlar şifreli kalır. Diğer notlar ve resimler yedek dosyasını açan kişi tarafından okunabilir. Dosyayı güvenli bir yere kaydedin.")},confirmButton={TextButton(onClick={exportConfirm=false;exporter.launch("DoNote-${LocalDate.now()}.json")}){Text("Dosyayı kaydet")}},dismissButton={TextButton(onClick={exportConfirm=false}){Text("Vazgeç")}})
    if(colorDialog) { var color by remember {mutableStateOf(Repo.str("accent","#c7c7db"))};AlertDialog(onDismissRequest={colorDialog=false},title={Text("Vurgu rengi")},text={ColorPicker(color){color=it}},confirmButton={TextButton(onClick={Repo.setting("accent",color);colorDialog=false}){Text("Uygula")}},dismissButton={TextButton(onClick={Repo.setting("accent","#c7c7db");colorDialog=false}){Text("DoFit varsayılanı")}}) }
}
@Composable fun SettingToggle(title:String,description:String,key:String,default:Boolean,revision:Int) { val value=remember(revision){Repo.bool(key,default)};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title);Text(description,style=MaterialTheme.typography.bodySmall,color=Muted) };Switch(value,{Repo.setting(key,it)}) } }
@Composable fun CategoryDialog(c:Category,cats:List<Category>,dismiss:()->Unit,save:(Category)->Unit) { var name by remember {mutableStateOf(c.name)};var color by remember {mutableStateOf(c.color)};val duplicate=cats.any { it.id!=c.id&&it.name.equals(name.trim(),true) };AlertDialog(onDismissRequest=dismiss,title={Text(if(c.name.isBlank())"Yeni kategori" else "Kategoriyi düzenle")},text={Column { OutlinedTextField(name,{if(it.length<=60)name=it},label={Text("Kategori adı")},singleLine=true,isError=duplicate);if(duplicate)Text("Bu isimde kategori var.",color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(14.dp));Text("Kategori rengi");ColorPicker(color){color=it} }},confirmButton={TextButton(enabled=name.isNotBlank()&&!duplicate,onClick={save(c.copy(name=name.trim(),color=color))}){Text("Kaydet")}},dismissButton={TextButton(onClick=dismiss){Text("Vazgeç")}}) }
fun java.io.InputStream.readBytesLimited(max:Int):ByteArray { val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=read(buffer);if(count<0)break;require(out.size()+count<=max){"Dosya 64 MB sınırını aşıyor."};out.write(buffer,0,count)};return out.toByteArray() }
