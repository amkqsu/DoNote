package `fun`.dogon.note

import android.app.Application
import android.content.Context
import android.text.Html
import androidx.room.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun uid() = UUID.randomUUID().toString()
fun validColor(s: String) = Regex("#[0-9a-fA-F]{6}").matches(s)
data class Block(val id: String = uid(), val kind: String = "text", val html: String = "", val checked: Boolean = false, val image: String = "") {
    fun json() = JSONObject().put("id",id).put("kind",kind).put("html",html).put("checked",checked).put("image",image)
    companion object { fun parse(j: JSONObject) = Block(j.optString("id",uid()),j.optString("kind","text"),j.optString("html"),j.optBoolean("checked"),j.optString("image")) }
}
data class Content(val title: String = "", val blocks: List<Block> = listOf(Block())) {
    fun json() = JSONObject().put("title",title).put("blocks",JSONArray(blocks.map { it.json() })).toString()
    fun plain() = blocks.joinToString("\n") { if(it.kind == "image") "[Resim]" else (if(it.kind == "check") (if(it.checked) "☑ " else "☐ ") else if(it.kind == "bullet") "• " else if(it.kind == "quote") "❝ " else "") + Html.fromHtml(it.html,Html.FROM_HTML_MODE_COMPACT).toString().trim() }
    fun copyText() = listOf(title,plain()).filter { it.isNotBlank() }.joinToString("\n\n")
    companion object { fun parse(s: String): Content { val j=JSONObject(s);val a=j.getJSONArray("blocks");return Content(j.optString("title"),List(a.length()) { Block.parse(a.getJSONObject(it)) }) } }
}
@Entity(tableName="notes")
data class Note(@PrimaryKey val id: String = uid(), val payload: String = Content().json(), val category: String = "personal", val icon: String = "note", val color: String = "#c7c7db", val created: Long = System.currentTimeMillis(), val updated: Long = System.currentTimeMillis(), val position: Long = System.currentTimeMillis(), val pinned: Boolean = false, val archived: Boolean = false, val mode: String = "none", val salt: String = "", val wrapped: String = "", val bio: String = "", val reminder: String = "") {
    fun content() = if(mode == "none") runCatching { Content.parse(payload) }.getOrDefault(Content()) else Content("Kilitli not", emptyList())
    fun json() = JSONObject().put("id",id).put("payload",payload).put("category",category).put("icon",icon).put("color",color).put("created",created).put("updated",updated).put("position",position).put("pinned",pinned).put("archived",archived).put("mode",if(mode=="none") "none" else "pin").put("salt",salt).put("wrapped",wrapped).put("reminder",reminder)
}
@Entity(tableName="categories")
data class Category(@PrimaryKey val id: String = uid(), val name: String, val color: String = "#c7c7db", val position: Int = 0, val uses: Int = 0) {
    fun json() = JSONObject().put("id",id).put("name",name).put("color",color).put("position",position).put("uses",uses)
}
@Dao interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY pinned DESC, position ASC") fun observe(): Flow<List<Note>>
    @Query("SELECT * FROM notes ORDER BY pinned DESC, position ASC") suspend fun all(): List<Note>
    @Query("SELECT * FROM notes WHERE id = :id") suspend fun get(id: String): Note?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(n: Note)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putAll(n: List<Note>)
    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: String)
    @Query("SELECT * FROM categories ORDER BY position") fun categories(): Flow<List<Category>>
    @Query("SELECT * FROM categories ORDER BY position") suspend fun cats(): List<Category>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun cat(c: Category)
    @Query("UPDATE categories SET uses = uses + 1 WHERE id = :id") suspend fun used(id: String)
    @Query("UPDATE notes SET category = :replacement WHERE category = :id") suspend fun reassign(id: String,replacement: String)
    @Query("DELETE FROM categories WHERE id = :id") suspend fun deleteCat(id: String)
}
@Database(entities=[Note::class,Category::class],version=1,exportSchema=false)
abstract class NoteDb: RoomDatabase() { abstract fun dao(): NoteDao }
class NoteApp: Application() { override fun onCreate() { super.onCreate();Repo.init(this) } }
object Repo {
    lateinit var db: NoteDb
    lateinit var context: Context
    private val crashGuard=CoroutineExceptionHandler { _,e -> android.util.Log.e("DoNote","Arka plan görevi hata verdi, uygulama kapatılmadı",e) }
    val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO+crashGuard)
    val dao get()=db.dao()
    val prefs get()=context.getSharedPreferences("preferences",Context.MODE_PRIVATE)
    val revision=MutableStateFlow(0)
    fun init(c: Context) { if(::db.isInitialized)return;context=c.applicationContext;db=Room.databaseBuilder(context,NoteDb::class.java,"donote.db").setJournalMode(androidx.room.RoomDatabase.JournalMode.TRUNCATE).addCallback(object:androidx.room.RoomDatabase.Callback(){ override fun onOpen(db:androidx.sqlite.db.SupportSQLiteDatabase){db.execSQL("PRAGMA secure_delete=ON")} }).build();scope.launch { runCatching { if(dao.cats().isEmpty()) defaults().forEach { dao.cat(it) };ReminderEngine.restore(context) }.onFailure { android.util.Log.e("DoNote","Başlangıç görevi hata verdi",it) } } }
    fun defaults()=listOf("work" to "İş","ideas" to "Fikir","personal" to "Kişisel","shopping" to "Alışveriş","health" to "Sağlık","education" to "Eğitim","other" to "Diğer").mapIndexed { i,p -> Category(p.first,p.second,palette[i],i) }
    fun bool(k:String,default:Boolean=false)=prefs.getBoolean(k,default)
    fun str(k:String,default:String="")=prefs.getString(k,default)?:default
    fun setting(k:String,v:Boolean) { prefs.edit().putBoolean(k,v).apply();revision.value++ }
    fun setting(k:String,v:String) { prefs.edit().putString(k,v).apply();revision.value++ }
    suspend fun save(n:Note) { db.withTransaction { if(dao.get(n.id)==null)dao.used(n.category);dao.put(n) };ReminderEngine.schedule(context,n);NoteWidget.refresh(context) }
    suspend fun remove(n:Note) { dao.delete(n.id);ReminderEngine.cancel(context,n.id);NoteWidget.refresh(context) }
    suspend fun restore(n:Note) { save(n) }
    suspend fun deleteCategory(c:Category) { db.withTransaction { dao.reassign(c.id,"other");dao.deleteCat(c.id) } }
    suspend fun export():String = db.withTransaction { JSONObject().put("format","DoNote").put("schema",1).put("version","4.00").put("notes",JSONArray(dao.all().map { it.json() })).put("categories",JSONArray(dao.cats().map { it.json() })).put("settings",JSONObject().put("autoOrder",bool("autoOrder")).put("featured",str("featured")).put("counter",bool("counter",true)).put("filters",bool("filters",true)).put("animations",bool("animations",true)).put("accent",str("accent","#c7c7db"))).toString(2) }
    suspend fun import(raw:String):Pair<Int,Int> {
        val backup=Backup.parse(raw)
        var added=0;var skipped=0
        db.withTransaction {
            val existing=dao.all().map { it.id }.toSet();val cats=dao.cats().toMutableList();val mapping=mutableMapOf<String,String>()
            backup.categories.forEach { incoming ->
                val match=cats.find { it.id==incoming.id && it.name==incoming.name } ?: cats.find { it.name.equals(incoming.name,true) }
                if(match!=null) mapping[incoming.id]=match.id else {
                    val new=incoming.copy(id=if(cats.any { it.id==incoming.id }) uid() else incoming.id,position=cats.size)
                    dao.cat(new);cats.add(new);mapping[incoming.id]=new.id
                }
            }
            backup.notes.forEach { n -> if(n.id in existing) skipped++ else { dao.put(n.copy(category=mapping[n.category]?:"other",bio="",mode=if(n.mode=="none") "none" else "pin"));added++ } }
        }
        val s=backup.settings
        listOf("autoOrder","counter","filters","animations").forEach { if(s.has(it)) setting(it,s.getBoolean(it)) }
        if(validColor(s.optString("accent")))setting("accent",s.getString("accent"))
        val featured=s.optString("featured");if(dao.cats().any { it.id==featured })setting("featured",featured)
        ReminderEngine.restore(context);NoteWidget.refresh(context)
        return added to skipped
    }
}
data class Backup(val notes:List<Note>,val categories:List<Category>,val settings:JSONObject) {
    companion object {
        fun parse(raw:String):Backup {
            require(raw.toByteArray().size<=64*1024*1024) { "Yedek en fazla 64 MB olabilir." }
            val j=JSONObject(raw);require(j.optString("format")=="DoNote" && j.optInt("schema")==1) { "Geçerli bir DoNote JSON yedeği seçin." }
            val a=j.getJSONArray("notes");val c=j.getJSONArray("categories");require(a.length()<=5000 && c.length() in 1..200) { "Yedek sınırı: 5000 not ve 200 kategori." }
            val ids=mutableSetOf<String>()
            val cats=List(c.length()) { i -> val x=c.getJSONObject(i);val id=x.getString("id");require(id.length in 1..80 && ids.add(id));val name=x.getString("name");val color=x.getString("color");require(name.isNotBlank()&&name.length<=60&&validColor(color));Category(id,name,color,i,x.optInt("uses").coerceIn(0,1000000)) }
            ids.clear()
            val notes=List(a.length()) { i ->
                val x=a.getJSONObject(i);val id=x.getString("id");require(Regex("[a-zA-Z0-9-]{1,80}").matches(id)&&ids.add(id)) { "Tekrarlanan veya geçersiz not kimliği." }
                val mode=x.getString("mode");require(mode in listOf("none","pin"));val payload=x.getString("payload");require(payload.length<=24*1024*1024)
                if(mode=="none") validateContent(Content.parse(payload)) else { require(Vault.bytes(x.getString("salt")).size==16 && Vault.bytes(x.getString("wrapped")).size==60 && Vault.bytes(payload).size>=28) }
                val icon=x.optString("icon");require(icon in iconKeys || icon.isEmpty());val color=x.optString("color");require(validColor(color))
                val reminder=x.optString("reminder");if(reminder.isNotBlank()) Reminder.parse(reminder)
                Note(id,payload,x.optString("category","other"),icon,color,x.optLong("created",System.currentTimeMillis()),x.optLong("updated",System.currentTimeMillis()),x.optLong("position",i.toLong()),x.optBoolean("pinned"),x.optBoolean("archived"),mode,x.optString("salt"),x.optString("wrapped"),"",reminder)
            }
            val s=j.optJSONObject("settings")?:JSONObject();listOf("autoOrder","counter","filters","animations").forEach { if(s.has(it))require(s.get(it) is Boolean) }
            return Backup(notes,cats,s)
        }
        fun validateContent(c:Content) { require(c.title.length<=500 && c.blocks.size<=500);c.blocks.forEach { require(it.kind in listOf("text","check","image","h1","h2","bullet","quote"));require(it.html.length<=1000000);if(it.kind=="image") { require(it.image.length<=4*1024*1024);val bytes=android.util.Base64.decode(it.image,android.util.Base64.DEFAULT);val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true };android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,options);require(options.outWidth in 1..2048 && options.outHeight in 1..2048) { "Yedekte geçersiz veya aşırı büyük resim." } } } }
    }
}
