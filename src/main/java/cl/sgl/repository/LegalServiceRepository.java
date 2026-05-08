package cl.sgl.repository;

import cl.sgl.entity.LegalService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio para la entidad LegalService.
 * Proporciona métodos de acceso a datos para servicios.
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@Repository
public interface LegalServiceRepository extends JpaRepository<LegalService, Long> {

    /**
     * Busca un servicio por su nombre.
     *
     * @param name Nombre del servicio
     * @return Optional con el servicio si existe
     */
    Optional<LegalService> findByName(String name);

    /**
     * Busca todos los servicios activos.
     *
     * @return Lista de servicios activos
     */
    List<LegalService> findByActiveTrue();

    /**
     * Verifica si existe un servicio con el nombre indicado
     * (para validar unicidad en creación/edición).
     *
     * @param name Nombre del servicio
     * @return true si existe
     */
    boolean existsByName(String name);
}
