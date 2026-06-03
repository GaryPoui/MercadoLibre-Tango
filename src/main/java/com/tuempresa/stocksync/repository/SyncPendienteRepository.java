package com.tuempresa.stocksync.repository;

import com.tuempresa.stocksync.model.SyncPendiente;
import com.tuempresa.stocksync.model.SyncPendiente.EstadoSync;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SyncPendienteRepository extends JpaRepository<SyncPendiente, Long> {

    @Query("SELECT sp FROM SyncPendiente sp WHERE sp.estado = :estado " +
           "AND sp.proximoIntento <= :ahora ORDER BY sp.createdAt ASC")
    List<SyncPendiente> findPendientesListos(EstadoSync estado, LocalDateTime ahora);

    long countByEstado(EstadoSync estado);

    List<SyncPendiente> findBySkuAndEstado(String sku, EstadoSync estado);
}
