package com.nasuyun.integration.log4j;

public class Resources {
    static String pipeline = "{\n" +
            "  \"processors\": [\n" +
            "    {\n" +
            "      \"grok\": {\n" +
            "        \"field\": \"message\",\n" +
            "        \"patterns\": [\n" +
            "          \"\\\\[%{TIMESTAMP_ISO8601:@timestamp}\\\\]\\\\[%{DATA:hostname}\\\\]\\\\[%{WORD:level}(%{SPACE})?\\\\]\\\\[%{DATA:class}(%{SPACE})?\\\\](%{SPACE})?(?<msg>(.|\\\\r|\\\\n)*)\"\n" +
            "        ],\n" +
            "        \"ignore_failure\": true\n" +
            "      }\n" +
            "    },\n" +
            "    {\n" +
            "      \"remove\": {\n" +
            "        \"field\": [\n" +
            "          \"message\"\n" +
            "        ],\n" +
            "        \"ignore_failure\": true\n" +
            "      }\n" +
            "    }\n" +
            "  ]\n" +
            "}";

    static String log4jTemplate6 = "{\n" +
            "  \"order\": 0,\n" +
            "  \"index_patterns\": [\n" +
            "    \"log4j-*\"\n" +
            "  ],\n" +
            "  \"settings\": {\n" +
            "    \"index\": {\n" +
            "      \"refresh_interval\": \"5s\",\n" +
            "      \"number_of_shards\": \"1\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"mappings\": {\n" +
            "    \"_doc\": {\n" +
            "      \"dynamic_templates\": [\n" +
            "        {\n" +
            "          \"strings_as_keyword\": {\n" +
            "            \"mapping\": {\n" +
            "              \"ignore_above\": 1024,\n" +
            "              \"type\": \"keyword\"\n" +
            "            },\n" +
            "            \"match_mapping_type\": \"string\"\n" +
            "          }\n" +
            "        }\n" +
            "      ],\n" +
            "      \"properties\": {\n" +
            "        \"@timestamp\": {\n" +
            "          \"type\": \"date\"\n" +
            "        },\n" +
            "        \"class\": {\n" +
            "          \"type\": \"text\",\n" +
            "          \"analyzer\": \"simple\"\n" +
            "        },\n" +
            "        \"msg\": {\n" +
            "          \"type\": \"text\",\n" +
            "          \"analyzer\": \"simple\"\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"aliases\": {}\n" +
            "}\n";

    static String log4jTemplate7 = "{\n" +
            "  \"order\": 0,\n" +
            "  \"index_patterns\": [\n" +
            "    \"log4j-*\"\n" +
            "  ],\n" +
            "  \"settings\": {\n" +
            "    \"index\": {\n" +
            "      \"refresh_interval\": \"5s\",\n" +
            "      \"number_of_shards\": \"1\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"mappings\": {\n" +
            "    \"dynamic_templates\": [\n" +
            "      {\n" +
            "        \"strings_as_keyword\": {\n" +
            "          \"mapping\": {\n" +
            "            \"ignore_above\": 1024,\n" +
            "            \"type\": \"keyword\"\n" +
            "          },\n" +
            "          \"match_mapping_type\": \"string\"\n" +
            "        }\n" +
            "      }\n" +
            "    ],\n" +
            "    \"properties\": {\n" +
            "      \"@timestamp\": {\n" +
            "        \"type\": \"date\"\n" +
            "      },\n" +
            "      \"class\": {\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"simple\"\n" +
            "      },\n" +
            "      \"msg\": {\n" +
            "        \"type\": \"text\",\n" +
            "        \"analyzer\": \"simple\"\n" +
            "      }\n" +
            "    }\n" +
            "  },\n" +
            "  \"aliases\": {}\n" +
            "}\n";
}
