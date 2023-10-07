package com.amaze.filemanager.filesystem.ftp;

import org.apache.commons.net.ftp.FTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

public class FTPClientImpl implements NetCopyClient<FTPClient> {

    private static final Logger logger = LoggerFactory.getLogger(FTPClientImpl.class);

    private final FTPClient ftpClient;

    public static final String ANONYMOUS = "anonymous";

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz1234567890";

    private static String randomString(int strlen) {
        StringBuilder result = new StringBuilder(strlen);
        Random random = new Random();
        for (int i = 0; i < strlen; i++) {
            result.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return result.toString();
    }

    public static String generateRandomEmailAddressForLogin() {
        return generateRandomEmailAddressForLogin(8, 5, 3);
    }

    public static String generateRandomEmailAddressForLogin(int usernameLen, int domainPrefixLen, int domainSuffixLen) {
        String username = randomString(usernameLen);
        String domainPrefix = randomString(domainPrefixLen);
        String domainSuffix = randomString(domainSuffixLen);
        return username + "@" + domainPrefix + "." + domainSuffix;
    }

    public FTPClientImpl(FTPClient ftpClient) {
        this.ftpClient = ftpClient;
    }

    @Override
    public FTPClient getClientImpl() {
        return ftpClient;
    }

    @Override
    public boolean isConnectionValid() {
        return ftpClient.isAvailable();
    }

    @Override
    public boolean isRequireThreadSafety() {
        return true;
    }

    @Override
    public void expire() {
        try {
            ftpClient.disconnect();
        } catch (IOException e) {
            logger.warn("Error closing FTPClient connection", e);
        }
    }

    public static InputStream wrap(File inputFile) throws IOException {
        FileInputStream fileInputStream = new FileInputStream(inputFile);
        return new InputStream() {
            @Override
            public int read() throws IOException {
                return fileInputStream.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return fileInputStream.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return fileInputStream.read(b, off, len);
            }

            @Override
            public void reset() throws IOException {
                fileInputStream.reset();
            }

            @Override
            public int available() throws IOException {
                return fileInputStream.available();
            }

            @Override
            public void close() throws IOException {
                fileInputStream.close();
                if (inputFile.delete()) {
                    logger.info("Deleted temporary file: " + inputFile.getAbsolutePath());
                } else {
                    logger.warn("Failed to delete temporary file: " + inputFile.getAbsolutePath());
                }
            }

            @Override
            public boolean markSupported() {
                return fileInputStream.markSupported();
            }

            @Override
            public void mark(int readlimit) {
                fileInputStream.mark(readlimit);
            }

            @Override
            public long skip(long n) throws IOException {
                return fileInputStream.skip(n);
            }
        };
    }

    public static OutputStream wrap(OutputStream outputStream, FTPClient ftpClient) {
        return new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                outputStream.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                outputStream.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                outputStream.flush();
            }

            @Override
            public void close() throws IOException {
                outputStream.close();
                try {
                    ftpClient.completePendingCommand();
                } catch (IOException e) {
                    logger.warn("Error completing FTP command", e);
                }
            }
        };
    }
}

