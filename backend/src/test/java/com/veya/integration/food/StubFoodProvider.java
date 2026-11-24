package com.veya.integration.food;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Controlled test double of the Open Food Facts API: a local JDK HTTP server whose behaviour each test chooses
 * (normal answer, HTTP 500, slow answer for the timeout path). No network access to the real provider is needed.
 */
public final class StubFoodProvider implements AutoCloseable {

    public enum Mode { OK, ERROR, SLOW }

    public static final String BODY = """
            {"products":[
              {"code":"4000000000001","product_name":"Quinoa, cooked","nutriments":{"energy-kcal_100g":120,
               "proteins_100g":4.4,"fat_100g":1.92,"carbohydrates_100g":21.3}},
              {"code":"4000000000002","product_name":"Implausible","nutriments":{"energy-kcal_100g":2000,
               "proteins_100g":1,"fat_100g":1,"carbohydrates_100g":1}},
              {"code":"","product_name":"No code","nutriments":{"energy-kcal_100g":100,"proteins_100g":1,
               "fat_100g":1,"carbohydrates_100g":1}},
              {"code":"4000000000003","product_name":"Missing macros","nutriments":{"energy-kcal_100g":100}}
            ]}""";

    private final HttpServer server;
    private volatile Mode mode = Mode.OK;

    public StubFoodProvider() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        server.createContext("/cgi/search.pl", exchange -> {
            try {
                if (mode == Mode.SLOW) {
                    Thread.sleep(1500);
                }
                byte[] body = (mode == Mode.ERROR ? "{\"error\":true}" : BODY).getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(mode == Mode.ERROR ? 500 : 200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // client gave up (timeout test)
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void mode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
