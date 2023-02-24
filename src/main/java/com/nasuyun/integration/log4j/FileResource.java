package com.nasuyun.integration.log4j;

import org.apache.logging.log4j.core.util.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.net.URL;

public class FileResource {

    public String getSource(String path) {
        URL resource = getClass().getClassLoader().getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("file not found!");
        } else {
            try {
                File file = org.apache.logging.log4j.core.util.FileUtils.fileFromUri(resource.toURI());
                return IOUtils.toString(new FileReader(file));
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
    }
}
