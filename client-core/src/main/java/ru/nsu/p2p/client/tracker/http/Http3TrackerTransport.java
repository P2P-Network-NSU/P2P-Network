package ru.nsu.p2p.client.tracker.http;

import static ru.nsu.p2p.client.error.ClientOperationExceptions.createInvalidStateException;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.KeyStore;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import ru.nsu.p2p.client.http.AbstractHttp3Transport;
import tech.kwik.flupke.Http3ClientBuilder;

final class Http3TrackerTransport extends AbstractHttp3Transport {

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String DEFAULT_KEYSTORE_RESOURCE = "client-truststore.p12";
    private static final String DEFAULT_KEY_PASSWORD = "changeit";

    private final X509TrustManager trustManager;

    Http3TrackerTransport(int readTimeoutMs) {
        super(readTimeoutMs);
        try {
            this.trustManager = loadTrustManager(
                DEFAULT_KEYSTORE_RESOURCE,
                DEFAULT_KEY_PASSWORD.toCharArray()
            );
        } catch (RuntimeException e) {
            close();
            throw e;
        }
    }

    @Override
    protected HttpClient createHttpClient() {
        try {
            return new Http3ClientBuilder()
                .trustManager(trustManager)
                .build();
        } catch (Exception e) {
            throw createInvalidStateException(
                "Ошибка инициализации HTTP/3 клиента трекера: " + e.getMessage()
            );
        }
    }

    private static X509TrustManager loadTrustManager(String resourceName, char[] password) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KEYSTORE_TYPE);
            try (InputStream is = Http3TrackerTransport.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
                if (is == null) {
                    throw new IllegalStateException(
                        "Файл " + resourceName + " не найден в ресурсах"
                    );
                }
                trustStore.load(is, password);
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
            );
            tmf.init(trustStore);

            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager x509TrustManager) {
                    return x509TrustManager;
                }
            }
            throw new IllegalStateException("X509TrustManager не найден в TrustManagerFactory");
        } catch (Exception e) {
            throw createInvalidStateException(
                "Ошибка загрузки truststore трекера: " + e.getMessage()
            );
        }
    }
}