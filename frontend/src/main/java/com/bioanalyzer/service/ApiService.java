package com.bioanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.bioanalyzer.MainApp;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ApiService {

    private final ObjectMapper jackson = new ObjectMapper();

    // ── Health check ───────────────────────────────────────────
    public boolean isBackendAlive() {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpGet req = new HttpGet(MainApp.BACKEND_URL + "/api/health");
            return client.execute(req, r -> r.getCode() == 200);
        } catch (IOException e) {
            return false;
        }
    }

    // ── Auto-detect sequence type ──────────────────────────────
    public String detectType(String sequence) throws IOException {
        ObjectNode body = jackson.createObjectNode();
        body.put("sequence", sequence);

        String response = post("/api/detect", jackson.writeValueAsString(body));
        JsonNode root = jackson.readTree(response);
        return root.get("type").asText();
    }

    // ── Main analysis ──────────────────────────────────────────
    public JsonNode analyze(String sequence, String type) throws IOException {
        ObjectNode body = jackson.createObjectNode();
        body.put("sequence", sequence);
        body.put("type", type);

        String response = post("/api/analyze", jackson.writeValueAsString(body));
        JsonNode root   = jackson.readTree(response);

        if (!root.has("success") || !root.get("success").asBoolean()) {
            throw new IOException(root.has("error")
                ? root.get("error").asText()
                : "Analysis failed");
        }
        return root.get("result");
    }

    // ── Mutation detection ─────────────────────────────────────
    public JsonNode detectMutations(String seq1, String seq2) throws IOException {
        ObjectNode body = jackson.createObjectNode();
        body.put("sequence1", seq1);
        body.put("sequence2", seq2);

        String response = post("/api/mutate", jackson.writeValueAsString(body));
        JsonNode root   = jackson.readTree(response);

        if (!root.has("success") || !root.get("success").asBoolean()) {
            throw new IOException(root.has("error")
                ? root.get("error").asText()
                : "Mutation analysis failed");
        }
        return root.get("result");
    }

    // ── Internal POST helper ───────────────────────────────────
    private String post(String endpoint, String json) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost req = new HttpPost(MainApp.BACKEND_URL + endpoint);
            req.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            return client.execute(req, response -> {
                int code = response.getCode();
                String body = new String(
                    response.getEntity().getContent().readAllBytes(),
                    StandardCharsets.UTF_8
                );
                if (code != 200) {
                    throw new IOException("Backend error " + code + ": " + body);
                }
                return body;
            });
        }
    }
}
