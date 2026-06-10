package com.tuempresa.stocksync.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Auditoría de seguridad: registra accesos a endpoints sensibles.
 */
@Aspect
@Component
@Slf4j
public class SecurityAuditAspect {

    @Around("@annotation(org.springframework.security.access.prepost.PreAuthorize)")
    public Object auditSecuredEndpoint(ProceedingJoinPoint joinPoint) throws Throwable {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        String method = joinPoint.getSignature().toShortString();
        String ip = getClientIp();

        log.info("[SECURITY AUDIT] User={} IP={} Action={}", username, ip, method);

        try {
            Object result = joinPoint.proceed();
            log.info("[SECURITY AUDIT] User={} Action={} Result=SUCCESS", username, method);
            return result;
        } catch (Exception e) {
            log.warn("[SECURITY AUDIT] User={} Action={} Result=FAILED Error={}",
                    username, method, e.getMessage());
            throw e;
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                return xff != null ? xff.split(",")[0].trim() : request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "unknown";
    }
}
