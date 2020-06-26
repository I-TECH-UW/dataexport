package org.itech.fhir.dataexport.core.service.impl;

import org.itech.fhir.dataexport.core.service.CrudService;
import org.springframework.data.repository.CrudRepository;

public abstract class CrudServiceImpl<T, ID> implements CrudService<T, ID> {

	private CrudRepository<T, ID> repository;

	public CrudServiceImpl(CrudRepository<T, ID> repository) {
		this.repository = repository;
	}

	@Override
	public CrudRepository<T, ID> getDAO() {
		return repository;
	}

}
