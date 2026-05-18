package cl.sgl.config;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Utilidad estática de sanitización XSS usando OWASP Java HTML Sanitizer.
 * Política: ningún HTML permitido — elimina todas las etiquetas y atributos.
 * El contenido de <script> y <style> también se elimina.
 *
 * Protección contra SQLi: JPA/Hibernate usa queries parametrizadas por defecto.
 * Esta clase cubre la capa de XSS en campos de texto libre.
 *
 * Historia: SGL-098 SEC-INPUT
 */
public final class InputSanitizer {

    private static final PolicyFactory NO_HTML = new HtmlPolicyBuilder().toFactory();

    private InputSanitizer() {}

    /**
     * Elimina todo HTML del string. Retorna null si la entrada es null.
     * El texto plano dentro de etiquetas normales se conserva;
     * el contenido de script/style se descarta.
     *
     * @param input texto a sanitizar
     * @return texto limpio sin HTML
     */
    public static String sanitize(String input) {
        if (input == null) return null;
        return NO_HTML.sanitize(input);
    }
}
