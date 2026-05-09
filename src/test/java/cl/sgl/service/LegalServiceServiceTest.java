package cl.sgl.service;

import cl.sgl.dto.CreateLegalServiceRequest;
import cl.sgl.dto.LegalServiceResponse;
import cl.sgl.dto.ServicePublicDTO;
import cl.sgl.dto.UpdateLegalServiceRequest;
import cl.sgl.entity.LegalService;
import cl.sgl.exception.ResourceNotFoundException;
import cl.sgl.repository.LegalServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para ServiceService.
 * Utiliza Mockito para aislar la lógica de negocio.
 *
 * Historias: SGL-052 ADM-SERV-CRUD, SGL-018 AG-SELECT-MAT
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ServiceService Tests")
class LegalServiceServiceTest {

    @Mock
    private LegalServiceRepository serviceRepository;

    @InjectMocks
    private LegalServiceService legalServiceService;

    private LegalService testService;
    private CreateLegalServiceRequest createRequest;
    private UpdateLegalServiceRequest updateRequest;

    @BeforeEach
    void setUp() {
        testService = LegalService.builder()
            .id(1L)
            .name("Divorcio Contencioso")
            .description("Trámite de divorcio con contestación")
            .price(new BigDecimal("500000"))
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        createRequest = CreateLegalServiceRequest.builder()
            .name("Divorcio Contencioso")
            .description("Trámite de divorcio con contestación")
            .price(new BigDecimal("500000"))
            .active(true)
            .build();

        updateRequest = UpdateLegalServiceRequest.builder()
            .name("Divorcio Contencioso Actualizado")
            .price(new BigDecimal("550000"))
            .build();
    }

    @Test
    @DisplayName("Crear servicio exitosamente")
    void testCreateService_Success() {
        // Arrange
        when(serviceRepository.existsByName(anyString())).thenReturn(false);
        when(serviceRepository.save(any(LegalService.class))).thenReturn(testService);

        // Act
        LegalServiceResponse response = legalServiceService.createService(createRequest);

        // Assert
        assertNotNull(response);
        assertEquals("Divorcio Contencioso", response.getName());
        assertEquals(new BigDecimal("500000"), response.getPrice());
        assertEquals(true, response.getActive());

        verify(serviceRepository, times(1)).existsByName("Divorcio Contencioso");
        verify(serviceRepository, times(1)).save(any(LegalService.class));
    }

