package io.wulfcodes.messaging.auth.service.impl;

import io.wulfcodes.messaging.auth.exception.UserNotFoundException;
import io.wulfcodes.messaging.auth.mapper.UserMapper;
import io.wulfcodes.messaging.auth.model.dto.response.UserResponse;
import io.wulfcodes.messaging.auth.model.dto.response.UserSummaryResponse;
import io.wulfcodes.messaging.auth.model.po.User;
import io.wulfcodes.messaging.auth.repository.UserRepository;
import io.wulfcodes.messaging.auth.service.spec.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserResponse getCurrentUser(String userId) {
        return userMapper.toResponse(findUser(userId));
    }

    @Override
    public UserSummaryResponse getById(String userId) {
        return userMapper.toSummary(findUser(userId));
    }

    @Override
    public List<UserSummaryResponse> searchByUsername(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        return userMapper.toSummaries(userRepository.findTop20ByUsernameStartingWithIgnoreCaseOrderByUsernameAsc(prefix.trim()));
    }

    private User findUser(String userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }
}
