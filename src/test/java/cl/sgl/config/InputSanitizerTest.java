package cl.sgl.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios para InputSanitizer.
 * Historia: SGL-098 SEC-INPUT
 */
@DisplayName("InputSanitizer Tests")
class InputSanitizerTest {

    @Test
    @DisplayName("Texto plano sin HTML no se altera")
    void sanitize_textoPlano_sinCambios() {
        assertEquals("Juan Pérez González", InputSanitizer.sanitize("Juan Pérez González"));
    }

    @Test
    @DisplayName("Etiqueta <script> y su contenido son eliminados")
    void sanitize_scriptTag_contentEliminado() {
        String resultado = InputSanitizer.sanitize("<script>alert('xss')</script>");
        assertFalse(resultado.contains("<script>"), "No debe contener <script>");
        assertFalse(resultado.contains("alert("),   "El contenido del script debe eliminarse");
    }

    @Test
    @DisplayName("Etiqueta HTML normal es eliminada pero el texto se conserva")
    void sanitize_tagNormal_soloTexto() {
        assertEquals("texto en negrita", InputSanitizer.sanitize("<b>texto en negrita</b>"));
    }

    @Test
    @DisplayName("Atributo onerror en img es eliminado")
    void sanitize_imgConOnerror_eliminado() {
        String resultado = InputSanitizer.sanitize("<img src=x onerror=alert(1)>");
        assertFalse(resultado.contains("onerror"),  "No debe contener onerror");
        assertFalse(resultado.contains("<img"),     "No debe contener <img");
    }

    @Test
    @DisplayName("Input null retorna null")
    void sanitize_null_retornaNull() {
        assertNull(InputSanitizer.sanitize(null));
    }

    @Test
    @DisplayName("Inyección con javascript: en atributo es eliminada")
    void sanitize_javascriptProtocol_eliminado() {
        String resultado = InputSanitizer.sanitize("<a href=\"javascript:alert(1)\">click</a>");
        assertFalse(resultado.contains("javascript:"), "No debe contener javascript:");
    }

    @Test
    @DisplayName("String vacío retorna string vacío")
    void sanitize_stringVacio_retornaVacio() {
        assertEquals("", InputSanitizer.sanitize(""));
    }

    @Test
    @DisplayName("Texto con caracteres especiales chilenos se conserva")
    void sanitize_caracteresEspeciales_conservados() {
        String input = "María José Núñez — Santiago, Chile";
        assertEquals(input, InputSanitizer.sanitize(input));
    }
}
