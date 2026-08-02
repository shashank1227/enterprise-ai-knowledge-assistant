package com.enterprise.knowledge.mapper;

import com.enterprise.knowledge.domain.User;
import com.enterprise.knowledge.dto.response.UserProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * MapStruct mapper for User entity to DTO conversion.
 * Automatically generates implementation at compile time.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "roles", source = "user", qualifiedByName = "extractRoles")
    UserProfileResponse toUserProfileResponse(User user);

    @Named("extractRoles")
    default Set<String> extractRoles(User user) {
        return user.getUserRoles().stream()
            .map(role -> role.getName())
            .collect(Collectors.toSet());
    }
}
