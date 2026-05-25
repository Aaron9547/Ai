package com.aaron.cloud.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aaron.cloud.common.config.properties.AiRagProperties;
import java.util.List;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;

class ElasticsearchRagHostParserTest {

    @Test
    void resolveUsesConfigHostPorts() {
        AiRagProperties.Elasticsearch es = new AiRagProperties.Elasticsearch();
        es.getConfig().setHostPorts("192.168.37.17:19201;192.168.37.17:19202");
        String eff = ElasticsearchRagHostParser.resolveEffectiveUriString(es);
        assertEquals("http://192.168.37.17:19201,http://192.168.37.17:19202", eff);
        List<HttpHost> hosts = ElasticsearchRagHostParser.parseHttpHosts(eff);
        assertEquals(2, hosts.size());
        assertEquals("192.168.37.17", hosts.get(0).getHostName());
        assertEquals(19201, hosts.get(0).getPort());
    }

    @Test
    void resolveUsesConfigHostPorts_trimmed() {
        AiRagProperties.Elasticsearch es = new AiRagProperties.Elasticsearch();
        es.getConfig().setHostPorts("h.example:9200");
        String eff = ElasticsearchRagHostParser.resolveEffectiveUriString(es);
        assertTrue(eff.contains("http://h.example:9200"));
    }

    @Test
    void parseHttpHosts_splitsSemicolonInUris() {
        List<HttpHost> hosts =
                ElasticsearchRagHostParser.parseHttpHosts("http://a:9201;http://b:9202");
        assertEquals(2, hosts.size());
        assertEquals("a", hosts.get(0).getHostName());
        assertEquals("b", hosts.get(1).getHostName());
    }
}
