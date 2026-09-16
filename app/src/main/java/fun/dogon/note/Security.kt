package `fun`.dogon.note

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.room.withTransaction
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Vault {
    fun random(size:Int)=ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun b64(b:ByteArray)=Base64.encodeToString(b,Base64.NO_WRAP)
    fun bytes(s:String)=Base64.decode(s,Base64.DEFAULT)
    fun derive(pin:String,salt:ByteArray):ByteArray { val spec=PBEKeySpec(pin.toCharArray(),salt,210000,256);return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() } }
    fun encrypt(data:ByteArray,key:ByteArray):String { val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"));return b64(c.iv+c.doFinal(data)) }
    fun decrypt(data:String,key:ByteArray):ByteArray { val d=bytes(data);require(d.size>=28);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,d.copyOfRange(0,12)));return c.doFinal(d.copyOfRange(12,d.size)) }
    fun lock(n:Note,c:Content,pin:String,mode:String="pin"):Pair<Note,ByteArray> {
        require(Regex("[0-9]{6,12}").matches(pin)) { "PIN 6–12 rakam olmalı." }
        val salt=random(16);val key=random(32);val derived=derive(pin,salt)
        val result=n.copy(mode=mode,salt=b64(salt),wrapped=encrypt(key,derived),bio="",payload=encrypt(c.json().toByteArray(),key))
        derived.fill(0);return result to key
    }
    fun unlock(n:Note,pin:String):Pair<Content,ByteArray> { val derived=derive(pin,bytes(n.salt));val key=try { decrypt(n.wrapped,derived) } finally { derived.fill(0) };return Content.parse(String(decrypt(n.payload,key))) to key }
}

object DefaultLock {
    private const val alias="donote.default.lock"
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if(!ks.containsAlias(alias)) {
            val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
            generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generator.generateKey()
        }
        return ks.getKey(alias,null) as SecretKey
    }
    private fun store(pin:String) { val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());Repo.setting("defaultLock",Vault.b64(c.iv+c.doFinal(pin.toByteArray()))) }
    suspend fun change(pin:String) {
        require(Regex("[0-9]{6,12}").matches(pin)) { "PIN 6–12 rakam olmalı." }
        val old=get()
        if(old!=null&&old!=pin) Repo.db.withTransaction {
            Repo.dao.all().filter { it.mode=="default" }.forEach { n ->
                val (content,oldKey)=Vault.unlock(n,old);oldKey.fill(0)
                val base=n.copy(mode="none",salt="",wrapped="",bio="",payload=content.json())
                val (locked,newKey)=Vault.lock(base,content,pin,"default");newKey.fill(0);Repo.dao.put(locked)
            }
        }
        store(pin)
        NoteWidget.refresh(Repo.context)
    }
    fun get():String? {
        val raw=Repo.str("defaultLock");if(raw.isBlank())return null
        return runCatching { val d=Vault.bytes(raw);val c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,d.copyOfRange(0,12)));String(c.doFinal(d.copyOfRange(12,d.size))) }.getOrNull()
    }
    suspend fun clear() { Repo.db.withTransaction { Repo.dao.all().filter { it.mode=="default" }.forEach { Repo.dao.put(it.copy(mode="pin")) } };Repo.setting("defaultLock","");NoteWidget.refresh(Repo.context) }
}
