package com.lovivan.aem.sentry;

import ch.qos.logback.core.Appender;
import io.sentry.DefaultSentryClientFactory;
import io.sentry.marshaller.json.JsonMarshaller;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

import java.util.Dictionary;
import java.util.Hashtable;

@Component(configurationPolicy = ConfigurationPolicy.REQUIRE)
@Designate(ocd = SentryAppenderRegistrar.Config.class)
public class SentryAppenderRegistrar {

    private static final String ROOT = "ROOT";

    private io.sentry.logback.SentryAppender appender;
    private ServiceRegistration serviceRegistration;

    @ObjectClassDefinition(name = "Sentry Appender configuration", description = "Configuration to setup logging to sentry.io")
    @interface Config {
        @AttributeDefinition(name = "Sentry DSN", description = "DSN parameter to connect to sentry.io")
        String getDSN();

        @AttributeDefinition(name = "Loggers", description = "The logback loggers to be applied to the appender. Ex: org.apache:INFO")
        String[] getLoggers();

        @AttributeDefinition(name = "\"In Application\" Stack Frames", description = "Sentry differentiates stack frames that are directly related to your application (\"in application\") from " +
                "stack frames that come from other packages such as the standard library, frameworks, or other dependencies. " +
                "You can configure which package prefixes your application uses, which takes a comma separated list.")
        String getInApplicationPackages() default "";

        @AttributeDefinition(name = "Distribution", description = "The application distribution that will be sent with each event", required = false)
        String getDistribution() default "";

        @AttributeDefinition(name = "Environment", description = "The application environment that will be sent with each event", required = false)
        String getEnvironment() default "";

        @AttributeDefinition(name = "Server name", description = "The server name that will be sent with each event", required = false)
        String getServerName() default "";

        @AttributeDefinition(name = "Release", description = "The application version that will be sent with each event", required = false)
        String getRelease() default "";

        @AttributeDefinition(name = "Timeout", description = "A timeout is set to avoid blocking Sentry threads because establishing a connection is taking too long.", required = false)
        int getTimeout() default 1000;

        @AttributeDefinition(name = "Max message length", description = "By default only the first 1000 characters of a message will be sent to the server. This can be changed", required = false)
        int getMaxMessageLength() default JsonMarshaller.DEFAULT_MAX_MESSAGE_LENGTH;

        @AttributeDefinition(name = "Sample Rate", description = "Sentry can be configured to sample events. This option takes a number from 0.0 to 1.0, representing the percent of events to allow " +
                "through to server", required = false)
        double getSampleRate() default -1;

        @AttributeDefinition(name = "Enable compression", description = "It’s possible to manually enable/disable the compression", required = false)
        boolean getCompression() default true;

        @AttributeDefinition(name = "Enable uncaught exception handler", description = "An UncaughtExceptionHandler is configured that will send exceptions to Sentry", required = false)
        boolean getUncaughtHandlerEnabled() default true;

        @AttributeDefinition(name = "Hide common stacktraces", description = "Sentry can use the “in application” system to hide frames in chained exceptions", required = false)
        boolean getStacktraceHideCommon() default true;

        @AttributeDefinition(name = "Enable Buffer", description = "Enable or disable buffering", required = false)
        boolean getBufferEnabled() default DefaultSentryClientFactory.BUFFER_ENABLED_DEFAULT;

        @AttributeDefinition(name = "Buffer directory", description = "Sentry can be configured to write events to a specified directory on disk anytime communication with the Sentry server fails", required = false)
        String getBufferDir() default "";

        @AttributeDefinition(name = "Buffer size", description = "The maximum number of events that will be stored on disk defaults to 50", required = false)
        int getBufferSize() default DefaultSentryClientFactory.BUFFER_SIZE_DEFAULT;

        @AttributeDefinition(name = "Buffer flush time", description = "If a buffer directory is provided, a background thread will periodically attempt to re-send the events that are found on disk" +
                ". Value in ms", required = false)
        long getBufferFlushTime() default DefaultSentryClientFactory.BUFFER_FLUSHTIME_DEFAULT;

        @AttributeDefinition(name = "Enable buffer graceful shutdown", description = "To disable the graceful shutdown", required = false)
        boolean getBufferGracefulShutdown() default true;

        @AttributeDefinition(name = "Buffer shutdown timeout", description = "By default, the buffer flushing thread is given 1 second to shutdown gracefully, but this can be adjusted", required = false)
        int getBufferShutdownTimeout() default 1000;

