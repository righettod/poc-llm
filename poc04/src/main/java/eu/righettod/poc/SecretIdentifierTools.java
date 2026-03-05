package eu.righettod.poc;

import com.github.pemistahl.lingua.api.Language;
import com.github.pemistahl.lingua.api.LanguageDetector;
import com.github.pemistahl.lingua.api.LanguageDetectorBuilder;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.Tool;
import org.passay.PasswordData;
import org.passay.entropy.ShannonEntropy;
import org.passay.entropy.ShannonEntropyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Collection of TOOLS to help the identification of the type of a secret.<br/>
 * Detection patterns are just for the POC so for sure they are buggy!
 */
public class SecretIdentifierTools {

    private final Logger logger = LoggerFactory.getLogger(SecretIdentifierTools.class);

    @Tool(value = "Identify if the given secret is a Amazon AWS key identifier.", returnBehavior = ReturnBehavior.TO_LLM)
    boolean isAWSKeyID(@P(value = "secret", required = true) String secret) {
        boolean isKey = false;
        if (secret != null) {
            isKey = Pattern.matches("^(ABIA|ACCA|AGPA|AIDA|AIP[AÄ]|AKIA|ANPA|ANVA|APKA|AROA|ASCA|ASIA)[A-Z0-9]+$", secret.trim());
        }
        logger.info("[TOOLS:isAWSKeyID] Called with '{}' and returned '{}'.", secret, isKey);
        return isKey;
    }

    @Tool(value = "Identify if the given secret is a Amazon AWS key value.", returnBehavior = ReturnBehavior.TO_LLM)
    boolean isAWSKeyValue(@P(value = "secret", required = true) String secret) {
        boolean isKey = false;
        if (secret != null) {
            isKey = Pattern.matches("^[A-Za-z0-9+/=]{40}$", secret.trim());
        }
        logger.info("[TOOLS:isAWSKeyValue] Called with '{}' and returned '{}'.", secret, isKey);
        return isKey;
    }

    @Tool(value = "Identify if the given secret is a cryptographic asymmetric key.", returnBehavior = ReturnBehavior.TO_LLM)
    boolean isCryptographicAsymmetricKey(@P(value = "secret", required = true) String secret) {
        boolean isKey = false;
        if (secret != null) {
            isKey = secret.contains("PRIVATE KEY");
        }
        logger.info("[TOOLS:isCryptographicAsymmetricKey] Called with '{}' and returned '{}'.", secret, isKey);
        return isKey;
    }

    @Tool(value = "Identify if the given secret is a URL.", returnBehavior = ReturnBehavior.TO_LLM)
    boolean isURL(@P(value = "secret", required = true) String secret) {
        boolean isURL = false;
        if (secret != null) {
            try {
                URI u = new URI(secret);
                isURL = (u.toURL().getProtocol() != null);
            } catch (Exception e) {
                isURL = false;
            }
        }
        logger.info("[TOOLS:isURL] Called with '{}' and returned '{}'.", secret, isURL);
        return isURL;
    }

    @Tool(value = "Identify if the given secret is a password.", returnBehavior = ReturnBehavior.TO_LLM)
    boolean isPassword(@P(value = "secret", required = true) String secret) {
        boolean isPassword = false;
        if (secret != null) {
            PasswordData data = new PasswordData(secret);
            ShannonEntropy entropyComputer = ShannonEntropyFactory.createEntropy(false, data);
            double entropyBits = entropyComputer.estimate();
            isPassword = (entropyBits >= 4.5);
        }
        logger.info("[TOOLS:isPassword] Called with '{}' and returned '{}'.", secret, isPassword);
        return isPassword;
    }

    @Tool(value = "Identify if the given secret is a sentence.", returnBehavior = ReturnBehavior.TO_LLM)
    boolean isSentence(@P(value = "secret", required = true) String secret) {
        boolean isSentence = false;
        if (secret != null) {
            LanguageDetector detector = LanguageDetectorBuilder.fromAllLanguages().build();
            Language lang = detector.detectLanguageOf(secret);
            isSentence =!lang.name().isBlank();
        }
        logger.info("[TOOLS:isSentence] Called with '{}' and returned '{}'.", secret, isSentence);
        return isSentence;
    }

}
