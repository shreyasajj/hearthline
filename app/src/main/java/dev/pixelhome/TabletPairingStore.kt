package dev.pixelhome

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Scoped HA credential only. Separate key, file and trust domain from Sendspin. */
class TabletPairingStore(context: Context) {
    private val file = java.io.File(context.noBackupFilesDir, "hearthline_pairing_v1")
    private val identity = java.io.File(context.noBackupFilesDir, "hearthline_installation_v1")
    val tabletId: String = installationId(identity, file.exists())
    private val alias = "hearthline_ha_pairing_v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey(alias,null) as? SecretKey) ?: run {
            check(!file.exists()) { "Pairing key unavailable. Forget and pair again." }
            KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            }.generateKey()
        }
    }
    fun load(): JSONObject? {
        if(!file.exists()) return null
        val bytes = file.readBytes(); require(bytes.size in 29..8192)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        cipher.updateAAD("hearthline:ha:v1".toByteArray())
        return JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
    }
    fun save(origin: String, entry: String, credential: String, telemetry: Boolean) {
        val value = JSONObject().put("origin",origin).put("entry_id",entry).put("credential",credential).put("telemetry",telemetry)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        cipher.updateAAD("hearthline:ha:v1".toByteArray())
        val atomic = android.util.AtomicFile(file)
        val output=atomic.startWrite()
        try { output.write(cipher.iv+cipher.doFinal(value.toString().toByteArray())); atomic.finishWrite(output) }
        catch(e: Exception) { atomic.failWrite(output); throw e }
    }
    fun forget() {
        check(!file.exists() || file.delete())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
}

/**
 * Reads the installation UUID, creating it atomically on first use. An unreadable or corrupt ID is
 * replaced only while no pairing exists: a paired installation keeps failing closed instead, because
 * HA has bound the pairing to the old ID and silently changing it would orphan that pairing.
 */
internal fun installationId(identity: java.io.File, paired: Boolean): String {
    val existing = runCatching { identity.readText(Charsets.UTF_8).trim() }.getOrNull()
    if(existing != null && validInstallationId(existing)) return existing
    check(!paired) { "Installation identity unavailable. Forget the server and pair again." }
    val created = UUID.randomUUID().toString()
    // Plain java.nio so this stays JVM-testable: write a sibling, then atomically replace.
    val temporary = java.io.File(identity.parentFile, identity.name + ".new")
    temporary.writeText(created, Charsets.UTF_8)
    java.nio.file.Files.move(temporary.toPath(), identity.toPath(),
        java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
    return created
}

/** Lowercase canonical UUID, matching what HA accepts for pairing and discovery. */
internal fun validInstallationId(value: String) =
    value.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
