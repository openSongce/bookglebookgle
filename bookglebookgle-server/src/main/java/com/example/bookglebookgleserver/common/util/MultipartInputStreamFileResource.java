package com.example.bookglebookgleserver.common.util;

import org.springframework.core.io.ByteArrayResource;

import java.io.IOException;
import java.io.InputStream;

public class MultipartInputStreamFileResource extends ByteArrayResource {

    private String fileName;

    public MultipartInputStreamFileResource(InputStream inputStream, String fileName) throws IOException {
        super(inputStream.readAllBytes());
        this.fileName = fileName;
    }

    @Override
    public String getFilename() {
        return fileName;
    }
}
