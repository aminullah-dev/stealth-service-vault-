package com.safebeauty.app.util

import com.safebeauty.app.ui.theme.AppStrings

/**
 * How a salon's rating reads to a customer.
 *
 * Ratings run 1–5, so a stored 0.0 means "nobody has reviewed this salon yet",
 * not "this salon scored zero". Printing it as "0.0" said the opposite of the
 * truth: on a marketplace whose whole promise is a trustworthy salon, every new
 * listing looked like the worst-rated one on the platform.
 */
fun ratingLabel(rating: Double, strings: AppStrings): String =
    if (rating <= 0.0) strings.ratingNew else "%.1f".format(rating)

/** True when the salon has no reviews yet, so callers can drop the star icon. */
fun isUnrated(rating: Double): Boolean = rating <= 0.0
