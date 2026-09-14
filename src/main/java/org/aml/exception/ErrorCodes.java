package org.aml.exception;

import org.aml.constants.AMLConstants;

public class ErrorCodes {

    public static final String USER_NOT_FOUND = AMLConstants.USER_NOT_FOUND_CODE;
    public static final String USER_ALREADY_EXISTS = AMLConstants.USER_ALREADY_EXISTS_CODE;

    public static final String INVALID_CREDENTIALS = AMLConstants.INVALID_CREDENTIALS_CODE;
    public static final String INVALID_TOKEN =  AMLConstants.INVALID_TOKEN_CODE;
    public static final String ACCESS_DENIED =  AMLConstants.ACCESS_DENIED_CODE;

    public static final String INTERNAL_SERVER_ERROR =  AMLConstants.INTERNAL_SERVER_ERROR_CODE;

    private ErrorCodes() {
    }
}