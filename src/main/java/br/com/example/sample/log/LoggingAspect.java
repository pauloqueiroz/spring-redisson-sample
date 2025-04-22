package br.com.example.sample.log;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LogManager.getLogger(LoggingAspect.class);

    @Pointcut("within(@org.springframework.stereotype.Controller *) || within(@org.springframework.web.bind.annotation.RestController *)")
    public void controllerBean() {
    }

    @Pointcut("execution(* com.example..service..*(..))")
    public void serviceMethod() {
    }

    @Around("controllerBean() || serviceMethod()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String clientIP = request != null ? request.getRemoteAddr() : "N/A";

        String classType = joinPoint.getTarget().getClass().getSimpleName().contains("Controller") ? "Controller" : "Service";

        // Put into MDC (ThreadContext)
        ThreadContext.put("clientIP", clientIP);
        ThreadContext.put("classType", classType);

        String args = SensitiveDataFilter.filterSensitiveData(Arrays.toString(joinPoint.getArgs()));

        logger.info("[{}] IP={} Method={} Class={} Args={}",
                classType,
                clientIP,
                joinPoint.getSignature().getName(),
                joinPoint.getTarget().getClass().getSimpleName(),
                args);

        Object result = joinPoint.proceed();

        ThreadContext.clearMap();
        return result;
    }

}

