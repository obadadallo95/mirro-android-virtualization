package app.mirro.android.domain.engine.container.context

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/**
 * Creates an AppWidgetManager whose service calls cannot claim the target package as an
 * installed widget provider. Widgets are not virtualized yet, so target provider queries are
 * intentionally represented as empty results instead of leaking a package/UID mismatch to the
 * system service.
 */
internal object VirtualAppWidgetManagerFactory {

    private const val TAG = "MirroVirtualAppWidget"

    fun create(context: VirtualContext): Any? = runCatching {
        val hostManager = context.baseContext.getSystemService(Context.APPWIDGET_SERVICE) ?: return null
        val serviceField = findField(hostManager, "mService") ?: return null
        val realService = serviceField.apply { isAccessible = true }.get(hostManager) ?: return null
        val serviceInterface = Class.forName("com.android.internal.appwidget.IAppWidgetService")
        val handler = InvocationHandler { _, method, args ->
            val touchesTargetPackage = args.orEmpty().any { argument ->
                when (argument) {
                    is ComponentName -> argument.packageName == context.identity.originalPackageName
                    is String -> argument == context.identity.originalPackageName
                    else -> false
                }
            }

            if (touchesTargetPackage) {
                defaultValue(method.returnType)
            } else {
                try {
                    method.invoke(realService, *(args ?: emptyArray()))
                } catch (error: InvocationTargetException) {
                    if (error.targetException is SecurityException) {
                        defaultValue(method.returnType)
                    } else {
                        throw error.targetException
                    }
                }
            }
        }
        val serviceProxy = Proxy.newProxyInstance(
            serviceInterface.classLoader,
            arrayOf(serviceInterface),
            handler
        )
        val constructor = AppWidgetManager::class.java.getDeclaredConstructor(
            Context::class.java,
            serviceInterface
        ).apply { isAccessible = true }
        constructor.newInstance(context, serviceProxy)
    }.onFailure { error ->
        Log.w(TAG, "Unable to create virtual AppWidgetManager; using host service", error)
    }.getOrNull()

    private fun findField(receiver: Any, name: String) = generateSequence(receiver.javaClass) { it.superclass }
        .mapNotNull { type -> runCatching { type.getDeclaredField(name) }.getOrNull() }
        .firstOrNull()

    private fun defaultValue(type: Class<*>): Any? = when {
        type == Boolean::class.javaPrimitiveType -> false
        type == Byte::class.javaPrimitiveType -> 0.toByte()
        type == Short::class.javaPrimitiveType -> 0.toShort()
        type == Int::class.javaPrimitiveType -> 0
        type == Long::class.javaPrimitiveType -> 0L
        type == Float::class.javaPrimitiveType -> 0f
        type == Double::class.javaPrimitiveType -> 0.0
        type == Char::class.javaPrimitiveType -> '\u0000'
        type == IntArray::class.java -> IntArray(0)
        java.util.List::class.java.isAssignableFrom(type) -> emptyList<Any>()
        else -> null
    }
}
