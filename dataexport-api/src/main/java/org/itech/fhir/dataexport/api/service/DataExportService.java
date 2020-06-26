package org.itech.fhir.dataexport.api.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;

public interface DataExportService {

	Future<DataExportStatus> exportNewDataFromLocalToRemote(DataExportTask dataExportTask)
			throws InterruptedException, ExecutionException, TimeoutException;

}
