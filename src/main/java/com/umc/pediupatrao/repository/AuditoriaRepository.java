package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface AuditoriaRepository extends Repository<RegistroAuditoria, String>{

    RegistroAuditoria save(RegistroAuditoria registro);

    List<RegistroAuditoria> findAllByOrderByDataHoraDesc();

    List<RegistroAuditoria> findByEntidadeOrderByDataHoraDesc(String entidade);
}
