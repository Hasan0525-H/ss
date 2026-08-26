package com.vibe.app.feature.agent.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts provider errors into short user-friendly messages.
 *
 * Important:
 * - We only translate errors that clearly indicate exhausted credits/quotas.
 * - We do NOT convert every HTTP 429 into "balance exhausted", because
 *   provider-side overload/rate limiting can also return 429.
 */
object AgentErrorMessageFormatter {

    fun format(message: String): String {
        if (message.isBlank()) {
            return localized(
                arabic = "حدث خطأ. حاول مرة أخرى.",
                english = "Something went wrong. Please try again.",
            )
        }

        val normalized = message.lowercase(Locale.US)

        /*
         * OpenRouter free daily model quota.
         *
         * Example:
         * "Rate limit exceeded: free-models-per-day..."
         */
        if (
            normalized.contains("free-models-per-day") ||
            normalized.contains("openrouter_free_tier_daily")
        ) {
            val resetAt = extractRateLimitReset(message)

            return if (resetAt != null) {
                val dateText = formatResetDate(resetAt)

                localized(
                    arabic = "انتهى الرصيد المجاني. يتجدد في $dateText.",
                    english = "Free quota ended. It resets on $dateText.",
                )
            } else {
                localized(
                    arabic = "انتهى الرصيد المجاني. حاول بعد التجديد.",
                    english = "Free quota ended. Try again after the reset.",
                )
            }
        }

        /*
         * Paid balance / credits exhausted.
         *
         * Do not classify ordinary upstream overload as a paid balance error.
         */
        val paidBalanceError =
            normalized.contains("insufficient credits") ||
                normalized.contains("insufficient balance") ||
                normalized.contains("credit balance") ||
                normalized.contains("credits exhausted") ||
                normalized.contains("balance exhausted") ||
                normalized.contains("not enough credits") ||
                normalized.contains("payment required")

        if (paidBalanceError) {
            return localized(
                arabic = "انتهى الرصيد المدفوع. يرجى الشحن مرة أخرى.",
                english = "Paid balance exhausted. Please add credits.",
            )
        }

        /*
         * Provider temporarily overloaded / unavailable.
         */
        val temporaryProviderError =
            normalized.contains("temporarily overloaded") ||
                normalized.contains("temporarily unavailable") ||
                normalized.contains("upstream error") ||
                normalized.contains("service unavailable")

        if (temporaryProviderError) {
            return localized(
                arabic = "الخدمة مشغولة حاليًا. حاول مرة أخرى.",
                english = "The service is busy. Please try again.",
            )
        }

        /*
         * Tool-routing errors should remain understandable but short.
         */
        if (
            normalized.contains("no endpoints found that support tool use") ||
            normalized.contains("tool use")
        ) {
            return localized(
                arabic = "النموذج الحالي لا يدعم أدوات إنشاء التطبيقات.",
                english = "This model does not support app-building tools.",
            )
        }

        /*
         * Model restricted to agentic harnesses.
         */
        if (
            normalized.contains("only available on agentic harnesses") ||
            normalized.contains("only available on agentic")
        ) {
            return localized(
                arabic = "هذا النموذج غير متاح لهذا النوع من الاستخدام.",
                english = "This model is not available for this type of use.",
            )
        }

        /*
         * Generic fallback.
         *
         * We deliberately do not expose the huge provider JSON to the user.
         */
        return localized(
            arabic = "حدث خطأ أثناء الاتصال بالذكاء الاصطناعي.",
            english = "An error occurred while contacting the AI.",
        )
    }

    private fun localized(
        arabic: String,
        english: String,
    ): String {
        return if (Locale.getDefault().language.equals("ar", ignoreCase = true)) {
            arabic
        } else {
            english
        }
    }

    /**
     * OpenRouter sends X-RateLimit-Reset as a Unix timestamp in milliseconds.
     *
     * Example:
     * X-RateLimit-Reset: 1787788800000
     */
    private fun extractRateLimitReset(message: String): Long? {
        val regex = Regex(
            pattern = """X-RateLimit-Reset["']?\s*[:=]\s*["']?(\d{10,13})""",
            option = RegexOption.IGNORE_CASE,
        )

        val match = regex.find(message) ?: return null

        val raw = match.groupValues
            .getOrNull(1)
            ?.toLongOrNull()
            ?: return null

        /*
         * Unix timestamps in OpenRouter are normally milliseconds.
         * If seconds are supplied, convert them.
         */
        return if (raw < 10_000_000_000L) {
            raw * 1000L
        } else {
            raw
        }
    }

    private fun formatResetDate(timestampMillis: Long): String {
        val locale = Locale.getDefault()

        val pattern =
            if (locale.language.equals("ar", ignoreCase = true)) {
                "dd/MM/yyyy HH:mm"
            } else {
                "dd/MM/yyyy HH:mm"
            }

        return SimpleDateFormat(pattern, locale)
            .format(Date(timestampMillis))
    }
}
