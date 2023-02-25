# log4j appender for nasu elasticsearch serverless

## How use

pom.xml
```xml
    <dependency>
        <groupId>com.nasuyun</groupId>
        <artifactId>integration-log4j</artifactId>
        <version>{version}</version>
    </dependency>
```

log4j2.properties
```
appender.nes.type=Elasticsearch
appender.nes.name=nes
appender.nes.username=<nasuyun cloud_application username>
appender.nes.password=<nasuyun cloud_application username>

rootLogger.level=info
rootLogger.appenderRef.stdout.ref=nes
```