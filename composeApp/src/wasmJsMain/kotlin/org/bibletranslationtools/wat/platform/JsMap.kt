package org.bibletranslationtools.wat.platform

external class JsMap<K: JsAny, V: JsAny?>: JsAny

operator fun <K: JsAny, V: JsAny?> JsMap<K, V>.get(key: K): V? =
    jsMapGet(this, key)

operator fun <K: JsAny, V: JsAny?> JsMap<K, V>.set(key: K, value: V) {
    jsMapSet(this, key, value)
}

val <K: JsAny, V: JsAny?> JsMap<K, V>.keys: JsArray<K>
    get() = getKeys(this)

val <K: JsAny, V: JsAny?> JsMap<K, V>.values: JsArray<V>
    get() = getValues(this)

private fun <K: JsAny, V: JsAny?> getKeys(map: JsMap<K, V>): JsArray<K> =
    js("Object.keys(map)")

private fun <K: JsAny, V: JsAny?> getValues(map: JsMap<K, V>): JsArray<V> =
    js("Object.values(map)")

@Suppress("RedundantNullableReturnType", "UNUSED_PARAMETER")
private fun <K: JsAny, V: JsAny?> jsMapGet(map: JsMap<K, V>, key: K): V? =
    js("map[key]")

@Suppress("UNUSED_PARAMETER")
private fun <K: JsAny, V: JsAny?> jsMapSet(map: JsMap<K, V>, key: K, value: V) {
    js("map[key] = value")
}

fun <K: JsAny, V: JsAny?> JsMap<K, V>.toMap(): Map<K, V?> {
    @Suppress("UNCHECKED_CAST")
    val map = mutableMapOf<K, V?>()
    this.keys.toList().forEach { key ->
        map[key] = this[key]
    }
    return map
}