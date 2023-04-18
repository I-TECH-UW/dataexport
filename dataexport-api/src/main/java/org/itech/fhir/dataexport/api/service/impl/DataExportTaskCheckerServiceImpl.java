package org.itech.fhir.dataexport.api.service.impl;

import java.time.Instant;
import java.util.Optional;

import org.itech.fhir.dataexport.api.service.DataExportService;
import org.itech.fhir.dataexport.api.service.DataExportTaskCheckerService;
import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.itech.fhir.dataexport.core.service.DataExportTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DataExportTaskCheckerServiceImpl implements DataExportTaskCheckerService {

    @Value("${org.openelisglobal.fhir.subscriber}")
    private Optional<String> defaultRemoteServer;

    private DataExportTaskService dataExportTaskService;
    private DataExportService dataExportService;

    public DataExportTaskCheckerServiceImpl(DataExportTaskService dataExportTaskService,
            DataExportService dataExportService) {
        log.info(this.getClass().getName() + " has started");
        this.dataExportTaskService = dataExportTaskService;
        this.dataExportService = dataExportService;
    }

    @Override
    @Scheduled(initialDelay = 10 * 1000, fixedRate = 60 * 1000)
    @Transactional
    public void checkDataRequestNeedsRunning() {
        if (defaultRemoteServer.isPresent() && !"".equals(defaultRemoteServer.get())) {
            log.trace("checking if servers need data import to be done");

            Iterable<DataExportTask> dataExportTasks = dataExportTaskService.getDAO().findAll();
            for (DataExportTask dataExportTask : dataExportTasks) {
                if (maximumTimeHasPassed(dataExportTask)) {
                    log.debug("server found with dataExportTask needing to be run");
                    log.debug("data export task with id: " + dataExportTask.getId() + " is already running");
                    dataExportService.exportNewDataFromLocalToRemote(dataExportTask);
                }
            }
        } else {
            log.debug("not starting data request as there is no remote server specified to send it to");
        }
    }

    private boolean maximumTimeHasPassed(DataExportTask dataExportTask) {
        if (dataExportTask.getMaxDataExportInterval() == 0 || dataExportTask.getMaxDataExportInterval() == null) {
            return false;
        }
        Instant now = Instant.now();
        Instant lastAttemptInstant = dataExportTaskService.getLatestInstantForDataExportTask(dataExportTask);
        Instant nextScheduledRuntime = lastAttemptInstant.plus(dataExportTask.getMaxDataExportInterval(),
                DataExportTask.MAX_INTERVAL_UNITS);
        return now.isAfter(nextScheduledRuntime);
    }

}
