package org.itech.fhir.dataexport.api.service.impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.ResourceType;
import org.itech.fhir.dataexport.api.service.DataExportService;
import org.itech.fhir.dataexport.api.service.DataExportStatusService;
import org.itech.fhir.dataexport.core.dao.DataExportAttemptDAO;
import org.itech.fhir.dataexport.core.model.DataExportAttempt;
import org.itech.fhir.dataexport.core.model.DataExportAttempt.DataExportStatus;
import org.itech.fhir.dataexport.core.model.DataExportTask;
import org.itech.fhir.dataexport.core.service.DataExportTaskService;
import org.itech.fhir.dataexport.core.service.FhirClientFetcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.AdditionalRequestHeadersInterceptor;
import ca.uhn.fhir.rest.param.DateRangeParam;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@Transactional
public class DataExportServiceImpl implements DataExportService {

    @Value("${org.openelisglobal.fhirstore.uri}")
    private String localFhirStore;

    @Value("${org.openelisglobal.fhir.subscriber}")
    private String defaultRemoteServer;

    @Value("${org.openelisglobal.fhir.subscriber.resources}")
    private String[] defaultResources;

    @Value("${org.openelisglobal.fhir.subscriber.resource.singleTransaction:false}")
    private Boolean singleTransaction;

    @Value("${org.openelisglobal.fhir.subscriber.resource.allowParallel:false}")
    private Boolean allowParallel;

    private Set<Long> runningTasks = new HashSet<>();

    private DataExportTaskService dataExportTaskService;
    private DataExportAttemptDAO dataExportAttemptDAO;
    private DataExportStatusService dataExportStatusService;
    private FhirContext fhirContext;
    private FhirClientFetcher clientFetcher;

    public DataExportServiceImpl(DataExportTaskService dataExportTaskService, DataExportAttemptDAO dataExportAttemptDAO,
            DataExportStatusService dataExportStatusService, FhirContext fhirContext, FhirClientFetcher clientFetcher) {
        this.dataExportTaskService = dataExportTaskService;
        this.dataExportAttemptDAO = dataExportAttemptDAO;
        this.dataExportStatusService = dataExportStatusService;
        this.fhirContext = fhirContext;
        this.clientFetcher = clientFetcher;
    }

    @Override
    @Async
    public Future<DataExportStatus> exportNewDataFromLocalToRemote(DataExportTask dataExportTask) {
        if (allowParallel || !taskIsRunning(dataExportTask)) {
            runningTasks.add(dataExportTask.getId());
            DataExportAttempt dataExportAttempt = dataExportAttemptDAO.save(new DataExportAttempt(dataExportTask));
            DataExportStatus status = runDataExportAttempt(dataExportAttempt);
            runningTasks.remove(dataExportTask.getId());
            return new AsyncResult<>(status);
        } else {
            log.warn(
                    "export for this task is already running. Parallel exports for the same task are not allowed in the current configuration");
            return new AsyncResult<>(DataExportStatus.NOT_RAN);
        }
    }

    private boolean taskIsRunning(DataExportTask dataExportTask) {
        return runningTasks.contains(dataExportTask.getId());
    }

    private DataExportStatus runDataExportAttempt(DataExportAttempt dataExportAttempt) {
        List<Bundle> localBundles = new ArrayList<>();
        DataExportStatus status = getBundlesFromLocalServer(dataExportAttempt, localBundles);
        if (status.equals(DataExportStatus.COLLECTED)) {
            status = sendBundlesToRemote(dataExportAttempt, localBundles);
        }

        return status;
    }

