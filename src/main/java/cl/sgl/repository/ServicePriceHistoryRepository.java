package cl.sgl.repository;

import cl.sgl.entity.ServicePriceHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para el historial de precios de servicios.
 *
 * Historia: SGL-053 ADM-SERV-PRICE
 */
@Repository
public interface ServicePriceHistoryRepository extends JpaRepository<ServicePriceHistory, Long> {

    /**
     * Retorna el historial de precios de un servicio, ordenado del más reciente al más antiguo.
     */
    List<ServicePriceHistory> findByServiceIdOrderByFechaCambioDesc(Long serviceId);
}