    @Test
    @DisplayName("Crear servicio falla con nombre duplicado")
    void testCreateService_DuplicateName() {
        // Arrange
        when(serviceRepository.existsByName(anyString())).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            legalServiceService.createService(createRequest);
        });

        verify(serviceRepository, times(1)).existsByName("Divorcio Contencioso");
        verify(serviceRepository, never()).save(any(LegalService.class));
    }

    @Test
    @DisplayName("Obtener servicio por ID exitosamente")
    void testGetServiceById_Success() {
        // Arrange
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));

        // Act
        LegalServiceResponse response = legalServiceService.getServiceById(1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Divorcio Contencioso", response.getName());

        verify(serviceRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Obtener servicio por ID falla cuando no existe")
    void testGetServiceById_NotFound() {
        // Arrange
        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            legalServiceService.getServiceById(999L);
        });

        verify(serviceRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Obtener todos los servicios")
    void testGetAllServices_Success() {
        // Arrange
        LegalService service2 = LegalService.builder()
            .id(2L)
            .name("Herencias")
            .description("Trámite de herencias")
            .price(new BigDecimal("350000"))
            .active(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        when(serviceRepository.findAll()).thenReturn(List.of(testService, service2));

        // Act
        List<LegalServiceResponse> responses = legalServiceService.getAllServices();

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Divorcio Contencioso", responses.get(0).getName());
        assertEquals("Herencias", responses.get(1).getName());

        verify(serviceRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Obtener servicios activos")
    void testGetActiveServices_Success() {
        // Arrange
        when(serviceRepository.findByActiveTrue()).thenReturn(List.of(testService));

        // Act
        List<LegalServiceResponse> responses = legalServiceService.getActiveServices();

        // Assert
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertTrue(responses.get(0).getActive());

        verify(serviceRepository, times(1)).findByActiveTrue();
    }

    @Test
    @DisplayName("Actualizar servicio exitosamente")
    void testUpdateService_Success() {
        // Arrange
        LegalService updatedService = LegalService.builder()
            .id(1L)
            .name("Divorcio Contencioso Actualizado")
            .description("Trámite de divorcio con contestación")
            .price(new BigDecimal("550000"))
            .active(true)
            .createdAt(testService.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
        when(serviceRepository.existsByName("Divorcio Contencioso Actualizado")).thenReturn(false);
        when(serviceRepository.save(any(LegalService.class))).thenReturn(updatedService);

        // Act
        LegalServiceResponse response = legalServiceService.updateService(1L, updateRequest);

        // Assert
        assertNotNull(response);
        assertEquals("Divorcio Contencioso Actualizado", response.getName());
        assertEquals(new BigDecimal("550000"), response.getPrice());

        verify(serviceRepository, times(1)).findById(1L);
        verify(serviceRepository, times(1)).save(any(LegalService.class));
    }

    @Test
    @DisplayName("Actualizar servicio falla cuando no existe")
    void testUpdateService_NotFound() {
        // Arrange
        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            legalServiceService.updateService(999L, updateRequest);
        });

        verify(serviceRepository, times(1)).findById(999L);
        verify(serviceRepository, never()).save(any(LegalService.class));
    }

    @Test
    @DisplayName("Actualizar servicio falla con nombre duplicado")
    void testUpdateService_DuplicateName() {
        // Arrange
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
        when(serviceRepository.existsByName("Divorcio Contencioso Actualizado")).thenReturn(true);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            legalServiceService.updateService(1L, updateRequest);
        });

        verify(serviceRepository, times(1)).findById(1L);
        verify(serviceRepository, never()).save(any(LegalService.class));
    }

    @Test
    @DisplayName("Eliminar servicio exitosamente")
    void testDeleteService_Success() {
        // Arrange
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));

        // Act
        legalServiceService.deleteService(1L);

        // Assert
        verify(serviceRepository, times(1)).findById(1L);
        verify(serviceRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Eliminar servicio falla cuando no existe")
    void testDeleteService_NotFound() {
        // Arrange
        when(serviceRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> {
            legalServiceService.deleteService(999L);
        });

        verify(serviceRepository, times(1)).findById(999L);
        verify(serviceRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Actualizar solo el nombre del servicio")
    void testUpdateService_PartialUpdate_Name() {
        // Arrange
        UpdateLegalServiceRequest partialUpdate = UpdateLegalServiceRequest.builder()
            .name("Nuevo Nombre")
            .build();

        LegalService updatedService = LegalService.builder()
            .id(1L)
            .name("Nuevo Nombre")
            .description(testService.getDescription())
            .price(testService.getPrice())
            .active(testService.getActive())
            .createdAt(testService.getCreatedAt())
            .updatedAt(LocalDateTime.now())
            .build();

        when(serviceRepository.findById(1L)).thenReturn(Optional.of(testService));
        when(serviceRepository.existsByName("Nuevo Nombre")).thenReturn(false);
        when(serviceRepository.save(any(LegalService.class))).thenReturn(updatedService);

        // Act
        LegalServiceResponse response = legalServiceService.updateService(1L, partialUpdate);

        // Assert
        assertNotNull(response);
        assertEquals("Nuevo Nombre", response.getName());
        assertEquals(testService.getPrice(), response.getPrice()); // Sin cambios

        verify(serviceRepository, times(1)).save(any(LegalService.class));
    }

    // ── SGL-018 AG-SELECT-MAT ─────────────────────────────────────────

    @Test
    @DisplayName("getPublicServices retorna solo servicios activos con campos públicos")
    void testGetPublicServices_Success() {
        when(serviceRepository.findByActiveTrue()).thenReturn(List.of(testService));

        List<ServicePublicDTO> result = legalServiceService.getPublicServices();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals("Divorcio Contencioso", result.get(0).getNombre());
        assertEquals("Trámite de divorcio con contestación", result.get(0).getDescripcion());
        assertEquals(new BigDecimal("500000"), result.get(0).getPrecio());

        verify(serviceRepository, times(1)).findByActiveTrue();
    }

    @Test
    @DisplayName("getPublicServices retorna lista vacía cuando no hay servicios activos")
    void testGetPublicServices_Empty() {
        when(serviceRepository.findByActiveTrue()).thenReturn(List.of());

        List<ServicePublicDTO> result = legalServiceService.getPublicServices();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
