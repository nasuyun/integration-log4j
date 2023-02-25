package com.nasuyun.integration.log4j;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.apache.logging.log4j.core.layout.PatternLayout;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Sends log events over Elasticsearch .
 */
@Plugin(name = "Elasticsearch", category = Node.CATEGORY, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class EsAppender extends AbstractAppender {

    public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<EsAppender> {

        @PluginBuilderAttribute
        private String host = "https://router.nasuyun.com:9200";

        @PluginBuilderAttribute
        @Required(message = "No username provided for Elasticsearch Appender")
        private String username;

        @PluginBuilderAttribute
        @Required(message = "No password provided for Elasticsearch Appender")
        private String password;

        @PluginBuilderAttribute
        private int connectTimeoutSeconds = 30;

        @PluginBuilderAttribute
        private int refreshSeconds = 5;

        @PluginBuilderAttribute
        private String pipeline = "log4j";

        @PluginBuilderAttribute
        private String rolloverPolicy = "day";

        @PluginBuilderAttribute
        private boolean debug = false;

        @PluginBuilderAttribute
        private boolean verifyConnection = true;

        @Override
        public EsAppender build() {
            final EsManager esManager = new EsConnectionManager(getConfiguration(), getConfiguration().getLoggerContext(),
                    getName(), host, username, password, connectTimeoutSeconds, refreshSeconds, pipeline, rolloverPolicy, debug, verifyConnection);
            String pattern = "[%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}][" + hostname() + "][%-5p][%-25c{1.}] %marker %m%n";

            Layout layout = getLayout();
            if (layout == null) {
                PatternLayout.createDefaultLayout();
                layout = PatternLayout.newBuilder().withPattern(pattern).withCharset(Charset.defaultCharset()).build();
            }
            return new EsAppender(getName(), layout, getFilter(), isIgnoreExceptions(), esManager);
        }

        public String getHost() {
            return host;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public boolean isVerifyConnection() {
            return verifyConnection;
        }

        public B setHost(final String host) {
            this.host = host;
            return asBuilder();
        }

        public B setUsername(final String username) {
            this.username = username;
            return asBuilder();
        }

        public B setPassword(final String password) {
            this.password = password;
            return asBuilder();
        }

        public B setConnectTimeoutSeconds(final int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            return asBuilder();
        }

        public B setRolloverPolicy(final String rolloverPolicy) {
            this.rolloverPolicy = rolloverPolicy;
            return asBuilder();
        }

        public B setDebug(final boolean debug) {
            this.debug = debug;
            return asBuilder();
        }

        public B setVerifyConnection(final boolean verifyConnection) {
            this.verifyConnection = verifyConnection;
            return asBuilder();
        }
    }

    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    private final EsManager manager;

    private EsAppender(final String name, final Layout<? extends Serializable> layout, final Filter filter,
                       final boolean ignoreExceptions, final EsManager manager) {
        super(name, filter, layout, ignoreExceptions);
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public void start() {
        super.start();
        manager.startup();
    }

    @Override
    public void append(final LogEvent event) {
        try {
            manager.send(getLayout(), event);
        } catch (final Exception e) {
            error("Unable to send HTTP in appender [" + getName() + "]", event, e);
        }
    }

    @Override
    public boolean stop(final long timeout, final TimeUnit timeUnit) {
        setStopping();
        boolean stopped = super.stop(timeout, timeUnit, false);
        stopped &= manager.stop(timeout, timeUnit);
        setStopped();
        return stopped;
    }

    @Override
    public String toString() {
        return "NasuElasticsearchAppender{" +
                "name=" + getName() +
                ", state=" + getState() +
                '}';
    }

    private static String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown_ip";
        }
    }

    public static void main(String[] args) {
        System.out.println(hostname());
    }

}
