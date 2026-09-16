package `fun`.dogon.note

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
    fun lock(n:Note,c:Content,pin:String):Pair<Note,ByteArray> {
        require(Regex("[0-9]{6,12}").matches(pin)) { "PIN 6–12 rakam olmalı." }
        val salt=random(16);val key=random(32);val derived=derive(pin,salt)
        val result=n.copy(mode="pin",salt=b64(salt),wrapped=encrypt(key,derived),bio="",payload=encrypt(c.json().toByteArray(),key))
        derived.fill(0);return result to key
    }
    fun unlock(n:Note,pin:String):Pair<Content,ByteArray> { val derived=derive(pin,bytes(n.salt));val key=try { decrypt(n.wrapped,derived) } finally { derived.fill(0) };return Content.parse(String(decrypt(n.payload,key))) to key }
    fun bioAvailable(a:Activity):Boolean {
        if(Build.VERSION.SDK_INT<28)return false
        if(!(a.getSystemService(Activity.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure)return false
        return if(Build.VERSION.SDK_INT>=30) a.getSystemService(android.hardware.biometrics.BiometricManager::class.java).canAuthenticate(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)==android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS else if(Build.VERSION.SDK_INT>=29) a.getSystemService(android.hardware.biometrics.BiometricManager::class.java).canAuthenticate()==android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS else a.packageManager.hasSystemFeature("android.hardware.fingerprint") || a.packageManager.hasSystemFeature("android.hardware.biometrics.face")
    }
    fun bioCipher(id:String,encrypted:String?=null):Cipher {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply { load(null) };val alias="donote.$id"
        if(!ks.containsAlias(alias)) {
            val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
            val builder=KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setUserAuthenticationRequired(true).setInvalidatedByBiometricEnrollment(true)
            if(Build.VERSION.SDK_INT>=30)builder.setUserAuthenticationParameters(0,KeyProperties.AUTH_BIOMETRIC_STRONG)
            generator.init(builder.build());generator.generateKey()
        }
        val key=ks.getKey(alias,null) as SecretKey;val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        if(encrypted==null)cipher.init(Cipher.ENCRYPT_MODE,key) else cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes(encrypted).copyOfRange(0,12)))
        return cipher
    }
    fun biometric(a:Activity,cipher:Cipher,done:(Cipher)->Unit,error:(String)->Unit):CancellationSignal? {
        if(Build.VERSION.SDK_INT<28) { error("Bu cihazda biyometri kullanılamıyor. PIN kullanın.");return null }
        val signal=CancellationSignal()
        val builder=BiometricPrompt.Builder(a).setTitle("DoNote kilidi").setSubtitle("Notunuza güvenli erişim").setNegativeButton("Vazgeç",a.mainExecutor) { _,_->error("Biyometrik işlem iptal edildi.") }
        if(Build.VERSION.SDK_INT>=30)builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        builder.build().authenticate(BiometricPrompt.CryptoObject(cipher),signal,a.mainExecutor,object:BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult) { result.cryptoObject?.cipher?.let(done) }
            override fun onAuthenticationError(code:Int,message:CharSequence) { error(message.toString()) }
        })
        return signal
    }
}
