package com.amaze.filemanager.filesystem.ftp;

import com.amaze.filemanager.filesystem.smb.CifsContexts;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetCopyConnectionInfo {

    public static final String MULTI_SLASH = "(?<=[^:])(//+)";
    public static final char AND = '&';
    public static final char AT = '@';
    public static final char SLASH = '/';
    public static final char COLON = ':';

    public static final String URI_REGEX = "^(?:(?![^:@]+:[^:@/]*@)([^:/?#.]+):)?(?://)?((?:(([^:@]*)(?::([^:@]*))?)?@)?([^:/?#]*)(?::(\\d*))?)(((/(?:[^?#](?![^?#/]*\\.[^?#/.]+(?:[?#]|$)))*/?)?([^?#/]*))(?:\\?([^#]*))?(?:#(.*))?)";

    private String prefix;
    private String host;
    private int port;
    private String username;
    private String password;
    private String defaultPath;
    private String queryString;
    private Map<String, String> arguments;
    private String filename;

    public NetCopyConnectionInfo(String url) {
        Pattern pattern = Pattern.compile(URI_REGEX);
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            prefix = matcher.group(1) + "://";
            host = matcher.group(6);
            String credential = matcher.group(3);
            if (!credential.contains(String.valueOf(COLON))) {
                username = credential;
                password = null;
            } else {
                username = credential.substring(0, credential.indexOf(COLON));
                password = credential.substring(credential.indexOf(COLON) + 1);
            }
            port = matcher.group(7).isEmpty() ? 0 : Integer.parseInt(matcher.group(7));
            queryString = matcher.group(12).isEmpty() ? null : matcher.group(12);
            arguments = new HashMap<>();
            if (queryString != null) {
                String[] pairs = queryString.split(String.valueOf(AND));
                for (String pair : pairs) {
                    String[] keyValue = pair.split("=");
                    arguments.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
                }
            }
            defaultPath = parseDefaultPath(matcher.group(9), matcher.group(10), matcher.group(11));
            filename = matcher.group(11).isEmpty() ? null : matcher.group(11);
        } else {
            throw new IllegalArgumentException("Unable to parse URI");
        }
    }

    private String parseDefaultPath(String path1, String path2, String path3) {
        String path = path1.isEmpty() ? SLASH + "" : path1;
        if (path.equals(SLASH + "")) {
            return path;
        }
        if (!path.endsWith(String.valueOf(SLASH)) && path3.isEmpty()) {
            return path2;
        } else {
            return path;
        }
    }

    public String lastPathSegment() {
        if (filename != null && !filename.isEmpty()) {
            return filename;
        } else if (defaultPath != null && !defaultPath.isEmpty()) {
            String[] segments = defaultPath.split(String.valueOf(SLASH));
            return segments[segments.length - 1];
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (!username.isEmpty()) {
            builder.append(prefix).append(username).append(AT).append(host);
        } else {
            builder.append(prefix).append(host);
        }
        if (port != 0) {
            builder.append(COLON).append(port);
        }
        if (defaultPath != null) {
            builder.append(defaultPath);
        }
        return builder.toString();
    }


    public String getPrefix() {
        return prefix;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getFilename() {
        return filename;
    }
}

