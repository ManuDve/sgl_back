package cl.sgl.config;

import cl.transbank.webpay.webpayplus.WebpayPlus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura el cliente WebpayPlus del SDK de Transbank.
 * En ambiente de integración se usan las credenciales de prueba.
 * Para producción, sobreescribir TRANSBANK_ENV=production y las credenciales reales.
 *
 * Historia: SGL-080 PAY-POC
 */
@Configuration
public class TransbankConfig {

    @Value("${transbank.commerce-code}")
    private String commerceCode;

    @Value("${transbank.api-key}")
    private String apiKey;

    @Value("${transbank.environment:integration}")
    private String environment;

    @Bean
    public WebpayPlus.Transaction webpayTransaction() {
        if ("production".equalsIgnoreCase(environment)) {
            return WebpayPlus.Transaction.buildForProduction(commerceCode, apiKey);
        }
        return WebpayPlus.Transaction.buildForIntegration(commerceCode, apiKey);
    }
}
