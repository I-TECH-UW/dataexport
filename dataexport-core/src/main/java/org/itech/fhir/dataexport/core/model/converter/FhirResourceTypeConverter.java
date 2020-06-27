package org.itech.fhir.dataexport.core.model.converter;

import java.util.stream.Stream;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.hl7.fhir.r4.model.ResourceType;

@Converter
public class FhirResourceTypeConverter implements AttributeConverter<ResourceType, String> {

	@Override
	public String convertToDatabaseColumn(ResourceType resourceType) {
		if (resourceType == null) {
			return null;
		}
		return resourceType.toString();
	}

	@Override
	public ResourceType convertToEntityAttribute(String code) {
		if (code == null) {
			return null;
		}

		return Stream.of(ResourceType.values()).filter(resourceType -> code.equals(resourceType.toString()))
				.findFirst()
				.orElseThrow(IllegalArgumentException::new);
	}

}
