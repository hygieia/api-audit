package com.capitalone.dashboard.exception;

import com.capitalone.dashboard.model.AuditException;
import com.capitalone.dashboard.util.CommonConstants;
import com.capitalone.dashboard.util.ConversionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@ControllerAdvice
public class ExceptionHandlerAdvice {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandlerAdvice.class);

    private static final String API_USER_KEY = "apiUser";
    private static final String UNKNOWN_USER = "unknown";

    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleException(HttpServletRequest request, Exception e) {
        String correlation_id = request.getHeader(CommonConstants.HEADER_CLIENT_CORRELATION_ID);
        String apiUser = request.getHeader(API_USER_KEY);
        apiUser = (StringUtils.isEmpty(apiUser) ? UNKNOWN_USER : apiUser);
        String parameters = ConversionUtils.flattenMap(request.getParameterMap());

        String responseStatusMessage = ((e instanceof AuditException) ? ("Bad request: ") : "") + e.getMessage();
        int responseCode = (e instanceof AuditException) ? HttpStatus.BAD_REQUEST.value() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        HttpStatus httpStatus = (e instanceof AuditException) ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;

        LOGGER.error("correlation_id=" + correlation_id
                + ", requester=" + apiUser
                + ", application=hygieia, service=api-audit"
                + ", uri=" + request.getRequestURI()
                + ", request_method=" + request.getMethod()
                + ", response_code=" + responseCode
                + ", response_status_message=" + responseStatusMessage
                + ", response_status=failed"
                + ", client_ip=" + request.getRemoteAddr()
                + ", x-forwarded-for=" + request.getHeader("x-forwarded-for")
                + (StringUtils.equalsIgnoreCase(request.getMethod(), "GET") ? ", " + parameters : StringUtils.EMPTY));
        return ResponseEntity
                .status(httpStatus)
                .body(responseStatusMessage);
    }
}


