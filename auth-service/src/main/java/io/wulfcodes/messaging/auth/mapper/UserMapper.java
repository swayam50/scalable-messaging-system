package io.wulfcodes.messaging.auth.mapper;

import io.wulfcodes.messaging.common.model.dto.response.UserResponse;
import io.wulfcodes.messaging.common.model.dto.response.UserSummaryResponse;
import io.wulfcodes.messaging.auth.model.po.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * po -> dto conversion. MapStruct generates the implementation at compile time
 * (no reflection at runtime), and fails the build if a target field is left unmapped.
 */
@Mapper
public interface UserMapper {

    @Mapping(target = "createdAt", source = "audit.createdAt")
    UserResponse toResponse(User user);

    UserSummaryResponse toSummary(User user);

    List<UserSummaryResponse> toSummaries(List<User> users);
}
