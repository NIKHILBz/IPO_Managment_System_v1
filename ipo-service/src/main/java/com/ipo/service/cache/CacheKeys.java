package com.ipo.service.cache;

public class CacheKeys {

    // Cache names
    public static final String COMPANY_CACHE = "companies";
    public static final String IPO_CACHE = "ipos";
    public static final String INVESTOR_CACHE = "investors";
    public static final String INVESTMENT_CACHE = "investments";
    public static final String APPLICATION_CACHE = "applications";

    // Cache key patterns
    public static final String COMPANY_ID_KEY = "company::%d";
    public static final String COMPANY_NAME_KEY = "company::name::%s";
    public static final String IPO_ID_KEY = "ipo::%d";
    public static final String IPO_STATUS_KEY = "ipo::status::%s";
    public static final String IPO_COMPANY_KEY = "ipo::company::%d";
    public static final String INVESTOR_ID_KEY = "investor::%d";
    public static final String INVESTOR_EMAIL_KEY = "investor::email::%s";
    public static final String INVESTMENT_ID_KEY = "investment::%d";
    public static final String INVESTMENT_IPO_KEY = "investment::ipo::%d";
    public static final String INVESTMENT_INVESTOR_KEY = "investment::investor::%d";
    public static final String APPLICATION_ID_KEY = "application::%d";
    public static final String APPLICATION_NUMBER_KEY = "application::number::%s";
    public static final String APPLICATION_IPO_KEY = "application::ipo::%d";
    public static final String APPLICATION_INVESTOR_KEY = "application::investor::%d";

    // TTL values in minutes
    public static final long DEFAULT_TTL = 10;
    public static final long SHORT_TTL = 5;
    public static final long LONG_TTL = 60;
}
