package cl.sgl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Registro inmutable de cada cambio de precio de un servicio legal.
 * Se crea automáticamente cuando el admin actualiza el precio vía PATCH /precio.
 *
 * Historia: SGL-053 ADM-SERV-PRICE
 */
@Entity
@Table(name = "service_price_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServicePriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private LegalService service;

    @Column(name = "precio_anterior", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(name = "precio_nuevo", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioNuevo;

    @Column(name = "fecha_cambio", nullable = false, updatable = false)
    private LocalDateTime fechaCambio;

    @PrePersist
    protected void onCreate() {
        fechaCambio = LocalDateTime.now();
    }
}