    private DataExportStatus getBundlesFromLocalServer(DataExportAttempt dataExportAttempt, List<Bundle> localBundles) {

        DataExportTask dataExportTask = dataExportAttempt.getDataExportTask();
        Instant lastSuccess = dataExportTaskService.getLatestSuccessInstantForDataExportTask(dataExportTask);
        DateRangeParam dateRange = new DateRangeParam().setLowerBoundInclusive(Date.from(lastSuccess))
                .setUpperBoundInclusive(Date.from(dataExportAttempt.getStartTime()));
        Bundle bundle = null;
        try {
            dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.REQUESTING);

            IGenericClient sourceFhirClient = clientFetcher.getFhirClient(localFhirStore);

            for (String resource : defaultResources) {
                Bundle localSearchBundle = sourceFhirClient//
                        .search()//
                        .forResource(resource)//
                        .lastUpdated(dateRange)//
                        .returnBundle(Bundle.class).execute();
                localBundles.add(localSearchBundle);
                bundle = localSearchBundle;
                log.trace("received json " + fhirContext.newJsonParser().encodeResourceToString(localSearchBundle));
                while (localSearchBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
                    localSearchBundle = sourceFhirClient.loadPage().next(localSearchBundle).execute();
                    log.trace("received json " + fhirContext.newJsonParser().encodeResourceToString(localSearchBundle));
                    localBundles.add(localSearchBundle);
                }
            }
        } catch (RuntimeException e) {
            log.error("error occured while retrieving resources from local fhir store", e);
            log.error(getStackTrace(e));
            if (bundle != null) {
                log.trace(fhirContext.newJsonParser().encodeResourceToString(bundle));
            }
            dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.FAILED);
            return DataExportStatus.FAILED;
        }
        log.debug("dataexport: number of bundles collected: " + localBundles.size());
        dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.COLLECTED);
        return DataExportStatus.COLLECTED;
    }

    private DataExportStatus sendBundlesToRemote(DataExportAttempt dataExportAttempt, List<Bundle> localSearchBundles) {
        boolean anyTransactionSucceeded = false;
        Bundle bundle = null;
        int count = 0;
        try {
            dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.EXPORTING);

            IGenericClient remoteFhirClient = clientFetcher.getFhirClient(defaultRemoteServer);
            AdditionalRequestHeadersInterceptor interceptor = new AdditionalRequestHeadersInterceptor();
            Map<String, String> headers = dataExportAttempt.getDataExportTask().getHeaders();

            for (Entry<String, String> header : headers.entrySet()) {
                interceptor.addHeaderValue(header.getKey(), header.getValue());
            }
            remoteFhirClient.registerInterceptor(interceptor);
            if (singleTransaction) {
                log.info("exporting FHIR resources in a single transaction");
                localSearchBundles = translateBundlesToSingleBundle(localSearchBundles);
            } else {
                log.info("exporting FHIR resources in multiple transactions");
            }

            log.info("dataexport: number of bundles to export: " + localSearchBundles.size());

            for (Bundle localSearchBundle : localSearchBundles) {
                bundle = localSearchBundle;
                Bundle transactionBundle = createTransactionBundleFromSearchResponseBundle(localSearchBundle);
                bundle = transactionBundle;
                if (transactionBundle.hasEntry()) {
                    log.trace("sending bundle to remote: "
                            + fhirContext.newJsonParser().encodeResourceToString(transactionBundle));
                    Bundle transactionResponseBundle = remoteFhirClient//
                            .transaction()//
                            .withBundle(transactionBundle).execute();
                    log.trace("received transaction response bundle from remote: "
                            + fhirContext.newJsonParser().encodeResourceToString(transactionResponseBundle));
                    anyTransactionSucceeded = true;
                } else {
                    log.trace("empty transaction bundle. not sending to remote");
                }
                ++count;
            }
            dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, DataExportStatus.SUCCEEDED);
            return DataExportStatus.SUCCEEDED;
        } catch (RuntimeException e) {
            log.error("error occured while sending resources to remote fhir store. Sent " + count
                    + " bundles successfully", e);
            log.error(getStackTrace(e));
            if (bundle != null) {
                log.trace(fhirContext.newJsonParser().encodeResourceToString(bundle));
            }
            DataExportStatus status = DataExportStatus.FAILED;
            if (anyTransactionSucceeded) {
                status = DataExportStatus.INCOMPLETE;
            }
            dataExportStatusService.changeDataRequestAttemptStatus(dataExportAttempt, status);
            return status;
        }
    }

    private List<Bundle> translateBundlesToSingleBundle(List<Bundle> localSearchBundles) {
        Bundle bundle = new Bundle();
        for (Bundle localSearchBundle : localSearchBundles) {
            for (BundleEntryComponent bundleEntry : localSearchBundle.getEntry()) {
                bundle.addEntry(bundleEntry);
            }
        }
        return Arrays.asList(bundle);
    }

    private Bundle createTransactionBundleFromSearchResponseBundle(Bundle searchBundle) {
        Bundle transactionBundle = new Bundle();
        transactionBundle.setType(BundleType.TRANSACTION);
        for (BundleEntryComponent searchComponent : searchBundle.getEntry()) {
            if (searchComponent.hasResource() && searchComponent.getResource() instanceof DomainResource) {
                DomainResource resource = (DomainResource) searchComponent.getResource();
                if (!resource.hasExtension("http://hapifhir.io/fhir/StructureDefinition/resource-placeholder")
                        || !((BooleanType) resource
                                .getExtensionByUrl("http://hapifhir.io/fhir/StructureDefinition/resource-placeholder")
                                .getValue()).booleanValue()) {
                    BundleEntryComponent transactionComponent = createTransactionComponentFromSearchComponent(
                            searchComponent);
                    transactionBundle.addEntry(transactionComponent);
                    transactionBundle.setTotal(transactionBundle.getTotal() + 1);
                }
            }
        }
        return transactionBundle;
    }

    private BundleEntryComponent createTransactionComponentFromSearchComponent(BundleEntryComponent searchComponent) {
        ResourceType resourceType = searchComponent.getResource().getResourceType();
        String sourceResourceId = searchComponent.getResource().getIdElement().getIdPart();
        if (StringUtils.isNumeric(sourceResourceId)) {
            throw new IllegalArgumentException("id cannot be a number. Numbers are reserved for local entities only");
        }

        BundleEntryComponent transactionComponent = new BundleEntryComponent();
        transactionComponent.setResource(searchComponent.getResource());

        transactionComponent.getRequest().setMethod(HTTPVerb.PUT);
        transactionComponent.getRequest().setUrl(resourceType + "/" + sourceResourceId);

        return transactionComponent;
    }

    public static String getStackTrace(final Throwable throwable) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

}
