package cl.sgl.service;

import cl.sgl.dto.CreateLegalServiceRequest;
import cl.sgl.dto.LegalServiceResponse;
import cl.sgl.dto.UpdateLegalServiceRequest;
import cl.sgl.entity.LegalService;
import cl.sgl.exception.ResourceNotFoundException;
import cl.sgl.repository.LegalServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de lógica de negocio para gestión de servicios legales.
 * Maneja CRUD de servicios, validaciones y transformaciones DTO.
 *
 * Historia: SGL-052 ADM-SERV-CRUD
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class LegalServiceService {

    private final LegalServiceRepository serviceRepository;

    /**
     * Crea un nuevo servicio.
     *
     * @param request DTO con datos del nuevo servicio
     * @return DTO con los datos del servicio creado
     * @throws IllegalArgumentException si el nombre ya existe
     */
    public LegalServiceResponse createService(CreateLegalServiceRequest request) {
        log.info("Creando nuevo servicio: {}", request.getName());

        // Validar que el nombre sea único
        if (serviceRepository.existsByName(request.getName())) {
            log.warn("Intento de crear servicio con nombre duplicado: {}", request.getName());
            throw new IllegalArgumentException("Ya existe un servicio con el nombre: " + request.getName());
        }

        // Crear la entidad
        LegalService service = LegalService.builder()
            .name(request.getName())
            .description(request.getDescription())
            .price(request.getPrice())
            .active(request.getActive() != null ? request.getActive() : true)
            .build();

        // Guardar en base de datos
        LegalService savedService = serviceRepository.save(service);
        log.info("Servicio creado exitosamente con ID: {}", savedService.getId());

        return mapToResponse(savedService);
    }

    /**
     * Obtiene un servicio por ID.
     *
     * @param id ID del servicio
     * @return DTO con los datos del servicio
     * @throws ResourceNotFoundException si el servicio no existe
     */
    public LegalServiceResponse getServiceById(Long id) {
        log.debug("Obteniendo servicio con ID: {}", id);

        LegalService service = serviceRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Servicio no encontrado con ID: {}", id);
                return new ResourceNotFoundException("Servicio con ID " + id + " no encontrado");
            });

        return mapToResponse(service);
    }

    /**
     * Obtiene todos los servicios.
     *
     * @return Lista de DTOs con datos de todos los servicios
     */
    public List<LegalServiceResponse> getAllServices() {
        log.debug("Obteniendo todos los servicios");

        List<LegalService> services = serviceRepository.findAll();
        return services.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los servicios activos.
     *
     * @return Lista de DTOs con datos de servicios activos
     */
    public List<LegalServiceResponse> getActiveServices() {
        log.debug("Obteniendo servicios activos");

        List<LegalService> services = serviceRepository.findByActiveTrue();
        return services.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Actualiza un servicio existente.
     *
     * @param id ID del servicio a actualizar
     * @param request DTO con los datos a actualizar
     * @return DTO con los datos del servicio actualizado
     * @throws ResourceNotFoundException si el servicio no existe
     * @throws IllegalArgumentException si el nombre ya existe en otro servicio
     */
    public LegalServiceResponse updateService(Long id, UpdateLegalServiceRequest request) {
        log.info("Actualizando servicio con ID: {}", id);

        LegalService service = serviceRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Servicio no encontrado para actualizar, ID: {}", id);
                return new ResourceNotFoundException("Servicio con ID " + id + " no encontrado");
            });

        // Validar que el nuevo nombre sea único (si se proporciona y es diferente)
        if (request.getName() != null &&
            !request.getName().equals(service.getName()) &&
            serviceRepository.existsByName(request.getName())) {
            log.warn("Intento de actualizar servicio con nombre duplicado: {}", request.getName());
            throw new IllegalArgumentException("Ya existe un servicio con el nombre: " + request.getName());
        }

        // Actualizar solo los campos proporcionados
        if (request.getName() != null) {
            service.setName(request.getName());
        }
        if (request.getDescription() != null) {
            service.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            service.setPrice(request.getPrice());
        }
        if (request.getActive() != null) {
            service.setActive(request.getActive());
        }

        LegalService updatedService = serviceRepository.save(service);
        log.info("Servicio actualizado exitosamente, ID: {}", id);

        return mapToResponse(updatedService);
    }

    /**
     * Elimina un servicio por ID.
     *
     * @param id ID del servicio a eliminar
     * @throws ResourceNotFoundException si el servicio no existe
     */
    public void deleteService(Long id) {
        log.info("Eliminando servicio con ID: {}", id);

        LegalService service = serviceRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Servicio no encontrado para eliminar, ID: {}", id);
                return new ResourceNotFoundException("Servicio con ID " + id + " no encontrado");
            });

        serviceRepository.deleteById(id);
        log.info("Servicio eliminado exitosamente, ID: {}", id);
    }

    /**
     * Convierte una entidad LegalService a su DTO LegalServiceResponse.
     *
     * @param service Entidad LegalService
     * @return DTO LegalServiceResponse
     */
    private LegalServiceResponse mapToResponse(LegalService service) {
        return LegalServiceResponse.builder()
            .id(service.getId())
            .name(service.getName())
            .description(service.getDescription())
            .price(service.getPrice())
            .active(service.getActive())
            .createdAt(service.getCreatedAt())
            .updatedAt(service.getUpdatedAt())
            .build();
    }
}
