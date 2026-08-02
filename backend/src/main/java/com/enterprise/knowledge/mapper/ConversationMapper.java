package com.enterprise.knowledge.mapper;

import com.enterprise.knowledge.domain.Conversation;
import com.enterprise.knowledge.dto.response.ConversationResponse;
import org.mapstruct.Mapper;

/**
 * MapStruct mapper for Conversation entity to DTO conversion.
 */
@Mapper(componentModel = "spring")
public interface ConversationMapper {

    ConversationResponse toConversationResponse(Conversation conversation);
}
