package org.itech.fhir.dataexport.core.model;

import java.time.Instant;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

import org.itech.fhir.dataexport.core.model.base.PersistenceEntity;
import org.itech.fhir.dataexport.core.model.converter.DataExportStatusConverter;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "data_export_attempt")
public class DataExportAttempt extends PersistenceEntity<Long> {

	public enum DataExportStatus {
		GENERATED('G'), REQUESTING('R'), COLLECTED('C'), EXPORTING('E'), SUCCEEDED('S'), FAILED('F'), INCOMPLETE('I'),
		NOT_RAN('N');

		private char code;

		private DataExportStatus(char code) {
			this.code = code;
		}

		public char getCode() {
			return code;
		}
	}

	// persistence
	@Column(name = "start_time", updatable = false)
	// validation
	@NotNull
	private Instant startTime;

	@Column(name = "end_time")
	private Instant endTime;

	// persistence
	@ManyToOne
	@JoinColumn(name = "data_export_task_id", nullable = false, updatable = false)
	// validation
	@NotNull
	private DataExportTask dataExportTask;

	// persistence
	@Convert(converter = DataExportStatusConverter.class)
	@Column(name = "data_export_status")
	// validation
	@NotNull
	private DataExportStatus dataExportStatus;

	DataExportAttempt() {
	}

	public DataExportAttempt(DataExportTask dataExportTask) {
		startTime = Instant.now();
		dataExportStatus = DataExportStatus.GENERATED;
		this.dataExportTask = dataExportTask;
	}

}
