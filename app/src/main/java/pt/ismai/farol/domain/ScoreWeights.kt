package pt.ismai.farol.domain

/**
 * Score weights and thresholds (section 4 of the guide). A single place for all the numbers
 * used by the evaluators in `domain/evaluators`.
 */
object ScoreWeights {
    // Maximum weight of each check (they add up to 100 for a domain).
    const val CERTIFICATE = 25
    const val DOMAIN_AGE = 20
    const val HEADERS = 20
    const val EMAIL = 20
    const val EXPOSURE = 15

    /** IP owner is informative: it never counts toward the score. */
    const val IP_OWNER = 0

    /** Scale of the final score (0..100). */
    const val SCALE = 100

    // Certificate: >30 days = maximum; 8..30 = medium; <=7 (or expired) = short; invalid = 0.
    const val CERT_DAYS_SAFE = 30
    const val CERT_DAYS_CRITICAL = 7
    const val CERT_POINTS_MEDIUM = 15
    const val CERT_POINTS_CRITICAL = 5

    // Domain age: >365 days = maximum; 90..365 = medium; 30..89 = young; <30 = 0.
    const val DAYS_PER_YEAR = 365
    const val DAYS_PER_MONTH = 30
    const val AGE_DAYS_OLD = 365
    const val AGE_DAYS_MEDIUM = 90
    const val AGE_DAYS_YOUNG = 30
    const val AGE_POINTS_MEDIUM = 12
    const val AGE_POINTS_YOUNG = 6

    // Security headers (they add up to 20).
    const val HEADER_HSTS = 6
    const val HEADER_CSP = 5
    const val HEADER_CONTENT_TYPE = 3
    const val HEADER_FRAME = 3
    const val HEADER_REFERRER = 3
    const val HEADERS_THRESHOLD_OK = 16
    const val HEADERS_THRESHOLD_WARNING = 8

    // Email anti-spoofing (they add up to 20).
    const val EMAIL_SPF = 8
    const val EMAIL_DMARC = 6
    const val EMAIL_DMARC_POLICY = 6

    // Exposure: starts at the maximum and loses points for each port beyond the web ports.
    const val EXPOSURE_PORT_PENALTY = 3
    val WEB_PORTS: Set<Int> = setOf(80, 443)
}
