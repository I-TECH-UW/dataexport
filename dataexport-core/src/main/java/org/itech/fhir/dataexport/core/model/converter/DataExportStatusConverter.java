package org.itech.fhir.dataexport.core.model.converter;

import java.util.stream.Stream;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;

@Converter
public class DataExportStatusConverter implements AttributeConverter<DataExportStatus, Character> {

	@Override
	public Character convertToDatabaseColumn(DataExportStatus dataExportStatus) {
		if (dataExportStatus == null) {
			return null;
		}
		return dataExportStatus.getCode();
	}

	@Override
	public DataExportStatus convertToEntityAttribute(Character code) {
		if (code == null) {
			return null;
		}

		return Stream.of(DataExportStatus.values()).filter(dataExportStatus -> code.equals(dataExportStatus.getCode()))
				.findFirst()
				.orElseThrow(IllegalArgumentException::new);
	}

}

