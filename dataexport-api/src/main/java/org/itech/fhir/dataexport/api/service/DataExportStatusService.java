package org.itech.fhir.dataexport.api.service;

import org.itech.fhir.dataexport.api.service.event.DataExportStatusEvent;
import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;

public interface DataExportStatusService {

	void changeDataRequestAttemptStatus(DataExportAttempt dataExportAttempt, DataExportStatus dataExportStatus);

	void onApplicationEvent(DataExportStatusEvent event);

}
