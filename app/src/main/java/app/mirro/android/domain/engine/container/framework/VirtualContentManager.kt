package app.mirro.android.domain.engine.container.framework

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import app.mirro.android.domain.engine.container.model.VirtualRuntimeIdentity
import java.io.FileNotFoundException

class VirtualContentManager(private val identity: VirtualRuntimeIdentity) {
    private val providers = LinkedHashMap<String, ContentProvider>()
    private val authorities = LinkedHashMap<String, String>()

    fun register(authority: String, providerName: String, provider: ContentProvider): ClassifiedValue<Boolean> {
        val existing = authorities[authority]
        if (existing != null && existing != providerName) {
            return ClassifiedValue(false, VirtualValueOrigin.UNSUPPORTED, "${VirtualFailureCategory.AUTHORITY_COLLISION}: $authority")
        }
        authorities[authority] = providerName
        providers[providerName] = provider
        return ClassifiedValue(true, VirtualValueOrigin.GUEST_VALUE)
    }

    fun provider(uri: Uri): ContentProvider? = authorities[uri.authority]?.let(providers::get)

    fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sortOrder: String?): ClassifiedValue<Cursor> =
        provider(uri)?.let { ClassifiedValue(it.query(uri, projection, selection, args, sortOrder), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "${VirtualFailureCategory.PROVIDER_ROUTE_MISSING}: ${uri.authority}")

    fun insert(uri: Uri, values: ContentValues): ClassifiedValue<Uri> =
        provider(uri)?.let { ClassifiedValue(it.insert(uri, values), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")

    fun update(uri: Uri, values: ContentValues, selection: String?, args: Array<String>?): ClassifiedValue<Int> =
        provider(uri)?.let { ClassifiedValue(it.update(uri, values, selection, args), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")

    fun delete(uri: Uri, selection: String?, args: Array<String>?): ClassifiedValue<Int> =
        provider(uri)?.let { ClassifiedValue(it.delete(uri, selection, args), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")

    fun call(uri: Uri, method: String, arg: String?, extras: Bundle?): ClassifiedValue<Bundle> =
        provider(uri)?.let { ClassifiedValue(it.call(method, arg, extras), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")

    fun getType(uri: Uri): ClassifiedValue<String> =
        provider(uri)?.let { ClassifiedValue(it.getType(uri), VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")

    fun openFile(uri: Uri, mode: String): ClassifiedValue<ParcelFileDescriptor> = runCatching {
        provider(uri)?.openFile(uri, mode)?.let { descriptor -> ClassifiedValue<ParcelFileDescriptor>(descriptor, VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue<ParcelFileDescriptor>(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")
    }.getOrElse { ClassifiedValue<ParcelFileDescriptor>(null, VirtualValueOrigin.UNSUPPORTED, "${VirtualFailureCategory.URI_GRANT_UNSUPPORTED}: ${it.message}") }

    fun openAssetFile(uri: Uri, mode: String): ClassifiedValue<AssetFileDescriptor> = runCatching {
        provider(uri)?.openAssetFile(uri, mode)?.let { descriptor -> ClassifiedValue<AssetFileDescriptor>(descriptor, VirtualValueOrigin.GUEST_VALUE) }
            ?: ClassifiedValue<AssetFileDescriptor>(null, VirtualValueOrigin.UNSUPPORTED, "provider not registered")
    }.getOrElse { ClassifiedValue<AssetFileDescriptor>(null, VirtualValueOrigin.UNSUPPORTED, "${VirtualFailureCategory.URI_GRANT_UNSUPPORTED}: ${it.message}") }

    fun authorities(): Set<String> = authorities.keys.toSet()
}

class VirtualFileProviderUriMapper(private val identity: VirtualRuntimeIdentity) {
    private val grants = LinkedHashMap<String, Uri>()

    fun map(authority: String, fileName: String): ClassifiedValue<Uri> {
        if (authority.isBlank() || fileName.contains("..") || fileName.contains('/')) {
            return ClassifiedValue(null, VirtualValueOrigin.UNSUPPORTED, "invalid FileProvider path")
        }
        val virtualAuthority = "${authority}.${identity.cloneId.take(12)}"
        val uri = Uri.parse("content://$virtualAuthority/root/$fileName")
        grants[uri.toString()] = uri
        return ClassifiedValue(uri, VirtualValueOrigin.GUEST_VALUE)
    }

    fun owns(uri: Uri): Boolean = grants.containsKey(uri.toString())
}
