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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.util.Strings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class EsConnectionManager extends EsManager {

    private final String host;
    private final String username;
    private final String password;
    private final int connectTimeoutSeconds;
    private final String rolloverPolicy;
    private final Bulking bulking;
    private final String pipeline;
    private final boolean debug;
    private final HttpClient client;
    private final int esVersion;
    private final boolean ready;
    private final boolean verifyConnection;

    private static final String index = "log4j";

    public EsConnectionManager(final Configuration configuration, final LoggerContext loggerContext, final String name,
                               final String host,
                               final String username,
                               final String password,
                               final int connectTimeoutSeconds,
                               final int refreshSeconds,
                               final String pipeline,
                               final String rolloverPolicy,
                               final boolean debug,
                               final boolean verifyConnection) {
        super(configuration, loggerContext, name);
        this.host = host;
        this.username = username;
        this.password = password;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.rolloverPolicy = rolloverPolicy;
        this.bulking = new Bulking(refreshSeconds);
        this.pipeline = pipeline;
        this.debug = debug;
        this.client = HttpClient.newHttpClient();
        this.verifyConnection = verifyConnection;
        this.esVersion = ensureVersion();
        boolean ensureLog4jTemplate = ensureLog4jTemplate();
        boolean ensureLog4jPipeline = ensureLog4jPipeline();
        this.ready = esVersion > 0 && ensureLog4jTemplate && ensureLog4jPipeline;
        if (ready == false) {
            System.err.println("Startup Log4j Elasticsearch Appender Failure, Please enabled debug (appender.nes.debug=true) to view verbose");
            if (verifyConnection) {
                throw new ConfigurationException(String.format("connected[%s] ensureLog4jTemplate[%s] ensureLog4jPipeline[%s]", esVersion > 0, ensureLog4jPipeline, ensureLog4jTemplate));
            }
        } else {
            System.out.println("Startup Log4j Elasticsearch Appender Successed");
        }
    }

    @Override
    public void send(final Layout<?> layout, final LogEvent event) {
        bulking.add(layout, event);
    }

    /**
     * 初始化，获取ES版本
     */
    private int ensureVersion() {
        String body = httpGet("/", e -> {
            e.printStackTrace();
        });
        if (Strings.isEmpty(body)) {
            return -1;
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(body);
            String version = node.get("version").get("number").asText();
            return Integer.valueOf(version.substring(0, version.indexOf(".")));
        } catch (Exception e) {
            if (debug) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    /**
     * 创建模板
     */
    private boolean ensureLog4jTemplate() {
        if (esVersion > 0) {
            String body = httpGet("/_template/log4j", null);
            if (Strings.isNotEmpty(body) && body.contains("log4j")) {
                return true;
            }
            String templateFileSource = esVersion == 6 ? ConfigFile.log4jTemplate6 : ConfigFile.log4jTemplate7;
            return httpPut("/_template/log4j", templateFileSource);
        } else {
            return false;
        }
    }

    private boolean ensureLog4jPipeline() {
        if (esVersion > 0) {
            String body = httpGet("/_ingest/pipeline/log4j", null);
            if (Strings.isNotEmpty(body) && body.contains("log4j")) {
                return true;
            }
            String pipeline = ConfigFile.pipeline;
            return httpPut("/_ingest/pipeline/log4j", pipeline);
        } else {
            return false;
        }
    }

    class Bulking {
        private final ScheduledExecutorService threadPool;
        private final BlockingQueue<String> events;

        Bulking(int refresh) {
            this.threadPool = Executors.newSingleThreadScheduledExecutor();
            this.events = new ArrayBlockingQueue<>(2000);
            this.threadPool.scheduleAtFixedRate(() -> refresh(), refresh, refresh, TimeUnit.SECONDS);
        }

        void add(final Layout<?> layout, final LogEvent event) {
            final byte[] msg = layout.toByteArray(event);
            Map body = Map.of("message", new String(msg));
            ObjectMapper mapper = new ObjectMapper();
            try {
                String json = mapper.writeValueAsString(body);
                events.add(json);
                if (events.size() >= 1000) {
                    threadPool.execute(() -> refresh());
                }
            } catch (JsonProcessingException e) {
                if (debug) {
                    e.printStackTrace();
                }
            }
        }

        void refresh() {
            if (events.isEmpty()) {
                return;
            }
            String indexName = index + indexSuffix();
            StringBuffer buffer = new StringBuffer();
            for (String messageJson : events) {
                buffer.append(buildIndexRequest(indexName, messageJson));
            }
            httpPost("/_bulk?pipeline=" + pipeline, buffer.toString());
            events.clear();
        }

        private String buildIndexRequest(String index, String source) {
            StringBuffer buffer = new StringBuffer();
            buffer.append("{ \"index\" : { \"_index\" : \"" + index + "\"");
            buffer.append(esVersion == 7 ? "}}" : ",\"_type\":\"_doc\"}}");
            buffer.append("\n");
            buffer.append(source);
            buffer.append("\n");
            return buffer.toString();
        }

    }

    private String httpGet(String path, Consumer<Exception> exceptionConsumer) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + path))
                .header("Content-Type", "application/json")
                .header("Authorization", getBasicAuthenticationHeader(username, password))
                .timeout(Duration.ofSeconds(connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 400) {
                exceptionConsumer.accept(new IllegalArgumentException(response.body()));
            } else {
                return response.body();
            }
        } catch (Exception e) {
            if (exceptionConsumer != null) {
                exceptionConsumer.accept(e);
            }
        }
        return null;
    }

    private boolean httpPost(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + path))
                .header("Content-Type", "application/json")
                .header("Authorization", getBasicAuthenticationHeader(username, password))
                .timeout(Duration.ofSeconds(connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 30))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 400) {
                if (debug) {
                    System.err.println("[http-post] " + path + " error :" + response.body());
                }
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private boolean httpPut(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(host + path))
                .header("Content-Type", "application/json")
                .header("Authorization", getBasicAuthenticationHeader(username, password))
                .timeout(Duration.ofSeconds(connectTimeoutSeconds > 0 ? connectTimeoutSeconds : 30))
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 400) {
                if (debug) {
                    System.err.println("[http-put] " + path + " error :" + response.body());
                }
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            if (debug) {
                e.printStackTrace();
            }
            return false;
        }
    }

    private String indexSuffix() {
        Date now = new Date();
        String suffix = rolloverPolicy.equalsIgnoreCase("year") ? yearDateFormat.format(now) :
                rolloverPolicy.equalsIgnoreCase("month") ? monthDateFormat.format(now) :
                        rolloverPolicy.equalsIgnoreCase("day") ? dayDateFormat.format(now) :
                                null;
        return Strings.isNotEmpty(suffix) ? "-" + suffix : "";
    }

    private static final String getBasicAuthenticationHeader(String username, String password) {
        String valueToEncode = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
    }

    private static SimpleDateFormat dayDateFormat = new SimpleDateFormat("yyyy.MM.dd");
    private static SimpleDateFormat monthDateFormat = new SimpleDateFormat("yyyy.MM");
    private static SimpleDateFormat yearDateFormat = new SimpleDateFormat("yyyy");
}
