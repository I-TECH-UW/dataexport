package org.itech.fhir.dataexport.core.service;

import java.time.Instant;

import org.itech.fhir.dataexport.core.dao.DataExportTaskDAO;
import org.itech.fhir.dataexport.core.model.DataExportTask;

public interface DataExportTaskService extends CrudService<DataExportTask, Long> {

	@Override
	DataExportTaskDAO getDAO();

	Instant getLatestSuccessInstantForDataExportTask(DataExportTask dataExportTask);

	Instant getLatestInstantForDataExportTask(DataExportTask dataExportTask);

}
