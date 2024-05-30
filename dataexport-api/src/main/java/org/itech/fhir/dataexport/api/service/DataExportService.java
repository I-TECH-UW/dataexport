package org.itech.fhir.dataexport.api.service;

import java.util.concurrent.Future;

import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.itech.fhir.dataexport.core.model.DataExportTask;

public interface DataExportService {

    Future<DataExportStatus> exportNewDataFromLocalToRemote(DataExportTask dataExportTask);

}
