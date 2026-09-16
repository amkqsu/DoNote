package `fun`.dogon.note

import android.app.Instrumentation
import android.app.Activity
import android.os.Bundle
import kotlinx.coroutines.runBlocking

class Verification:Instrumentation() {
    override fun onCreate(arguments:Bundle?) { super.onCreate(arguments);start() }
    override fun onStart() {
        val lines=mutableListOf<String>()
        fun test(name:String,block:()->Unit) { try{block();lines.add("PASS $name")}catch(t:Throwable){lines.add("FAIL $name: ${t.message}")} }
        Repo.init(targetContext)
        val content=Content("Test notu",listOf(Block(html="<b>Kalın</b> ve <i>italik</i>"),Block(kind="check",html="Görev",checked=true)))
        val original=Note(id="verification-${uid()}",payload=content.json())
        var encrypted=original
        test("Rich text and checklist serialization") { check(Content.parse(content.json())==content);check(content.plain().contains("☑")) }
        test("PIN encryption and decryption") { encrypted=Vault.lock(original,content,"135790").first;check(!encrypted.payload.contains("Test notu"));check(Vault.unlock(encrypted,"135790").first==content) }
        test("Wrong PIN is rejected") { check(runCatching { Vault.unlock(encrypted,"111111") }.isFailure) }
        test("Locked content is hidden") { check(encrypted.content().title=="Kilitli not");check(encrypted.content().blocks.isEmpty()) }
        test("Database save and delete") { runBlocking { Repo.save(encrypted);check(Repo.dao.get(encrypted.id)?.payload==encrypted.payload);Repo.remove(encrypted);check(Repo.dao.get(encrypted.id)==null) } }
        test("Unsafe URL is rejected") { check(!safeUrl("javascript:alert(1)"));check(safeUrl("https://example.com")) }
        val report=lines.joinToString("\n")
        finish(if(lines.any{it.startsWith("FAIL")})Activity.RESULT_CANCELED else Activity.RESULT_OK,Bundle().apply{putString("stream",report)})
    }
}
