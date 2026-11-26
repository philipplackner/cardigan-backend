package com.plcoding.cardigan.common.fake

import org.springframework.beans.factory.ObjectProvider
import java.util.stream.Stream

/** An [ObjectProvider] over fixed [items] in the given order, standing in for a bean lookup. */
fun <T : Any> objectProviderOf(vararg items: T): ObjectProvider<T> = object : ObjectProvider<T> {
    override fun getObject(): T = items.single()
    override fun getIfAvailable(): T? = items.singleOrNull()
    override fun stream(): Stream<T> = Stream.of(*items)
    override fun orderedStream(): Stream<T> = Stream.of(*items)
}
