package com.example.urbanagent;

import com.example.urbanagent.ai.application.ModelProperties;
import com.example.urbanagent.ai.integration.OpenAICompatibleModelClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAICompatibleModelClientTest {

    @Test
    void shouldUseDedicatedEmbeddingEndpointWithoutChatAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ModelProperties properties = new ModelProperties();
        properties.setBaseUrl("http://chat.local");
        properties.setApiKey("chat-secret");
        properties.setEmbeddingBaseUrl("http://embedding.local");
        properties.setEmbeddingModel("text-embedding-bge-base-zh-v1.5");
        properties.setConnectTimeoutMs(0);
        properties.setReadTimeoutMs(0);
        OpenAICompatibleModelClient client = new OpenAICompatibleModelClient(builder, properties);

        server.expect(requestTo("http://embedding.local/v1/embeddings"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> assertThat(request.getHeaders().containsKey("Authorization")).isFalse())
                .andRespond(withSuccess("""
                        {"data":[{"index":0,"embedding":[0.1,0.2,0.3]}]}
                        """, MediaType.APPLICATION_JSON));

        float[] vector = client.embed("测试文本");

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify();
    }
}
