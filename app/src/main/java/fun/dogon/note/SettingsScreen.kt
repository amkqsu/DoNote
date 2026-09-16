package `fun`.dogon.note

import android.app.Activity
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
    val revision by Repo.revision.collectAsState();val scope=rememberCoroutineScope();var editCategory by remember { mutableStateOf<Category?>(null) };var deleteCategory by remember { mutableStateOf<Category?>(null) };var featuredMenu by remember { mutableStateOf(false) };var busy by remember { mutableStateOf(false) };var imported by remember { mutableStateOf<String?>(null) };var summary by remember { mutableStateOf("") };var exportConfirm by remember { mutableStateOf(false) };var colorDialog by remember { mutableStateOf(false) };var lockDialog by remember { mutableStateOf(false) }
    val featured=remember(revision){Repo.str("featured")}
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) { busy=true;scope.launch { val result=withContext(Dispatchers.IO){runCatching { val json=Repo.export();activity.contentResolver.openOutputStream(uri,"wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }?:error("Dosya açılamadı") }};onMessage(if(result.isSuccess)"JSON yedeği kaydedildi" else "Yedek kaydedilemedi: ${result.exceptionOrNull()?.localizedMessage}");busy=false } } }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) { busy=true;scope.launch {
        val result=withContext(Dispatchers.IO){runCatching { val bytes=activity.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(64*1024*1024) }?:error("Dosya okunamadı");val raw=bytes.toString(Charsets.UTF_8);val backup=Backup.parse(raw);raw to backup }}
        result.onSuccess { (raw,backup)->imported=raw;summary="${backup.notes.size} not ve ${backup.categories.size} kategori bulundu. Mevcut notlar korunur; aynı kimlikli notlar atlanır. Tercihler yedekten alınır. Kilitli notların PIN'i değişmez." }.onFailure { onMessage("İçe aktarılamadı: ${it.localizedMessage?:"Geçersiz dosya"}") };busy=false
    } } }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title={Text("Ayarlar")},navigationIcon={IconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"Geri")}})
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item { Panel { Text("Kategoriler",style=MaterialTheme.typography.titleMedium);cats.forEachIndexed { index,c->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Icon(noteIcon(c.icon),null,Modifier.size(20.dp),tint=hexColor(c.color));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f).clickable{editCategory=c}){Text(c.name);if(c.id==featured)Text("Öne çıkan",style=MaterialTheme.typography.labelSmall,color=Muted)}
                    IconButton(enabled=index>0,onClick={scope.launch { Repo.db.withTransaction { Repo.dao.cat(c.copy(position=cats[index-1].position));Repo.dao.cat(cats[index-1].copy(position=c.position)) } }},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.KeyboardArrowUp,"Kategoriyi yukarı taşı")}
                    IconButton(onClick={editCategory=c},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.Edit,"Kategoriyi düzenle",Modifier.size(19.dp))}
                    if(c.id!="other")IconButton(onClick={deleteCategory=c},modifier=Modifier.size(36.dp)){Icon(Icons.Outlined.DeleteOutline,"Kategoriyi sil",Modifier.size(19.dp))}
                }
            };OutlinedButton(enabled=cats.size<200,onClick={editCategory=Category(name="",position=(cats.maxOfOrNull {it.position}?:0)+1,icon="note")},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Add,null);Text("Yeni kategori")}
                Box { OutlinedButton(onClick={featuredMenu=true}){Text("Öne çıkan: ${cats.find{it.id==featured}?.name?:"Yok"}");Icon(Icons.Outlined.ExpandMore,null)};DropdownMenu(featuredMenu,{featuredMenu=false}) { DropdownMenuItem(text={Text("Yok")},onClick={Repo.setting("featured","");featuredMenu=false});cats.forEach{c->DropdownMenuItem(text={Text(c.name)},leadingIcon={Icon(noteIcon(c.icon),null,tint=hexColor(c.color))},onClick={Repo.setting("featured",c.id);featuredMenu=false})} } }
                SettingToggle("Kullanıma göre sırala","autoOrder",false,revision)
            } }
            item { Panel { Text("Görünüm",style=MaterialTheme.typography.titleMedium);SettingToggle("Not sayacı","counter",true,revision);SettingToggle("Hızlı filtreler","filters",true,revision);SettingToggle("Hafif animasyonlar","animations",true,revision);val current=Repo.str("animationSpeed","1.0").toFloatOrNull()?.coerceIn(0.5f,2f)?:1f;Text("Geçiş hızı · ${String.format("%.1fx",current)}");Slider(current,{Repo.setting("animationSpeed",String.format(java.util.Locale.US,"%.2f",it))},valueRange=0.5f..2f,steps=5);OutlinedButton(onClick={colorDialog=true}){Text("Vurgu rengini seç")} } }
            item { Panel { Text("Kilit",style=MaterialTheme.typography.titleMedium);ActionRow(if(DefaultLock.get()==null)"Varsayılan PIN ayarla" else "Varsayılan PIN'i değiştir",Icons.Outlined.Lock){lockDialog=true};if(DefaultLock.get()!=null)ActionRow("Varsayılan PIN'i kaldır",Icons.Outlined.LockOpen){scope.launch { withContext(Dispatchers.IO){DefaultLock.clear()};onMessage("Varsayılan PIN kaldırıldı") }} } }
            item { Panel { Text("Notlar ve yedekleme",style=MaterialTheme.typography.titleMedium);ActionRow("Arşiv",Icons.Outlined.Inventory2,onArchive);ActionRow("JSON dışa aktar",Icons.Outlined.UploadFile){if(!busy)exportConfirm=true};ActionRow("JSON içe aktar",Icons.Outlined.Download){if(!busy)importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))} } }
        }
    }
    if(editCategory!=null) { val c=editCategory!!;CategoryDialog(c,cats,{editCategory=null}){new->scope.launch { Repo.dao.cat(new) };editCategory=null} }
    if(deleteCategory!=null) { val c=deleteCategory!!;AlertDialog(onDismissRequest={deleteCategory=null},title={Text("Kategoriyi sil?")},text={Text("${c.name} kategorisindeki notlar ${cats.find { it.id=="other" }?.name?:"Diğer"} kategorisine taşınacak. Notlar silinmez.")},confirmButton={TextButton(onClick={scope.launch { Repo.deleteCategory(c);if(featured==c.id)Repo.setting("featured","") };deleteCategory=null}){Text("Kategoriyi sil")}},dismissButton={TextButton(onClick={deleteCategory=null}){Text("Vazgeç")}}) }
    if(imported!=null)AlertDialog(onDismissRequest={if(!busy)imported=null},title={Text("Yedeği içe aktar")},text={Text(summary)},confirmButton={TextButton(enabled=!busy,onClick={val raw=imported!!;busy=true;scope.launch { val result=withContext(Dispatchers.IO){runCatching { Repo.import(raw) }};result.onSuccess{onMessage("${it.first} not eklendi, ${it.second} mevcut not atlandı")}.onFailure{onMessage("İçe aktarma başarısız: ${it.localizedMessage}")};busy=false;imported=null }}){Text("İçe aktar")}},dismissButton={TextButton(enabled=!busy,onClick={imported=null}){Text("Vazgeç")}})
    if(exportConfirm)AlertDialog(onDismissRequest={exportConfirm=false},title={Text("JSON yedeği oluştur")},text={Text("Kilitli notlar şifreli kalır. Diğer notlar ve resimler yedek dosyasını açan kişi tarafından okunabilir.")},confirmButton={TextButton(onClick={exportConfirm=false;exporter.launch("DoNote-${LocalDate.now()}.json")}){Text("Dosyayı kaydet")}},dismissButton={TextButton(onClick={exportConfirm=false}){Text("Vazgeç")}})
    if(colorDialog) { var color by remember {mutableStateOf(Repo.str("accent","#c7c7db"))};AlertDialog(onDismissRequest={colorDialog=false},title={Text("Vurgu rengi")},text={ColorPicker(color){color=it}},confirmButton={TextButton(onClick={Repo.setting("accent",color);colorDialog=false}){Text("Uygula")}},dismissButton={TextButton(onClick={Repo.setting("accent","#c7c7db");colorDialog=false}){Text("Varsayılan")}}) }
    if(lockDialog)DefaultPinDialog({lockDialog=false}){onMessage(it);lockDialog=false}
}
@Composable fun SettingToggle(title:String,key:String,default:Boolean,revision:Int) { val value=remember(revision){Repo.bool(key,default)};Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(title,Modifier.weight(1f));Switch(value,{Repo.setting(key,it)}) } }
@Composable fun CategoryDialog(c:Category,cats:List<Category>,dismiss:()->Unit,save:(Category)->Unit) { var name by remember {mutableStateOf(c.name)};var color by remember {mutableStateOf(c.color)};var icon by remember {mutableStateOf(c.icon)};val duplicate=cats.any { it.id!=c.id&&it.name.equals(name.trim(),true) };AlertDialog(onDismissRequest=dismiss,title={Text(if(c.name.isBlank())"Yeni kategori" else "Kategoriyi düzenle")},text={Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(name,{if(it.length<=60)name=it},label={Text("Kategori adı")},singleLine=true,isError=duplicate);if(duplicate)Text("Bu isimde kategori var.",color=MaterialTheme.colorScheme.error);Spacer(Modifier.height(14.dp));Text("Kategori simgesi");iconKeys.chunked(5).forEach { row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.Start)){row.forEach { key->IconButton(onClick={icon=key},modifier=Modifier.background(if(icon==key)Raised else Color.Transparent,RoundedCornerShape(10.dp))){Icon(noteIcon(key),iconLabels.getOrElse(iconKeys.indexOf(key)){key},tint=hexColor(color))}}} };Spacer(Modifier.height(14.dp));Text("Kategori rengi");ColorPicker(color){color=it} }},confirmButton={TextButton(enabled=name.isNotBlank()&&!duplicate,onClick={save(c.copy(name=name.trim(),color=color,icon=icon))}){Text("Kaydet")}},dismissButton={TextButton(onClick=dismiss){Text("Vazgeç")}}) }
@Composable fun DefaultPinDialog(dismiss:()->Unit,done:(String)->Unit) { var pin by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();AlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text("Varsayılan kilit PIN'i")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){OutlinedTextField(pin,{if(it.length<=12&&it.all(Char::isDigit))pin=it},label={Text("PIN")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword),singleLine=true);OutlinedTextField(confirm,{if(it.length<=12&&it.all(Char::isDigit))confirm=it},label={Text("PIN tekrar")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.NumberPassword),singleLine=true);if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error);if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}},confirmButton={TextButton(enabled=!busy&&pin.length>=6&&pin==confirm,onClick={busy=true;scope.launch{runCatching{withContext(Dispatchers.IO){DefaultLock.change(pin)}}.onSuccess{done("Varsayılan PIN kaydedildi")}.onFailure{error=it.localizedMessage?:"PIN kaydedilemedi";busy=false}}}){Text("Kaydet")}},dismissButton={TextButton(enabled=!busy,onClick=dismiss){Text("Vazgeç")}}) }
fun java.io.InputStream.readBytesLimited(max:Int):ByteArray { val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val count=read(buffer);if(count<0)break;require(out.size()+count<=max){"Dosya 64 MB sınırını aşıyor."};out.write(buffer,0,count)};return out.toByteArray() }
