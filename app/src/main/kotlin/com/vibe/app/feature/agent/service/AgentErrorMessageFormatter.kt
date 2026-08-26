package com.vibe.app.feature.agent.service

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Converts provider errors into concise messages suitable for the chat UI.
 *
 * This intentionally hides raw provider JSON, quota metadata,
 * user IDs, headers, and other technical information.
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
         * Examples:
         * free-models-per-day
         * openrouter_free_tier_daily
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
         */
        val paidBalanceError =
            normalized.contains("insufficient credits") ||
                normalized.contains("insufficient balance") ||
                normalized.contains("credit balance") ||
                normalized.contains("credits exhausted") ||
                normalized.contains("balance exhausted") ||
                normalized.contains("not enough credits") ||
                normalized.contains("payment required") ||
                normalized.contains("quota exceeded") ||
                normalized.contains("billing")

        if (paidBalanceError) {
            return localized(
                arabic = "انتهى الرصيد المدفوع. يرجى الشحن مرة أخرى.",
                english = "Paid balance exhausted. Please add credits.",
            )
        }

        /*
         * Temporary provider overload / unavailable service.
         */
        val temporaryProviderError =
            normalized.contains("temporarily overloaded") ||
                normalized.contains("temporarily unavailable") ||
                normalized.contains("upstream error") ||
                normalized.contains("service unavailable") ||
                normalized.contains("server error")

        if (temporaryProviderError) {
            return localized(
                arabic = "الخدمة مشغولة حاليًا. حاول مرة أخرى.",
                english = "The service is busy. Please try again.",
            )
        }

        /*
         * Tool-routing errors.
         */
        if (
            normalized.contains("no endpoints found that support tool use")
        ) {
            return localized(
                arabic = "النموذج الحالي لا يدعم أدوات إنشاء التطبيقات.",
                english = "This model does not support app-building tools.",
            )
        }

        /*
         * Models restricted to agentic harnesses.
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
         * HTTP 401 / authentication.
         */
        if (
            normalized.contains("\"code\":401") ||
            normalized.contains("unauthorized") ||
            normalized.contains("invalid api key") ||
            normalized.contains("invalid_api_key")
        ) {
            return localized(
                arabic = "مفتاح API غير صالح.",
                english = "Invalid API key.",
            )
        }

        /*
         * HTTP 403 / forbidden.
         */
        if (
            normalized.contains("\"code\":403") ||
            normalized.contains("forbidden") ||
            normalized.contains("access denied")
        ) {
            return localized(
                arabic = "الوصول إلى النموذج غير مسموح.",
                english = "Access to this model is not allowed.",
            )
        }

        /*
         * HTTP 404 / model or endpoint unavailable.
         */
        if (
            normalized.contains("\"code\":404") ||
            normalized.contains("model not found") ||
            normalized.contains("endpoint not found")
        ) {
            return localized(
                arabic = "النموذج غير متاح حاليًا.",
                english = "The selected model is currently unavailable.",
            )
        }

        /*
         * Generic rate limit.
         *
         * IMPORTANT:
         * Do not treat every 429 as an exhausted free balance.
         * A 429 can also be a temporary request rate limit.
         */
        if (
            normalized.contains("\"code\":429") ||
            normalized.contains("rate limit exceeded") ||
            normalized.contains("too many requests")
        ) {
            return localized(
                arabic = "تم تجاوز حد الطلبات. حاول مرة أخرى لاحقًا.",
                english = "Request limit reached. Please try again later.",
            )
        }

        /*
         * Generic fallback.
         *
         * Never expose the provider's complete JSON response.
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
        return if (
            Locale.getDefault()
                .language
                .equals("ar", ignoreCase = true)
        ) {
            arabic
        } else {
            english
        }
    }

    /**
     * Extracts OpenRouter's X-RateLimit-Reset timestamp.
     *
     * Supports both seconds and milliseconds.
     */
    private fun extractRateLimitReset(
        message: String,
    ): Long? {
        val regex = Regex(
            pattern =
                """X-RateLimit-Reset["']?\s*[:=]\s*["']?(\d{10,13})""",
            option = RegexOption.IGNORE_CASE,
        )

        val match = regex.find(message) ?: return null

        val raw = match
            .groupValues
            .getOrNull(1)
            ?.toLongOrNull()
            ?: return null

        /*
         * Unix timestamps in milliseconds are normally 13 digits.
         * Unix timestamps in seconds are normally 10 digits.
         */
        return if (raw < 10_000_000_000L) {
            raw * 1000L
        } else {
            raw
        }
    }

    private fun formatResetDate(
        timestampMillis: Long,
    ): String {
        return SimpleDateFormat(
            "dd/MM/yyyy HH:mm",
            Locale.getDefault(),
        ).format(
            Date(timestampMillis),
        )
    }
}
