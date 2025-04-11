package org.bibletranslationtools.wat.platform.markers

import kotlin.reflect.KClass

interface IMarker {
    val contents: List<IMarker>
    fun getIdentifier(): String
    fun getPosition(): Int
    fun setPosition(value: Int)
    fun getAllowedContents(): List<Any>
    fun preProcess(input: String): String
    fun tryInsert(input: IMarker): Boolean
    fun getHierarchyToMarker(target: IMarker): List<IMarker>
    fun <T: IMarker> getChildMarkers(clazz: KClass<T>): List<T>
    fun <T: IMarker, P: IMarker> getChildMarkers(
        clazz: KClass<T>,
        ignoredParents: List<KClass<out P>> = emptyList()
    ): List<T>
    fun getLastDescendant(): IMarker
}

expect open class MarkerWrapper : IMarker {
    val wrapper: Any
    override val contents: List<IMarker>
    override fun getPosition(): Int
    override fun setPosition(value: Int)
    override fun getIdentifier(): String
    override fun getAllowedContents(): List<Any>
    override fun preProcess(input: String): String
    override fun tryInsert(input: IMarker): Boolean
    override fun getHierarchyToMarker(target: IMarker): List<IMarker>
    override fun <T : IMarker> getChildMarkers(clazz: KClass<T>): List<T>
    override fun <T : IMarker, P : IMarker> getChildMarkers(
        clazz: KClass<T>,
        ignoredParents: List<KClass<out P>>
    ): List<T>
    override fun getLastDescendant(): IMarker
}

expect object MarkerFactory {
    fun create(marker: Any): IMarker
}
