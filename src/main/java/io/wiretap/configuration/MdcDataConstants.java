package io.wiretap.configuration;

public class MdcDataConstants {

    /**
     * MDC key that the logback pattern {@code #asJson{%mdc{DB-QUERY-INFO}}} reads from
     * to emit a separate {@code db_query_info} object describing the SQL query.
     */
    public static final String DB_QUERY_INFO = "DB-QUERY-INFO";
}
