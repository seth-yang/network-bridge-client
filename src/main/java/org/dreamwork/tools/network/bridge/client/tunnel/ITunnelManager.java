package org.dreamwork.tools.network.bridge.client.tunnel;

import java.util.concurrent.TimeoutException;

public interface ITunnelManager {
    void attach () throws TimeoutException;
    void detach ();
    void updateReconnectTimeout (int timeout);

    void onManagerAttached ();
    void onManagerClose ();
}
