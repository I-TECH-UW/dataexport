package org.itech.fhir.dataexport.api.service.event;

import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.springframework.context.ApplicationEvent;

import lombok.Getter;

@Getter
public class DataExportStatusEvent extends ApplicationEvent {

	private static final long serialVersionUID = -9058748235265104418L;

	private DataExportAttempt dataExportAttempt;
	private DataExportStatus dataExportStatus;

	public DataExportStatusEvent(Object source, DataExportAttempt dataExportAttempt,
			DataExportStatus dataExportStatus) {
		super(source);
		this.dataExportAttempt = dataExportAttempt;
		this.dataExportStatus = dataExportStatus;
	}

}
