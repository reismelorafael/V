package com.vectras.vm.network;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLHandshakeException;

public class RequestNetworkController {
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";

    public static final int REQUEST_PARAM = 0;
    public static final int REQUEST_BODY = 1;

    private static final int SOCKET_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 25000;

    private static RequestNetworkController mInstance;
    private final ExecutorService ioPool = Executors.newCachedThreadPool();

    public static synchronized RequestNetworkController getInstance() {
        if (mInstance == null) {
            mInstance = new RequestNetworkController();
        }
        return mInstance;
    }

    public void execute(final RequestNetwork requestNetwork, String method, String url, final String tag, final RequestNetwork.RequestListener requestListener) {
        ioPool.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String finalUrl = buildFinalUrl(url, requestNetwork, method);
                EndpointValidator.requireValidHttpUrl(finalUrl, "request url");
                URL target = new URL(finalUrl);
                connection = (HttpURLConnection) target.openConnection();
                connection.setConnectTimeout(SOCKET_TIMEOUT);
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setUseCaches(false);
                connection.setRequestMethod(method);
                connection.setRequestProperty("Cache-Control", "no-cache, no-store");

                applyHeaders(connection, requestNetwork.getHeaders());
                writeBodyIfNeeded(connection, requestNetwork, method);

                int code = connection.getResponseCode();
                InputStream source = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
                String responseBody = source == null ? "" : readUtf8(source).trim();
                HashMap<String, Object> responseHeaders = mapHeaders(connection.getHeaderFields());

                requestNetwork.getActivity().runOnUiThread(() -> requestListener.onResponse(tag, responseBody, responseHeaders));
            } catch (SSLHandshakeException e) {
                String detail = e.getMessage() == null || e.getMessage().isEmpty() ? "unknown TLS handshake error" : e.getMessage();
                String errorMessage = "TLS handshake failed: " + detail;
                requestNetwork.getActivity().runOnUiThread(() -> requestListener.onErrorResponse(tag, errorMessage));
            } catch (Exception e) {
                String errorMessage = e.getMessage() == null || e.getMessage().isEmpty()
                        ? e.getClass().getSimpleName()
                        : e.getClass().getSimpleName() + ": " + e.getMessage();
                requestNetwork.getActivity().runOnUiThread(() -> requestListener.onErrorResponse(tag, errorMessage));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private static String buildFinalUrl(String url, RequestNetwork requestNetwork, String method) throws IOException {
        if (!GET.equals(method) || requestNetwork.getRequestType() != REQUEST_PARAM || requestNetwork.getParams().isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        if (!url.contains("?")) {
            sb.append('?');
        } else if (!url.endsWith("&") && !url.endsWith("?")) {
            sb.append('&');
        }

        boolean first = true;
        for (Map.Entry<String, Object> param : requestNetwork.getParams().entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(URLEncoder.encode(param.getKey(), StandardCharsets.UTF_8.name()));
            sb.append('=');
            sb.append(URLEncoder.encode(String.valueOf(param.getValue()), StandardCharsets.UTF_8.name()));
        }
        return sb.toString();
    }

    private static void applyHeaders(HttpURLConnection connection, HashMap<String, Object> headers) {
        for (Map.Entry<String, Object> header : headers.entrySet()) {
            connection.setRequestProperty(header.getKey(), String.valueOf(header.getValue()));
        }
    }

    private static void writeBodyIfNeeded(HttpURLConnection connection, RequestNetwork requestNetwork, String method) throws IOException {
        if (GET.equals(method)) {
            return;
        }

        String body;
        String contentType;
        if (requestNetwork.getRequestType() == REQUEST_BODY) {
            body = new JSONObject(requestNetwork.getParams()).toString();
            contentType = "application/json; charset=utf-8";
        } else {
            body = toFormEncoded(requestNetwork.getParams());
            contentType = "application/x-www-form-urlencoded; charset=utf-8";
        }

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setRequestProperty("Content-Length", String.valueOf(payload.length));

        try (OutputStream out = new BufferedOutputStream(connection.getOutputStream())) {
            out.write(payload);
            out.flush();
        }
    }

    private static String toFormEncoded(HashMap<String, Object> params) throws IOException {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            first = false;
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.name()));
            sb.append('=');
            sb.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8.name()));
        }
        return sb.toString();
    }

    private static HashMap<String, Object> mapHeaders(Map<String, List<String>> headerFields) {
        HashMap<String, Object> map = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : headerFields.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            List<String> values = entry.getValue();
            map.put(key, values == null || values.isEmpty() ? "" : values.get(0));
        }
        return map;
    }

    private static String readUtf8(InputStream in) throws IOException {
        try (InputStream source = new BufferedInputStream(in);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = source.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }
}