        @AttributeDefinition(name = "Enable async", description = "To disable the async mode, set to false", required = false)
        boolean getAsync() default true;

        @AttributeDefinition(name = "Enable async graceful shutdown", description = "It is possible to disable the graceful shutdown. This might lead to some log entries being lost if the log " +
                "application doesn’t shut down the SentryClient instance nicely", required = false)
        boolean getAsyncGracefulShutdown() default true;

        @AttributeDefinition(name = "Async shutdown timeout", description = "By default, the asynchronous connection is given 1 second to shutdown gracefully, but this can be adjusted", required = false)
        int getAsyncShutdownTimeout() default 1000;

        @AttributeDefinition(name = "Size of async queues", description = "The default queue used to store unprocessed events is limited to 50 items. Additional items added once the queue is full " +
                "are dropped and never sent to the Sentry server. Depending on the environment (if the memory is sparse) it is important to be able to control the size of that queue to avoid memory" +
                " issues.", required = false)
        int getAsyncQueueSize() default DefaultSentryClientFactory.QUEUE_SIZE_DEFAULT;

        @AttributeDefinition(name = "Number of async threads", description = "It’s possible to manually set the number of threads", required = false)
        int getAsyncThreads() default -1;

        @AttributeDefinition(name = "Async priority", description = "In most cases sending logs to Sentry isn’t as important as an application running smoothly, so the threads have a minimal " +
                "priority", required = false)
        int getAsyncPriority() default Thread.MIN_PRIORITY;
    }

    @Activate
    protected void active(BundleContext context, Config config) {
        setupSystemProperties(config);
        appender = new io.sentry.logback.SentryAppender();
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("loggers", ArrayUtils.isEmpty(config.getLoggers()) ? new String[]{ROOT} : config.getLoggers());
        serviceRegistration = context.registerService(Appender.class.getName(), appender, props);
    }

    @Deactivate
    protected void deactivate() {
        if (appender != null) {
            appender.stop();
            appender = null;
        }

        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }
    }

    private void setupSystemProperties(Config config) {
        System.setProperty("sentry.dsn", config.getDSN());
        System.setProperty("stacktrace.app.packages", config.getInApplicationPackages());
        System.setProperty("stacktrace.hidecommon", String.valueOf(config.getStacktraceHideCommon()));
        System.setProperty("uncaught.handler.enabled", String.valueOf(config.getUncaughtHandlerEnabled()));
        if (config.getSampleRate() != -1) {
            System.setProperty("sample.rate", String.valueOf(config.getSampleRate()));
        }
        System.setProperty("buffer.enabled", String.valueOf(config.getBufferEnabled()));
        setSystemPropertyIfNotBlank("buffer.dir", config.getBufferDir());
        System.setProperty("buffer.size", String.valueOf(config.getBufferSize()));
        System.setProperty("buffer.flushtime", String.valueOf(config.getBufferFlushTime()));
        System.setProperty("buffer.shutdowntimeout", String.valueOf(config.getBufferShutdownTimeout()));
        System.setProperty("buffer.gracefulshutdown", String.valueOf(config.getBufferGracefulShutdown()));
        System.setProperty("async", String.valueOf(config.getAsync()));
        System.setProperty("async.shutdowntimeout", String.valueOf(config.getAsyncShutdownTimeout()));
        System.setProperty("async.gracefulshutdown", String.valueOf(config.getAsyncGracefulShutdown()));
        System.setProperty("async.queuesize", String.valueOf(config.getAsyncQueueSize()));
        if (config.getSampleRate() != -1) {
            System.setProperty("async.threads", String.valueOf(config.getAsyncThreads()));
        }
        System.setProperty("async.priority", String.valueOf(config.getAsyncPriority()));
        System.setProperty("compression", String.valueOf(config.getCompression()));
        System.setProperty("maxmessagelength", String.valueOf(config.getMaxMessageLength()));
        System.setProperty("timeout", String.valueOf(config.getTimeout()));
        setSystemPropertyIfNotBlank("release", config.getRelease());
        setSystemPropertyIfNotBlank("dist", config.getDistribution());
        setSystemPropertyIfNotBlank("environment", config.getEnvironment());
        setSystemPropertyIfNotBlank("servername", config.getServerName());
    }

    private void setSystemPropertyIfNotBlank(String propertyKey, String propertyValue) {
        if (StringUtils.isNotBlank(propertyValue)) {
            System.setProperty(propertyKey, propertyValue);
        }
    }
}
