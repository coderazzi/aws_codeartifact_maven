package net.coderazzi.aws_codeartifact_maven.utils;

import com.intellij.openapi.diagnostic.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

class StreamReader implements Runnable {

    public final static String ENCODING = "UTF-8"; // python 3 (aws cli) encoding
    private final static Logger LOGGER = Logger.getInstance(StreamReader.class);
    private final String description;
    private final InputStream inputStream;
    private final Thread thread;
    protected final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    public StreamReader(String description, InputStream inputStream) {
        this.description = description;
        this.inputStream = inputStream;
        this.thread = new Thread(this);
        this.thread.start();
    }

    public String getOutput() {
        try {
            this.thread.join();
        } catch (InterruptedException ex) {
            // thread interrupted, app being stopped, nothing else to do here
        }
        String read = getRead();
        return read.isEmpty() ? null : read;
    }

    @Override
    public void run() {
        try {
            while (true) {
                int b = inputStream.read();
                if (b == -1) {
                    break;
                }
                byteArrayOutputStream.write(b);
            }
        } catch (IOException ex) {
            try {
                byte[] b = ("Error reading " + description).getBytes(ENCODING);
                byteArrayOutputStream.reset();
                byteArrayOutputStream.write(b, 0, b.length);
            } catch (UnsupportedEncodingException uex) {
                LOGGER.error(uex);
            }
        }
    }

    protected String getRead() {
        try {
            return byteArrayOutputStream.toString(ENCODING);
        } catch (UnsupportedEncodingException ex) {
            LOGGER.error(ex);
            return "";
        }
    }
}
