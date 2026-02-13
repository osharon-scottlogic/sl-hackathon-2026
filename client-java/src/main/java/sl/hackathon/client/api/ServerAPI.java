package sl.hackathon.client.api;

import sl.hackathon.client.dtos.Action;
import java.io.IOException;

public interface ServerAPI {
    void connect(String serverURL) throws Exception;

    void send(String playerId, Action[] actions) throws IOException;

    void close();

    boolean isConnected();
}
