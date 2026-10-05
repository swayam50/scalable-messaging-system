package io.wulfcodes.messaging.auth.service.spec;

import io.wulfcodes.messaging.common.model.dto.response.UserResponse;
import io.wulfcodes.messaging.common.model.dto.response.UserSummaryResponse;

import java.util.List;

public interface UserService {

    UserResponse getCurrentUser(String userId);

    UserSummaryResponse getById(String userId);

    List<UserSummaryResponse> searchByUsername(String prefix);
}
