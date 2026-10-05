package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.PresenceResponse;

import java.util.List;

public interface PresenceService {

    /** The user opened their first session on this node. */
    void userConnected(String userId);

    /**
     * The user's last session on this node closed.
     *
     * @param movingNodes true when the socket was closed only so the user reconnects to a new owner
     *                    node (rebalancing): the user is not really going offline, so contacts are
     *                    not notified
     */
    void userDisconnected(String userId, boolean movingNodes);

    /** Online state and last-seen time of the given users, asking each user's owner node. */
    List<PresenceResponse> presenceOf(List<String> userIds);
}
