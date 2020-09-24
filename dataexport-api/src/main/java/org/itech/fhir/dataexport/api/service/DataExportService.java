package org.itech.fhir.dataexport.api.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.itech.fhir.dataexport.core.model.DataExportTask;

public interface DataExportService {

	Future<DataExportStatus> exportNewDataFromSourceToRemote(DataExportTask dataExportTask,
			DataExportSource dataExportSource)
			throws InterruptedException, ExecutionException, TimeoutException;

}
