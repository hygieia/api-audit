package com.capitalone.dashboard.exception;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.util.CommonConstants;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;

@ControllerAdvice
public class ExceptionHandlerAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);

    private static final String API_USER_KEY = "apiUser";
    private static final String UNKNOWN_USER = "unknown";

    @ExceptionHandler(AuditException.class)
    public ResponseEntity<String> handleException(HttpServletRequest request, AuditException e) {
        String correlation_id = request.getHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID);
        String apiUser = request.getHeader(API_USER_KEY);
        apiUser = (StringUtils.isEmpty(apiUser) ? UNKNOWN_USER : apiUser);
        String parameters = MapUtils.isEmpty(request.getParameterMap())? "NONE" :
                Collections.list(request.getParameterNames()).stream()
                        .map(p -> p + ":" + Arrays.asList( request.getParameterValues(p)) )
                        .collect(Collectors.joining(","));

        String response_status_message="Bad request: " + e.getMessage();

        LOGGER.error("correlation_id=" + correlation_id
                + ", requester=" + apiUser
                + ", application=hygieia, service=api-audit"
                + ", uri=" + request.getRequestURI()
                + ", request_method=" + request.getMethod()
                + ", response_code=" + HttpStatus.BAD_REQUEST.value()
                + ", response_status_message="+response_status_message
                + ", response_status=failed"
                + ", client_ip=" + request.getRemoteAddr()
                + (StringUtils.equalsIgnoreCase(request.getMethod(), "GET") ? ", request_params="+parameters :  StringUtils.EMPTY ));
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response_status_message);
    }
}


