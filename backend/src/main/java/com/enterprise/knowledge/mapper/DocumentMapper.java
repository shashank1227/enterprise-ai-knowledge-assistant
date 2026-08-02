package com.enterprise.knowledge.mapper;

import com.enterprise.knowledge.domain.Document;
import com.enterprise.knowledge.dto.response.DocumentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for Document entity to DTO conversion.
 */
@Mapper(componentModel = "spring")
public interface DocumentMapper {

    @Mapping(target = "uploadedBy", source = "uploadedBy.id")
    @Mapping(target = "uploadedByName", source = "uploadedBy.fullName")
    @Mapping(target = "chunkCount", expression = "java(document.getChunks().size())")
    DocumentResponse toDocumentResponse(Document document);
}
