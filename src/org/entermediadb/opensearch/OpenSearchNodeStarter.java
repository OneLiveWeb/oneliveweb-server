package org.entermediadb.opensearch;
import java.nio.file.Path;
import java.util.Collections;

import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.node.InternalSettingsPreparer;
import org.opensearch.node.Node;
import org.opensearch.transport.Netty4Plugin;

public class OpenSearchNodeStarter {

    private Node node;

    public void startNode(Path dataDirectory) {
        Settings settings = Settings.builder()
                .put("path.home", dataDirectory)
                .put("http.enabled", "true")
                .build();

        this.node = new Node(InternalSettingsPreparer.prepareEnvironment(settings, null, Collections.emptyMap(), null), 
                            Collections.singletonList(Netty4Plugin.class)).start();
    }

    public RestHighLevelClient createClient() {
        return new RestHighLevelClient(RestClient.builder(node.getHttpServerTransport().boundAddress().publishAddress()));
    }

    public void stopNode() {
        try {
            if (node != null) {
                node.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
