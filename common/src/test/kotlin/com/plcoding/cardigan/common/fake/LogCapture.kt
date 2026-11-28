package com.plcoding.cardigan.common.fake

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** Records the events logged by [loggerClass]'s logger until [close] is called. */
class LogCapture(
    loggerClass: Class<*>,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(loggerClass) as Logger
    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    init {
        logger.addAppender(appender)
    }

    val errors: List<ILoggingEvent>
        get() = appender.list.filter { it.level == Level.ERROR }

    override fun close() {
        logger.detachAppender(appender)
        appender.stop()
    }
}
