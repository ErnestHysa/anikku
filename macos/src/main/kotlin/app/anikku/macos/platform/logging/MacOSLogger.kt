package app.anikku.macos.platform.logging

import app.anikku.macos.platform.storage.MacOSStorageProvider
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy
import org.slf4j.LoggerFactory

/**
 * macOS logger configuration using SLF4J + Logback.
 * Matches the existing Android logging levels (EHLogLevel) where applicable.
 *
 * Two appenders:
 * - Console: logs to stdout
 * - Rolling file: logs to ~/Library/Application Support/Anikku/logs/anikku.log
 */
object MacOSLogger {

    fun initialize(storageProvider: MacOSStorageProvider) {
        val context = LoggerFactory.getILoggerFactory() as LoggerContext

        val encoder = PatternLayoutEncoder()
        encoder.context = context
        encoder.pattern = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
        encoder.start()

        // Console appender
        val consoleAppender = ConsoleAppender<ILoggingEvent>()
        consoleAppender.context = context
        consoleAppender.encoder = encoder
        consoleAppender.start()

        // File appender
        storageProvider.logsDirectory.mkdirs()
        val fileAppender = RollingFileAppender<ILoggingEvent>()
        fileAppender.context = context
        fileAppender.file = "${storageProvider.logsDirectory.absolutePath}/anikku.log"
        fileAppender.encoder = encoder

        val rollingPolicy = TimeBasedRollingPolicy<ILoggingEvent>()
        rollingPolicy.context = context
        rollingPolicy.fileNamePattern = "${storageProvider.logsDirectory.absolutePath}/anikku.%d{yyyy-MM-dd}.log"
        rollingPolicy.maxHistory = 7
        rollingPolicy.setParent(fileAppender)
        rollingPolicy.start()

        fileAppender.rollingPolicy = rollingPolicy
        fileAppender.start()

        // Configure root logger
        val rootLogger = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        rootLogger.level = Level.INFO
        rootLogger.addAppender(consoleAppender)
        rootLogger.addAppender(fileAppender)

        // Configure app-specific logger
        val appLogger = context.getLogger("app.anikku") as ch.qos.logback.classic.Logger
        appLogger.level = Level.DEBUG
        appLogger.isAdditive = false
        appLogger.addAppender(consoleAppender)
        appLogger.addAppender(fileAppender)
    }

    fun <T : Any> getLogger(clazz: Class<T>): org.slf4j.Logger {
        return LoggerFactory.getLogger(clazz)
    }

    inline fun <reified T : Any> getLogger(): org.slf4j.Logger {
        return LoggerFactory.getLogger(T::class.java)
    }
}
